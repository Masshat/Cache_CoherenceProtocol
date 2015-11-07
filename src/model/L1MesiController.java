package model;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.List;

import utils.Utile;

import model.LineState.cacheSlotState;
import model.Request.cmd_t;

/**
 * This class implements a L1 MESI controller. The l1StartId purpose is to make a correspondence between the processor srcid,
 * ranging from 0 to nb_caches - 1, and the srcid on the network.
 * 
 * @author QLM
 */
public class L1MesiController implements L1Controller {
	
	/**
	 *  Offset for L1 caches srcid 
	 */
	static final int l1StartId = 10;
	
	
	private enum FsmState {
		FSM_IDLE,
		FSM_WRITE_UPDATE,
		FSM_MISS,
		FSM_MISS_WAIT,
		FSM_WRITE_BACK,
		FSM_INVAL,
	}
	
	/**
	 * Global initiator and target index
	 */
	private int r_srcid;
	/**
	 * srcid of the associated processor
	 */
	private int r_procid;
	
	/**
	 * Registers for saving information between states
	 */
	private boolean r_ignore_rsp; // ignore next response when receiving it
	private cmd_t r_cmd_req;
	private long r_wb_addr; // write-back address
	private List<Long> r_wb_buf; // write-back buffer
	private boolean r_rsp_miss_ok; // response to the miss has been received (set by "rsp_fsm")
	private boolean r_current_wb; // true if a write-back is currently being done; there can be only one at a time
	
	/**
	 * Number of words in a line
	 */
	private int m_words;
	/**
	 * Current cycle
	 */
	private int m_cycle;
	
	private String m_name;
	
	private CacheL1 m_cache_l1;
	
	/**
	 * Channels
	 */
	private Channel p_in_req; // incoming coherence requests from ram
	private Channel p_out_rsp; // outgoing coherence responses to ram
	private Channel p_out_req; // outgoing direct requests to ram
	private Channel p_in_rsp; // incoming direct responses from ram
	private Channel p_in_iss_req; // incoming processor requests
	private Channel p_out_iss_rsp; // outgoing processor responses
	
	private FsmState r_fsm_state;
	private FsmState r_fsm_prev_state;
	
	/**
	 * Last coherence request received from the ram, written by method getRequest()
	 */
	private Request m_req;
	/**
	 * Last direct response received from the ram, written by method getResponse()
	 */
	private Request m_rsp;
	/**
	 * Last processor request received from the ram, written by method getIssRequest()
	 */
	private Request m_iss_req;
	
	
	private long align(long addr) {
		return (addr & ~((1 << (2 + Utile.log2(m_words))) - 1));
	}
	

	public L1MesiController(String name, int procid, int nways, int nsets, int nwords, Channel req_to_mem, Channel rsp_from_mem, Channel req_from_mem,
			Channel rsp_to_mem, Channel req_from_iss, Channel rsp_to_iss) {
		r_procid = procid;
		r_srcid = l1StartId + procid;
		m_words = nwords;
		m_name = name;
		m_cycle = 0;
		p_in_req = req_from_mem;
		p_out_rsp = rsp_to_mem;
		p_out_req = req_to_mem;
		p_in_rsp = rsp_from_mem;
		p_in_iss_req = req_from_iss;
		p_out_iss_rsp = rsp_to_iss;
		m_cache_l1 = new CacheL1("CacheL1", procid, nways, nsets, nwords);
		p_in_req.addTgtidTranslation(r_srcid, this); // Associate the component to its srcid for the channel
		p_in_rsp.addTgtidTranslation(r_srcid, this);
		p_in_iss_req.addTgtidTranslation(r_srcid, this); // the channel index is 0 since the processor is connected to a single L1
		reset();
	}
	

	void reset() {
		r_fsm_state = FsmState.FSM_IDLE;
		r_fsm_prev_state = FsmState.FSM_IDLE;
		r_ignore_rsp = false;
		r_cmd_req = cmd_t.NOP;
		r_wb_addr = 0;
		r_wb_buf = null;
		r_rsp_miss_ok = false;
		r_current_wb = false;
		m_cycle = 0;
	}
	
	
	/**
	 * Reads the next processor request.
	 * The request read is placed into the m_iss_req member structure.
	 * Must be called only if p_in_iss_req.empty(this) == false
	 * Note: This function can be called twice for the same request
	 * (it does not consumes the request)
	 * so addToFinishedReqs can be called twice.
	 */
	private void getIssRequest() {
		m_iss_req = p_in_iss_req.front(this);
		m_iss_req.setStartCycle(m_cycle); // Must be done here since proc requests can be added before simulation starts
		p_in_iss_req.addToFinishedReqs(this);
		System.out.println(m_name + " gets:\n" + m_iss_req);
	}
	
	
	/**
	 * Sends a response to the processor and consumes the request in
	 * p_in_iss_req.
	 * @param addr The address of the reponse
	 * @param type Type of the response
	 * @param data Data value if the type of the response is RSP_READ_WORD
	 */
	private void sendIssResponse(long addr, cmd_t type, long data) {
		p_in_iss_req.popFront(this); // remove request from channel
		List<Long> l = new ArrayList<Long>();
		l.add(data);
		Request req = null;
		if (type == cmd_t.RSP_WRITE_WORD) {
			req = new Request(addr, r_srcid, // srcid
					r_procid, // targetid (srcid of the proc)
					type, // cmd
					m_cycle, // start cycle
					0); // max duration
		}
		else if (type == cmd_t.RSP_READ_WORD) {
			req = new Request(addr, r_srcid, // srcid
					r_procid, // targetid (srcid of the proc)
					type, // cmd
					m_cycle, // start cycle
					0, // max_duration
					l, // data
					0xF); // be
		}
		else {
			assert (false);
		}
		
		p_out_iss_rsp.pushBack(req);
	}
	

