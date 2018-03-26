package ibsp.cache.access.route;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import ibsp.cache.access.Config;
import ibsp.cache.access.redis.processor.IRedisProcessor;
import ibsp.cache.access.redis.processor.NIORedisProcessor;
import ibsp.cache.access.route.GroupInfo.NodeType;
import ibsp.cache.access.route.GroupInfo.OperateType;

public class Node {
	private int hashId;
	private String id;
	private String ip;
	private int port;
	private int start_slot;
	private int end_slot;
	private boolean enabled;
	private NodeType flag;
	private OperateType type;
	private IRedisProcessor destProcessor;
	
	private Lock nodeLock = new ReentrantLock();

	public Node(String id) {
	    this.id = id;
	}
		
	public Node(String id, String ip, int port, int startSlot, int endSlot, OperateType type, NodeType flag) {
		this(id, ip, port, startSlot, endSlot, type, flag, true);
	}

	public Node(String id, String ip, int port, int startSlot, int endSlot, OperateType type, NodeType flag, boolean enabled) {
		this.id = id;
		this.ip = ip;
		this.port = port;
		this.start_slot = startSlot;
		this.end_slot = endSlot;
		this.type = type;
		this.flag = flag;
		this.enabled = enabled;
		this.hashId = hashCode();	
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getStart_slot() {
		return start_slot;
	}

	public void setStart_slot(int start_slot) {
		this.start_slot = start_slot;
	}

	public int getEnd_slot() {
		return end_slot;
	}

	public void setEnd_slot(int end_slot) {
		this.end_slot = end_slot;
	}
	
	public int getPort() {
		return this.port;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public NodeType getFlag() {
		return flag;
	}

	public void setFlag(NodeType flag) {
		this.flag = flag;
	}

	public OperateType getType() {
		return type;
	}

	public void setType(OperateType type) {
		this.type = type;
	}
		
	public int getHashId() {
		return hashId;
	}
	
	public IRedisProcessor getProcessor() {
		return destProcessor;
	}

	public void newProcessor() throws IOException {
		nodeLock.lock();
		try {
			if (destProcessor == null) {
				destProcessor = new NIORedisProcessor(this.ip, this.port, Config.getConfig().getFixed_conn_per_redis());
			}
		} finally {
			nodeLock.unlock();
		}
	}
	
	public void notifyClose() {
		nodeLock.lock();
		try {
			if(destProcessor!=null) {
				destProcessor.destroy();
				destProcessor = null;				
			}
		} finally {
			nodeLock.unlock();
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((ip == null) ? 0 : ip.hashCode());
		result = prime * result + port + start_slot + end_slot;
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
		Node other = (Node) obj;
		if (ip == null) {
			if (other.ip != null)
				return false;
		} else if (!ip.equals(other.ip))
			return false;
		if (port != other.port)
			return false;
		if (!id.equalsIgnoreCase(other.getId()))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Node [hashId=" + hashId + ", id=" + id + ", ip=" + ip
				+ ", port=" + port + ", start_slot=" + start_slot
				+ ", end_slot=" + end_slot + ", enabled=" + enabled + ", flag="
				+ flag + ", type=" + type + ", destProcessor=" + destProcessor
				+ ", nodeLock=" + nodeLock + "]";
	}
	
}