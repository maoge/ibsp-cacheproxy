package ibsp.cache.access.redis.processor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import ibsp.cache.access.Config;
import ibsp.cache.access.exception.RouteException;
import ibsp.cache.access.exception.RouteException.ROUTERRERINFO;
import ibsp.cache.access.pool.mempool.BufferPool;
import ibsp.cache.access.pool.mempool.BufferProxy;
import ibsp.cache.access.pool.mempool.ByteArray;
import ibsp.cache.access.pool.mempool.ByteArrayPool;
import ibsp.cache.access.pool.mempool.RedisRequestPool;
import ibsp.cache.access.redis.connection.IRedisConnection;
import ibsp.cache.access.redis.connection.NIORedisConnection;
import ibsp.cache.access.request.RedisRequest;
import ibsp.cache.access.request.Request;
import ibsp.cache.access.util.CONSTS;
import ibsp.cache.access.util.CommUtil;
import ibsp.cache.access.util.HealthMonitor;
import ibsp.cache.access.util.IDGenerator;

public class NIORedisProcessor implements IRedisProcessor, Runnable {
	
	public static final Logger logger = Logger.getLogger(NIORedisProcessor.class);
	
	private BufferPool buffPool;
	private ByteArrayPool byteArrPool;
	private RedisRequestPool requestPool;
	private long maxIdleTime;
	private int revBuffSize;
	
	private String ip;
	private int port;
	private String connUrl;
	
	private Selector selector;
	
	private int connNumPerRedis;
	private volatile boolean isConnected = false;
	private AtomicBoolean bNeedClose = new AtomicBoolean(false);
	
	private IRedisConnection[] redisConns;
	
	private ConcurrentLinkedQueue<NIORedisConnection> connToReg;
	private AtomicInteger idleConnCnt;  // 闲置连接数, 便于监控拿
	
	private volatile boolean bRunning = false;
	
	private int randomIdx = 0;
	
	private LiveChecker checker;
	private ScheduledExecutorService checkExecutor;
	
	public NIORedisProcessor(String ip, int port, int connNumPerRedis) throws IOException {
		super();
		
		this.buffPool = BufferPool.getPool();
		this.byteArrPool = ByteArrayPool.getPool();
		this.requestPool = RedisRequestPool.getPool();
		this.maxIdleTime = Config.getConfig().getSelect_wait_time();
		this.revBuffSize = Config.getConfig().getSo_revbuf_size();
		
		this.selector = Selector.open();
		this.connNumPerRedis = connNumPerRedis;
		
		this.ip = ip;
		this.port = port;
		this.connUrl = ip + ":" + port;
		
		this.bNeedClose = new AtomicBoolean(false);
		
		redisConns = new IRedisConnection[connNumPerRedis];
		connToReg = new ConcurrentLinkedQueue<NIORedisConnection>();//new ArrayBlockingQueue<NIORedisConnection>(connNumPerRedis);
		idleConnCnt = new AtomicInteger();
		
		createConnections(connNumPerRedis);
		
		if (isConnected) {
			bRunning = true;
			Thread processThread = new Thread(this);
			processThread.setDaemon(false);
			processThread.setName("NIORedisProcessor_" + ip + "_" + port);
			processThread.start();
		}
		
		this.checker = new LiveChecker();
		long interval = Config.getConfig().getRedis_check_interval();
		checkExecutor = Executors.newScheduledThreadPool(1);
		checkExecutor.scheduleAtFixedRate(checker, interval, interval, TimeUnit.MILLISECONDS);
	}
	
	public void createConnections(int connNumPerRedis) throws IOException {
		for (int i = 0; i < connNumPerRedis; i++) {
			try {
				connect(i);
			} catch (IOException e) {
				logger.error(e.getStackTrace(), e);
				
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
				}
				
				// 重试
				connect(i);
			}
			HealthMonitor.getMonitor().incConnRedisCount();
		}
		
