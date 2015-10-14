package model;

/**
 * A simple interface to be able to manipulate different components of the
 * topcell in the same way.
 * @author QLM
 *
 */
public interface Module {

	public int getSrcid();
	
	public String getName();
	
	public void simulate1Cycle();
	
}
