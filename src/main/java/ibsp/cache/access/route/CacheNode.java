package ibsp.cache.access.route;

import ibsp.cache.access.Config;
import ibsp.cache.access.redis.processor.IRedisProcessor;
import ibsp.cache.access.redis.processor.NIORedisProcessor;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CacheNode {
	private int hashId;
	private String id;
	private String ip;
	private int port;
	private boolean enabled;
	private IRedisProcessor destProcessor;
	
	private Lock nodeLock = new ReentrantLock();
		
	public CacheNode(String id, String ip, int port) {
		this(id, ip, port, true);
	}

	public CacheNode(String id, String ip, int port, boolean enabled) {
		this.id = id;
		this.ip = ip;
		this.port = port;
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
	
	public int getPort() {
		return this.port;
	}
	
	public void setPort(int port) {
		this.port = port;
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
		result = prime * result + (enabled ? 1231 : 1237);
		result = prime * result + hashId;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((ip == null) ? 0 : ip.hashCode());
		result = prime * result
				+ ((nodeLock == null) ? 0 : nodeLock.hashCode());
		result = prime * result + port;
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
		CacheNode other = (CacheNode) obj;
		if (enabled != other.enabled)
			return false;
		if (hashId != other.hashId)
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (ip == null) {
			if (other.ip != null)
				return false;
		} else if (!ip.equals(other.ip))
			return false;
		if (nodeLock == null) {
			if (other.nodeLock != null)
				return false;
		} else if (!nodeLock.equals(other.nodeLock))
			return false;
		if (port != other.port)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Node [hashId=" + hashId + ", id=" + id + ", ip=" + ip
				+ ", port=" + port + ", enabled=" + enabled
				+ ", nodeLock=" + nodeLock + "]";
	}
	
}