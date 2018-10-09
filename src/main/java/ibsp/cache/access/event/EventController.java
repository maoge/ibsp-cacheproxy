package ibsp.cache.access.event;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import ibsp.cache.access.configure.MetadataConfigProxyService;
import ibsp.cache.access.util.BasicOperation;
import ibsp.cache.access.util.CONSTS;
import ibsp.cache.access.util.IVarObject;
import ibsp.cache.access.util.SVarObject;


public class EventController {
	
	private static Logger logger = LoggerFactory.getLogger(EventController.class);
	
	private volatile boolean isLsnrInited;          // 本机监听是否完成初始化
	private String lsnrIP;                          // 本机和管理平台交互的ip
	private int    lsnrPort;                        // 本机用于接收管理平台下发事件的端口
	private EventSockListener evSockLsnr;           // 管理平台下发事件接收
	
	private AtomicInteger eventCntInQueue;
	private ConcurrentLinkedQueue<JSONObject> eventQueue;
	
	private volatile boolean isTimerRunnerInited;
	private Thread timerEventThread;                // 合并统计TPS、统计上报、EventDisptcher到一个线程处理
	private TimerEventRunner timerEventRunner;
	
	private ReentrantLock lock = null;
	private String servID;
	
	private static EventController theInstance = null;
	private static ReentrantLock intanceLock = null;

	static {
		intanceLock = new ReentrantLock();
	}

	public EventController() {
		lock = new ReentrantLock();
		
		isLsnrInited = false;
		eventCntInQueue = new AtomicInteger(0);
		eventQueue      = new ConcurrentLinkedQueue<JSONObject>();
		lsnrPort = -1;
		servID = MetadataConfigProxyService.getInstance().getServiceID();
	}

	public static EventController getInstance() {
		try {
			intanceLock.lock();
			if (theInstance != null) {
				return theInstance;
			} else {
				theInstance = new EventController();
				theInstance.initListener();
				theInstance.initTimerEventRunner();
			}
		} finally {
			intanceLock.unlock();
		}

		return theInstance;
	}
	
