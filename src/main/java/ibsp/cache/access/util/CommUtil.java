package ibsp.cache.access.util;

public class CommUtil {
	
    public static int LONG_MAXLEN = 19;   // long MAX_VALUE = 0x7fffffffffffffffL
    public static int INT_MAXLEN  = 10;   // int  MAX_VALUE = 0x7fffffff

    final static int[]  INT_SECTION  = {
        9,      99,      999,      9999,      99999, 
        999999, 9999999, 99999999, 999999999, 2147483647};
	
	final static long[] LONG_SECTION = {
        9L,                99L,                999L,                9999L,               99999L, 
        999999L,           9999999L,           99999999L,           999999999L,          9999999999L, 
        99999999999L,      999999999999L,      9999999999999L,      99999999999999L,     999999999999999L, 
        9999999999999999L, 99999999999999999L, 999999999999999999L, 9223372036854775807L };
	
    final static char[] digits = {
        '0' , '1' , '2' , '3' , '4' , '5' ,
        '6' , '7' , '8' , '9' , 'a' , 'b' ,
        'c' , 'd' , 'e' , 'f' , 'g' , 'h' ,
        'i' , 'j' , 'k' , 'l' , 'm' , 'n' ,
        'o' , 'p' , 'q' , 'r' , 's' , 't' ,
        'u' , 'v' , 'w' , 'x' , 'y' , 'z'
    };
	
    final static char [] DigitTens = {
        '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
        '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
        '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
        '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
        '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
        '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
        '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
        '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
        '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
        '9', '9', '9', '9', '9', '9', '9', '9', '9', '9',
    };

    final static char [] DigitOnes = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    };
	
	public static int getIntLength(int value) {
		int i = 0;
		do {
		} while (i < INT_MAXLEN && value > INT_SECTION[i++]);
		return i;
	}
	
	public static int getLongLength(long value) {
		int i = 0;
		do {
		} while (i < LONG_MAXLEN && value > LONG_SECTION[i++]);
		return i;
	}
	
	/*
	 * long -> byte[]
	 * @ buf for output
	 * @ val for input
	 */
	public static void getLongBytes(long i, int size, byte[] buf) {
		getChars(i, size, buf);
	}
	
	public static void main(String[] args) {
		byte[] bytes = new byte[6];
		
		long start = System.currentTimeMillis();
		long total = 10000000;
		
		for (int i = 0; i < total; i++) {
			CommUtil.getLongBytes(999999, 6, bytes);
//			CommUtil.printBuff(bytes);
		}
		long end = System.currentTimeMillis();
		long diff = end - start;
		long tps = (total*1000)/diff;
		
		System.out.println("diff:" + diff + ", tps:" + tps);
	}
	
	public static void printBuff(byte[] bytes) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < bytes.length; i++) {
			sb.append((char) bytes[i]);
		}
		
		String s = sb.toString();
		System.out.println(s);
	}
	
    static void getChars(long i, int index, byte[] buf) {
        long q;
        int r;
        int charPos = index;
        char sign = 0;

        if (i < 0) {
            sign = '-';
            i = -i;
        }

        // Get 2 digits/iteration using longs until quotient fits into an int
        while (i > Integer.MAX_VALUE) {
            q = i / 100;
            // really: r = i - (q * 100);
            r = (int)(i - ((q << 6) + (q << 5) + (q << 2)));
            i = q;
            buf[--charPos] = (byte)DigitOnes[r];
            buf[--charPos] = (byte)DigitTens[r];
        }

        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int)i;
        while (i2 >= 65536) {
            q2 = i2 / 100;
            // really: r = i2 - (q * 100);
            r = i2 - ((q2 << 6) + (q2 << 5) + (q2 << 2));
            i2 = q2;
            buf[--charPos] = (byte)CommUtil.DigitOnes[r];
            buf[--charPos] = (byte)CommUtil.DigitTens[r];
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i2 <= 65536, i2);
        for (;;) {
            q2 = (i2 * 52429) >>> (16+3);
            r = i2 - ((q2 << 3) + (q2 << 1));  // r = i2-(q2*10) ...
            buf[--charPos] = (byte)CommUtil.digits[r];
            i2 = q2;
            if (i2 == 0) break;
        }
        if (sign != 0) {
            buf[--charPos] = (byte)sign;
        }
    }

}
