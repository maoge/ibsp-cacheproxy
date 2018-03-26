package ibsp.cache.access.pool.threadpool;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import ibsp.cache.access.Config;

public final class WorkerPool {
	private static WorkerPool pool;
	
	private final int corePoolSize;
	private final int maxPoolSize;
	private final int keepAliveTime;
	private final int workQueueLen;
	private final ThreadPoolExecutor poolExecutor;
	
	public static void setPool(WorkerPool pool) {
		WorkerPool.pool = pool;
	}
	
	public static WorkerPool getPool() {
		return WorkerPool.pool;
	}
	
	public WorkerPool() {
		
		corePoolSize = Config.getConfig().getThread_pool_coresize();
		maxPoolSize = Config.getConfig().getThread_pool_maxsize();
		keepAliveTime = Config.getConfig().getThread_pool_keepalivetime();
		workQueueLen = Config.getConfig().getThread_pool_workqueue_len();
		
		poolExecutor = new ThreadPoolExecutor(corePoolSize, maxPoolSize,
				keepAliveTime, TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(workQueueLen));
		
	}
	
	public void execute(Runnable command) {
		poolExecutor.execute(command);
	}
	
}
