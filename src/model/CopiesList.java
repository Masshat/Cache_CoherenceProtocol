package model;

/**
 * Implementation an explicit list of copies.
 * Only made to look more real than using a hashset...
 * @author QLM
 */
public class CopiesList {
	
	private int laListe;
	
	public CopiesList() {
		laListe = 0;
	}
	
	public CopiesList(CopiesList cl) {
		this.laListe = cl.laListe;
	}

	void add(int cache_id) {

		assert (cache_id < 32);
		laListe = laListe | (1 << cache_id);
	}

	boolean hasOtherCopy(int cache_id) {
		for (int i = 0; i < 32; i++) {
			if ((laListe & (1 << i)) != 0 && i != cache_id) {
				return true;
			}
		}
		return false;
	}

	void remove(int cache_id) {
		assert (cache_id < 32);
		laListe = laListe & (~(1 << cache_id));
	}

	void removeAll() {
		laListe = 0;
	}

	int getNextOwner() {
		for (int i = 0; i < 32; i++) {
			if ((laListe & (1 << i)) != 0) {
				return i;
			}
		}
		return -1;
	}

	int nbCopies() {
		int i;
		int res = 0;
		long l = laListe;
		for (i = 0; i < 32; i++) {
			if ((l & 1) != 0) {
				res++;
			}
			l = l >> 1;
		}
		return res;
	}

	boolean hasCopy(int id) {
		return (laListe & (1 << id)) != 0;
	}
	
	public String toString() {
		String res = "0x" + Integer.toHexString(laListe);
		return res;
	}

}
