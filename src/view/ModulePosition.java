package view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import model.Module;

/** 
 * This class is a part of the view package, because the information it contains
 * is relative to the positioning of the procs/caches/memory in the chronogram.
 * Basically, it makes a link between modules (and their srcid) and a position
 * on the screen.
 * The positions added must range from 0 to N-1 for N components to display.
 * @author QLM
 * 
 */
public class ModulePosition {

	private SortedMap<Integer, Module> index2module = new TreeMap<Integer, Module>();
	private Map<Integer, Module> tgtid2module = new HashMap<Integer, Module>();
	private Map<Module, Integer> module2index = new HashMap<Module, Integer>();
	
	public void addModulePosition(Module m, Integer p) {
		assert(!index2module.containsKey(p));
		assert(!module2index.containsKey(m));
		assert(!tgtid2module.containsKey(m.getSrcid()));
		//System.out.println("Adding in " + this + " assoc (" + m.getSrcid() + ", " + p + ")");
		index2module.put(p, m);
		tgtid2module.put(m.getSrcid(), m);
		module2index.put(m, p);
	}
	
	public int getModuleIndex(int tgtid) {
		assert(tgtid2module.containsKey(tgtid));
		Module m = tgtid2module.get(tgtid);
		assert(module2index.containsKey(m));
		return module2index.get(m);
	}

	public List<Module> getOrderedModules() {
		return new ArrayList<Module>(index2module.values());
	}

}
