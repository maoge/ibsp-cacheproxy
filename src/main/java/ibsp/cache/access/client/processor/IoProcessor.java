package ibsp.cache.access.client.processor;

import ibsp.cache.access.session.NIOSession;

public interface IoProcessor {
	
	public void addSession(NIOSession session);
	
	public void closeSession(NIOSession session);
	
	public void destroy();

}
