package model;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import model.LineState.cacheSlotState;

import utils.Utile;

/**
 * This class models the data and metadata parts of the L1 cache.
 * Partially taken from soclib.
 * @author QLM
 */
public class CacheL1 {

	private long [] r_data;
	private long [] r_tag;
	private cacheSlotState [] r_state;
	private boolean [] r_lru;
	private boolean [] r_dirty;
	private boolean [] r_exclu;
	
	private int traceLevel = 1;

	int m_id; // @QM unused
	private int m_ways;
	private int m_sets;
	private int m_words;

	private AddressMaskingTable m_x;
	private AddressMaskingTable m_y;
	private AddressMaskingTable m_z;

	private Long data(int way, long set, long word) {
		return r_data[(way * m_sets * m_words) + ((int) set * m_words) + (int) word];
	}

	private void setData(int way, long set, long word, long val) {
		r_data[(way * m_sets * m_words) + ((int) set * m_words) + (int) word] = val;
	}

	private Long tag(int way, long set) {
		return r_tag[(way * m_sets) + (int) set];
	}

	private void setTag(int way, long set, long val) {
		r_tag[(way * m_sets) + (int) set] = val;
	}

	private Boolean lru(int way, long set) {
		return r_lru[(way * m_sets) + (int) set];
	}

	private void setLru(int way, long set, boolean val) {
		r_lru[(way * m_sets) + (int) set] = val;
	}

	private Boolean dirty(int way, long set) {
		return r_dirty[(way * m_sets) + (int) set];
	}

	private void setDirty(int way, long set, boolean val) {
		r_dirty[(way * m_sets) + (int) set] = val;
	}

	private Boolean exclu(int way, long set) {
		return r_exclu[(way * m_sets) + (int) set];
	}

	private void setExclu(int way, long set, boolean val) {
		r_exclu[(way * m_sets) + (int) set] = val;
	}

	private cacheSlotState state(int way, long set) {
		return r_state[(way * m_sets) + (int) set];
	}

	private void setState(int way, long set, cacheSlotState val) {
		r_state[(way * m_sets) + (int) set] = val;
	}

	private void setCacheLru(int way, long set) {
		int way2;

		setLru(way, set, true);

		for (way2 = 0; way2 < m_ways; way2++) {
			if (lru(way2, set) == false) {
				return;
			}
		}
		// if all lines are new, they all become old
		for (way2 = 0; way2 < m_ways; way2++) {
			setLru(way2, set, false);
		}
	}

	CacheL1(String name, int id, int nways, int nsets, int nwords) {
		
		this.m_id = id;

		this.m_ways = nways;
		this.m_sets = nsets;
		this.m_words = nwords;

		m_x = new AddressMaskingTable(Utile.log2(nwords), 2); // 2 = log2(sizeof(word))
		m_y = new AddressMaskingTable(Utile.log2(nsets), Utile.log2(nwords) + 2);
		m_z = new AddressMaskingTable(8 * 8 - Utile.log2(nsets)
				- Utile.log2(nwords) - 2, Utile.log2(nsets)
				+ Utile.log2(nwords) + 2);
		assert(Utile.isPowerOf2(nways));
		assert(Utile.isPowerOf2(nsets));
		assert(Utile.isPowerOf2(nwords));
		assert(nwords <= 16);
		assert(nsets <= 1024);
		assert(nways <= 4);

		if (traceLevel > 4) {
			System.out.println("constructing " + name);
			System.out.println(" - nways  = " + nways);
			System.out.println(" - nsets  = " + nsets);
			System.out.println(" - nwords = " + nwords);
			System.out.println(" m_x: " + m_x);
			System.out.println(" m_y: " + m_y);
			System.out.println(" m_z: " + m_z);
			System.out.println();
		}
		r_data = new long[nways * nsets * nwords];
		r_tag = new long[nways * nsets];
		r_state = new cacheSlotState[nways * nsets];
		r_lru = new boolean[nways * nsets];
		r_dirty = new boolean[nways * nsets];
		r_exclu = new boolean[nways * nsets];
		
		reset();
	}

	public void reset() {
		for (int way = 0; way < m_ways; way++) {
			for (int set = 0; set < m_sets; set++) {
				for (int word = 0; word < m_words; word++) {
					setData(way, set, word, 0);
				}
				setTag(way, set, 0);
				setState(way, set, cacheSlotState.EMPTY);
				setLru(way, set, false);
				setDirty(way, set, false);
				setExclu(way, set, false);
			}
		}
	}

	boolean isSameLine(long ad1, long ad2) {
		long tag1 = m_z.get(ad1);
		long tag2 = m_z.get(ad2);
		long set1 = m_y.get(ad1);
		long set2 = m_y.get(ad2);
		return (tag1 == tag2 && set1 == set2);
	}
	

