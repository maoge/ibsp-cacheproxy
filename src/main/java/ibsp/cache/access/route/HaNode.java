package ibsp.cache.access.route;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibsp.cache.access.configure.MetadataConfigProxyService;

public class HaNode {
	
	private static final Logger logger = LoggerFactory.getLogger(HaNode.class);
	
	private String id;
	private CacheNode master;
	private List<CacheNode> slavers;
	
	public HaNode(String id) {
		this.id = id;
		slavers = new ArrayList<CacheNode>();
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setMaster(CacheNode node) {
		this.master = node;
	}
	
	public CacheNode getMaster() {
		return this.master;
	}
	
	public void addSlave(CacheNode node) {
		if (node == null)
			return;
		
		slavers.add(node);
	}
	
	public List<CacheNode> getSlaves() {
		return slavers;
	}
	
	public void init() throws IOException {
		if (master == null)
			return;
		
		master.newProcessor();
	}

	public void doHaSwitch(String newMasterId) {
		CacheNode newMaster = null;
		for (CacheNode slave : slavers) {
			if (slave.getId().equals(newMasterId)) {
				newMaster = slave;
				break;
			}
		}
		
		try {
			newMaster.newProcessor();
			this.slavers.remove(newMaster);
			CacheNode oldMaster = this.master;
			this.master = newMaster;
			this.slavers.add(oldMaster);
			oldMaster.notifyClose();
			
			logger.info("redis HA switch success, new master is "+newMaster.getIp()+":"+newMaster.getPort());;
		} catch (Exception e) {
			logger.error("HA node do redis ha switch failed...", e);
		}
	}
}
