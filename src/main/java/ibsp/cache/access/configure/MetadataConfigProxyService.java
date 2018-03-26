package ibsp.cache.access.configure;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ctg.itrdc.cache.common.Constants;
import com.ctg.itrdc.cache.common.TaskStatusConstants;
import com.ctg.itrdc.cache.common.cacheconf.bean.AccessConfig;
import com.ctg.itrdc.cache.common.cacheconf.bean.AccessNode;
import com.ctg.itrdc.cache.common.cacheconf.bean.GroupConfig;
import com.ctg.itrdc.cache.common.cacheconf.bean.GroupNode;
import com.ctg.itrdc.cache.common.cacheconf.bean.MasterNode;
import com.ctg.itrdc.cache.common.cacheconf.bean.SlaveNode;
import com.ctg.itrdc.cache.common.exception.CacheConfigException;
import com.ctg.itrdc.cache.common.utils.CacheConfigUtils;
import com.github.zkclient.IZkDataListener;

import ibsp.cache.access.Config;
import ibsp.cache.access.route.GroupInfo;
import ibsp.cache.access.route.Node;
import ibsp.cache.access.route.Proxy;
import ibsp.cache.access.route.GroupInfo.NodeType;
import ibsp.cache.access.route.GroupInfo.OperateType;
import ibsp.cache.access.util.CONSTS;
import ibsp.cache.access.util.HttpUtils;
import ibsp.cache.access.util.RedisUtil;
import ibsp.cache.access.util.SVarObject;

public class MetadataConfigProxyService implements IConfigProxyService {
	
	private static final Logger logger = LoggerFactory.getLogger(MetadataConfigProxyService.class);
	private static final ReentrantLock monitor = new ReentrantLock();
	private static final ReentrantLock updateLock = new ReentrantLock();
	private static MetadataConfigProxyService instance = null;
	
	private boolean                initiated;
	private String                 serviceId;
	private String                 proxyId;
	private MetasvrUrlConfig       metasvrUrl;
	private Proxy                  proxy;
	private GroupInfo              groupInfo;  //IBSP接入机不允许服务多个分组
	private final MasterNodeParseCallback masterParseCallback = new MasterNodeParseCallback();
	private final SlaveNodeParseCallback  slaveParseCallback = new SlaveNodeParseCallback();
	private enum ActionType {
		ADD, UPDATE, REMOVE;
	}

	public static MetadataConfigProxyService getInstance(String serviceId, String proxyId, String metaServerAddress) {
		monitor.lock();
		try {
			if(instance==null) instance = new MetadataConfigProxyService(serviceId, proxyId, metaServerAddress);
		}finally {
			monitor.unlock();
		}
		return instance;
	}

	public static MetadataConfigProxyService getInstance() {
		return instance;
	}

	private MetadataConfigProxyService(String serviceId, String proxyId, String metasvrUrl) {
		this.initiated = false;
		this.serviceId = serviceId;
		this.proxyId = proxyId;
		this.metasvrUrl = new MetasvrUrlConfig(metasvrUrl);
		this.groupInfo = new GroupInfo(serviceId, false);
	}

	@Override
	public void close() {
		metasvrUrl.close();
	}
	
