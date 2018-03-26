package ibsp.cache.access.util;

public class KeyHash {
	
	public static int keyHashSlot(byte[] key, int len) {
		return CRC16.hash(key, len) & 0x3FFF;
	}
	
}
