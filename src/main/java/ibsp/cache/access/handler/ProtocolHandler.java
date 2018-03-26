package ibsp.cache.access.handler;

import java.io.IOException;
import java.io.Serializable;
import java.nio.BufferOverflowException;

import ibsp.cache.access.exception.RouteException;
import ibsp.cache.access.protocal.reader.InputReador;
import ibsp.cache.access.request.Request;
import ibsp.cache.access.respond.Respond;
import ibsp.cache.access.session.NIOSession;

public abstract class ProtocolHandler implements Serializable {
	private static final long serialVersionUID = 1L;
	protected final NIOSession session;
	public ProtocolHandler(NIOSession session) {
		super();
		this.session = session ;
	}
	
	public abstract Request readFromClient(Request request) throws BufferOverflowException, IOException, RouteException;
	
	public abstract Respond readRespond(Request request) throws BufferOverflowException, IOException, RouteException;
	
	public abstract InputReador getInputReador();
	
	public abstract void recycle(); 
}