	@Override
	/**
	 * 从meta server拉取配置
	 */
	public void loadConfigInfo() {
		
		if (this.initiated) return;
		String proxyInfoUrl = String.format("%s/%s/%s?%s", metasvrUrl.getNextUrl(), 
				CONSTS.CACHE_SERVICE, CONSTS.FUN_GET_PROXY_INFO, "INST_ID="+this.proxyId);
		String cacheNodeUrl = String.format("%s/%s/%s?%s", metasvrUrl.getNextUrl(), 
				CONSTS.CACHE_SERVICE, CONSTS.FUN_GET_CLUSTER_INFO, "SERV_ID="+this.serviceId);
		updateLock.lock();
		
		try {
			//init proxy info
			SVarObject sVarInvoke = new SVarObject();
			boolean retInvoke = HttpUtils.getData(proxyInfoUrl, sVarInvoke);
			if (retInvoke) {
				JSONObject jsonObj = JSONObject.parseObject(sVarInvoke.getVal());
				if (jsonObj.getIntValue(CONSTS.JSON_HEADER_RET_CODE) == CONSTS.REVOKE_OK) {
					JSONObject object = jsonObj.getJSONObject(CONSTS.JSON_HEADER_RET_INFO);
					if (this.proxy == null) this.proxy = new Proxy();
					
					proxy.setAddress(object.getString("IP")+":"+object.getString("PORT"));
					proxy.setGroups(this.serviceId);
					proxy.setJmxport(Integer.parseInt(object.getString("STAT_PORT")));
					proxy.setProxyName(this.proxyId);
				} else {
					logger.error("接入机初始化异常！"+jsonObj.getString(CONSTS.JSON_HEADER_RET_INFO));
				}
			}
			
			//init cluster info
			if (proxy != null) {
				retInvoke = HttpUtils.getData(cacheNodeUrl, sVarInvoke);
				if (retInvoke) {
					JSONObject jsonObj = JSONObject.parseObject(sVarInvoke.getVal());
					if (jsonObj.getIntValue(CONSTS.JSON_HEADER_RET_CODE) == CONSTS.REVOKE_OK) {
						JSONArray array = jsonObj.getJSONArray(CONSTS.JSON_HEADER_RET_INFO);
						
						//TODO
						System.out.println(array);
					} else {
						logger.error("接入机初始化异常！"+jsonObj.getString(CONSTS.JSON_HEADER_RET_INFO));
					}
				}
			}
			
//			boolean bChangeProxyGroups = false;
//			AccessConfig accessConfig = null;
//			AccessNode accessNode = null;
//			GroupConfig groupConfig = null;
//			StringBuffer sbfConfigData = new StringBuffer();
//			if(!configUtils.chkConfigVersion(accessVersion, zkRootPath + Constants.DEFL_PATH_SPLIT + Constants.DEFL_ACCESS_ZK_PATH_NAME)) {
//				accessConfig = configUtils.getAccessConfig(zkRootPath);
//				accessNode = configUtils.getAccessNode(zkRootPath, proxyName);
//				accessVersion = accessConfig.getVersion();
//				if(accessNode==null) {
//					throw new Exception(proxyName + "节点配置信息为空!");
//				}
//				proxyConfigInfo = JSONObject.toJSONString(accessNode);
//				log.info("load proxyConfigInfo:" + proxyConfigInfo);
//				bChangeProxyGroups = initProxyInfo(accessNode);
//			}
//			sbfConfigData.append("proxy").append("^").append(proxyConfigInfo).append("\r\n"); 				    			
//
//			if(bChangeProxyGroups || !configUtils.chkConfigVersion(groupsVersion, zkRootPath + Constants.DEFL_PATH_SPLIT + Constants.DEFL_GROUPS_ZK_PATH_NAME)) {
//				groupsConfigInfo.clear();
//				groupConfig = configUtils.getGroupConfig(zkRootPath);
//				groupsVersion = groupConfig.getVersion();
//				if(proxy!=null) {
//					Set<String> tmpGroups = new TreeSet<>();
//					GroupNode groupNode = null;
//					String[] groups = proxy.getGroups().split(",");
//					for(String group : groups) {
//						for(GroupNode gNode : groupConfig.getGroupNodes()) {
//							if(gNode.getGroupName().toUpperCase().equals(group.toUpperCase())) {
//								groupNode = gNode;
//								break;
//							}
//						}
//						if(groupNode==null) {
//							log.error(group + "节点信息不存在!");
//						} else {
//							String strGroupsConfig = JSONObject.toJSONString(groupNode);
//							log.info("load groupConfigInfo, groupId:" + group + "," + strGroupsConfig);
//							GroupInfo groupInfo = getGroupInfo(group);
//							if(groupInfo==null) {
//								GroupNode groupNodeTmp = JSONObject.parseObject(strGroupsConfig, GroupNode.class);
//								groupInfo = addGroupInfo(new GroupInfo(group, groupNodeTmp.getIsRWsep()), groupNode); 		
//								initGroupInfo(groupInfo, groupNode);
//							} else {
//								//group 配置信息发生变化
//								if(!groupInfo.getLastestTime().equals(groupNode.getLastestTime())) {
//									updateGroupInfo(groupInfo, groupNode);
//								}
//							}
//							tmpGroups.add(group);
//							groupsConfigInfo.add(group + "^" + strGroupsConfig);
//						}
//					}
//					//删除无效的group信息,即分组不存在proxy的分组列表中
//					String[] oldGroups = proxy.getOldgroups().split(",");
//					for(String group : oldGroups) {
//						if(!tmpGroups.contains(group) && !"".equals(group)) {
//							GroupInfo groupInfo = getGroupInfo(group);					        
//							removeGroupInfo(groupInfo);
//						}
//					}
//					tmpGroups.clear();
//					tmpGroups = null;
//				}
//			}
//
//			for(String groupConfigInfo : groupsConfigInfo) {
//				sbfConfigData.append(groupConfigInfo).append("\r\n");
//			}
//
//			try {
//				FileUtils.writeStringToFile(getConfigFile(), sbfConfigData.toString());
//			} catch (Exception e) {
//				log.error("proxy config file write failed!", e);	
//			}
			this.initiated = true;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			updateLock.unlock();
		}
	}

	public Proxy getProxyInfo() {
		return proxy;
	}

	public GroupInfo getGroupInfo(String groupid) {
		return this.groupInfo;
	}

