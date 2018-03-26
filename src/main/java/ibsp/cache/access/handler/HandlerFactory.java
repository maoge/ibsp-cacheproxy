package ibsp.cache.access.handler;

import ibsp.cache.access.session.NIOSession;

public interface HandlerFactory {
	
	public ProtocolHandler createHandler(NIOSession session);
	
}
