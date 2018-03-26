package ibsp.cache.access.request;

import ibsp.cache.access.exception.RouteException;
import ibsp.cache.access.handler.ProtocolHandler;
import ibsp.cache.access.session.NIOSession;

public abstract class Request {
	
	protected volatile NIOSession session;
	
	public Request() {
		
	}
	
	public Request(NIOSession  session) {
		this.session = session;
	}
	
	public ProtocolHandler getProtocolHandler() {
		return session.getProtocolHandler();
	}

	public NIOSession getSession() {
		return this.session;
	}

	public void exceptionEnd(RouteException setErrorCode, boolean needDecReq) {
		session.exceptionEnd(setErrorCode, setErrorCode.getSendBack(),
				setErrorCode.getCloseClient(), needDecReq);
	}
	
	public void intrestClientRead() {
		session.intrestClientRead();
	}
	
}
