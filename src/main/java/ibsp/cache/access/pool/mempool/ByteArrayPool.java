package ibsp.cache.access.pool.mempool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import ibsp.cache.access.Config;
import ibsp.cache.access.exception.NotEnoughException;

public class ByteArrayPool {
	private static final Logger LOGGER = Logger.getLogger(ByteArrayPool.class);
	private static ByteArrayPool pool;
	
	private int level0 = 10000;
	private int level1 = 10000;
	private int level2 = 100;
	
	private int[] level_tab;
	private static AtomicInteger[] count_tab = { new AtomicInteger(0), new AtomicInteger(0), new AtomicInteger(0) };
	private final List<BlockQueuePool<ByteArray>> items = new ArrayList<BlockQueuePool<ByteArray>>();
	
	private static long timeout = Config.getConfig().getPool_borrow_timeout();
	
	private class ByteArrayFactory implements ObjectFactory<ByteArray> {

		private int chunksize;
		
		public ByteArrayFactory(int chunksize) {
			this.chunksize = chunksize;
		}
		
		@Override
		public PoolObject<ByteArray> create() {
			return new PoolObject<ByteArray>(new ByteArray(chunksize));
		}

		@Override
		public void destroy() {
			
		}

		@Override
		public void active(PoolObject<ByteArray> object) {
			
		}

		@Override
		public void passtive(PoolObject<ByteArray> object) {
			
		}
		
	}
	
	public static void setPool(ByteArrayPool pool) {
		ByteArrayPool.pool = pool;
	}

	public static ByteArrayPool getPool() {
		return ByteArrayPool.pool;
	}
	
	public ByteArrayPool() {
		level0 = Config.getConfig().getBytearray_level0();
		level1 = Config.getConfig().getBytearray_level1();
		level2 = Config.getConfig().getBytearray_level2();
		
		level_tab = new int[]{ level0, level1, level2 };
		
		for (int i = 0; i < level_tab.length; i++) {
			int count = Config.getConfig().getByte_arr_pool_level_size(i);
			BlockQueuePool<ByteArray> byteArrPool = new BlockQueuePool<ByteArray>(count,count, new ByteArrayFactory(level_tab[i]));
			
			items.add(byteArrPool);
			count_tab[i].set(count);
		}
	}
	
	public ByteArray allocate(int size) {
		int level = getLevel(size);
		
		if (level != -1) {
			ByteArray node = null;
			
			BlockQueuePool<ByteArray> byteArrPool = items.get(level);
			try {
				if ( (node = byteArrPool.borrowObject(timeout)) != null)
					count_tab[level].decrementAndGet();
			} catch (NotEnoughException e) {
				LOGGER.error(e);
			}
			if (node == null) {
				node = createByteArray(level_tab[level]);
			}
			return node;
		} else {
			return createByteArray(size);
		}
	}
	
	public void recycle(ByteArray node) {
		int byteSize = node.getCapacity();
		if (!checkValid(byteSize))
			return;
		
		node.clear();
		
		int level = getLevel(byteSize);
		BlockQueuePool<ByteArray> byteArrPool = items.get(level);
		if (byteArrPool != null) {
			try {
				if (byteArrPool.returnObject(node, timeout))
					count_tab[level].incrementAndGet();
			} catch (NotEnoughException e) {
				LOGGER.error(e);
			}
		}
	}
	
	private boolean checkValid(int byteSize) {
		boolean find = false;
		for (int chunkSize : level_tab) {
			if (byteSize == chunkSize) {
				find = true;
				break;
			}
		}
		return find;
	}
	
	private int getLevel(int size) {
		int level = -1;
		boolean find = false;
		for (int chunkSize : level_tab) {
			level++;
			if (size <= chunkSize) {
				find = true;
				break;
			}
		}
		return find ? level : -1;
	}
	
	private ByteArray createByteArray(int size) {
		return new ByteArray(new byte[size]);
	}
	
	public AtomicInteger[] getCountTab() {
		return count_tab;
	}
	
}
