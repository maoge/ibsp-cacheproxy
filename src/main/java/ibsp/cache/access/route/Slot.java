package ibsp.cache.access.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import ibsp.cache.access.util.IDGenerator;

public class Slot implements Comparable<Object> {
	
	private long id;

	// startSlot, endSlot不能重叠, 否则compareTo会不正常
	private int startSlot = -1;
	private int endSlot = -1;
	private volatile int nodeCnt;  // slot 区段对应有几个redis实例 (从挂多个)
	private ArrayList<String> nodes;
	private Map<String, Boolean> nodeStatMap; // node 对应状态:enabled标记
	private int seed = 0;
	private static int DEFAULT_SIZE = 4;
	
	public Slot(int startSlot, int endSlot) {
		this.id = IDGenerator.getGeneralIDGenerator().nextID();
		this.startSlot = startSlot;
		this.endSlot = endSlot;
		this.nodeCnt = 0;
		nodes = new ArrayList<String>(DEFAULT_SIZE);
		nodeStatMap = new HashMap<String, Boolean>();
	}
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}
	
	public int getStartSlot() {
		return startSlot;
	}

	public void setStartSlot(int startSlot) {
		this.startSlot = startSlot;
	}

	public int getEndSlot() {
		return endSlot;
	}

	public void setEndSlot(int endSlot) {
		this.endSlot = endSlot;
	}
	
	public boolean isInMargin(int slot) {
		return slot>= startSlot && slot <= endSlot;
	}
	
	public int compare(int slot) {
		if (startSlot > slot)
			return -1;
		
		if (endSlot < slot)
			return 1;
		
		return 0;
	}
	
	public void addNodeName(String node, boolean enableFalg) {
		nodes.add(node);
		nodeStatMap.put(node, enableFalg);
		incNodeCnt();
	}
	
	public void removeNodeName(String node) {
		for (String s : nodes) {
			if (s.equals(node)) {
				nodes.remove(s);
				nodeStatMap.remove(node);
				decNodeCnt();
				break;
			}
		}
		
	}
	
	public void updateEnableStat(String nodeName, boolean stat) {
		nodeStatMap.put(nodeName, stat);
	}
	
	public void incNodeCnt() {
		nodeCnt++;
	}
	
	public void decNodeCnt() {
		nodeCnt--;
	}
	
	public int getNodeCnt() {
		return nodeCnt;
	}
	
	public String getRandomNode() {
		if (nodeCnt > 1) {
			// 有多个节点时循环找到一个状态为enabled的节点
			String nodeName = null;
			for (int i=0; i < nodeCnt; i++) {
				nodeName = nodes.get(seed++ % nodeCnt);
				if (nodeStatMap.get(nodeName))
					return nodeName;
			}
			return null;
		} else if (nodeCnt == 1) {
			return nodes.get(0);
		} else
			return null;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + endSlot;
		result = prime * result + startSlot;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Slot other = (Slot) obj;
		if (endSlot != other.endSlot)
			return false;
		if (startSlot != other.startSlot)
			return false;
		return true;
	}

	@Override
	public int compareTo(Object o) {
		if (this.equals(o))
			return 0;
		
		Slot other = (Slot) o;
		if (startSlot < other.startSlot)
			return -1;
		if (endSlot > other.endSlot)
			return 1;
		
		return 0;
	}

	@Override
	public String toString() {
		return "Slot [id=" + id + ", startSlot=" + startSlot + ", endSlot="
				+ endSlot + "]";
	}
	
}
