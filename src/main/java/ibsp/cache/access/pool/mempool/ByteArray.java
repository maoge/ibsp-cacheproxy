package ibsp.cache.access.pool.mempool;

public class ByteArray {
	private byte[] bytes;
	private int len;
	private int capacity;
	
	public ByteArray(int size) {
		bytes = new byte[size];
		capacity = size;
	}
	
	public ByteArray(byte[] bytes) {
		this.setBytes(bytes);
		this.capacity = bytes.length;
	}

	public byte[] getBytes() {
		return bytes;
	}

	public void setBytes(byte[] bytes) {
		this.bytes = bytes;
	}

	public int getLen() {
		return len;
	}

	public void setLen(int len) {
		this.len = len;
	}
	
	public int getCapacity() {
		return this.capacity;
	}
	
	public void clear() {
//		if (len > 0)
//			Arrays.fill(bytes, 0, len, (byte)0);
//		else
//			Arrays.fill(bytes, (byte)0);
		this.len = 0;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		int size = bytes.length;
		for (int i = 0; i < size; i++) {
			sb.append((char) bytes[i]);
		}
		
		return sb.toString();
	}
	
}