	public void shutdown() {
		try {
			if (isTimerRunnerInited) {
				timerEventRunner.stopRunning();
				timerEventThread.join();
				timerEventRunner = null;
				timerEventThread = null;
				isTimerRunnerInited = false;
			}
				
			if (isLsnrInited) {
				if (evSockLsnr != null && evSockLsnr.IsStart()) {
					evSockLsnr.stop();
					evSockLsnr = null;
					isLsnrInited = false;
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	public boolean pushEventMsg(JSONObject event) {
		if (event == null)
			return false;
		
		boolean ret = eventQueue.offer(event);
		if (ret) {
			eventCntInQueue.incrementAndGet();
		} else {
			logger.error("event push fail.");
		}
		
		return ret;
	}
	
	public JSONObject popEventMsg() {
		if (eventCntInQueue.get() == 0)
			return null;
		
		JSONObject event = eventQueue.poll();
		if (event != null) {
			eventCntInQueue.decrementAndGet();
		}
		
		return event;
	}

	
	private void initTimerEventRunner() {
		try {
			lock.lock();
			if (isTimerRunnerInited)
				return;
			
			if (timerEventRunner == null)
				timerEventRunner = new TimerEventRunner();
			
			timerEventThread = new Thread(timerEventRunner);
			timerEventThread.setDaemon(true);
			timerEventThread.setName("Router.TimerEventThread");
			timerEventThread.start();
			
			isTimerRunnerInited = true;
		} finally {
			lock.unlock();
		}
	}
	
	private void initListener() {
		
		initLsnrIP();
		if (lsnrIP == null) {
			logger.error("init event listener error:lsnrIP is null!");
			return;
		}
		
		for (int i = 0; i < CONSTS.BATCH_FIND_CNT; i++) {
			try {
				lock.lock();
				if (isLsnrInited)
					break;
				
				initLsnrPort();
				if (lsnrPort == -1) {
					logger.error("init event listener error:lsnrPort illigal!");
					continue;
				}
				
				if (evSockLsnr == null) {
					evSockLsnr = new EventSockListener(lsnrIP, lsnrPort);
					evSockLsnr.start();
					isLsnrInited = true;
					
					logger.info("event listener start ok, with addr {}:{}", lsnrIP, lsnrPort);
					break;
				}
			} catch (Exception e) {
				logger.error("Event Socket listener start error:{}, port:{} is used, retry another port.", e.getMessage(), lsnrPort);
				
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
				}
			} finally {
				lock.unlock();
			}
		}
	}
	
	private void initLsnrIP() {
		SVarObject sVarIP = new SVarObject();

		int cnt = 0;
		boolean ok = false;
		while (cnt < CONSTS.GET_IP_RETRY) {
			int ret = BasicOperation.getLocalIP(sVarIP);
			if (ret == CONSTS.REVOKE_OK) {
				lsnrIP = sVarIP.getVal();
				ok = true;
				break;
			} else {
				try {
					Thread.sleep(CONSTS.GET_IP_RETRY_INTERVAL);
				} catch (InterruptedException e) {
					logger.error(e.getMessage(), e);
				}
			}
		}

		if (!ok) {
			logger.error("BasicOperation.getLocalIP error, localIP use 0.0.0.0");
			lsnrIP = null;
		}
	}
	
	private void initLsnrPort() {
		IVarObject iVarPort = new IVarObject();

		int ret = BasicOperation.getUsablePort(lsnrIP, iVarPort);
		if (ret == CONSTS.REVOKE_OK) {
			lsnrPort = iVarPort.getVal();
		} else {
			logger.error("BasicOperation.getUsablePort error, lsnrPort not initalized.");
			lsnrPort = -1;
		}	
	}
	

	/**
	 * 内部线程，管理客户端统计信息上报，服务端下发事件的处理等
	 */
	private class TimerEventRunner implements Runnable {
		
		private volatile boolean bRunning;
		private long lastReportTS;
		private long lastUrlChkTS;
		private long currTS;
		
		public TimerEventRunner() {
			currTS = System.currentTimeMillis();
			lastReportTS  = currTS;
			lastUrlChkTS  = currTS;
		}
		
		@Override
		public void run() {
			bRunning = true;
			JSONObject event = null;

			while (bRunning) {
				try {
					if ((event = popEventMsg()) != null) {
						dealEventMsg(event);
					} else {
						Thread.sleep(CONSTS.EVENT_DISPACH_INTERVAL);
					}
					
					currTS = System.currentTimeMillis();
					
					if ((currTS - lastReportTS) > CONSTS.REPORT_INTERVAL) {
						doReport();
						lastReportTS = currTS;
					}
					
					if ((currTS - lastUrlChkTS) > CONSTS.RECONNECT_INTERVAL) {
						doUrlCheck();
						lastUrlChkTS = currTS;
					}
					
					
				} catch(Exception e) {
					logger.error(e.getMessage(), e);
				}
			}
		}
		
		public void stopRunning() {
			bRunning = false;
		}
		
		private void dealEventMsg(JSONObject event) {
			int code = event.getInteger(CONSTS.EV_CODE);
			String servID = event.getString(CONSTS.EV_SERV_ID);
			String jsonStr = event.getString(CONSTS.EV_JSON_STR);
			JSONObject obj = JSONObject.parseObject(jsonStr);
			
			switch (EventType.get(code)) {
			case e63:
				MetadataConfigProxyService.getInstance().doHASwitch(servID,
						obj.getString("CLUSTER_ID"), obj.getString("NEW_MASTER_ID"));
				break;
			default:
				break;
			}
		}
		
		private void doReport() {
			String lsnrAddr = String.format("%s:%d", lsnrIP, lsnrPort);
			BasicOperation.putClientStatisticInfo("cureuprapapa", lsnrAddr, servID);
		}
		
		private void doUrlCheck() {
			MetadataConfigProxyService.getInstance().doUrlCheck();
		}
		
	}

}
