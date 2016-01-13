package model;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import utils.Utile;

/**
 * Simple class for a memory constituted of segments. It provides facilities to access data and metadata.
 * @author QLM
 *
 */
public class Ram {
	
	/**
	 * Block states.
	 * It is possible to use only one of the Exclusive and Modified states in the write-back implementation.
	 * The ZOMBIE state should not be used here.
	 */
	enum BlockState {
		INVALID, VALID, EXCLUSIVE, MODIFIED, ZOMBIE,
	}
	
	int m_id; // @QM unused
	private int m_words;
	private int m_nbseg;
	
	private Vector<Segment> m_seglist;
	private Vector<long []> m_ram;
	private long [] m_nbsets;
	
	private Vector<CopiesList []> r_copies;
	private Vector<BlockState []> r_state;
	
	private AddressMaskingTable m_y;
	
	
	public Ram(String name, int nwords, Vector<Segment> seglist) {
		m_seglist = seglist;
		m_words = nwords;
		m_nbseg = seglist.size();
		m_y = new AddressMaskingTable(8 * 8 - Utile.log2(nwords) - 2, // 2 = log2(sizeof(word))
				Utile.log2(nwords) + 2);
		
		assert (!seglist.isEmpty()) : "Ram error : no segment allocated";
		
		for (Segment seg : m_seglist) {
			System.out.println(seg);
		}
		
		// memory allocation
		m_ram = new Vector<long []>();
		m_nbsets = new long[m_nbseg];
		r_copies = new Vector<CopiesList []>();
		r_state = new Vector<BlockState []>();
		
		int i = 0;
		for (Segment seg : m_seglist) {
			m_ram.add(new long[(seg.size() + 3) / 4]);
			m_nbsets[i] = seg.size() >> (2 + Utile.log2(m_words));
			r_copies.add(new CopiesList[(int) m_nbsets[i]]);
			for (int j = 0; j < m_nbsets[i]; j++) {
				r_copies.get(i)[j] = new CopiesList();
			}
			r_state.add(new BlockState[(int) m_nbsets[i]]);
			i++;
		}
		
		reset();
	}
	
	
	/**
	 * Initializes the memory to 0
	 */
	void reset() {
		for (int seg = 0; seg < m_nbseg; seg++) {
			for (int i = 0; i < m_nbsets[seg]; i++) {
				r_copies.get(seg)[i].removeAll();
				r_state.get(seg)[i] = BlockState.VALID;
			}
			for (int i = 0; i < (m_seglist.get(seg).size() + 3) / 4; i++) {
				m_ram.get(seg)[i] = 0;
			}
		}
	}
	
