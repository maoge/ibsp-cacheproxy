package ibsp.cache.access.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

public class HealthMonitor {
	private static final Logger LOGGER = Logger.getLogger(HealthMonitor.class);
    private volatile long revReqTotalCnt;     // 接入机收到的总请求总数
	private volatile long normalRepTotalCnt;  // 接入机正常返回总数
	private volatile long exceptionTotalCnt;  // 接入机内部错误总数
	private AtomicInteger connClientCount;     // 接入机上的客户端连接数
	private AtomicInteger reqInQueue;         // 接入机中待处理队列长度
	private IMonitor IMONITOR;
	
	private volatile double processTime; 	//接入机处理请求的平均时长
	private volatile double maxProcessTime;  //接入机处理请求的最大时长
	private AtomicInteger connRedisCount;  //接入机的REDIS连接数
	private volatile Map<String, AtomicLong> groupReqCnt;  //每个分组的请求数
	
	private static HealthMonitor monitor;
	private PerformanceRecorder recorder;
	private Thread perfRecThread;
	
	
	public static void setMonitor(HealthMonitor monitor) {
		HealthMonitor.monitor = monitor;
	}
	
	public static HealthMonitor getMonitor() {
		return HealthMonitor.monitor;
	}
	
	public HealthMonitor() {
		this.revReqTotalCnt = 0;
		this.normalRepTotalCnt = 0;
		this.exceptionTotalCnt = 0;
		this.groupReqCnt = new HashMap<String, AtomicLong>();
		
		reqInQueue = new AtomicInteger(0);
		connClientCount = new AtomicInteger(0);
		connRedisCount = new AtomicInteger(0);
		
		recorder = new PerformanceRecorder();
		perfRecThread = new Thread(recorder);
		perfRecThread.setDaemon(false);
		perfRecThread.setName("PerformanceRecorder");
		perfRecThread.start();
	}
	
	public static IMonitor getIMonitor() {
		return HealthMonitor.monitor.IMONITOR;
	}
	
	public void incGroupReqCnt(String group) {
		if (groupReqCnt.containsKey(group)) {
			groupReqCnt.get(group).incrementAndGet();
		} else {
			groupReqCnt.put(group, new AtomicLong(1L));
		}
	}
	
	public Map<String, AtomicLong> getGroupReqCnt() {
		return groupReqCnt;
	}
	
	public void removeGroupCnt(String group) {
		LOGGER.warn("remove group "+group);
		if (groupReqCnt.containsKey(group)) {
			groupReqCnt.remove(group);
		}
	}
	
	public void incConnClientCount() {
		connClientCount.incrementAndGet();
	}
	
	public void decConnClientCount() {
		connClientCount.decrementAndGet();
	}
	
	public int getConnClientCount() {
		return connClientCount.get();
	}
	
	public void incConnRedisCount() {
		connRedisCount.incrementAndGet();
	}
	
	public void decConnRedisCount() {
		connRedisCount.decrementAndGet();
	}
	
	public int getConnRedisCount() {
		return connRedisCount.get();
	}
	
	public void incReqInQueue() {
		reqInQueue.incrementAndGet();
	}
	
	public void decReqInQueue() {
		reqInQueue.decrementAndGet();
	}
	
	public long incRevReqTotalCntAndGet() {
		revReqTotalCnt++;
		if (revReqTotalCnt == Long.MAX_VALUE) {
			clear();  // revReqTotalCnt比 normalRepTotalCnt， exceptionTotalCnt都大， 只要判断revReqTotalCnt达到最大值就归零
			recorder.clear();
		}
		
		return revReqTotalCnt;
	}
	
	public long getRevReqTotalCnt() {
		return revReqTotalCnt;
	}
	
	public double addProcessTimeAndGet(double value) {
		processTime += value;
		return processTime;
	}
	
	public double getProcessTime() {
		return processTime;
	}
	
	public void setMaxProcessTime(double value) {
		if (value>this.maxProcessTime) {
			this.maxProcessTime = value;
		}
	}
	
	public double getMaxProcessTime() {
		return this.maxProcessTime;
	}
	
	public void clearMaxProcessTime() {
		this.maxProcessTime = 0;
	}
	
	public long incNormalRepTotalCntAndGet() {
		return ++normalRepTotalCnt;
	}
	
	public long incExceptionTotalCntAndGet() {
		return ++exceptionTotalCnt;
	}
	
	public long getExceptionTotalCnt() {
		return exceptionTotalCnt;
	}
	
	private void clear() {
		revReqTotalCnt = 0L;
		normalRepTotalCnt = 0L;
		exceptionTotalCnt = 0L;
		processTime = 0.0D;
		for (String key : groupReqCnt.keySet()) {
			groupReqCnt.put(key, new AtomicLong(0L));
		}
	}


	@SuppressWarnings("unused")
	private class PerformanceRecorder implements Runnable {
		private static final long RECORD_INTERVAL = 10 * 1000L;
		private long lastRecordTS;
		private long lastRevReqTotalCnt;
		private long lastNormalRepTotalCnt;
		private long lastExceptionTotalCnt;
		private double lastProcessTime;
		private long maxTPS;
		
		private volatile boolean bRun = true;
		
		public PerformanceRecorder() {
			lastRecordTS = SystemTimer.currentTimeMillis();
			lastRevReqTotalCnt = revReqTotalCnt;
			lastNormalRepTotalCnt = normalRepTotalCnt;
			lastExceptionTotalCnt = exceptionTotalCnt;
			lastProcessTime = processTime;
			maxTPS = 0;
		}

		@Override
		public void run() {
			while (bRun) {
				// 先sleep, 防止启动时 deltaTS == 0
				try {
					Thread.sleep(RECORD_INTERVAL);
				} catch (InterruptedException e) {
					LOGGER.error(e);
				}
				
				long currTS = System.currentTimeMillis();
				
				long deltaRevReq = revReqTotalCnt - lastRevReqTotalCnt;
				long deltaNormalRep = normalRepTotalCnt - lastNormalRepTotalCnt;
				long deltaException = exceptionTotalCnt - lastExceptionTotalCnt;
				double deltaProcessTime = processTime - lastProcessTime;
				long deltaTS = currTS - lastRecordTS;
				
				long revReqTPS = deltaRevReq*1000 / deltaTS;
				long normalTPS = deltaNormalRep*1000 / deltaTS;
				long exceptionTPS = deltaException*1000 / deltaTS;
				double avrProcessTime = deltaProcessTime / deltaNormalRep;
				long repTotalCnt = normalRepTotalCnt + exceptionTotalCnt;

				LOGGER.info("RevReqTPS:" + revReqTPS + ", NormalWriteBackTPS:" + normalTPS +
						", ExceptionTPS:" + exceptionTPS + ", ReqInQueue:" + reqInQueue.get() +
						", connClientCount:" + connClientCount.get() + ", AvrProcessTime:" +
						avrProcessTime);
				
				lastRecordTS = currTS;
				lastRevReqTotalCnt = revReqTotalCnt;
				lastNormalRepTotalCnt = normalRepTotalCnt;
				lastExceptionTotalCnt = exceptionTotalCnt;
				lastProcessTime = processTime;
			}
		}
		
		public synchronized void stopRun() {
			bRun = false;
		}
		
		public synchronized void clear() {
			lastRevReqTotalCnt = 0;
			lastNormalRepTotalCnt = 0;
			lastExceptionTotalCnt = 0;
			lastProcessTime = 0;
		}
		
	}
	
}