	/**
	 * Reads a single 32-bit word when the ZOMBIE state is used.
	 * Both data and directory are accessed.
	 * @param state The access status updated by this method:
	 *  - VALID : (matching tag) and (state == VALID)
	 *  - ZOMBIE : (matching tag) and (state == ZOMBIE)
	 *  - MISS : no matching tag or EMPTY state
	 *  @param ad The address to read
	 *  @param dt The data read, updated by this function (should be an empty list)
	 *  @return true if VALID or ZOMBIE, false otherwise
	 */
	boolean read(long ad, List<Long> dt, LineState state) {
		long tag = m_z.get(ad);
		long set = m_y.get(ad);
		long word = m_x.get(ad);
		//System.out.println("   read ad = 0x" + Long.toHexString(ad) + " - word = " + word);

		// default return values
		state.state = cacheSlotState.EMPTY;
		dt.clear();

		for (int way = 0; way < m_ways; way++) {
			if (tag == tag(way, set)) {
				if (state(way, set) == cacheSlotState.VALID) {
					state.state = cacheSlotState.VALID;
					state.dirty = dirty(way, set);
					state.exclu = exclu(way, set);
					dt.add(data(way, set, word));
					setCacheLru(way, set);
					return true;
				}
				else if (state(way, set) == cacheSlotState.ZOMBI) {
					state.state = cacheSlotState.ZOMBI;
					return true;
				}
			}
		}
		return false;
	}


	/**
	 * Updates the data part of the cache.
	 * The value written can be a word or less, specified by the be parameter
	 * @param ad The address to write
	 * @param dt The data to write
	 * @param be Bytes to write
	 */
	void write(long ad, long dt, int be) {
		long tag = m_z.get(ad);
		long set = m_y.get(ad);
		long word = m_x.get(ad);

		for (int way = 0; way < m_ways; way++) {
			if ((tag == tag(way, set)) && (state(way, set) == cacheSlotState.VALID)) {
				assert (exclu(way, set));
				long mask = Utile.be2mask(be);
				long prev = data(way, set, word);
				setData(way, set, word, (mask & dt) | (~mask & prev));
				setCacheLru(way, set);
				setDirty(way, set, true);
				return;
			}
		}
		assert(false);
	}


	
	/**
	 * Checks the cache state for a given address.
	 * Only the directory is accessed.
	 * @param state Line status, updated by this method:
	 * - VALID if (matching tag) and (state == VALID)
	 * - ZOMBIE if (matching tag) and (state == ZOMBIE)
	 * - EMPTY if no match or (state == EMPTY)
	 * @param ad The address to access
	 * This function can be used when we need to access the directory
	 * while we write in the data part with a different address in the same
	 * cycle.
	 * @param ad
	 * @param state
	 */
	void readDir(long ad, LineState state) {
		long ad_tag = m_z.get(ad);
		long ad_set = m_y.get(ad);

		for (int _way = 0; _way < m_ways; _way++) {
			if ((ad_tag == tag(_way, ad_set))
					&& (state(_way, ad_set) != cacheSlotState.EMPTY)) {
				state.state = state(_way, ad_set);
				state.dirty = dirty(_way, ad_set);
				state.exclu = exclu(_way, ad_set);
				return;
			}
		}
		// return value if not (VALID or ZOMBIE)
		state.state = cacheSlotState.EMPTY;
	}


	/**
	 * Updates a line status for a given address.
	 * Only the directory is accessed.
	 * @param state New line status to write in the directory
	 * @param ad The address for which to update the status
	 */
	void writeDir(long ad, LineState state) {
		long ad_tag = m_z.get(ad);
		long ad_set = m_y.get(ad);

		for (int _way = 0; _way < m_ways; _way++) {
			if ((ad_tag == tag(_way, ad_set))
					&& (state(_way, ad_set) != cacheSlotState.EMPTY)) {
				setState(_way, ad_set, state.state);
				setDirty(_way, ad_set, state.dirty);
				setExclu(_way, ad_set, state.exclu);
				return;
			}
		}
	}
	

	/**
	 * This function selects a victim slot (way) in an associative set.
	 * It can fail if all ways are in ZOMBIE state. The algorithm is the following:
	 * - searches first an EMPTY slot
	 * - if there is no empty slot, searches an OLD slot (according to the LRU bit), not in ZOMBIE state 
	 * - if there is none, we take the first not ZOMBIE slot.
	 * - if there is none, returns an empty result
	 * @param ad
	 * @return
	 */
	CacheAccessResult readSelect(long ad) {
		long _set = m_y.get(ad);

		CacheAccessResult result = new CacheAccessResult();
		result.found = false;
		result.victimFound = false;
		result.victimAddress = 0;
		result.victimDirty = false;
		result.data = null;

		// Search first empty slot
		for (int _way = 0; _way < m_ways && !(result.found); _way++) {
			if (state(_way, _set) == cacheSlotState.EMPTY) {
				result.found = true; // Empty slot: no victim
				return result;
			}
		}

		// Search first not zombie old slot
		for (int _way = 0; _way < m_ways && !(result.found); _way++) {
			if (!lru(_way, _set)
					&& (state(_way, _set) != cacheSlotState.ZOMBI)) {
				result.found = true;
				result.victimFound = true;
				result.victimAddress = (tag(_way, _set) * m_sets + _set) * m_words * 4;
				result.victimDirty = dirty(_way, _set);
				if (result.victimDirty) {
					result.data = new ArrayList<Long>();
					for (int word = 0; word < m_words; word++) {
						result.data.add(data(_way, _set, word));
					}
				}
				return result;
			}
		}

		// Search first not zombie slot
		for (int _way = 0; _way < m_ways && !(result.found); _way++) {
			if (state(_way, _set) != cacheSlotState.ZOMBI) {
				result.found = true;
				result.victimFound = true;
				result.victimAddress = (tag(_way, _set) * m_sets + _set) * m_words * 4;
				result.victimDirty = dirty(_way, _set);
				if (result.victimDirty) {
					result.data = new ArrayList<Long>();
					for (int word = 0; word < m_words; word++) {
						result.data.add(data(_way, _set, word));
					}
				}
				return result;
			}
		}
		// no slot found
		return result;
	}
	