	/**
	 * @param addr
	 * @return the list of copies of the block containing the address addr
	 */
	CopiesList copies(long addr) {
		long set = m_y.get(addr);
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				return r_copies.get(i)[(int) (set - m_y.get(m_seglist.get(i).baseAddress()))];
			}
		}
		assert (false);
		return null;
	}
	

	/**
	 * @param addr
	 * @return the Blockstate object of the line containing the address addr
	 */
	BlockState state(long addr) {
		long set = m_y.get(addr);
		System.out.println("   addr : 0x" + Long.toHexString(addr) + " - set = " + set);
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				return r_state.get(i)[(int) (set - m_y.get(m_seglist.get(i).baseAddress()))];
			}
		}
		assert (false);
		return BlockState.INVALID;
	}
	
	
	/**
	 * Sets the Blockstate bs for the line containing the address addr.
	 * @param addr
	 * @param bs
	 */
	void setState(long addr, BlockState bs) {
		long set = m_y.get(addr);
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				r_state.get(i)[(int) (set - m_y.get(m_seglist.get(i).baseAddress()))] = bs;
				return;
			}
		}
		assert (false);
	}
	

	/**
	 * Writes data wdata at address addr, for bytes enabled by be.
	 * @param addr The address to update
	 * @param wdata The data to write
	 * @param be The enabled bytes
	 * @return true if the current ram object contains the address (update done), false otherwise.
	 */
	boolean write(long addr, long wdata, int be) {
		long mask;
		long old_val, new_val;
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				int index = (int) ((addr - m_seglist.get(i).baseAddress()) / 4);
				mask = Utile.be2mask(be);
				old_val = m_ram.get(i)[index];
				new_val = wdata;
				m_ram.get(i)[index] = (old_val & ~mask) | (new_val & mask);
				return true;
			}
		}
		return false;
	}
	

	/**
	 * Writes a full line into memory
	 * @param addr The address of the line to update.
	 * @param wdata List of values to write containing as many elements as words per line.
	 * @return true if the ram contains the address and the update is done, false otherwise.
	 */
	boolean writeLine(long addr, List<Long> wdata) {
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				int index = (int) ((addr - m_seglist.get(i).baseAddress()) / 4);
				for (int word = 0; word < m_words; word++) {
					m_ram.get(i)[index + word] = wdata.get(word);
				}
				return true;
			}
		}
		return false;
	}
	
	
	/**
	 * @param addr
	 * @param cache_id
	 * @return true if the cache cache_id owns a copy of the line containing the address addr, false otherwise.
	 */
	boolean hasCopy(long addr, int cache_id) {
		return copies(addr).hasCopy(cache_id);
	}
	

	/**
	 * @param addr
	 * @param cache_id
	 * @return true if the line containing the address addr is owned by another cache that the one specified by cache_id.
	 */
	boolean hasOtherCopy(long addr, int cache_id) {
		return copies(addr).hasOtherCopy(cache_id);
	}
	
	/**
	 * Adds the cache cache_id to the list of copies for the line containing the address addr.
	 * @param addr
	 * @param cache_id
	 */
	void addCopy(long addr, int cache_id) {
		copies(addr).add(cache_id);
	}
	
	/**
	 * Removes the copy from the list of the line containing the address addr, for the cache cache_id.
	 * @param addr
	 * @param cache_id
	 */
	void removeCopy(long addr, int cache_id) {
		copies(addr).remove(cache_id);
	}
	
	
	/**
	 * Removes all copies for the line containing the address addr.
	 * @param addr
	 */
	void removeAllCopies(long addr) {
		copies(addr).removeAll();
	}
	

	/**
	 * @param addr
	 * @return The number of copies for the line containing the address addr.
	 */
	int nbCopies(long addr) {
		return copies(addr).nbCopies();
	}
	

	/**
	 * @param addr
	 * @return The list of copies (CopiesList object) for the line containing the address addr.
	 */
	CopiesList getCopies(long addr) {
		return copies(addr);
	}
	

	/**
	 * @param addr
	 * @return The Blockstate object of the line containing the address addr.
	 */
	BlockState getState(long addr) {
		return state(addr);
	}
	
	
	/**
	 * @param addr
	 * @return true if the line containing the address addr is in the "Modified" state, false otherwise.
	 */
	boolean isMod(long addr) {
		return state(addr) == BlockState.MODIFIED;
	}
	
	
	/** 
	 * @param addr
	 * @return true if the line containing the address addr if in "Exclusive" state, false otherwise
	 */
	boolean isExclu(long addr) {
		return state(addr) == BlockState.EXCLUSIVE;
	}
	
	
	/** 
	 * Note: Should be used for assertion checks only.
	 * @param addr
	 * @return true if the ram contains the address addr, false otherwise.
	 */
	boolean containsAddr(long addr) {
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * @param addr
	 * @return A list of values corresponding to the words of the line containing the address addr.
	 */
	List<Long> getLine(long addr) {
		List<Long> res = new ArrayList<Long>();
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				int index = (int) ((addr - m_seglist.get(i).baseAddress()) / 4);
				for (int word = 0; word < m_words; word++) {
					res.add(m_ram.get(i)[index + word]);
				}
				return res;
			}
		}
		return null;
	}
	
}
