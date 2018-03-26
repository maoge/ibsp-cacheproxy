package ibsp.cache.access.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import ibsp.cache.access.configure.IConfigProxyService;
//import ibsp.cache.access.configure.ZkConfigProxyService;
import ibsp.cache.access.configure.MetadataConfigProxyService;

public class Metrics implements Runnable {
	//开放的指标
	public static long access_request_tps;			//接入机请求时段内平均TPS
	public static long access_request_excepts;			//接入机异常请求次数
	public static double access_process_av_time;			//接入机时段内请求处理平均耗时
	public static double access_process_max_time;			//接入机时段内请求处理最大耗时
	public static long access_client_conns;			//接入机当前客户端连接数
	public static long access_redis_conns;			//接入机当前REDIS连接数
	public static String[] access_group_list;			//接入机当前服务的分组
	public static Map<String, Long> access_group_tps;				//接入机分组对应TPS
	public static Map<String, Long> access_group_onetime_tps;				//分组某个时刻的TPS
	
	private HealthMonitor monitor;
	private IConfigProxyService service;
	private static final long INTERVAL = 60 * 1000L;
	private long lastRecordTS;
	private long lastRevReqTotalCnt;
	private long lastExceptionTotalCnt;
	private double lastProcessTime;
	private Map<String, Long> lastGroupReqCnt;
	private Map<String, Long> groupStartTime;
	
	private volatile boolean bRun = true;
	private static final Logger LOGGER = Logger.getLogger(Metrics.class);
	
	public Metrics() {
		this.monitor = HealthMonitor.getMonitor();
//		this.service = ZkConfigProxyService.getInstance();
		this.service = MetadataConfigProxyService.getInstance();
		lastRecordTS = SystemTimer.currentTimeMillis();
		lastRevReqTotalCnt = monitor.getRevReqTotalCnt();
		lastExceptionTotalCnt = monitor.getExceptionTotalCnt();
		lastProcessTime = monitor.getProcessTime();
		lastGroupReqCnt = new HashMap<String, Long>();
		access_group_tps = new HashMap<String, Long>();
		access_group_onetime_tps = new HashMap<String, Long>();
		groupStartTime = new HashMap<String, Long>();
	}
	
