package ibsp.cache.access.route;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import ibsp.cache.access.configure.ZkConfigProxyService;
import ibsp.cache.access.util.CRC16;

public class GroupInfo {
	private static final Logger Log = LoggerFactory.getLogger(GroupInfo.class);
	private final ReadWriteLock updateLock = new ReentrantReadWriteLock();
	private String groupid;
	private String lastestTime;
	private boolean isRWSep;  // 读写分离开关
    private Map<String, Node> mapNodes;
	
	private SortedSlotArray masterSlotArr;  // master slot 区段列表
	private SortedSlotArray slaveSlotArr;   // slave  slot 区段列表
	
	public enum OperateType {
		READ, WRITE;
	}
	
	public enum NodeType {
		MASTER, SLAVE;
	}
	
	public GroupInfo(String groupid, boolean bRWSep) {
		this.groupid = groupid;
		this.isRWSep = false;
		mapNodes = new ConcurrentSkipListMap<String, Node>();
		
		masterSlotArr = new SortedSlotArray();
		slaveSlotArr  = new SortedSlotArray();
	}
	
	public String getLastestTime() {
		return lastestTime;
	}

	public void setLastestTime(String lastestTime) {
		this.lastestTime = lastestTime;
	}
	
	public boolean isRWSep() {
		return isRWSep;
	}

	public void setRWSep(boolean isRWSep) {
		this.isRWSep = isRWSep;
	}

	/**
	 * 增加节点配置
	 * @param node
	 */
	public void addNode(final Node node) {
		updateLock.writeLock().lock();
		try {
			boolean bMaster = NodeType.MASTER.equals(node.getFlag());
			SortedSlotArray slotArr = bMaster ? masterSlotArr : slaveSlotArr;
			
			int start = node.getStart_slot();
			int end = node.getEnd_slot();
			
			if (bMaster) {
				
				//主节点，先检查槽段是否冲突
				if (slotArr.checkConflict(start, end)) {
					Log.error("~槽段冲突,节点名:["+node.getId()+"],ip:["+node.getIp()+"],port:["+node.getPort()+"]!");
				} else {
					Slot slot = new Slot(start, end);
					slot.addNodeName(node.getId(), node.isEnabled());
					mapNodes.put(node.getId(), node);
					slotArr.addSlot(slot);
				}
				
			} else {

				Slot slot = slotArr.searchAccurately(start, end);
				if (slot==null) {
					// 槽段下挂第一个实例
					slot = new Slot(start, end);
					slot.addNodeName(node.getId(), node.isEnabled());
					mapNodes.put(node.getId(), node);
					slotArr.addSlot(slot);
				} else {
					// 从节点可以继续添加
					slot.addNodeName(node.getId(), node.isEnabled());
					mapNodes.put(node.getId(), node);
				}
			}

			node.newProcessor();
		} catch (IOException e) {
			Log.error("~无法创建redis主机连接,节点名:["+node.getId()+"],ip:["+node.getIp()+"],port:["+node.getPort()+"]!", e);
		} finally {
		    updateLock.writeLock().unlock();
		}
		
	}
	
	/**
	 * 修改节点变更信息 
	 */
	public void updateNode(Node node) {
		boolean bReConnect = false;
		updateLock.writeLock().lock();
        try {
    		Node _node = mapNodes.get(node.getId());
    		if (_node == null)
    			return;
    		
    		bReConnect = !_node.getIp().equals(node.getIp()) || _node.getPort()!=node.getPort();
    		if (bReConnect) {
    			removeNode(_node, false);  			
    			addNode(node);
    		} else {
    			_node.setFlag(node.getFlag());
    			_node.setType(node.getType());
    			if (_node.isEnabled() != node.isEnabled()) {
    				_node.setEnabled(node.isEnabled());
    				updateSlotEnableStat(_node);
    				//如果实例置为不可用，需要在zk上写日志，以便确保所有接入机都已经收到disable信息
//    				if (!node.isEnabled()) {
//    					ZkConfigProxyService service = ZkConfigProxyService.getInstance();
//    					service.writeDisableLog(node.getIp()+":"+node.getPort());
//    				}
    			}
    			//节点配置 slot 变更, 重新配置路由信息
    			if(_node.getStart_slot()!=node.getStart_slot() || _node.getEnd_slot()!=node.getEnd_slot()) {
    				removeNodeFromSlotArr(_node); 
    				
        			_node.setStart_slot(node.getStart_slot());
        			_node.setEnd_slot(node.getEnd_slot());
        			
    				addNodeToSlotArr(node);
    			}
    		}
        } finally {
            updateLock.writeLock().unlock();
        }
	}
	
