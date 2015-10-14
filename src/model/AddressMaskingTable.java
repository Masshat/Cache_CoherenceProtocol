package model;

/**
 * This class is taken from soclib, and serves to facilitate address manipulation.
 * @author QLM
 *
 */
public class AddressMaskingTable {

	long m_use_bits;  // Number of bits to consider in the middle of the word
	long m_drop_bits; // Number of LSB bits to ignore
	long m_low_mask;

	void init(long use_bits, long drop_bits) {
		// assert(((use_bits + drop_bits) <= (sizeof(data_t) * 8)) &&
		// "Error in AddressMaskingTable : use_bits + drop_bits too large");

		m_use_bits = use_bits;
		m_drop_bits = drop_bits;
		m_low_mask = (1 << use_bits) - 1;
	}

	public AddressMaskingTable() {
		init(0, 0);
	}

	public AddressMaskingTable(long use_bits, long drop_bits) {
		init(use_bits, drop_bits);
	}

	public AddressMaskingTable(long mask) {
		long use_bits = 0;
		long drop_bits = 0;
		long m = mask;

		assert (mask != 0);

		while ((m & 1) == 0) {
			drop_bits++;
			m >>= 1;
		}

		while ((m & 1) != 0 && (use_bits + drop_bits <= 8 * 4)) {
			use_bits++;
			m >>= 1;
		}

		init(use_bits, drop_bits);
		assert (this.mask() == mask);
	}

	long mask() {
		return m_low_mask << m_drop_bits;
	}

	long getUse() {
		return m_use_bits;
	}

	long getDrop() {
		return m_drop_bits;
	}

	long get_value(long where) {
		return (where >> m_drop_bits) & m_low_mask;
	}

	long get(long where) {
		return (where >> m_drop_bits) & m_low_mask;
	}

	public String toString() {
		String res = "<AMT: use = " + m_use_bits + ", drop = " + m_drop_bits
				+ ", mask = 0x" + Long.toHexString(mask()) + ">";
		return res;
	}

}
