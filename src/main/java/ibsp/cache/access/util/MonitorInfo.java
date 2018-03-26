package ibsp.cache.access.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MonitorInfo {

	private AtomicInteger reqCount;      // 请求数
	private AtomicLong maxTime;       // 最大耗时(毫秒)
	private AtomicLong minTime;       // 最小耗时(毫秒)
	private AtomicLong totalTime;     // 总耗时(毫秒)

	public MonitorInfo() {
		reqCount = new AtomicInteger(0);
		maxTime = new AtomicLong(0);
		minTime = new AtomicLong(Long.MAX_VALUE);
		totalTime = new AtomicLong(0);
	}
	
    public void refresh(long spendTime) {
        reqCount.incrementAndGet();
        
        if (spendTime > maxTime.get())
            maxTime.set(spendTime);
        
        if (spendTime < minTime.get())
            minTime.set(spendTime);
        
        totalTime.addAndGet(spendTime);
    }
    
	public void clear() {
		reqCount.set(0);
		maxTime.set(0);
		minTime.set(0);
		totalTime.set(0);
	}

	public int getReqCount() {
		return reqCount.get();
	}

	public void setReqCount(int reqCount) {
		this.reqCount.set(reqCount);
	}

	public long getMaxTime() {
		return maxTime.get();
	}

	public void setMaxTime(long maxTime) {
		this.maxTime.set(maxTime);
	}

	public long getMinTime() {
		return minTime.get();
	}

	public void setMinTime(int minTime) {
		this.minTime.set(minTime);
	}

	public long getTotalTime() {
		return totalTime.get();
	}

	public void setTotalTime(int totalTime) {
		this.totalTime.set(totalTime);
	}
	
	public void incReqCount() {
		this.reqCount.incrementAndGet();
	}
	
	public void incTotalTime(int time) {
		this.totalTime.addAndGet(time);
	}
	
	public String getStatisticInfo() {
		StringBuffer sb = new StringBuffer("");
		sb.append(reqCount.get()).append(CONSTS.VAL_SPLITER);
		sb.append(maxTime.get()).append(CONSTS.VAL_SPLITER);
		sb.append(minTime.get()).append(CONSTS.VAL_SPLITER);
		sb.append(totalTime.get());
		return sb.toString();
	}

}