	/**
	 * Updates the cache with a line copy. The data part and directory are accessed.
	 * @param ad The address of the line
	 * @param buf A list containing the values of the words of the line
	 * @param exclu true if the line is in exclusive state, false otherwise
	 * Note: when the ZOMBIE state is used, the controller must guarantee that at least one way
	 * is not in ZOMBIE state, using the readSelect method.
	 */
	void setLine(long ad, List<Long> buf, boolean exclu) {
		long _set = m_y.get(ad);
		long tag = m_z.get(ad);

		// Search first empty slot
		for (int _way = 0; _way < m_ways; _way++) {
			if (state(_way, _set) == cacheSlotState.EMPTY) {
				setTag(_way, _set, tag);
				setState(_way, _set, cacheSlotState.VALID);
				setExclu(_way, _set, exclu);
				setCacheLru(_way, _set);

				for (int _word = 0; _word < m_words; _word++) {
					setData(_way, _set, _word, buf.get(_word));
				}
				return;
			}
		}

		// Search first not zombie old slot
		for (int _way = 0; _way < m_ways; _way++) {
			if (!lru(_way, _set)
					&& (state(_way, _set) != cacheSlotState.ZOMBI)) {
				setTag(_way, _set, tag);
				setState(_way, _set, cacheSlotState.VALID);
				setExclu(_way, _set, exclu);
				setCacheLru(_way, _set);

				for (int _word = 0; _word < m_words; _word++) {
					setData(_way, _set, _word, buf.get(_word));
				}
				return;
			}
		}
		assert(false);
	}

	
	
	/**
	 * Invalidates a line.
	 * There are 2 types of invalidations:
	 *  - full : the line is invalidated (full_inval = true)
	 *  - read only: the line can't be written anymore but can still be read (full_inval = false)
	 * In case the line is dirty, it must be written back to memory; the new values are sent in the
	 * invalidation response.
	 */
	CacheAccessResult inval(long ad, boolean full_inval) {

		long tag = m_z.get(ad);
		long set = m_y.get(ad);

		CacheAccessResult result = new CacheAccessResult();

		result.victimFound = false;
		result.victimAddress = 0;
		result.victimDirty = false;
		result.data = null;

		for (int way = 0; way < m_ways; way++) {
			if (tag == tag(way, set) && state(way, set) == cacheSlotState.VALID) {
				result.victimFound = true;
				result.victimAddress = (tag(way, set) * m_sets + set) * m_words * 4;
				result.victimDirty = dirty(way, set);
				if (result.victimDirty) {
					result.data = new ArrayList<Long>();
					for (int word = 0; word < m_words; word++) {
						result.data.add(data(way, set, word));
					}
				}
				if (full_inval) {
					setState(way, set, cacheSlotState.EMPTY);
					setLru(way, set, false);
				}
				setExclu(way, set, false);
				setDirty(way, set, false);
				return result;
			}
		}
		return result;
	}


	void fileTrace(String filename) {
		PrintWriter content = null;
		try {
			content = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		for (int nway = 0; nway < m_ways; nway++) {
			for (int nset = 0; nset < m_sets; nset++) {
				content.println(state(nway, nset) + " / ");
				content.printf("way %d / ", (int) nway);
				content.printf("set %d / ", (int) nset);
				content.printf("@ = %08zX / ",
						((tag(nway, nset) * m_sets + nset) * m_words * 4));
				for (int nword = m_words; nword > 0; nword--) {
					long data = data(nway, nset, nword - 1);
					content.printf("%08X ", data);
				}
				content.printf("\n");
			}
		}
		content.close();
	}

	void printTrace() {
		System.out.printf("STATE | D | X | Way | Set | Address    | Data\n");
		for (int way = 0; way < m_ways; way++) {
			for (int set = 0; set < m_sets; set++) {
				long addr = ((tag(way, set)) * m_words * m_sets + m_words * set) * 4;
				int d = (dirty(way, set) ? 1 : 0);
				int x = (exclu(way, set) ? 1 : 0);
				System.out.printf("%s | %d | %d | %3d | %3d | 0x%-8x",
						state(way, set), d, x, way, set, addr);

				for (int word = 0; word < m_words; word++) {
					System.out.printf(" | 0x%-8x", data(way, set, word));
				}
				System.out.println();
			}
		}
	}

}
