package utils;

import java.util.Random;

/**
 * Utility static functions
 * @author QLM
 *
 */
public abstract class Utile {

	public static int randInt(int min, int max) {

		Random rand = new Random();

		// nextInt is exclusive of the top value,
		// adding 1 to make it inclusive
		int randomNum = rand.nextInt((max - min) + 1) + min;

		return randomNum;
	}

	public static int log2(int n) {
		if (n == 0) {
			return -1;
		}
		return 31 - Integer.numberOfLeadingZeros(n);
	}

	public static long be2mask(int be) {
		long mask = 0;
		if ((be & 0x1) == 0x1) {
			mask = mask | 0x000000FF;
		}
		if ((be & 0x2) == 0x2) {
			mask = mask | 0x0000FF00;
		}
		if ((be & 0x4) == 0x4) {
			mask = mask | 0x00FF0000;
		}
		if ((be & 0x8) == 0x8) {
			mask = mask | 0xFF000000;
		}
		return mask;
	}

	public static boolean isPowerOf2(int n) {
		assert(n > 0);
		return (n & (n - 1)) == 0;
	}
}