	private <T> T parse(ParseCallback<T> action, MasterNode mNode, SlaveNode sNode, GroupInfo groupInfo, Node node, ActionType type) throws Exception {
		T t = (T)action.doParse(mNode, sNode, node);
		if(t==null) return null;
		if(t instanceof Node) {
			NodeAction(groupInfo, (Node)t, type);
		} else if (t instanceof Node[]) {
			for(int i=0; i < ((Node[])t).length; i++) 
			{
				if(((Node[])t)[i]!=null) {
					NodeAction(groupInfo, ((Node[])t)[i], type);
				}
			}
		}
		return t;
	}

	private void NodeAction(GroupInfo groupInfo, Node node, ActionType type) throws Exception {
		if(type.equals(ActionType.ADD)) {
			groupInfo.addNode(node);
		} else if(type.equals(ActionType.UPDATE)) {
			groupInfo.updateNode(node);
		} else if(type.equals(ActionType.REMOVE)) {
			groupInfo.removeNode(node, true);
		}
	}

	/**
	 * 初始化group信息
	 * @param groupInfo
	 * @param gNode
	 * @throws Exception
	 */
	private void initGroupInfo(GroupInfo groupInfo, GroupNode gNode) throws Exception {
		Node masterNode = null;
		for(MasterNode mNode : gNode.getNodes()) {
			masterNode = parse(masterParseCallback, mNode, null, groupInfo, new Node(mNode.getMasterName()), ActionType.ADD);
			for(SlaveNode sNode : mNode.getSlaveNodes()) {
				parse(slaveParseCallback, null, sNode, groupInfo, masterNode, ActionType.ADD);										
			}
		}
	}

	/**
	 * 更新group信息
	 * @param groupInfo
	 * @param gNode
	 * @throws Exception
	 */
	private void updateGroupInfo(GroupInfo groupInfo, GroupNode gNode) throws Exception {
		Node masterNode = null;
		List<MasterNode> nodes = gNode.getNodes();
		
		//删除group中无效结点，先删后加，否则会产生槽段冲突
		for(Map.Entry<String, Node> mapNode : groupInfo.getNodes().entrySet()) {
			boolean ok = false;
			for (MasterNode node : nodes) {
				if (node.getMasterName().equals(mapNode.getKey())) {
					ok = true;
					break;
				}
			}
				
			if (!ok) {
				NodeAction(groupInfo, mapNode.getValue(), ActionType.REMOVE);
			}
		}
		
		//新增或更新group结点
		for(MasterNode mNode : gNode.getNodes()) {
			if(groupInfo.getNodes().containsKey(mNode.getMasterName())) {
				masterNode = parse(masterParseCallback, mNode, null, groupInfo, new Node(mNode.getMasterName()), ActionType.UPDATE);
			} else {
				masterNode = parse(masterParseCallback, mNode, null, groupInfo, new Node(mNode.getMasterName()), ActionType.ADD);
			}
			for(SlaveNode sNode : mNode.getSlaveNodes()) {
				if(groupInfo.getNodes().containsKey(sNode.getSlaveName())) {
					parse(slaveParseCallback, null, sNode, groupInfo, masterNode, ActionType.UPDATE);									
				} else {
					parse(slaveParseCallback, null, sNode, groupInfo, masterNode, ActionType.ADD);
				}
			}
		}

		groupInfo.setGroupid(gNode.getGroupName());
		groupInfo.setRWSep(gNode.getIsRWsep());
		groupInfo.setLastestTime(gNode.getLastestTime());
	}



	private class MasterNodeParseCallback implements ParseCallback<Node> {
		public Node doParse(MasterNode mNode, SlaveNode sNode, Node masterNode) throws Exception {
			String[] url = mNode.getConnUrl().split(":");
			//根据node标志状态转换成读写状态
			OperateType operateType = TaskStatusConstants.CONS_DILATATION_DATA_PROCESS.equals(mNode.getStatus()) ? OperateType.READ : OperateType.WRITE;
			return new Node(masterNode.getId(), url[0], Integer.parseInt(url[1]), Integer.parseInt(mNode.getStartSlot()), Integer.parseInt(mNode.getEndSlot()), operateType, NodeType.MASTER, mNode.isEnabled());    								
		}
	}

	private class SlaveNodeParseCallback implements ParseCallback<Node> {
		public Node doParse(MasterNode mNode, SlaveNode sNode, Node masterNode) throws Exception {
			String[] url = sNode.getConnUrl().split(":");
			//根据node标志状态转换成读写状态
			OperateType operateType = TaskStatusConstants.CONS_DILATATION_DATA_PROCESS.equals(sNode.getStatus()) ? OperateType.READ : OperateType.WRITE;
			return new Node(sNode.getSlaveName(), url[0], Integer.parseInt(url[1]), masterNode.getStart_slot(), masterNode.getEnd_slot(), operateType, NodeType.SLAVE, sNode.isEnabled());				
		}
	}

	private interface ParseCallback<T> {
		T doParse(MasterNode mNode, SlaveNode sNode, Node masterNode) throws Exception;
	}

}
