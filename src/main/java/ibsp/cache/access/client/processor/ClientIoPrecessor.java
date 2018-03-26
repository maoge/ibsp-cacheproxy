package ibsp.cache.access.client.processor;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.Logger;

import ibsp.cache.access.Config;
import ibsp.cache.access.exception.RouteException;
import ibsp.cache.access.exception.RouteException.ROUTERRERINFO;
import ibsp.cache.access.pool.threadpool.WorkerPool;
import ibsp.cache.access.request.RedisRequest;
import ibsp.cache.access.route.RouterProcessor;
import ibsp.cache.access.session.NIOSession;
import ibsp.cache.access.util.HealthMonitor;
import ibsp.cache.access.util.SystemTimer;

public class ClientIoPrecessor implements Runnable, IoProcessor {
	public static final Logger logger = Logger.getLogger(ClientIoPrecessor.class);
	private WorkerPool workerPool;
	private long maxIdleTime;
	private Selector selector;
	private long lastIdleCheckTime;
	private long currIdleCheckTime;
	private Thread thread ;
	private ArrayList<NIOSession> resps;
	private ArrayList<NIOSession> readRegistList;
	private ArrayList<NIOSession> closeList;
	
	private volatile boolean bRunning = false;
	
	RouterProcessor router;
	HealthMonitor moniter;
	
	public ClientIoPrecessor() throws IOException {
		this(Selector.open());
	}

	public ClientIoPrecessor(Selector selector) throws IOException {
		super();
		this.workerPool = WorkerPool.getPool();
		this.maxIdleTime = Config.getConfig().getSelect_wait_time();
		this.selector = selector;
		resps = new ArrayList<NIOSession>(Config.getConfig().getMax_clients());
		readRegistList = new ArrayList<NIOSession>(Config.getConfig().getMax_clients());
		closeList = new ArrayList<NIOSession>(Config.getConfig().getMax_clients());
		
		router = RouterProcessor.getRouter();
		moniter = HealthMonitor.getMonitor();
		
		this.thread = new Thread(this);
		this.thread.setDaemon(false);
		this.thread.setName("ClientIoPrecessor");
		this.thread.start();
	}

	public void destroy() {
		bRunning = false;
		
		if (selector != null) {
			Set<SelectionKey> keys = selector.keys();
			for (SelectionKey key : keys) {
				NIOSession session = (NIOSession) key.attachment();
				if (session != null) {
					addCloseList(session);
				}
			}
		}
	}
	
	public final void run() {
		bRunning = true;
		
		lastIdleCheckTime = SystemTimer.currentTimeMillis();
		currIdleCheckTime = lastIdleCheckTime;
		while(bRunning) {
			try {
				currIdleCheckTime = SystemTimer.currentTimeMillis();
				if ((currIdleCheckTime - lastIdleCheckTime) > 30000) {  // 30秒检查一次所有NioSession, 剔除超过10分钟没数据交互的连接
					lastIdleCheckTime = currIdleCheckTime;
					checkIdleSession();
				}
				
				doClose();
				doRegist();
				
				if (selector.select(maxIdleTime) == 0)
					continue;
				
				dispatch();
			} catch (Exception e) {
				logger.error("ClientIoPrecessor 异常",e);
			}
		}

	}
	
	public final void addSession(NIOSession session) {
		int size = moniter.getConnClientCount(); // selector.keys().size();

		if (size >= Config.getConfig().getMax_clients()) {
			logger.warn("客户端连接数过大,连接自动关闭");
			session.exceptionEnd(new RouteException(" 超过最大允许的客户端连接数", ROUTERRERINFO.e5, null, new Exception()), false);
		}else {
			synchronized (resps) {
				resps.add(session);
			}
			
			this.selector.wakeup();
		}

	}
	
	public final void addReadRegistList(NIOSession session) {
		synchronized (readRegistList) {
			readRegistList.add(session);
		}
			
		this.selector.wakeup();
	}
	
	public final void addCloseList(NIOSession session) {
		synchronized (closeList) {
			closeList.add(session);
		}
			
		this.selector.wakeup();
	}
	
	private void doClose() {
		NIOSession[] ses = null;
		if (closeList.size() > 0) {
			synchronized (closeList) {
				if (closeList.size() > 0) {
					ses = new NIOSession[closeList.size()];
					closeList.toArray(ses);
					closeList.clear();
				}
			}
		}
		
		if (ses != null) {
			for (int i = 0; i < ses.length; i++) {
				if (ses[i] != null) {
					closeSession(ses[i]);
				}
			}
		}
	}
	
	private void doRegist() {
		NIOSession[] ses = null;
		if (resps.size() > 0) {			
			synchronized (resps) {
				if (resps.size() > 0) {
					ses = new NIOSession[resps.size()];
					resps.toArray(ses);
					resps.clear();
				}
			}
		}

		if (ses != null) {
			for (int i = 0; i < ses.length; i++) {
				if (ses[i] != null) {
					init(ses[i]);
				}
			}
		}
		
		if (readRegistList.size() > 0) {			
			synchronized (readRegistList) {
				if (readRegistList.size() > 0) {
					ses = new NIOSession[readRegistList.size()];
					readRegistList.toArray(ses);
					readRegistList.clear();
				}
			}
		}
		
		if (ses != null) {
			for (int i = 0; i < ses.length; i++) {
				if (ses[i] != null) {
					initRead(ses[i]);
				}
			}
		}
	}

