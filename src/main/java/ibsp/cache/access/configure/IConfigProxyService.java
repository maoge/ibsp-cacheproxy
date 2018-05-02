package ibsp.cache.access.configure;

import ibsp.cache.access.route.GroupInfo;
import ibsp.cache.access.route.Proxy;

public interface IConfigProxyService extends IProxyService {
	
	/**
	 * 获取接入层完整配置信息
	 */
	public void loadConfigInfo();

	/**
	 * 返回指定groupid分组配置信息
	 * @param groupid
	 * @return
	 */
	public GroupInfo getGroupInfo(String groupid);
	
	/**
	 * 返回指定Proxy配置信息
	 * @param proxyName
	 * @return
	 */
	public Proxy getProxyInfo();
}
