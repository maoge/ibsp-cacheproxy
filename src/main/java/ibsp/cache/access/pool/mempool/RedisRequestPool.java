package ibsp.cache.access.pool.mempool;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import ibsp.cache.access.Config;
import ibsp.cache.access.exception.NotEnoughException;
import ibsp.cache.access.request.RedisRequest;

public class RedisRequestPool {
	private static final Logger LOGGER = Logger.getLogger(RedisRequestPool.class);
	private static RedisRequestPool pool;
	
	private static long timeout = Config.getConfig().getPool_borrow_timeout();
	
	public static void setPool(RedisRequestPool pool) {
		RedisRequestPool.pool = pool;
	}

	public static RedisRequestPool getPool() {
		return RedisRequestPool.pool;
	}
	
	private final BlockQueuePool<RedisRequest> items;
	private AtomicInteger validCounts;  // 方便统计内存池状态
	private int capacity;  // 总容量
	
	private class RedisRequestFactory implements ObjectFactory<RedisRequest> {

		@Override
		public PoolObject<RedisRequest> create() {
			return new PoolObject<RedisRequest>(new RedisRequest());
		}

		@Override
		public void destroy() {
			
		}

		@Override
		public void active(PoolObject<RedisRequest> object) {
			
		}

		@Override
		public void passtive(PoolObject<RedisRequest> object) {
			
		}
		
	}
	
	public RedisRequestPool() {
		capacity = Config.getConfig().getRedis_request_pool_size();
		validCounts = new AtomicInteger(capacity);
		this.items = new BlockQueuePool<RedisRequest>(capacity, capacity, new RedisRequestFactory());
	}
	
	public RedisRequest allocate() {
		RedisRequest node = null;
		
		try {
			if ( (node = items.borrowObject(timeout)) != null)
				validCounts.decrementAndGet();
		} catch (NotEnoughException e) {
			LOGGER.error(e);
		}
		
		if (node.isFinishedClientRead()) {
			LOGGER.warn("Bad request in pool, recycle it......");
			this.recycle(node);
			node = null;
		}
		if (node == null) {
			node = createNewRequest();
		}
		
		return node;
	}
	
	private RedisRequest createNewRequest() {
		return new RedisRequest();
	}

	public void recycle(RedisRequest request) {
		request.clear();
		
		try {
			if (items.returnObject(request, timeout))
				validCounts.incrementAndGet();
		} catch (NotEnoughException e) {
			LOGGER.error(e);
		}
	}
	
	public int getValidCounts() {
		return validCounts.get();
	}
	
}
