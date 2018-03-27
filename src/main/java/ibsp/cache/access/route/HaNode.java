package ibsp.cache.access.route;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HaNode {
	
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
	
	public void init() throws IOException {
		if (master == null)
			return;
		
		master.newProcessor();
	}

}
