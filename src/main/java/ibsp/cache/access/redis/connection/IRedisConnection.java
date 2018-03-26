package ibsp.cache.access.redis.connection;

import java.io.IOException;
import java.nio.ByteBuffer;

import ibsp.cache.access.exception.RouteException;
import ibsp.cache.access.request.Request;

public interface IRedisConnection {

	public void close() throws IOException;
	
	public void connect() throws IOException;
	
	public void reconnect() throws IOException;
	
	public int read(ByteBuffer dst) throws IOException;
	
	public void distroy() throws IOException;
	
	public void addRequest(Request request) throws RouteException;
	
}
