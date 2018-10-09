package ibsp.cache.access.configure;

import ibsp.cache.access.route.CacheNode;
import ibsp.cache.access.route.GroupInfo;
import ibsp.cache.access.route.HaNode;
import ibsp.cache.access.route.Proxy;
import ibsp.cache.access.route.Slot;
import ibsp.cache.access.util.CONSTS;
import ibsp.cache.access.util.HttpUtils;
import ibsp.cache.access.util.SVarObject;

import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class MetadataConfigProxyService implements IConfigProxyService {
	
	private static final Logger logger = LoggerFactory.getLogger(MetadataConfigProxyService.class);
	
	private static final String SLOT_REGEX = "^\\[(\\d+)\\,\\s*(\\d+)\\]$";
	
	private static final ReentrantLock monitor = new ReentrantLock();
	private static final ReentrantLock updateLock = new ReentrantLock();
	private static MetadataConfigProxyService instance = null;
	
	private boolean                initiated;
	private String                 serviceId;
	private String				   serviceName;
	private String                 proxyId;
	private MetasvrUrlConfig       metasvrUrl;
	private Proxy                  proxy;
	private GroupInfo              groupInfo;  //IBSP接入机不允许服务多个分组
	
	public static MetadataConfigProxyService getInstance(String proxyId, String metaServerAddress) {
		monitor.lock();
		try {
			if(instance==null) instance = new MetadataConfigProxyService(proxyId, metaServerAddress);
		}finally {
			monitor.unlock();
		}
		return instance;
	}

	public static MetadataConfigProxyService getInstance() {
		return instance;
	}

	private MetadataConfigProxyService(String proxyId, String metasvrUrl) {
		this.initiated = false;
		this.proxyId = proxyId;
		this.metasvrUrl = new MetasvrUrlConfig(metasvrUrl);
	}

	@Override
	public void close() {
		metasvrUrl.close();
	}
	
	public String getMetasvrUrl() {
		return metasvrUrl.getNextUrl();
	}
	
	public void putBrokenUrl(String url) {
		metasvrUrl.putBrokenUrl(url);
	}
	
	public void doUrlCheck() {
		metasvrUrl.doUrlCheck();
	}
	
	public String getProxyID() {
		return proxyId;
	}
	
	@Override
	/**
	 * 从meta server拉取配置
	 */
	public void loadConfigInfo() {
		
		if (this.initiated) return;
		String proxyInfoUrl = String.format("%s/%s/%s?%s", metasvrUrl.getNextUrl(), 
				CONSTS.CACHE_SERVICE, CONSTS.FUN_GET_PROXY_INFO, "INST_ID="+this.proxyId);
		updateLock.lock();
		
		try {
			//init proxy info
			boolean bRWSep = false;
			SVarObject sVarInvoke = new SVarObject();
			boolean retInvoke = HttpUtils.getData(proxyInfoUrl, sVarInvoke);
			if (retInvoke) {
				JSONObject jsonObj = JSONObject.parseObject(sVarInvoke.getVal());
				if (jsonObj.getIntValue(CONSTS.JSON_HEADER_RET_CODE) == CONSTS.REVOKE_OK) {
					JSONObject object = jsonObj.getJSONObject(CONSTS.JSON_HEADER_RET_INFO);
					String sRWSep = object.getString(CONSTS.JSON_HEADER_RW_SEPARATE);
					bRWSep = (sRWSep != null) && sRWSep.equals(CONSTS.RW_SEP_TRUE) ? true : false;
					if (this.proxy == null) this.proxy = new Proxy();
					
					this.serviceId = object.getString(CONSTS.JSON_HEADER_SERV_ID);
					this.serviceName = object.getString(CONSTS.JSON_HEADER_SERV_NAME);
					
					proxy.setAddress(object.getString(CONSTS.JSON_HEADER_IP)+":"+object.getString(CONSTS.JSON_HEADER_PORT));
					proxy.setGroups(this.serviceName);
					proxy.setJmxport(Integer.parseInt(object.getString(CONSTS.JSON_HEADER_STAT_PORT)));
					proxy.setProxyName(this.proxyId);
				} else {
					logger.error("接入机初始化异常！"+jsonObj.getString(CONSTS.JSON_HEADER_RET_INFO));
				}
			}
			
			String cacheNodeUrl = String.format("%s/%s/%s?%s", metasvrUrl.getNextUrl(), 
					CONSTS.CACHE_SERVICE, CONSTS.FUN_GET_CLUSTER_INFO, "SERV_ID="+this.serviceId);
			
			//init cluster info
			if (proxy != null && this.serviceId != null) {
				retInvoke = HttpUtils.getData(cacheNodeUrl, sVarInvoke);
				if (retInvoke) {
					JSONObject jsonObj = JSONObject.parseObject(sVarInvoke.getVal());
					if (jsonObj.getIntValue(CONSTS.JSON_HEADER_RET_CODE) == CONSTS.REVOKE_OK) {
						JSONArray array = jsonObj.getJSONArray(CONSTS.JSON_HEADER_RET_INFO);
						if (groupInfo == null) this.groupInfo = new GroupInfo(serviceId, bRWSep);
						
						int clusterSize = array.size();
						for (int i = 0; i < clusterSize; i++) {
							JSONObject nodeClusterJson = array.getJSONObject(i);
							
							String haNodeId = nodeClusterJson.getString(CONSTS.JSON_HEADER_CLUSTER_NODE_ID);
							String slot = nodeClusterJson.getString(CONSTS.JSON_HEADER_CACHE_SLOT);
							JSONArray nodeArr = nodeClusterJson.getJSONArray(CONSTS.JSON_HEADER_CACHE_NODE);
							int nodeSize = nodeArr.size();
							
							HaNode haNode = new HaNode(haNodeId);
							for (int j = 0; j < nodeSize; j++) {
								JSONObject nodeJson = nodeArr.getJSONObject(j);
								String nodeId  = nodeJson.getString(CONSTS.JSON_HEADER_CACHE_NODE_ID);
								String ip      = nodeJson.getString(CONSTS.JSON_HEADER_IP);
								String port    = nodeJson.getString(CONSTS.JSON_HEADER_PORT);
								boolean master = nodeJson.getString(CONSTS.JSON_HEADER_TYPE).equals(CONSTS.NODE_TYPE_MASTER);
								
								CacheNode node = new CacheNode(nodeId, ip, Integer.valueOf(port));
								if (master) {
									haNode.setMaster(node);
								} else {
									haNode.addSlave(node);
								}
							}
							
							haNode.init();
							processSlot(slot, haNode);
							logger.info("load group info: "+this.groupInfo.toString());
						}
					} else {
						logger.error("接入机slot初始化异常！"+jsonObj.getString(CONSTS.JSON_HEADER_RET_INFO));
					} 
				}
			}

			this.initiated = true;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		} finally {
			updateLock.unlock();
		}
	}
	
	public void doHASwitch(String servId, String clusterId, String newMasterId) {
		if (servId.equals(groupInfo.getGroupid())) {
			HaNode haNode = groupInfo.getHaNode(clusterId);
			haNode.doHaSwitch(newMasterId);
		}
	}
	
	private void processSlot(String slotInfo, HaNode haNode) {
		String[] slots = slotInfo.split(CONSTS.SLOT_SPLITER);
		Pattern pattern = Pattern.compile(SLOT_REGEX);
		
		for (String s : slots) {
			Matcher matcher = pattern.matcher(s);
			if (matcher.find()) {
				int startSlot = Integer.valueOf(matcher.group(1));
				int endSlot   = Integer.valueOf(matcher.group(2));
				
				Slot slot = new Slot(startSlot, endSlot, haNode);
				groupInfo.addSlot(slot);
			}
		}
		
	}

	public Proxy getProxyInfo() {
		return proxy;
	}

	public GroupInfo getGroupInfo(String groupid) {
		return this.groupInfo;
	}

}
