package ibsp.cache.access.handler;

import ibsp.cache.access.session.NIOSession;

public class RedisHandlerFactory implements HandlerFactory {

	@Override
	public ProtocolHandler createHandler(NIOSession session) {
		if (session.getProtocolHandler() == null) {
			RedisHandler handler = new RedisHandler(session);
			session.setProtocolHandler(handler);
		}
		
		return session.getProtocolHandler();
	}

}