	/**
	 * Reads and pops the next coherence request from a ram.
	 * The request read is placed into the m_req member structure.
	 * Must be called only if p_in_req.empty(this) == false
	 */
	private void getRequest() {
		m_req = p_in_req.front(this);
		assert (m_req.getNwords() == 0);
		p_in_req.popFront(this);
		System.out.println(m_name + " gets req:\n" + m_req);
	}
	

	/**
	 * Reads and pops the next direct response from a ram.
	 * The response read is placed into the m_rsp member structure.
	 * Must be called only if p_in_rsp.empty(this) == false
	 */
	private void getResponse() {
		m_rsp = p_in_rsp.front(this);
		p_in_rsp.popFront(this);
		System.out.println(m_name + " gets rsp:\n" + m_rsp);
	}
	

	/**
	 * Sends a request on a full line
	 * @param addr Address of the request
	 * @param type Type of the request
	 * @param data Values to update if appropriated, null otherwise
	 */
	private void sendRequest(long addr, cmd_t type, List<Long> rdata) {
		Request req = new Request(addr, r_srcid, -1, type, m_cycle, 3, rdata, 0xF);
		p_out_req.pushBack(req);
		System.out.println(m_name + " sends req:\n" + req);
	}
	

	/**
	 * Sends a request on a word
	 * @param addr Address of the request
	 * @param type Type of the request
	 * @param wdata Value to write if any
	 * @param be Byte Enable in case of write
	 */
	private void sendResponse(long addr, int tgtid, cmd_t type, List<Long> rdata) {
		Request rsp = new Request(addr, r_srcid, tgtid, type, m_cycle, 3, rdata, 0xF);
		p_out_rsp.pushBack(rsp);
		System.out.println(m_name + " sends rsp:\n" + rsp);
	}
	

