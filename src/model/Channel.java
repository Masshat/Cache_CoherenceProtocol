package model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * The problem addressed by this class is to route correctly requests and responses. Direct requests are routed by address,
 * while direct responses, coherence requests and coherence responses are routed by srcid (for L1 caches) or targetid (for memory controllers).
 * The routing mode is specified in the constructor and cannot be changed; the channel either routes:
 *  - by address (m_address_routing = true)
 *  - by srcid/targetid (in that case the term targetid or tgtid is used for both)
 * However, in case of address routing, the channel must update the "tgtid" field of the request, since it is needed for
 * the graphical representation.
 * 
 * A channel has a 1-to-N topology: requests are serialized when pushed back (different writers can share the same channel),
 * and then when the request delay has passed, requests are routed towards the good output "route". This routing is made via
 * two Maps:
 *  - m_tgtid2module in case of tgtid routing (resp. m_seg2module in case of address routing) whose keys are target_id found
 *    in the request (resp. segment containing the address of the request) and values the modules connected.
 *  - m_module2chanIdx whose keys are the modules and the values the channel index. These indexes are not know from the modules
 *    and are managed internally
 * Modules try to read from a channel by passing themselves as an argument; therefore, each module must register itself to the
 * channels associated to its input ports. This registration is made via the method AddTgtidTranslation (or AddAddrTranslation)
 * @author QLM
 */
public class Channel {

	private String m_name;
	private boolean m_address_routing;
	
	private int m_cycle = 0;
	
	private Map<Long, Module> m_tgtid2module = null;
	private Map<Segment, Module> m_seg2module = null;

	private Map<Module, Integer> m_module2chanIdx = null;
	
	private List<Request> m_reqs_in;
	private Vector<List<Request>> m_reqs_out;
	private List<Request> m_finished_reqs;

	/**
	 * @param name : name of the channel
	 * @param nbOutputs : number of modules connected to the output of the channel
	 * @param addressRouting : if true, the selection of the output request list is made by address
	 *                         if false, it is made by the tgtid
	 */
	public Channel(String name, int nbOutputs, boolean addressRouting, List<Request> finishedReq) {
		m_reqs_in = new ArrayList<Request>();
		m_reqs_out = new Vector<List<Request>>();
		m_finished_reqs = finishedReq;
		m_name = name;
		m_address_routing = addressRouting;
		m_module2chanIdx = new HashMap<Module, Integer>();
		if (m_address_routing) {
			m_seg2module = new HashMap<Segment, Module>();
		}
		else {
			m_tgtid2module = new HashMap<Long, Module>();
		}
		
		for (int i = 0; i < nbOutputs; i++) {
			m_reqs_out.add(new ArrayList<Request>());
		}
	}
	
	/**
	 * Adds the translation between the Module m and the targetid specified
	 * This method must be called only for channels routing by targetid
	 * @param tgtid : target id or srcid of the module
	 * @param m : The module
	 */
	public void addTgtidTranslation(long tgtid, Module m) {
		assert(!m_address_routing);
		m_tgtid2module.put(tgtid, m);
		m_module2chanIdx.put(m, m_module2chanIdx.size());
	}
	
	/**
	 * Adds the translation between the Module m and a segment list
	 * This method must be called only for channels routing by address
	 * @param tgtid : target id or srcid of the module
	 * @param m : The module
	 */
	public void addAddrTranslation(List<Segment> seglist, Module m) {
		assert(m_address_routing);
		for (Segment seg : seglist) {
			m_seg2module.put(seg, m);
		}
		m_module2chanIdx.put(m, m_module2chanIdx.size());
	}
	

	public void simulate1Cycle() {
		for (Request req : m_reqs_in) {
			req.simulate1Cycle();
		}

		while (!m_reqs_in.isEmpty() && m_reqs_in.get(0).toPop()) {
			if (m_address_routing) {
				int idx = -1;
				for (Segment seg : m_seg2module.keySet()) {
					if (seg.contains(m_reqs_in.get(0).getAddress())) {
						Module m = m_seg2module.get(seg);
						idx = m_module2chanIdx.get(m);
						break;
					}
				}
				assert(idx != -1);
				m_reqs_out.get(idx).add(m_reqs_in.get(0));
			}
			else {
				Module m = m_tgtid2module.get((long) m_reqs_in.get(0).getTgtid());
				int idx = m_module2chanIdx.get(m);
				m_reqs_out.get(idx).add(m_reqs_in.get(0));
			}
			m_reqs_in.remove(0);
		}
		m_cycle++;
	}

	/**
	 * Adds a request at the back of the channel
	 * If the channels routes by address, updates the tgtid field
	 * of the request
	 * @param req
	 */
	public void pushBack(Request req) {
		if (m_address_routing) {
			updateTgtid(req);
		}
		m_reqs_in.add(req);
	}

	
	/**
	 * Updates the tgtid field of a request, i.e. the targetid of the memory controller
	 * owning the target address of the request. 
	 * @param req
	 */
	private void updateTgtid(Request req) {
		assert(req.getTgtid() == -1);
		int tgtid = -1;
		for (Segment seg : m_seg2module.keySet()) {
			if (seg.contains(req.getAddress())) {
				tgtid = seg.getTgtid();
				break;
			}
		}
		req.updateTgtid(tgtid);
	}
	
	
	/**
	 * Pops the first request of the output port connected to the module m
	 * @param m
	 */
	void popFront(Module m) {
		int numOutput = m_module2chanIdx.get(m);
		Request req = m_reqs_out.get(numOutput).get(0);
		if (!req.addedToFinishedReqs) {
			req.addToFinishedReqs(m_cycle);
			m_finished_reqs.add(req);
		}
		m_reqs_out.get(numOutput).remove(0);
	}
	

	/**
	 * @param m the Reader Module
	 * @return the first Request in the output port connected to the module m
	 */
	Request front(Module m) {
		int numOutput = m_module2chanIdx.get(m);
		return m_reqs_out.get(numOutput).get(0);
	}

	
	/**
	 * @param m 
	 * @return true if the output port connected to m contains at least a request, false otherwise
	 */
	boolean empty(Module m) {
		int numOutput = m_module2chanIdx.get(m);
		return m_reqs_out.get(numOutput).isEmpty();
	}
	
	
	public void addToFinishedReqs(Module m) {
		int numOutput = m_module2chanIdx.get(m);
		Request req = m_reqs_out.get(numOutput).get(0);
		if (!req.addedToFinishedReqs) {
			req.addToFinishedReqs(m_cycle);
			m_finished_reqs.add(req);
		}
	}

	public String toString() {
		String res;
		res = "Contenu du channel " + m_name + ":\n";
		for (Request req : m_reqs_in) {
			res += req;
		}
		return res;
	}


}
