package ibsp.cache.access.configure;

import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import ibsp.cache.access.util.CONSTS;
import ibsp.cache.access.util.HttpUtils;
import ibsp.cache.access.util.SVarObject;

public class MetasvrUrlConfig {
	
	private static Logger logger = LoggerFactory.getLogger(MetasvrUrlConfig.class);

	private Vector<String> valildUrlVec;
	private Vector<String> invalildUrlVec;

	private volatile int validSize;
	private volatile int invalidSize;
	private long lastIndex;
	
	private boolean isCheckerRunning;
	private MetaServerChecker checker;
	private Thread checkThread;
	
	private ReentrantLock lock;

	public MetasvrUrlConfig(String metasvrUrl) {
		valildUrlVec   = new Vector<String>();
		invalildUrlVec = new Vector<String>();
		validSize      = 0;
		invalidSize    = 0;
		lastIndex      = 0L;
		isCheckerRunning = false;
		lock           = new ReentrantLock();
		
		String[] urls = metasvrUrl.split(",");
		for (String url : urls) {
//			String httpUrl = String.format("%s://%s", CONSTS.HTTP_PROTOCAL, url.trim());
			invalildUrlVec.add(url);
			invalidSize++;
		}
		this.doUrlCheck();
		if (invalidSize > 0) {
			startChecker();
		}
	}
	
	public void close() {
		this.stopChecker();
	}
	
	public void mergeRecovered(int idx) {
		try {
			lock.lock();
			if (idx < 0 || idx > invalidSize - 1)
				return;
			
			String url = invalildUrlVec.remove(idx);
			invalidSize--;
			
			valildUrlVec.add(url);
			validSize++;
		} finally {
			lock.unlock();
		}
	}
	
	public void putBrokenUrl(String url) {
		try {
			lock.lock();
			int idx = valildUrlVec.indexOf(url);
			
			if (idx < 0 || idx > validSize - 1)
				return;
			
			valildUrlVec.remove(idx);
			validSize--;
			
			invalildUrlVec.add(url);
			invalidSize++;
			startChecker();
		} finally {
			lock.unlock();
		}
	}

	public String getNextUrl() {
		if (validSize == 0) {
			logger.error("no valid root url!");
			return null;
		}

		try {
			lock.lock();
			int idx = (validSize == 1) ? 0 : (int) (lastIndex++ % validSize);
			return valildUrlVec.get(idx);
		}  finally {
			lock.unlock();
		}
	}
	
	public void doUrlCheck() {
		if (invalidSize <= 0) {
			return;
		}
		
		try {
			int idx = invalidSize - 1;
			for (; idx >= 0; idx--) {
				String url = invalildUrlVec.get(idx);
				String reqUrl = String.format("http://%s/%s/%s", url, CONSTS.META_SERVICE, CONSTS.FUN_URL_TEST);
				
				SVarObject sVarInvoke = new SVarObject();
				boolean retInvoke = HttpUtils.getData(reqUrl, sVarInvoke);
				if (retInvoke) {
					JSONObject jsonObj = JSONObject.parseObject(sVarInvoke.getVal());
					if (jsonObj.getIntValue(CONSTS.JSON_HEADER_RET_CODE) == CONSTS.REVOKE_OK) {
						mergeRecovered(idx);
					}
				}
			}
			if (invalidSize == 0) { 
				stopChecker();
			}
		} catch (Exception e) {
			logger.warn("Check metaserver error...", e);
		}
	}
	
	private void startChecker() {
		if (isCheckerRunning)
			return;
		
		isCheckerRunning = true;
		checker = new MetaServerChecker();
		checkThread = new Thread(checker);
		checkThread.start();
	}
	
	public void stopChecker() {
		if (!isCheckerRunning)
			return;
		
		isCheckerRunning = false;
		if (checker != null) {
			checker.stopRunning();
			checker = null;
			checkThread = null;
		}
	}

	private class MetaServerChecker implements Runnable {
		
		private volatile boolean running = false;
		private final long METASERVER_CHECK_INTERVAL = 3000L;

		@Override
		public void run() {
			running = true;
			
			while (running) {
				doUrlCheck();
				if (!running) break;
				
				try {
					Thread.sleep(METASERVER_CHECK_INTERVAL);
				} catch (InterruptedException e) {
				}
			}
		}
		
		public void stopRunning() {
			running = false;
		}
	}
}