	public void simulate1Cycle() {
		
		switch (r_fsm_state) {
			/* Massine */
		case FSM_IDLE:
			r_fsm_prev_state= FsmState.FSM_IDLE;
			if (! p_in_iss_req.empty(this)){
				getIssRequest();
				if (m_iss_req.getCmd()==cmd_t.READ_WORD){
					LineState state = new LineState();
					List<Long> data = new ArrayList<Long>();
					if (m_cache_l1.read(m_iss_req.getAddress(), data, state)){
						sendIssResponse(m_iss_req.getAddress(), cmd_t.RSP_READ_WORD, data.get(0));
					}else{
						r_fsm_state = FsmState.FSM_MISS;
						break;
					}
				}
				if (m_iss_req.getCmd()==cmd_t.WRITE_WORD){
					LineState state = new LineState();
					m_cache_l1.readDir(m_iss_req.getAddress(), state);
					if (state.exclu){
						r_fsm_prev_state=FsmState.FSM_IDLE;
						sendIssResponse(m_iss_req.getAddress(), cmd_t.RSP_WRITE_WORD, 0);
						state.dirty= true;
						m_cache_l1.writeDir(m_iss_req.getAddress(), state);
						m_cache_l1.write(m_iss_req.getAddress(), m_iss_req.getData().get(0), m_iss_req.getBe());
				
						break;

					}else{
						r_fsm_prev_state=FsmState.FSM_IDLE;
						r_fsm_state=FsmState.FSM_MISS;
						break;
					}
				}
					
			}
			if (! p_in_req.empty(this)){
				getRequest();
				if(m_req.getCmd()==cmd_t.INVAL){
					r_fsm_prev_state=FsmState.FSM_IDLE;
					r_fsm_state=FsmState.FSM_INVAL;
					break;
				}
			}
		case FSM_MISS:
			r_fsm_prev_state=FsmState.FSM_MISS;
			LineState state = new LineState();
			CacheAccessResult result;
			
			if (r_cmd_req == cmd_t.READ_WORD){
				result=m_cache_l1.readSelect(m_iss_req.getAddress());
				if(!result.victimDirty){
				sendRequest(m_iss_req.getAddress(), cmd_t.READ_LINE, null);
				r_cmd_req = cmd_t.READ_LINE;
				r_fsm_state = FsmState.FSM_MISS_WAIT;
			}else{
					r_wb_addr = m_iss_req.getAddress();
					r_wb_buf = m_iss_req.getData();
					r_fsm_state= FsmState.FSM_WRITE_BACK;
				}
				
			}else{
				 m_cache_l1.readDir(m_iss_req.getAddress(), state);
				if(state.state == cacheSlotState.VALID){
					sendRequest(m_iss_req.getAddress(), cmd_t.GETM, null);
					r_cmd_req= cmd_t.GETM;
					r_fsm_state = FsmState.FSM_MISS_WAIT;
				}else{
					result=m_cache_l1.readSelect(m_iss_req.getAddress());
					if (result.victimDirty){
						r_wb_addr = m_iss_req.getAddress();
						r_wb_buf = m_iss_req.getData();
						r_fsm_state = FsmState.FSM_WRITE_BACK;
					}else{
						sendRequest(m_iss_req.getAddress(), cmd_t.GETM_LINE, null);
						r_cmd_req = cmd_t.GETM_LINE;
						r_fsm_state=FsmState.FSM_MISS_WAIT;
					}
				}
			}
				break;
		case FSM_MISS_WAIT:
			r_fsm_prev_state=FsmState.FSM_MISS_WAIT;
			if(r_rsp_miss_ok){
				if (m_rsp.getCmd() == cmd_t.RSP_READ_LINE_EX){
				m_cache_l1.setLine(m_iss_req.getAddress(), m_rsp.getData(), true);
				}else{
					if (m_rsp.getCmd() == cmd_t.RSP_READ_LINE){
						m_cache_l1.setLine(m_iss_req.getAddress(), m_rsp.getData(), false);
					}else{
						m_cache_l1.setLine(m_iss_req.getAddress(), m_rsp.getData(), true);
					}
				}
				r_rsp_miss_ok=false;
				if(r_cmd_req==cmd_t.GETM || r_cmd_req == cmd_t.GETM_LINE){
					r_fsm_prev_state=FsmState.FSM_MISS_WAIT;
					r_fsm_state= FsmState.FSM_WRITE_UPDATE;
				}else{
					sendIssResponse(m_iss_req.getAddress(), cmd_t.RSP_READ_WORD, 
							m_rsp.getData().get(((int)m_iss_req.getAddress() % m_words)));
				r_fsm_state = FsmState.FSM_IDLE;	
				}
			}
			
			if (p_in_req.empty(this)) {
				getRequest();
				if(m_req.getCmd()==cmd_t.INVAL){
					r_fsm_state=FsmState.FSM_INVAL;
				}
			}
		case FSM_WRITE_UPDATE:
			r_fsm_prev_state=FsmState.FSM_WRITE_UPDATE;
			if (true) {
				LineState state_update = new LineState();
				//state_update.exclu = false;
				m_cache_l1.readDir(m_iss_req.getAddress(), state_update);
				state_update.dirty = true;
				m_cache_l1.writeDir(m_iss_req.getAddress(), state_update);
				sendIssResponse(m_iss_req.getAddress(), cmd_t.RSP_WRITE_WORD, 
						1);
				r_fsm_state=FsmState.FSM_IDLE;	
			}
		case FSM_INVAL:
			LineState state_inval =  new LineState();
			List<Long> data = new ArrayList<Long>();
			m_cache_l1.read(m_req.getAddress(), data,state_inval);
			if (m_req.getCmd() == cmd_t.INVAL){
				m_cache_l1.inval(m_req.getAddress(), true);
				if(state_inval.dirty){
				
					sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_INVAL_CLEAN, data);
				}else{
					sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_INVAL_DIRTY, data);
				}
					
			}else{ //INVAL_RO
				m_cache_l1.inval(m_req.getAddress(), false);
				
				if (state_inval.dirty){
					sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_INVAL_RO_DIRTY, data);
				}else{
					sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_INVAL_RO_CLEAN, data);
				}
			}
			r_fsm_state = r_fsm_prev_state;
			r_fsm_prev_state= FsmState.FSM_INVAL;
		case FSM_WRITE_BACK:
			r_fsm_prev_state = FsmState.FSM_WRITE_BACK;
			if(r_current_wb){
				//NOP
			}else{
				r_current_wb = true;
				sendRequest(r_wb_addr, cmd_t.WRITE_LINE, r_wb_buf);
				m_cache_l1.inval(r_wb_addr, true);
				r_fsm_state = FsmState.FSM_MISS;
			}
			/* Massine */
		
		default:
			assert (false);
			break;
		}
		
		System.out.println(m_name + " next state: " + r_fsm_state);
		
		// Following code equivalent to a 1-state FSM executing in parallel
		if (!p_in_rsp.empty(this)) {
			getResponse();
			if (m_rsp.getCmd() == cmd_t.RSP_READ_LINE || m_rsp.getCmd() == cmd_t.RSP_READ_LINE_EX || m_rsp.getCmd() == cmd_t.RSP_GETM ||
					m_rsp.getCmd() == cmd_t.RSP_GETM_LINE) {
				r_rsp_miss_ok = true;
			}
			else if (m_rsp.getCmd() == cmd_t.RSP_WRITE_LINE) {
				r_current_wb = false;
			}
			else {
				assert (false);
			}
		}
		m_cycle++;
	}
	

	public int getSrcid() {
		return r_srcid;
	}
	
	public String getName() {
		return m_name;
	}
}