	@Override
	public void run() {
		while (bRun) {
			try {
				Thread.sleep(INTERVAL);
			} catch (InterruptedException e) {
				LOGGER.error(e);
			}
			long currTS = System.currentTimeMillis();
			long currRevReqTotalCnt = monitor.getRevReqTotalCnt();
			long currExceptionCnt = monitor.getExceptionTotalCnt();
			double currProcessTime = monitor.getProcessTime();
			
			long deltaTS = currTS - lastRecordTS;
			long deltaRevReq = currRevReqTotalCnt - lastRevReqTotalCnt;
			double deltaProcessTime = currProcessTime - lastProcessTime;
			
			try {
				//接入机请求时段内平均TPS
				access_request_tps = deltaRevReq*1000 / deltaTS;
				LOGGER.info("Metrics: access_request_tps:"+access_request_tps);
			} catch (Exception e) {
				printErrorLog(e, 	"Error while getting average tps...");
			}
			
			try {
				//接入机异常请求次数
				access_request_excepts = currExceptionCnt - lastExceptionTotalCnt;
				LOGGER.info("Metrics: access_request_excepts:"+access_request_excepts);
			} catch (Exception e) {
				printErrorLog(e, 	"Error while getting exception count...");
			}
			
			try {
				//接入机时段内请求处理平均耗时
				access_process_av_time = deltaProcessTime / deltaRevReq;
				LOGGER.info("Metrics: access_process_av_time:"+access_process_av_time);
			} catch (Exception e) {
				printErrorLog(e, 	"Error while getting average process time...");
			}
				
			try {
				//接入机时段内请求处理最大耗时
				access_process_max_time = monitor.getMaxProcessTime();
				monitor.clearMaxProcessTime();
				LOGGER.info("Metrics: access_process_max_time:"+access_process_max_time);
			} catch (Exception e) {
				printErrorLog(e, 	"Error while getting max process time...");
			}
			
			try {
				//接入机当前客户端连接数
				access_client_conns = monitor.getConnClientCount();
				LOGGER.info("Metrics: access_client_conns:"+access_client_conns);
			} catch (Exception e) {
				printErrorLog(e, 	"Error while getting client connection count...");
			}
			
			try {
				//接入机当前REDIS连接数
				access_redis_conns = monitor.getConnRedisCount();
				LOGGER.info("Metrics: access_redis_conns:"+access_redis_conns);
			} catch (Exception e) {
				printErrorLog(e, 	"Error while getting redis connection count...");
			}
			
			try {
				//接入机当前服务的分组
				access_group_list = service.getProxyInfo().getGroups().split(",");
//				for (String key : monitor.getGroupReqCnt().keySet()) {
//
//					boolean exists = false;
//					for (int i=0; i<access_group_list.length; i++) {
//						if (key.equals(access_group_list[i])) {
//							exists = true;
//							break;
//						}
//					}
//					if (!exists) monitor.removeGroupCnt(key);
//				}
				StringBuilder groups = new StringBuilder("");
				for (int i=0; i<access_group_list.length; i++) {
					groups.append(access_group_list[i]);
					groups.append(",");
				}
				LOGGER.info("Metrics: access_group_list:"+groups);
			} catch (Exception e) {
				printErrorLog(e, 	"Error while getting access groups...");
			}
				
			Map<String, AtomicLong> currGroupReqCnt = monitor.getGroupReqCnt();
			try {
				//接入机分组对应TPS
				for (Map.Entry<String, AtomicLong> entry : currGroupReqCnt.entrySet()) {
					String key = entry.getKey();
					if (!groupStartTime.containsKey(key)) {
						groupStartTime.put(key, currTS-INTERVAL);
					}
					access_group_tps.put(key, entry.getValue().get()*1000 / (currTS-groupStartTime.get(key)));
				}
				StringBuilder groupTPS = new StringBuilder("");
				for (Map.Entry<String, Long> entry : access_group_tps.entrySet()) {
					groupTPS.append(entry.getKey()+":"+entry.getValue()+", ");
				}
				LOGGER.info("Metrics: access_group_tps:"+groupTPS);
			} catch (Exception e) {
				printErrorLog(e, 	"Error while getting group tps...");
			}
			
			try {
				//分组某个时刻的TPS
				for (Map.Entry<String, AtomicLong> entry : currGroupReqCnt.entrySet()) {
					String key = entry.getKey();
					long deltaGroupReq = 0;
					if (!lastGroupReqCnt.containsKey(key)) {
						deltaGroupReq = entry.getValue().get();
					} else {
						deltaGroupReq = entry.getValue().get() - lastGroupReqCnt.get(key);
					}
					access_group_onetime_tps.put(key, deltaGroupReq*1000 / deltaTS);
				}
				StringBuilder groupTPS = new StringBuilder("");
				for (Map.Entry<String, Long> entry : access_group_onetime_tps.entrySet()) {
					groupTPS.append(entry.getKey()+":"+entry.getValue()+", ");
				}
				LOGGER.info("Metrics: access_group_onetime_tps:"+groupTPS);
			} catch (Exception e) {
				printErrorLog(e, 	"Error while getting group one time tps...");
			}

			lastRecordTS = currTS;
			lastRevReqTotalCnt = currRevReqTotalCnt;
			lastExceptionTotalCnt = currExceptionCnt;
			lastProcessTime = currProcessTime;
			lastGroupReqCnt = new HashMap<String, Long>();
			for (Map.Entry<String, AtomicLong> entry : currGroupReqCnt.entrySet()) {
				String key = entry.getKey();
				lastGroupReqCnt.put(key, entry.getValue().get());
			}
		}
	}

	private void printErrorLog(Exception e, String message) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		LOGGER.error(message);
		LOGGER.error(sw);
	}
	
	public synchronized void stopRun() {
		bRun = false;
	}
}