	private void updateSlotEnableStat(Node node) {
		SortedSlotArray slotArr = node.getType().equals(OperateType.WRITE) ? masterSlotArr : slaveSlotArr;
		Slot slot = slotArr.binarySearch(node.getStart_slot());
		if (slot != null) {
			slot.updateEnableStat(node.getId(), node.isEnabled());
		}
	}
	
	/**
	 * 删除节点配置
	 * @param node
	 */
	public void removeNode(Node node, boolean lock) {
		if (lock) {
			updateLock.writeLock().lock();
		}
		try {
			node.notifyClose();
			mapNodes.remove(node.getId());
			removeNodeFromSlotArr(node);
		} finally {
			if (lock) {
				updateLock.writeLock().unlock();
			}
		}
	}
	
	private void addNodeToSlotArr(Node node) {
		boolean bMaster = NodeType.MASTER.equals(node.getFlag());
		SortedSlotArray slotArr = bMaster ? masterSlotArr : slaveSlotArr;
		
		int startNum = node.getStart_slot();
		int endNum = node.getEnd_slot();
		Slot slot = new Slot(startNum, endNum);
		slot.addNodeName(node.getId(), node.isEnabled());
		
		slotArr.addSlot(slot);
	}
	
	private void removeNodeFromSlotArr(Node node) {
		boolean bMaster = NodeType.MASTER.equals(node.getFlag());
		SortedSlotArray slotArr = bMaster ? masterSlotArr : slaveSlotArr;
		
		int start = node.getStart_slot();
		int end = node.getEnd_slot();
		
		Slot slot = slotArr.searchAccurately(start, end);
		
		if (slot==null) {
			Log.error("~删除节点错误，没有找到匹配的槽段：["+node.getId()+"],ip:["+node.getIp()+"],port:["+node.getPort()+"]!");
		} else {
			slot.removeNodeName(node.getId());
			if (slot.getNodeCnt()==0) {
				slotArr.removeSlot(slot);
			}
		}
	}
	
	/**
	 * 删除group的所有节点配置
	 */
	public void removeNodeForALL() {
	    for(Map.Entry<String, Node> mapNode : mapNodes.entrySet()) {
	        removeNode(mapNode.getValue(), true);
	    }
	    mapNodes.clear();
	    
	    masterSlotArr.clear();
	    slaveSlotArr.clear();
	}
	
	/**
	 * 根据slot查找对应主机, 从节点随机返回组中的一个节点
	 * @slot  槽号
	 * @type  读写类型
	 */	
	public Node getClusterNode(int slotNum, OperateType type) throws Exception {
	    updateLock.readLock().lock();
        try {
        	Slot slot = null;
        	// 读写是否分离, 否:读和写操作都在主节点; 是:写主,随机读任意从节点, 但是找不到或没有从节点时读同样操作主节点
        	if (isRWSep) {
        		slot = type.equals(OperateType.WRITE) ? masterSlotArr.binarySearch(slotNum)
					: slaveSlotArr.binarySearch(slotNum);
        		
        		if (slot == null && type.equals(OperateType.READ)) {
        			// 即便读写分离, 当未挂载从或者找不到从时必须使用主来操作
        			slot = masterSlotArr.binarySearch(slotNum);
        		}
        	} else {
        		slot = masterSlotArr.binarySearch(slotNum);
        	}
        	
        	if (slot == null) {
        		throw new Exception("~无法找到与["+slot+"]对应的redis主结点!");
        	}
        	
        	String nodeName = slot.getRandomNode();
        	if (nodeName == null) {
        		// 从节点状态全为enables= false时, 使用主节点
        		if (type.equals(OperateType.READ)) {
        			slot = masterSlotArr.binarySearch(slotNum);
        			nodeName = slot.getRandomNode();
        		}
        		
        		if (nodeName == null)
        			throw new Exception("~无法找到与["+slot+"]对应的redis主结点!");
        	}
        	
        	Node node = mapNodes.get(nodeName);
        	if (node == null)
        		throw new Exception("~无法找到与["+slot+"]对应的redis主结点!");
        	
        	if (type.equals(OperateType.WRITE)) {
        		if (node.isEnabled())
        			return node;
        		else
        			throw new Exception("~slot["+slot+"]对应的主结点已停止写入!");
        	} else {
        		return node;
        	}
        } finally {
            updateLock.readLock().unlock();
        }
	}
	
	/**
	 * 根据key查找对应主机, selectOne:true用随机算法返回主机列表一台主机,false:返回多个主机
	 * @param key
	 * @return Node
	 */
	public Node getClusterNode(byte[] key, OperateType type) throws Exception {
		//计算key的槽号	
		int slot = CRC16.hash(key, key.length) & 0x3FFF;
        return getClusterNode(slot, type);
	}

	public Map<String, Node> getNodes() {
	    return mapNodes;	
	}
	
	public String getGroupid() {
		return groupid;
	}

	public void setGroupid(String groupid) {
		this.groupid = groupid;
	}
	
}
