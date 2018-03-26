package ibsp.cache.access.configure;

import java.util.Collection;
import java.util.HashSet;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibsp.cache.access.TaskEngine;
import ibsp.cache.access.route.GroupInfo;
import ibsp.cache.access.route.Node;
import ibsp.cache.access.route.Proxy;
import ibsp.cache.access.route.GroupInfo.OperateType;

public class CacheProxyServiceImpl implements ICacheProxyService {
	private static final Logger log = LoggerFactory.getLogger(CacheProxyServiceImpl.class);
    private static final ReentrantLock monitor = new ReentrantLock();
    private static ICacheProxyService instance = null;
	private Collection<IProxyService> proxyService;
    private IConfigProxyService configProxyService;
    
    public static ICacheProxyService getInstance() {
    	monitor.lock();
    	try {
            if(instance==null) instance = new CacheProxyServiceImpl();
    	}finally {
    		monitor.unlock();
    	}
    	return instance;
    }
    
    private CacheProxyServiceImpl() {
    	this.proxyService = new HashSet<IProxyService>();
    }
    
	public IConfigProxyService getConfigProxyService() {
		return configProxyService;
	}

	public void setConfigProxyService(IConfigProxyService configProxyService) {
		this.configProxyService = configProxyService;
		proxyService.add(this.configProxyService);
	}

	public Proxy getProxyInfo() {
		return getConfigProxyService().getProxyInfo();
	}
	
	public GroupInfo getGroupInfo(String groupId) {
		return getConfigProxyService().getGroupInfo(groupId);
	}
	
	/**
	 * 根据groupid, slot, 操作类型 取对应redis实例 
	 */
	@Override
	public Node getDestNode(String groupId, int slot, OperateType type) throws Exception {
		GroupInfo groupInfo = configProxyService.getGroupInfo(groupId);
		if(groupInfo!=null) {
			return groupInfo.getClusterNode(slot, type);
		}
		return null;
	}

	/**
	 * 加载接入机配置
	 */
	@Override
	public void loadConfigInfo(boolean autoRefresh, int interval, TimeUnit timeunit, final ILoadConfigCallback loadConfigCallback) {
		if(autoRefresh) {
			TaskEngine.getInstance().schedule(new TimerTask(){
				public void run() {
					getConfigProxyService().loadConfigInfo();
					loadConfigCallback.handle();
				}
			}, 0, TimeUnit.MILLISECONDS.convert(interval, timeunit));
			log.info("autoRefresh configure ......");
		} else {
			log.info("load zk configure ......");
			getConfigProxyService().loadConfigInfo();
			loadConfigCallback.handle();
		}
	}
	
	public void close() {
	    for(IProxyService service : proxyService) {
	    	service.close();
	    }
	}
}
