package topcells;

import java.util.List;

import model.Module;
import model.Processor;
import model.Request;

/**
 * Simple interface to be able to manage different topcells in the controller
 * @author QLM
 *
 */
public interface Topcell {

	public void simulate1Cycle();
	
	public int getNbProcs();
	
	public int getNbMem();
	
	public int getNbCycles();
	
	public List<Request> getFinishedCacheRequests();
	public List<Request> getFinishedProcsRequests();
	
	public List<Module> getAllModules();
	public Processor getProcessor(int srcid);
	
}