	protected void init(NIOSession session) {
		try {
			// 防止 java.nio.channels.CancelledKeyException
			selector.selectNow();
			
			if (session.getClient().isConnected()) {
				session.getClient().configureBlocking(false);
				SelectionKey selectkey = session.getClient().register(selector, SelectionKey.OP_READ, session);
				
				if (selectkey == null) {
					logger.error("client sockchannel register OP_READ error ......");
					session.exceptionEnd(new RouteException("注册客户端OP_READ失败", ROUTERRERINFO.e4, null, new Exception()), false);
				}
				session.setClSelectKey(selectkey);
				
				moniter.incConnClientCount();
			}
		} catch (Throwable e) {
			logger.error(e);
			session.exceptionEnd(new RouteException("注册客户端OP_READ异常 init ......", ROUTERRERINFO.e4, null, e), false);
		}
	}
	
	protected void initRead(NIOSession session) {
		try {
			if (session == null)
				return;
			
			SelectionKey selectKey = session.getClSelectKey();
			if (selectKey != null/* && selectKey.isValid()*/) {
				selectKey.interestOps(selectKey.interestOps() | SelectionKey.OP_READ);
			}
		} catch (CancelledKeyException e) {
			logger.error("CancelledKeyException, session info:"
					+ session.getClientIP() + ":" + session.getClientPort(), e);
		} catch (Throwable e) {
			logger.error("remote closed and SelectionKey cancelled, session info:"
					+ session.getClientIP() + ":" + session.getClientPort(), e);
		}
	}
	
	// 关闭长时间没数据交互的连接
	private void checkIdleSession() {
		long sysTimestamp = SystemTimer.currentTimeMillis();
		
		Iterator<SelectionKey> it = selector.keys().iterator();
		while (it.hasNext()) {
			SelectionKey lsnrKey = (SelectionKey) it.next();
			NIOSession session = (NIOSession) lsnrKey.attachment();
			if (session == null) {
				lsnrKey.cancel();
			} else {
				long lastTimeForRead = session.getLastTimeForClientRead();
				if ((sysTimestamp - lastTimeForRead) > 600000) {  // 超过600s无数据交互，服务端主动断开与客户端
					try {
						addCloseList(session);
						logger.info("超过600s无数据交互，服务端主动断开与客户端: " + session.getClient().getRemoteAddress() + " 的连接");
					} catch (IOException e) {
						logger.error(e);
					}
				}
			}
		}
	}

	protected void dispatch() {
		Iterator<SelectionKey> it = selector.selectedKeys().iterator();
		while (it.hasNext()) {
		
			SelectionKey selectKey = (SelectionKey) it.next();
			it.remove(); // 取出之后，一定要从列表中移出，否则有可能会尝试再次处理（特别是在多处理线程）//目前值支持单线程读

			if (!selectKey.isValid())
				continue;
			
			if (selectKey.isReadable()) {
				NIOSession session = (NIOSession) selectKey.attachment();
				session.refreshLastTimeForClientRead();
				
				// 为避免同一READ事件触发多次, 先取消已注册事件, reader处理完再重新注册READ事件
				selectKey.interestOps(selectKey.interestOps() & (~selectKey.readyOps()));
				
				workerPool.execute(session.getReader());
			}
		}
	}

	public void closeSession(NIOSession session) {
		if(session != null) {
			logger.info("iosession destroy, remote address:" + session.getClientIP() + ":" + session.getClientPort());
			SocketChannel ch = session.getClient();

			SelectionKey key = session.getClSelectKey();
			if (key != null) {
				key.cancel();
				session.setClSelectKey(null);
			}
			try {
				ch.close();
				ch = null;
			} catch (Exception e) {
				logger.warn("关闭客户端失败:",e);
			}
			
			session.recycle();
			session = null;
			
			logger.info("decConnClientCount ......");
			moniter.decConnClientCount();
		}
	}
	
	public void addRequest(RedisRequest request) {
		moniter.incReqInQueue();
		router.addRequest(request);
		
//		BufferProxy desByteProxy = BufferPool.getPool().allocate(128);
//		ByteBuffer desByteBuffer = desByteProxy.getBuffer();
//		desByteBuffer.put(CONSTS.DOLLAR_BYTE);
//		desByteBuffer.putLong(request.getClientReqId());
//		desByteBuffer.put(CONSTS.CRLF);
//		desByteBuffer.put(CONSTS.DOLLAR_BYTE);
//		desByteBuffer.putInt(5);
//		desByteBuffer.put(CONSTS.CRLF);
//		desByteBuffer.put(CONSTS.REDIS_OK);
//		desByteBuffer.flip();
//		request.setBackResp(desByteProxy);
//		RespondWriter.getInstance().addBack(request);
	}
		
}
