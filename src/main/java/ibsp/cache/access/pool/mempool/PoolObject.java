package ibsp.cache.access.pool.mempool;

import ibsp.cache.access.util.SystemTimer;

public class PoolObject<T> {

	private T object;
	private long activetime;
	
	PoolObject(){
		
	}
	
	public PoolObject(T object) {
		this.object = object;
		this.activetime = SystemTimer.currentTimeMillis();
	}
	
	T getObject() {
		return object;
	}
	
	public T using(){
		this.activetime = SystemTimer.currentTimeMillis();
		return object;
	}
	

	public long getActivetime() {
		return activetime;
	}
	
	

}
