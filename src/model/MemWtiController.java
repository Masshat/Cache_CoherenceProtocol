package model;

import java.util.List;
import java.util.Vector;

import utils.Utile;
import model.Request.cmd_t;

/**
 * This class implements the memory controller for the WTI protocol.
 * @author QLM
 */
public class MemWtiController implements MemController {

	private enum FsmState {
		FSM_IDLE,
		FSM_WRITE_WORD,
		FSM_READ_LINE,
		FSM_DIR_UPDATE,
		FSM_INVAL,
		FSM_INVAL_SEND,
		FSM_INVAL_WAIT,
		FSM_RSP_READ,
		FSM_RSP_WRITE,
	}

	/**
	 * Initiator Index
	 */
	private int m_srcid;
	/**
	 * Number of words in a line
	 */
	private int m_words;
	/**
	 * Current cycle
	 */
	private int m_cycle;
	
	/**
	 * Srcid offset for Ram elements
	 */
	final static int memStartId = 100;

	private String m_name;

	private Ram m_ram;
	
	/**
	 * Channels
	 */
	private Channel p_in_req; // direct requests coming from the caches
	private Channel p_out_rsp; // responses to direct requests
	private Channel p_out_req; // coherence requests sent to caches
	private Channel p_in_rsp; // responses to coherence requests

	private CopiesList m_req_copies_list;
	private CopiesList m_rsp_copies_list;

	/***
	 * FSM state
	 */
	private FsmState r_fsm_state;

	/**
	 * Last direct request received from a L1 cache, written by method getRequest()
	 */
	private Request m_req;
	/**
	 * Last coherence response received from a L1 cache; written by method getResponse()  
	 */
	private Request m_rsp;
	
	/**
	 * Register used for updating the directory after sending invalidations
	 */
	private boolean r_writer_has_copy = false;
	
	private long align(long addr) {
		return (addr & ~((1 << (2 + Utile.log2(m_words))) - 1));
	}

	public MemWtiController(String name, int id, int nwords,
			Vector<Segment> seglist, Channel req_to_mem, Channel rsp_from_mem,
			Channel req_from_mem, Channel rsp_to_mem) {
		m_srcid = id + memStartId; // Id for srcid
		m_words = nwords;
		m_name = name;
		m_cycle = 0;
		p_in_req = req_to_mem;
		p_out_rsp = rsp_from_mem;
		p_out_req = req_from_mem;
		p_in_rsp = rsp_to_mem;
		m_ram = new Ram("Ram", nwords, seglist);
		for (Segment seg : seglist) {
			seg.setTgtid(m_srcid);
		}
		p_in_req.addAddrTranslation(seglist, this);
		p_in_rsp.addTgtidTranslation(m_srcid, this);
		reset();
	}

	void reset() {
		r_fsm_state = FsmState.FSM_IDLE;
		m_cycle = 0;
	}

	/**
	 * Reads and pops the next direct request from a L1 cache.
	 * The request read is placed into the m_req member structure.
	 * Must be called only if p_in_req.empty(this) == false
	 */
	private void getRequest() {
		m_req = p_in_req.front(this);
		p_in_req.popFront(this);
		System.out.println(m_name + " receives req:\n" + m_req);
	}

	/**
	 * Reads and pops the next coherence response from a L1 cache.
	 * The response read is placed into the m_rsp member structure.
	 * Must be called only if p_in_rsp.empty(this) == false
	 */
	private void getResponse() {
		m_rsp = p_in_rsp.front(this);
		p_in_rsp.popFront(this);
		System.out.println(m_name + " receives rsp:\n" + m_rsp);
	}

	/**
	 * Sends a coherence request to a L1 cache.
	 * @param addr The address of the request (e.g. address to invalidate)
	 * @param targetid srcid of the L1 cache to which send the request  
	 * @param type Type of the coherence request
	 */
	private void sendRequest(long addr, int targetid, cmd_t type) {
		Request req = new Request(addr, m_srcid, targetid, type, m_cycle, 3);
		p_out_req.pushBack(req);
		System.out.println(m_name + " sends req:\n" + req);
	}

	/**
	 * Sends a direct response to a L1 cache.
	 * @param addr The address of the request
	 * @param targetid srcid of the L1 cache to which send the response
	 * @param type Type of the response
	 * @param rdata Data associated with the response (typically, copy of a line)
	 */
	private void sendResponse(long addr, int targetid, cmd_t type, List<Long> rdata) {
		Request rsp = new Request(addr, m_srcid, targetid, type, m_cycle, 3, // max_duration
				rdata, 0xF);
		p_out_rsp.pushBack(rsp);
		System.out.println(m_name + " sends rsp:\n" + rsp);
	}

