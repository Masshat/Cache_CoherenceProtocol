package model;

/**
 * An object of this class represents the full state of a line in a cache
 * "ZOMBI" possesses no 'E' to have the same number of letters.
 * @author QLM
 *
 */
public class LineState {

	public enum cacheSlotState {
		EMPTY, VALID, ZOMBI,
	}

	cacheSlotState state;
	boolean dirty;
	boolean exclu;
	
}

