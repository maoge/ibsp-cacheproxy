package ibsp.cache.access.util;

import java.util.Map;

public interface IMonitor {
	
	public void accessMonitor(Map<String,String> monitorMap);
	
	public void clientMonitor(Map<String,String> monitorMap);
	
	public void redisMonitor(Map<String,String> monitorMap);
	
	public void accessMonitor(Map<String,String> monitorMap,String monitorType);
	
	public void clientMonitor(Map<String,String> monitorMap,String monitorType);
	
	public void redisMonitor(Map<String,String> monitorMap,String monitorType);
	
}
