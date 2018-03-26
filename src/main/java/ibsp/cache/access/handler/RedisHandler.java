package ibsp.cache.access.handler;

import java.io.IOException;
import java.nio.BufferOverflowException;

import org.apache.log4j.Logger;

import ibsp.cache.access.exception.RouteException;
import ibsp.cache.access.protocal.reader.InputReador;
import ibsp.cache.access.protocal.reader.NioRedisInputReador;
import ibsp.cache.access.request.RedisRequest;
import ibsp.cache.access.request.Request;
import ibsp.cache.access.respond.Respond;
import ibsp.cache.access.session.NIOSession;

public class RedisHandler extends ProtocolHandler {

	private static final long serialVersionUID = 1L;
	
	public static final Logger logger = Logger.getLogger(RedisHandler.class);

	protected NioRedisInputReador inputReador;
	
	public RedisHandler(NIOSession session) {
		super(session);
		this.inputReador = new NioRedisInputReador(session.getClient());
	}
	
	public InputReador getInputReador() {
		return inputReador;
	}
	
	/**
	 * @return 
	 * @throws RouteException 
	 * @throws IOException 
	 * @throws BufferOverflowException 
	 * */
	@Override
	public Request readFromClient(Request request)
			throws BufferOverflowException, IOException, RouteException {
		((RedisRequest) request).read();

		if (((RedisRequest) request).isFinishedClientRead()) {
			return request;
		} else {
			throw new BufferOverflowException();
		}
	}
	
	@Override
	public Respond readRespond(Request request) throws BufferOverflowException,
			IOException, RouteException {
		return null;
	}

	@Override
	public void recycle() {
		if (inputReador != null)
			inputReador.recycle();
	}

}
