package ibsp.cache.access.configure;

import java.util.concurrent.TimeUnit;

import ibsp.cache.access.route.CacheNode;
import ibsp.cache.access.route.GroupInfo;
import ibsp.cache.access.route.Proxy;
import ibsp.cache.access.route.GroupInfo.OperateType;

public interface ICacheProxyService {

	public IConfigProxyService getConfigProxyService();
	
	public void setConfigProxyService(IConfigProxyService configProxyService);
	
	public Proxy getProxyInfo();

	public GroupInfo getGroupInfo(String groupId);
	
	/**
	 * 根据groupid, slot, 操作类型获取对应的redis实例 集合.
     * @param groupId
     * @param slot
     * @param type
     * @return
	 * 
	 */
	public CacheNode getDestNode(String groupId, int slot, OperateType type) throws Exception;
	
	/**
	 * 获取接入层完整配置信息
	 */
	public void loadConfigInfo(boolean autoRefresh, int interval, TimeUnit timeunit, ILoadConfigCallback loadConfigCallback);
}
