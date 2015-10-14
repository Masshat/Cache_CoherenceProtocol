package topcells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import model.Channel;
import model.L1Controller;
import model.L1MesiController;
import model.L1WtiController;
import model.MemController;
import model.MemMesiController;
import model.MemWtiController;
import model.Module;
import model.Processor;
import model.Request;
import model.Segment;

/**
 * Topcell containing two processors and one memory bank.
 * The processorPerSrcid is somehow redundant with the processors vector, but it leverages the
 * constraint of respecting index == srcid
 * @author QLM
 *
 */
public class Top2Procs2Rams implements Topcell {

	private int nb_procs = 2;
	private int nb_rams = 2;
	private int nways = 1;
	private int nwords = 8;
	private int nsets = 16;
	private int cycle = 0;

	private Segment mem_seg0;
	private Segment mem_seg1;

	private Vector<Segment> seg_list0 = new Vector<Segment>();
	private Vector<Segment> seg_list1 = new Vector<Segment>();
	
	private Vector<L1Controller> l1_caches;
	private Vector<Processor> processors; 
	private MemController mem0;
	private MemController mem1;

	private Vector<Channel> iss_l1_req;
	private Vector<Channel> l1_iss_rsp;

	private Channel l1_mem_req;
	private Channel mem_l1_rsp;

	private Channel mem_l1_req;
	private Channel l1_mem_rsp;
	
	private List<Request> finishedCacheRequests = new ArrayList<Request>();
	private List<Request> finishedProcRequests = new ArrayList<Request>();

	private List<Module> moduleList = new ArrayList<Module>();
	
	private Map<Integer, Processor> processorPerSrcid = new HashMap<Integer, Processor>();
	
	public Top2Procs2Rams() {

		mem_seg0 = new Segment("mem_seg0", 0x0, 0x1000000, true);
		seg_list0.add(mem_seg0);
		
		mem_seg1 = new Segment("mem_seg1", 0x10000000, 0x1000000, true);
		seg_list1.add(mem_seg1);

		iss_l1_req = new Vector<Channel>();
		l1_iss_rsp = new Vector<Channel>();

		l1_mem_req = new Channel("l1_mem_req", nb_rams, true, finishedCacheRequests);
		mem_l1_rsp = new Channel("mem_l1_rsp", nb_procs, false, finishedCacheRequests);

		mem_l1_req = new Channel("mem_l1_req", nb_procs, false, finishedCacheRequests);
		l1_mem_rsp = new Channel("l1_mem_rsp", nb_rams, false, finishedCacheRequests);

		l1_caches = new Vector<L1Controller>(nb_procs);
		processors = new Vector<Processor>(nb_procs);
		for (int i = 0; i < nb_procs; i++) {
			Channel iss_l1 = new Channel("iss_l1_req_" + i, 1, false, finishedProcRequests);
			Channel l1_iss = new Channel("l1_iss_rsp_" + i, 1, false, finishedProcRequests);
			iss_l1_req.add(iss_l1);
			l1_iss_rsp.add(l1_iss);
			
			L1Controller l1Ctrl = new L1WtiController("L1 controller " + i, i, nways,
					nsets, nwords, l1_mem_req, mem_l1_rsp, mem_l1_req,
					l1_mem_rsp, iss_l1, l1_iss);
			l1_caches.add(l1Ctrl);
			
			Processor proc = new Processor("Processor " + i, i, iss_l1, l1_iss);
			processors.add(proc);
			processorPerSrcid.put(i, proc); // i = srcid
		}
		
		mem0 = new MemWtiController("Mem controller 0", 0, // ram_id
					nwords, seg_list0, l1_mem_req, mem_l1_rsp, mem_l1_req,
					l1_mem_rsp);
		
		mem1 = new MemWtiController("Mem controller 1", 1, // ram_id
				nwords, seg_list1, l1_mem_req, mem_l1_rsp, mem_l1_req,
				l1_mem_rsp);
	
		
		// Creating moduleList with a given order
		moduleList.add(processors.get(0));
		moduleList.add(l1_caches.get(0));
		moduleList.add(mem0);
		moduleList.add(mem1);
		moduleList.add(l1_caches.get(1));
		moduleList.add(processors.get(1));

		// Load requests (example)
		
//		processors.get(0).addWrite(0x00400000, 11);
//		processors.get(0).addRead (0x00400000);
//		processors.get(0).addRrite(0x00400008, 12);
//		processors.get(0).addRead (0x00400008);
//		processors.get(0).addWrite(0x00400010, 13);
//		processors.get(0).addRead (0x00400010);
//		processors.get(0).addWrite(0x00400018, 14);
//		processors.get(0).addRead (0x00400018);
//		
//		
//		processors.get(1).addWrite(0x00400004, 1);
//		processors.get(1).addRead (0x00400004);
//		processors.get(1).addWrite(0x0040000C, 2);
//		processors.get(1).addRead (0x0040000C);
//		processors.get(1).addWrite(0x00400014, 3);
//		processors.get(1).addRead (0x00400014);
//		processors.get(1).addWrite(0x0040001C, 4);
//		processors.get(1).addRead (0x0040001C);
		
	}

	public void simulate1Cycle() {
		// Simulate
		System.out.println("*** cycle " + cycle + " ***");

		for (int i = 0; i < nb_procs; i++) {
			processors.get(i).simulate1Cycle();
			
		}
		for (int i = 0; i < nb_procs; i++) {
			l1_caches.get(i).simulate1Cycle();
		}
		
		mem0.simulate1Cycle();
		mem1.simulate1Cycle();
		
		// Simulate Channels last
		for (int i = 0; i < nb_procs; i++) {
			iss_l1_req.get(i).simulate1Cycle();
			l1_iss_rsp.get(i).simulate1Cycle();
		}
		
		l1_mem_req.simulate1Cycle();
		mem_l1_rsp.simulate1Cycle();

		mem_l1_req.simulate1Cycle();
		l1_mem_rsp.simulate1Cycle();
		
		cycle++;
	}
	
	public int getNbProcs() {
		return nb_procs;
	}
	
	public int getNbMem() {
		return nb_rams;
	}
	
	public int getNbCycles() {
		return cycle;
	}
	
	public List<Request> getFinishedCacheRequests() {
		return finishedCacheRequests;
	}
	
	public List<Request> getFinishedProcsRequests() {
		return finishedProcRequests;
	}
	
	public List<Module> getAllModules() {
		return moduleList;
	}
	
	public Processor getProcessor(int srcid) {
		return processorPerSrcid.get(srcid);
	}
}
