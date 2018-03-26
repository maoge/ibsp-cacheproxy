package ibsp.cache.access.respond;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

import ibsp.cache.access.Config;
import ibsp.cache.access.client.processor.ClientIoPrecessor;
import ibsp.cache.access.pool.mempool.BufferProxy;
import ibsp.cache.access.pool.mempool.RedisRequestPool;
import ibsp.cache.access.request.RedisRequest;
import ibsp.cache.access.session.NIOSession;
import ibsp.cache.access.util.HealthMonitor;

public class RespondWriter {

	private static RespondWriter INSTANCE;
	
	private Writer[] writers;
	private int writerCnt;
	
	private RedisRequestPool requestPool;
	
	public RespondWriter() {
		writerCnt = Config.getConfig().getRespond_writer_cnt();
		requestPool = RedisRequestPool.getPool();
		writers = new Writer[writerCnt];
		for (int i = 0; i < writerCnt; i++) {
			Writer writer = new Writer();
			writers[i] = writer;
			Thread t = new Thread(writer);
			t.setDaemon(false);
			t.setName("RespondWriter_" + i);
			t.start();
		}
		
	}
	
	public static RespondWriter getInstance() {
		if (RespondWriter.INSTANCE == null) {
			RespondWriter.INSTANCE = new RespondWriter();
		}
		return INSTANCE;
	}
	
	public void addBack(RedisRequest request) {
		NIOSession session = request.getSession();
		if (session != null) {
			int idx = writerCnt > 1 ? (int)(session.getSessionID() % writerCnt) : 0;
			writers[idx].addBackQueue(request);
		} else {
			// 内部PING操作
			requestPool.recycle(request);
		}
	}
	
	public static final Logger writeLogger = Logger.getLogger(Writer.class);
	private class Writer implements Runnable {
		
		private BlockingQueue<RedisRequest> backQ;
		private RedisRequest lastWriteRequest;
		
		public Writer() {
			super();
			backQ = new ArrayBlockingQueue<RedisRequest>(Config.getConfig().getBackq_max_len());
		}
		
		@Override
		public void run() {
			while (true) {
				try {
					writeToClient();
				} catch (InterruptedException e) {
					writeLogger.error("backQ.take Interrupted ......", e);
				}
			}
		}
		
		public void addBackQueue(RedisRequest request) {
			backQ.offer(request);
		}
		
		public void writeToClient() throws InterruptedException {
			if (lastWriteRequest == null) {
				lastWriteRequest = backQ.take();
			}
			
			if (lastWriteRequest == null) {
				return;
			}
			
			if (lastWriteRequest.getSession() == null) {
				doRecycle();
				return;
			}
			
			boolean needClose = false;
			
			try {
				if (write(lastWriteRequest)) {
					HealthMonitor monitor = HealthMonitor.getMonitor();
					lastWriteRequest.refreshSendBkTs();
					lastWriteRequest.refreshSendBkNs();
					double processTime = lastWriteRequest.getIntervalMs();
					
					monitor.incNormalRepTotalCntAndGet();
					monitor.addProcessTimeAndGet(processTime);
					monitor.setMaxProcessTime(processTime);
					
					if (lastWriteRequest.isNeedClose()) {
						needClose = true;
					}
				}
			} catch (IOException e) {
				needClose = true;
			} finally {
				if (needClose) {
					NIOSession session = lastWriteRequest.getSession();
					
					ClientIoPrecessor precessor = (ClientIoPrecessor) session.getClIoProccessor();
					if (precessor != null) {
						if (session.getClSelectKey() != null) {
							writeLogger.error("RespondWriter remote closed, remote address:" + session.getClientIP() + ":" + session.getClientPort());
							session.destroy();
						}
					}
				}
				
				doRecycle();
			}
		}
		
		private void doRecycle() {
			requestPool.recycle(lastWriteRequest);
			lastWriteRequest = null;
			
			HealthMonitor.getMonitor().decReqInQueue();
		}
		
		private boolean write(RedisRequest request) throws IOException {
			boolean bFinished = false;
			
			BufferProxy buffPoxy = request.getBackResp();
			if (buffPoxy == null) {
				return true;
			}
			ByteBuffer byteBuff = buffPoxy.getBuffer();
			
			NIOSession session = request.getSession();
			if (session == null) {
				return true;
			}
			
			SocketChannel client = session.getClient();
			session.refreshLastTimeForClientWrite();
			if (client != null) {
				client.write(byteBuff);
			} else {
				return true;
			}

			if (!byteBuff.hasRemaining())
				bFinished = true;
			
			return bFinished;
		}
	}
	
}