	public void simulate1Cycle() {

		switch (r_fsm_state) {
			/* Massine */
		case FSM_IDLE:
			if(! p_in_req.empty(this)){
				getRequest();
				if(m_req.getCmd()==cmd_t.READ_LINE){
					r_fsm_state= FsmState.FSM_READ_LINE;
					break;
				}
				else if(m_req.getCmd()==cmd_t.WRITE_WORD){
					r_fsm_state = FsmState.FSM_WRITE_WORD;
					break;
				}
			}
			else if(! p_in_rsp.empty(this)){
				getResponse();
				if(m_rsp.getCmd()==cmd_t.INVAL){
					r_fsm_state=FsmState.FSM_DIR_UPDATE;
				}
			}
			break;
		case FSM_READ_LINE:
			m_ram.addCopy(m_req.getAddress(), m_req.getSrcid());
			
			r_fsm_state = FsmState.FSM_RSP_READ;
			break;
		case FSM_DIR_UPDATE:
			
			if(r_writer_has_copy){
				if (m_ram.hasCopy(m_req.getAddress(), m_req.getSrcid())){
				m_ram.removeAllCopies(m_req.getAddress());
				m_ram.addCopy(m_req.getAddress(), m_req.getSrcid());
				}else{
				m_ram.removeAllCopies(m_req.getAddress());
				}
				r_fsm_state = FsmState.FSM_RSP_WRITE;
				break;
			}else{
				m_ram.removeCopy(m_rsp.getAddress(), m_rsp.getSrcid());
				r_fsm_state= FsmState.FSM_IDLE;
			}
			break;
		case FSM_RSP_READ:
			sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_READ_LINE, m_ram.getLine(m_req.getAddress()));
			r_fsm_state = FsmState.FSM_IDLE;
			break;
		case FSM_INVAL:
			m_req_copies_list=m_ram.getCopies(m_req.getAddress());
			m_rsp_copies_list = new CopiesList();
			m_req_copies_list.remove(m_req.getSrcid());
			r_writer_has_copy=true;
			r_fsm_state = FsmState.FSM_INVAL_SEND;
			break;
		case FSM_INVAL_WAIT:
			if(! p_in_rsp.empty(this)){
				getResponse();
				m_rsp_copies_list.remove(m_rsp.getSrcid());
				if(m_rsp_copies_list.nbCopies() == 0){
					r_fsm_state = FsmState.FSM_DIR_UPDATE;
				}
				}
			break;
		case FSM_INVAL_SEND:
			int next = m_req_copies_list.getNextOwner();
			do{
			Request request = new Request(m_req.getAddress(), next, this.getSrcid(),
					cmd_t.INVAL, m_cycle, 2);
			sendRequest(m_req.getAddress(), next, cmd_t.INVAL);
			m_req_copies_list.remove(next);
			m_rsp_copies_list.add(next);
			next = m_req_copies_list.getNextOwner();
			}while (next != -1);
			m_req_copies_list= new CopiesList(m_rsp_copies_list);
			r_fsm_state = FsmState.FSM_INVAL_WAIT;
			break;
		case FSM_WRITE_WORD:
			//if (m_ram.nbCopies(m_req.getAddress()) !=1){
			//r_fsm_state =FsmState.FSM_INVAL;
			//}else {
			//	r_fsm_state = FsmState.FSM_RSP_WRITE;
			//}
			m_ram.write(m_req.getAddress(), m_req.getData().get(0), m_req.getBe());
			if (m_ram.nbCopies(m_req.getAddress()) ==1 && (!m_ram.getCopies(m_req.getAddress()).hasCopy(m_req.getSrcid()))){
				r_fsm_state = FsmState.FSM_INVAL;
			}else if (m_ram.nbCopies(m_req.getAddress()) > 1 ){
				r_fsm_state = FsmState.FSM_INVAL;	
			}else{
				r_fsm_state= FsmState.FSM_RSP_WRITE;
			}
			break;
		case FSM_RSP_WRITE:
			sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_WRITE_WORD, null);
			r_fsm_state =FsmState.FSM_IDLE;
			break;
			/* Massine */
		default:
			assert (false);
			break;
		} // end switch(r_fsm_state)
		System.out.println(m_name + " next state: " + r_fsm_state);

		m_cycle++;
	}
	
	public int getSrcid() {
		return m_srcid;
	}
	
	public String getName() {
		return m_name;
	}

}
