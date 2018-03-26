package ibsp.cache.access.util;

import java.util.concurrent.atomic.AtomicLong;

public class IDGenerator {

	private static IDGenerator GENERAL_ID_GEN;
	private static IDGenerator REQ_ID_GEN;
	private static IDGenerator SESSION_ID_GEN;
	
	private AtomicLong id;
	
	public IDGenerator() {
		id = new AtomicLong(0);
	}
	
	public static IDGenerator getReqIDGenerator() {
		if (REQ_ID_GEN == null) {
			IDGenerator.REQ_ID_GEN = new IDGenerator();
		}
		
		return IDGenerator.REQ_ID_GEN;
	}
	
	public static IDGenerator getSessionIDGenerator() {
		if (SESSION_ID_GEN == null) {
			IDGenerator.SESSION_ID_GEN = new IDGenerator();
		}
		
		return IDGenerator.SESSION_ID_GEN;
	}
	
	public static IDGenerator getGeneralIDGenerator() {
		if (GENERAL_ID_GEN == null) {
			IDGenerator.GENERAL_ID_GEN = new IDGenerator();
		}
		
		return IDGenerator.GENERAL_ID_GEN;
	}
	
	public long nextID() {
		long value = id.incrementAndGet();
		if (value == Long.MAX_VALUE) {
			id.set(0);
		}
		
		return value;
	}
	
	public static void main(String[] args) {
		System.out.println("start ......");
		long startTS = System.currentTimeMillis();
		long count = 100000000;
		for (int i = 0; i < count; i++) {
			IDGenerator.getReqIDGenerator().nextID();
		}
		long endTS = System.currentTimeMillis();
		
		long ts = endTS-startTS;
		System.out.println("end ......, ts:" + ts + ", tps:" + (count*1000)/ts );
	}
	
}
