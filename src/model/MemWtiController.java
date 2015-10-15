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
			/* To complete */
			
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
