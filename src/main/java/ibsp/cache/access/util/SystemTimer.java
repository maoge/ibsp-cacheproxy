package ibsp.cache.access.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 微秒级的计时器
 */

public class SystemTimer {
	
	private final static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private static final long tickUnit = 10;  //Long.parseLong(System.getProperty("notify.systimer.tick", "50"));
	private static volatile long time = System.nanoTime();
	
	private static class TimerTicker implements Runnable {
		public void run() {
			time = System.nanoTime();
		}
	}
	
	public static long currentTimeNano() {
		return time;
	}
	
	public static long currentTimeMillis() {
		return time/1000000;
	}
	
	static {
		executor.scheduleAtFixedRate(new TimerTicker(), tickUnit, tickUnit, TimeUnit.MICROSECONDS);
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					executor.shutdown();
				}
		});
	}

	public static void main(String[] args) {
		
		long start = System.nanoTime();
		long total = 100000000;
		
		long l1 = 0,l2 = 0;
		
		for (int i = 0; i < total; i++) {
//			long l = SystemMicroTimer.currentTimeMicro();
			long l = System.nanoTime();
			if (i == 0)
				l1 = l;
			if (i == 99999999)
				l2 = l;
		}
		
		long end = System.nanoTime();
		long diff = end - start;
		long tps = (total*1000000000)/diff;
		
		System.out.println("diff:" + diff + ", tps:" + tps);
		System.out.println("l1:" + l1 + ", l2:" + l2 + ", (l2 - l1):" + (l2 - l1));
		
	}

}
