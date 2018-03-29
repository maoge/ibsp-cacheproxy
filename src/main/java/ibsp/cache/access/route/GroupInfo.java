package ibsp.cache.access.route;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import ibsp.cache.access.util.CRC16;

public class GroupInfo {
	
	private final ReadWriteLock updateLock = new ReentrantReadWriteLock();
	private String groupid;
	private boolean isRWSep;  // 读写分离开关
    private Map<String, HaNode> mapHaNodes;
	
	private SortedSlotArray slotArr;
	
	public enum OperateType {
		READ, WRITE;
	}
	
	public enum NodeType {
		MASTER, SLAVE;
	}
	
	public GroupInfo(String groupid, boolean bRWSep) {
		this.groupid    = groupid;
		this.isRWSep    = bRWSep;
		this.mapHaNodes = new ConcurrentHashMap<String, HaNode>();
		this.slotArr    = new SortedSlotArray();
	}
	
	public boolean isRWSep() {
		return isRWSep;
	}

	public void setRWSep(boolean isRWSep) {
		this.isRWSep = isRWSep;
	}
	
	public void addSlot(final Slot slot) {
		if (slot == null)
			return;
		
		HaNode haNode = slot.getHaNode();
		if (!mapHaNodes.containsKey(haNode.getId())) {
			mapHaNodes.put(haNode.getId(), haNode);
		}
		
		slotArr.addSlot(slot);
	}

	/**
	 * 根据slot查找对应主机, 从节点随机返回组中的一个节点
	 * @slot  槽号
	 * @type  读写类型
	 */	
	public CacheNode getClusterNode(int slotNum, OperateType type) throws Exception {
	    updateLock.readLock().lock();
        try {
        	Slot slot = slotArr.binarySearch(slotNum);
        	if (slot == null) {
        		throw new Exception("~无法找到与["+slot+"]对应的redis主结点!");
        	}
        	
        	return slot.getHaNode().getMaster();
        } finally {
            updateLock.readLock().unlock();
        }
	}
	
	/**
	 * 根据key查找对应主机, selectOne:true用随机算法返回主机列表一台主机,false:返回多个主机
	 * @param key
	 * @return Node
	 */
	public CacheNode getClusterNode(byte[] key, OperateType type) throws Exception {
		//计算key的槽号	
		int slot = CRC16.hash(key, key.length) & 0x3FFF;
        return getClusterNode(slot, type);
	}
	
	public String getGroupid() {
		return groupid;
	}

	public void setGroupid(String groupid) {
		this.groupid = groupid;
	}
	
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder("");
		result.append(this.groupid + ": {");
		for (String key : this.mapHaNodes.keySet()) {
			result.append(key+": {");
			HaNode node = this.mapHaNodes.get(key);
			result.append("master: "+node.getMaster().getIp()+":"+node.getMaster().getPort()+", ");
			for (CacheNode slave : node.getSlaves()) {
				result.append("slave: "+slave.getIp()+":"+slave.getPort()+", ");
			}
			result.delete(result.length()-2, result.length()-1);
			result.append("}, " );
		}
		result.delete(result.length()-2, result.length()-1);
		result.append("}" );
		return result.toString();
	}
}
