package ibsp.cache.access.pool.mempool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import ibsp.cache.access.Config;
import ibsp.cache.access.exception.NotEnoughException;

public class BufferPool {
	private static final Logger LOGGER = Logger.getLogger(BufferPool.class);
	
	private static BufferPool pool;
	private static long timeout = Config.getConfig().getPool_borrow_timeout();
	
	public static void setPool(BufferPool pool) {
		BufferPool.pool = pool;
	}

	public static BufferPool getPool() {
		return BufferPool.pool;
	}
	
	private int level0 = 128;
	private int level1 = 2176;
	private int level2 = 32768;
	private int level3 = 1048576;
	
	private int[] level_tab;
	private static AtomicInteger[] count_tab = { new AtomicInteger(0), new AtomicInteger(0), new AtomicInteger(0), new AtomicInteger(0) };
	private final List<BlockQueuePool<BufferProxy>> items = new ArrayList<BlockQueuePool<BufferProxy>>();
	
	private class ByteBufferFactory implements ObjectFactory<BufferProxy> {

		private int chunksize;
		
		public ByteBufferFactory(int chunksize) {
			this.chunksize = chunksize;
		}
		
		@Override
		public PoolObject<BufferProxy> create() {
			return new PoolObject<BufferProxy>(new BufferProxy(chunksize, true));
		}

		@Override
		public void destroy() {
			
		}

		@Override
		public void active(PoolObject<BufferProxy> object) {
			
		}

		@Override
		public void passtive(PoolObject<BufferProxy> object) {
			
		}
		
	}
	
	public BufferPool() {
		level0 = Config.getConfig().getBytebuffer_level0();
		level1 = Config.getConfig().getBytebuffer_level1();
		level2 = Config.getConfig().getBytebuffer_level2();
		level3 = Config.getConfig().getBytebuffer_level3();
		
		level_tab = new int[]{ level0, level1, level2, level3 };
		
		for (int i = 0; i < level_tab.length; i++) {
			int count = Config.getConfig().getBuffer_pool_level_size(i);
			BlockQueuePool<BufferProxy> byteBuffPool = new BlockQueuePool<BufferProxy>(count,count, new ByteBufferFactory(level_tab[i]));
			
			items.add(byteBuffPool);
			count_tab[i].set(count);
		}
	}
	
	public BufferProxy allocate(int size) {
		int level = getLevel(size);
		
		if (level != -1) {
			BufferProxy node = null;
			
			BlockQueuePool<BufferProxy> byteBuffPool = items.get(level);
			try {
				if ( (node = byteBuffPool.borrowObject(timeout)) != null)
					count_tab[level].decrementAndGet();
			} catch (NotEnoughException e) {
				//LOGGER.error(e);
				node = createTempBuffer(level_tab[level]);
			}
			
			if (node == null) {
				node = createTempBuffer(level_tab[level]);
			}
			
			return node;
		} else {
			return createTempBuffer(size);
		}
		
//		return createTempBuffer(size);
	}
	
	public void recycle(BufferProxy buffer) {
		int byteSize = buffer.getBuffer().capacity();
		if (!checkValid(byteSize))
			return;
		
		buffer.clear();
		
		int level = getLevel(byteSize);
		BlockQueuePool<BufferProxy> byteBuffPool = items.get(level);
		if (byteBuffPool != null) {
			try {
				if (byteBuffPool.returnObject(buffer, timeout))
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

	private BufferProxy createTempBuffer(int size) {
		return new BufferProxy(size, false);
	}
	
	public AtomicInteger[] getCountTab() {
		return count_tab;
	}
	
}
