package model;

/**
 * This class implements a segment object, i.e. a contiguous part of a memory
 * @author QLM
 *
 */
public class Segment {

	String m_name;
	long m_base_address;
	int m_size;
	int m_tgtid;
	boolean m_cacheable;

	public Segment(String name, // segment name
			long base_address, // segment base address
			int size, // segment size (bytes)
			boolean cacheable) { // cacheable if true
		m_name = name;
		m_base_address = base_address;
		m_size = size;
		m_cacheable = cacheable;
	}

	long baseAddress() {
		return m_base_address;
	}

	int size() {
		return m_size;
	}

	boolean cacheable() {
		return m_cacheable;
	}

	String name() {
		return m_name;
	}
	
	void setTgtid(int index) {
		m_tgtid = index;
	}

	int getTgtid() {
		return m_tgtid;
	}

	boolean isOverlapping(Segment other) {
		long this_end = m_base_address + m_size;
		long other_end = other.m_base_address + other.m_size;
		if (other_end <= m_base_address) {
			return false;
		}
		if (this_end <= other.m_base_address) {
			return false;
		}
		return true;
	}

	public String toString() {
		String res;
		res = "<Segment \"" + m_name + "\": ";
		res += "base = 0x" + Long.toHexString(m_base_address);
		res += " / size = 0x" + Long.toHexString(m_size);
		res += " / tgtid = " + m_tgtid;
		res += " / " + (m_cacheable ? "cached" : "uncached");
		res += ">";
		return res;
	}

	boolean contains(long addr) {
		return (addr >= m_base_address && addr < m_base_address + m_size);
	}

}
