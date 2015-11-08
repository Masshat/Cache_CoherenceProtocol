package model;

import java.util.List;
import java.util.Vector;

import utils.Utile;

import model.Ram.BlockState;
import model.Request.cmd_t;

/**
 * This class implements the memory controller for the MESI protocol.
 * @author QLM
 */
public class MemMesiController implements MemController {

	private enum FsmState {
		FSM_IDLE,
		FSM_READ_LINE,
		FSM_GETM,
		FSM_WRITE_LINE,
		FSM_WRITE_WORD,
		FSM_INVAL,
		FSM_INVAL_SEND,
		FSM_INVAL_WAIT,
		FSM_DIR_UPDATE,
		FSM_RSP_GETM,
		FSM_RSP_READ,
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
	private final static int memStartId = 100;

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
	 * Registers used for saving information from one state to another
	 */
	private boolean r_rsp_full_line;
	private boolean r_write_back;
	private cmd_t r_rsp_type;

	/**
	 * Last direct request received from a L1 cache, written by method getRequest()
	 */
	private Request m_req;
	/**
	 * Last coherence response received from a L1 cache; written by method getResponse()  
	 */
	private Request m_rsp;


	private long align(long addr) {
		return (addr & ~((1 << (2 + Utile.log2(m_words))) - 1));
	}

	public MemMesiController(String name, int id, int nwords,
			Vector<Segment> seglist, Channel req_to_mem, Channel rsp_from_mem,
			Channel req_from_mem, Channel rsp_to_mem) {
		m_srcid = id + memStartId; // id is the id among the memories
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
		r_rsp_full_line = false;
		r_rsp_type = cmd_t.NOP;
		r_write_back = false;
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
			if (!p_in_req.empty(this)){
				getRequest();
				switch (m_req.getCmd()){
				case READ_LINE:
					r_fsm_state = FsmState.FSM_READ_LINE;
					break;
				case WRITE_LINE: 
					r_fsm_state = FsmState.FSM_WRITE_LINE;
					r_rsp_type = cmd_t.INVAL_RO;
					break;
				case GETM :
					r_fsm_state = FsmState.FSM_GETM;
					r_rsp_type = cmd_t.INVAL;
					break;
				case GETM_LINE:
					r_fsm_state = FsmState.FSM_GETM;
					r_rsp_type = cmd_t.INVAL;
					break;
				}
			}
			break;
		case FSM_READ_LINE:
			if (m_ram.isExclu(m_req.getAddress()) || m_ram.isMod(m_req.getAddress())){
				r_rsp_type = cmd_t.INVAL;
				r_fsm_state = FsmState.FSM_INVAL;
			}else{
				//m_ram.addCopy(m_req.getAddress(), m_req.getSrcid());
				//r_fsm_state = FsmState.FSM_RSP_READ;
				r_fsm_state = FsmState.FSM_RSP_READ;
			}
			break;

		case FSM_RSP_READ: 
			m_ram.addCopy(m_req.getAddress(), m_req.getSrcid());
			if(m_ram.nbCopies(m_req.getAddress())==1){
				m_ram.setState(m_req.getAddress(),BlockState.EXCLUSIVE);	
			}else{
				m_ram.setState(m_req.getAddress(), BlockState.VALID);
			}
			if(m_ram.isExclu(m_req.getAddress())){
				sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_READ_LINE_EX,
						m_ram.getLine(m_req.getAddress()));
			}else{
				sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_READ_LINE,
						m_ram.getLine(m_req.getAddress()));
			}
			r_fsm_state = FsmState.FSM_IDLE;
			break;

		case FSM_WRITE_LINE:
			m_ram.writeLine(m_req.getAddress(), m_req.getData());
			r_fsm_state= FsmState.FSM_DIR_UPDATE;
			break;
		case FSM_GETM:
			if (m_ram.hasOtherCopy(m_req.getAddress(), m_req.getSrcid())){
				r_fsm_state= FsmState.FSM_INVAL;
			}else{
				r_fsm_state=FsmState.FSM_DIR_UPDATE;
			}
			break;
		case FSM_RSP_GETM:
			if(m_req.getCmd()== cmd_t.GETM){
				sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_GETM, null);
			}else{
				sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_GETM_LINE, 
						m_ram.getLine(m_req.getAddress()));
			}
			r_fsm_state= FsmState.FSM_IDLE;
			break;
		case FSM_INVAL:
			m_req_copies_list = new CopiesList(m_ram.getCopies(m_req.getAddress()));
			if (m_req.getCmd()== cmd_t.GETM || m_req.getCmd() == cmd_t.GETM_LINE) {
				m_req_copies_list.remove(m_req.getSrcid());
			}
			if (m_req_copies_list.nbCopies()==0) {
				r_fsm_state= FsmState.FSM_RSP_READ;
			}else{
				r_fsm_state= FsmState.FSM_INVAL_SEND;
				m_rsp_copies_list=new CopiesList();
			}

			break;
		case FSM_INVAL_SEND:
			if (m_req.getCmd()== cmd_t.READ_LINE) {
				int nb = m_req_copies_list.getNextOwner();
				sendRequest(m_req.getAddress(), nb, cmd_t.INVAL_RO);
				m_req_copies_list.remove(nb);
				m_rsp_copies_list.add(nb);
				if(m_req_copies_list.nbCopies() == 0) r_fsm_state=FsmState.FSM_INVAL_WAIT;
			}else{
				int nb=m_req_copies_list.getNextOwner();
				sendRequest(m_req.getAddress(), nb, cmd_t.INVAL);
				m_req_copies_list.remove(nb);
				m_rsp_copies_list.add(nb);
				m_ram.removeCopy(m_req.getAddress(), nb);
				if(m_req_copies_list.nbCopies() == 0) r_fsm_state=FsmState.FSM_INVAL_WAIT;
			}
		case FSM_INVAL_WAIT:
			if (!p_in_rsp.empty(this)) {
				getResponse();
				m_rsp_copies_list.remove(m_rsp.getSrcid());
				if(m_rsp.getCmd()== cmd_t.RSP_INVAL_DIRTY || m_rsp.getCmd()== cmd_t.RSP_INVAL_RO_DIRTY){
					if (m_req.getAddress() == m_rsp.getAddress()){
						m_ram.writeLine(m_req.getAddress(), m_rsp.getData());
						r_write_back = true;
					}
				}
				if (m_rsp_copies_list.nbCopies() == 0){
					if (m_req.getCmd() == cmd_t.READ_LINE) {
						r_fsm_state= FsmState.FSM_RSP_READ;
					}else{
						r_fsm_state = FsmState.FSM_DIR_UPDATE;
					}
				}
			}
			break;
		case FSM_DIR_UPDATE:
			if (m_req.getCmd() == cmd_t.WRITE_LINE ) {
				m_ram.removeCopy(m_req.getAddress(), m_req.getSrcid());
				sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_WRITE_LINE, m_req.getData());
				r_fsm_state = FsmState.FSM_IDLE;
			}
			if (m_req.getCmd() ==  cmd_t.GETM || m_req.getCmd() ==  cmd_t.GETM_LINE) {
				m_ram.removeAllCopies(m_req.getAddress());
				m_ram.addCopy(m_req.getAddress(), m_req.getSrcid());
				m_ram.setState(m_req.getAddress(), BlockState.EXCLUSIVE);
				r_fsm_state= FsmState.FSM_RSP_GETM;
			}
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
