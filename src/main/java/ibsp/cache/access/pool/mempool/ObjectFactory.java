package ibsp.cache.access.pool.mempool;

public interface ObjectFactory<T> {
	
	
	/**
	 * 新建
	 * @return
	 */
	public PoolObject<T> create();
	
	/**
	 * 销毁
	 */
	public void destroy ();
	
	/**
	 * 激活
	 * @param object
	 */
	public void active(PoolObject<T> object);
	
	/**
	 * 钝化
	 * @param object
	 */
	public void passtive(PoolObject<T> object);
}
