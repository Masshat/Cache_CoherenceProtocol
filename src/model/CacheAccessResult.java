package model;

import java.util.List;

/**
 * A class to aggregate the results of a cache access (from cache controller to cache)
 * @author QLM
 *
 */
public class CacheAccessResult {
	boolean found;
	boolean victimFound;
	long victimAddress;
	boolean victimDirty;
	List<Long> data;
}
