package ibsp.cache.access.pool.mempool;

import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import ibsp.cache.access.exception.NotEnoughException;
import ibsp.cache.access.util.SystemTimer;

public class BlockQueuePool<T> {
	private static final Logger logger = Logger.getLogger(BlockQueuePool.class);
	private ArrayBlockingQueue<PoolObject<T>> idleObjects;
	private java.util.HashMap<T, PoolObject<T>> allObjects;
	private int max;
	protected final ObjectFactory<T> fatory;
	private AtomicInteger rssucc = new AtomicInteger();
	private AtomicInteger getsucc = new AtomicInteger();
	private AtomicInteger getnsucc = new AtomicInteger();
	
	protected BlockQueuePool(ObjectFactory<T> fatory){
		this.fatory = fatory;
	};

	public BlockQueuePool(int max,ObjectFactory<T> fatory){
		this(max, max, fatory);
	}
	
	/**
	 * @param max 兑现池最大缓存数量-初始化时创建
	 * @param fatory
	 */
	public BlockQueuePool(int initsize, int max,ObjectFactory<T> fatory) {
		super();
		idleObjects = new ArrayBlockingQueue<PoolObject<T>>(max);
		allObjects = new HashMap<T, PoolObject<T>>(max);
		this.max = max;
		if (initsize >max) {
			initsize = max;
		}
		this.fatory = fatory;
		for (int i =0 ; i < initsize; ) {
			PoolObject<T> p = create();
			if (p !=null) {
				idleObjects.add(p);
				i++;
			}
		}
	}


	private synchronized PoolObject<T> create() {
		PoolObject<T>  p = null;
		try {
			p = this.fatory.create();
			allObjects.put(p.getObject(), p);
			return p;
		} catch (Exception e) {
			if (p !=null ) {
				allObjects.remove(p.getObject());
			}
			logger.error("init pool error:",e);
			return null;
		}
	}


	public T borrowObject(long borrowMaxWaitMillis) throws NotEnoughException {
		PoolObject<T> p = null;
		try {
			p = idleObjects.poll(borrowMaxWaitMillis/2, TimeUnit.MILLISECONDS);
			if (p !=null ) {
				getsucc.incrementAndGet();
				return p.using();
			} else if (allObjects.size() < max) {
				p = create();
				getnsucc.incrementAndGet();
				return p.using();
			} else {
				throw new NotEnoughException("not enough buffer");
			}
		}catch (InterruptedException e) {
			throw new NotEnoughException("InterruptedException",e);
		}
	}


	public boolean returnObject(T obj,long borrowMaxWaitMillis) throws NotEnoughException {
		PoolObject<T> p = allObjects.get(obj);

		long c = SystemTimer.currentTimeMillis();
		if (p != null && p.getActivetime() <= c) {
			try {
				boolean rs = idleObjects.offer(p, borrowMaxWaitMillis,TimeUnit.MILLISECONDS);
				if ( !rs ) {
//					若添加失败
					synchronized(this) {
						allObjects.remove(obj);
					}
					logger.info("returnObject fail");
					return false;
				}else {
					rssucc.incrementAndGet();
				}
			} catch (InterruptedException e) {
				throw new NotEnoughException("InterruptedException",e);
			}
			return true;
		}
		return false;
	}


	public AtomicInteger getRssucc() {
		return rssucc;
	}


	public AtomicInteger getGetsucc() {
		return getsucc;
	}


	public AtomicInteger getGetnsucc() {
		return getnsucc;
	}

	public int idlesize() {
		return idleObjects.size();
	}
	
	public int allsize() {
		return allObjects.size();
	}

}