		isConnected = true;
	}
	
	private void connect(int i) throws IOException {
		NIORedisConnection conn = new NIORedisConnection(ip, port, this);
		conn.connect();
		
		redisConns[i] = conn;
		idleConnCnt.incrementAndGet();
	}
	
	public void addRegist(NIORedisConnection conn) {
		this.connToReg.offer(conn);
		this.selector.wakeup();
	}
	
	public boolean isConnected() {
		return isConnected;
	}
	
	@Override
	public void run() {
		while(bRunning) {
			try {
				doRegist();
				
				if (selector.select(maxIdleTime) == 0)
					continue;
				
				dispatch();
			} catch (Exception e) {
				logger.error("DestRedisProcessor 异常",e);
			}
		}
	}
	
	private void dispatch() {
		Iterator<SelectionKey> it = selector.selectedKeys().iterator();
		while (it.hasNext()) {
			SelectionKey selectKey = (SelectionKey) it.next();
			it.remove();
			
			if (!selectKey.isValid())
				continue;
			
			if (selectKey.isReadable()) {
				NIORedisConnection conn = (NIORedisConnection) selectKey.attachment();
				
				BufferProxy buf = buffPool.allocate(revBuffSize);
				int len = 0;
				try {
					len = conn.read(buf.getBuffer());
					
					if (len > 0) {
						buf.getBuffer().flip();
						conn.addParseData(buf);
					} else {
						buffPool.recycle(buf);
						buf = null;
						
						if (len < 0) {
							setDisConnected();
							conn.setDisConnected();
							logger.error("read error ......");
						} else if (len == 0) {
							logger.info("buffer full ......");
						}
					}
				} catch (IOException e) {
					logger.error(e.getStackTrace(), e);
					buffPool.recycle(buf);
					buf = null;
					
					setDisConnected();
					conn.setDisConnected();
				}
			}
		}
	}
	
	public void printBuffer(ByteBuffer buf) {
		StringBuffer sBuf = new StringBuffer("");
		int buflen = buf.limit();
		
		byte b = 0x00;
		for (int i = 0; i < buflen; i++) {
			b = buf.get(i);
			if (b == 13) {
				sBuf.append("\\r");
			} else if (b == 10) {
				sBuf.append("\\n");
			} else {
				sBuf.append((char)b);
			}
		}
		
		String s = sBuf.toString();
		
		System.out.println("rev from redis len:" + buflen);
		System.out.println("rev from redis context:" + s);
	}
	
	public void setDisConnected() {
		isConnected = false;
	}
	
	private void doRegist() {
		NIORedisConnection conn = null;
		
		while ((conn = connToReg.poll()) != null) {
			if (conn != null) {
				init(conn);
			}
		}
	}
	
	private void init(NIORedisConnection conn) {
		try {
			selector.selectNow();
			
			SocketChannel channel = conn.getChannel();
			SelectionKey selectkey = channel.register(selector, SelectionKey.OP_READ, conn);
			
			if (selectkey == null) {
				logger.error("DestRedisConnection sockchannel register OP_READ error ......");
			}
			
			conn.setSelKey(selectkey);
		} catch (ClosedChannelException e) {
			logger.error(e);
			
			try {
				conn.reconnect();
			} catch (IOException e1) {
				logger.error(e1);
			} finally {
				//idleConnCnt.incrementAndGet();
			}
		} catch (IOException e) {
			// selectNow error
			logger.error(e);
			//idleConnCnt.incrementAndGet();
		}
	}
	
	public void dispatchRequest(Request request) throws RouteException {
		if (isConnected) {
			int idx = randomIdx++ % connNumPerRedis;
			NIORedisConnection conn = (NIORedisConnection) redisConns[idx];
			conn.addRequest((RedisRequest) request);
		} else {
			RouteException e = new RouteException("redis disconnected, reconnecting ......", ROUTERRERINFO.e17, (RedisRequest)request);
			throw e;
		}
	}
	
	public void destroy() {
		bRunning = false;
		bNeedClose.set(true);
		
		checkExecutor.shutdown();
		isConnected = true;
		
		try {
			selector.close();
		} catch (IOException e) {
			logger.error(e.getStackTrace(), e);
		}
		
		destroyConnections();
	}
	
	private void destroyConnections() {
		for (IRedisConnection conn : redisConns) {
			if (conn != null) {
				try {
					conn.distroy();
				} catch (IOException e) {
					logger.error(e.getStackTrace(), e);
				}
				HealthMonitor.getMonitor().decConnRedisCount();
			}
		}
	}

	public String getConnUrl() {
		return connUrl;
	}

	public int getConnNumPerRedis() {
		return this.connNumPerRedis;
	}

	public int getIdleConnCnt() {
		return this.idleConnCnt.get();
	}
	
	// 功能: 如果isConnected false 做重连操作, true 则发送PING命令
	public static final Logger checkLogger = Logger.getLogger(LiveChecker.class);
	private class LiveChecker implements Runnable {
		
		private ByteArray idByteArr;
		
		public LiveChecker() {
			idByteArr = byteArrPool.allocate(CONSTS.PROTO_ID_MAXLEN);
		}
		
		@Override
		public void run() {
			
			if (isConnected) {
				sendPing();
			} else {
				reconnect();
			}
			
		}
		
		private void sendPing() {
			for (IRedisConnection conn : redisConns) {
				NIORedisConnection redisConn = (NIORedisConnection) conn;
				RedisRequest request = requestPool.allocate();
				try {
					BufferProxy resp = buffPool.allocate(CONSTS.CMD_PING_LEN);
					long id = IDGenerator.getReqIDGenerator().nextID();
					putBytes(resp, id);
					resp.flip();
					
					request.setResp(resp);
					request.setRedisReqId(id);
					redisConn.addRequest(request);
				} catch (RouteException e) {
					// PING 检测命令如果加不进队列，错误不用处理
					checkLogger.error(e.getStackTrace(), e);
				}
			}
		}
		
		private void putBytes(BufferProxy buf, long id) {
			buf.put(CONSTS.PROTO_HEAD);
			
			int idLen = CommUtil.getLongLength(id);
			CommUtil.getLongBytes(id, idLen, idByteArr.getBytes());
			buf.put(idByteArr.getBytes(), 0, idLen);
			idByteArr.clear();
			
			buf.put(CONSTS.CRLF);
			buf.put(CONSTS.CMD_PING);
		}
		
		private void reconnect() {
			boolean noError = true;
			
			for (IRedisConnection conn : redisConns) {
				try {
					conn.reconnect();
					isConnected = true;
				} catch (IOException e) {
					noError = false;
				}
				
				if (!noError)
					break;
			}
			
			if (noError)
				checkLogger.info("重连成功 ...... redis: " + ip + ":" + port);
			else
				checkLogger.error("重连失败 ...... redis: " + ip + ":" + port);
		}
		
	}

}
