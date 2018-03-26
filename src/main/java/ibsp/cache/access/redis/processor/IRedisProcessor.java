package ibsp.cache.access.redis.processor;

import ibsp.cache.access.exception.RouteException;
import ibsp.cache.access.request.Request;

public interface IRedisProcessor {

	public boolean isConnected();
	
	public void destroy();

	public String getConnUrl();

	public int getConnNumPerRedis();

	public int getIdleConnCnt();
	
	public void dispatchRequest(Request request) throws RouteException;

}
