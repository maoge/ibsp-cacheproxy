package ibsp.cache.access.session;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import ibsp.cache.access.client.processor.ClientIoPrecessor;
import ibsp.cache.access.client.processor.IoProcessor;
import ibsp.cache.access.exception.RouteException;
import ibsp.cache.access.exception.RouteException.ROUTERRERINFO;
import ibsp.cache.access.handler.ProtocolHandler;
import ibsp.cache.access.pool.mempool.BufferPool;
import ibsp.cache.access.pool.mempool.BufferProxy;
import ibsp.cache.access.pool.mempool.RedisRequestPool;
import ibsp.cache.access.protocal.reader.NioRedisInputReador;
import ibsp.cache.access.request.RedisRequest;
import ibsp.cache.access.respond.RespondWriter;
import ibsp.cache.access.route.GroupInfo.OperateType;
import ibsp.cache.access.util.CONSTS;
import ibsp.cache.access.util.HealthMonitor;
import ibsp.cache.access.util.IDGenerator;
import ibsp.cache.access.util.SafeEncoder;
import ibsp.cache.access.util.SystemTimer;

public class NIOSession {
	public static final Logger logger = Logger.getLogger(NIOSession.class);
	
	private RedisRequestPool requestPool;
	private BufferPool buffPool;
	private HealthMonitor monitor;

	private final long sessionID;
	private IoProcessor clIoProccessor;
	private ProtocolHandler protocolHandler;
	private Object clientRespond;

	private SocketChannel client;//客户端连接请求
	private SelectionKey clSelectKey;
	private String clientIP;
	private String clientPort;
	private ByteBuffer clientRespBuffer;
	
	private RedisRequest lastRequest;
	
	private Reader reader;
	
	/**
	 * 在执行路由请求后，该连接被创建
	 */
	private SocketChannel routeConn;//目标路由端连接


	private long lastTimeForClientRead;
	private long lastTimeForClientWrite;    
	private long lastTimeForRouteSend;
	
	private static AtomicLong normalWriteBack;     // 正常回包数
	private static AtomicLong exceptionWriteBack;  // 异常回包数
	
	static {
		normalWriteBack = new AtomicLong(0);
		exceptionWriteBack = new AtomicLong(0);
	}
	
	public NIOSession(SocketChannel client, IoProcessor clIoProccessor) {
		super();
		this.sessionID = IDGenerator.getSessionIDGenerator().nextID();
		this.requestPool = RedisRequestPool.getPool();
		this.buffPool = BufferPool.getPool();
		this.monitor = HealthMonitor.getMonitor();
		this.client = client;
		this.clIoProccessor = clIoProccessor;
		this.reader = new Reader(this, (ClientIoPrecessor)clIoProccessor);
		
		try {
			clientIP = client.getRemoteAddress().toString();  // 截取/ip:port
			int nBeg = clientIP.indexOf("/");
			int nEnd = clientIP.indexOf(":", nBeg);
			clientPort = clientIP.substring(nEnd + 1);
			clientIP = clientIP.substring(nBeg + 1, nEnd);
		} catch (IOException e) {
			logger.error(e);
		}
	}
	
	public long getSessionID() {
		return this.sessionID;
	}
	
	public Reader getReader() {
		return this.reader;
	}
	
	/**
	 * 处理过程异常，发送异常信息到客户端
	 */
	protected void sendexeceptionMsg(RouteException setErrorCode,RedisRequest request) {
		StringBuffer sb = new StringBuffer("");
		sb.append(CONSTS.CHAR_ERROR);
		sb.append("errorCode:" + setErrorCode.getErrorCode());
		sb.append(CONSTS.CHAR_CRLF);
		
		
		byte[] bytes = SafeEncoder.encode(sb.toString());
		
		BufferProxy backResp = request.getBackResp();
		int totalLen = bytes.length + CONSTS.RESPOND_HEAD_LEN;
		if (backResp == null) {
			backResp = buffPool.allocate(totalLen);
			request.setBackResp(backResp);
		} else {
			if (backResp.getBuffer().capacity() < totalLen) {
				buffPool.recycle(backResp);
				backResp = buffPool.allocate(totalLen);
				request.setBackResp(backResp);
			}
		}
		
		backResp.put(CONSTS.DOLLAR_BYTE);
		backResp.putLong(request.getClientReqId());
		backResp.put(CONSTS.CRLF);
		backResp.put(CONSTS.DOLLAR_BYTE);
		backResp.putInt(bytes.length);
		backResp.put(CONSTS.CRLF);
		
		backResp.put(bytes);
		backResp.flip();
		
		request.setRetCode(setErrorCode.getErrorCode());
		
		RespondWriter.getInstance().addBack(request);
		
		monitor.incExceptionTotalCntAndGet();
	}
	
	public void sendBackPong(RedisRequest request) {
		StringBuffer sb = new StringBuffer("");
		sb.append((char)CONSTS.PLUS_BYTE);
		sb.append("PONG");
		sb.append(CONSTS.CHAR_CRLF);
		
		byte[] bytes = SafeEncoder.encode(sb.toString());
		
		BufferProxy buf = buffPool.allocate(bytes.length + CONSTS.RESPOND_HEAD_LEN);
		buf.put(CONSTS.DOLLAR_BYTE);
		buf.putLong(request.getClientReqId());
		buf.put(CONSTS.CRLF);
		buf.put(CONSTS.DOLLAR_BYTE);
		buf.putInt(bytes.length);
		buf.put(CONSTS.CRLF);
		
		buf.put(bytes);
		buf.flip();
		
		request.setRetCode(CONSTS.NORMAL_RETCODE);
		request.setBackResp(buf);
		RespondWriter.getInstance().addBack(request);
		
		monitor.incNormalRepTotalCntAndGet();
	}
	
	public void destroy() {
		if (clIoProccessor != null) {
			((ClientIoPrecessor)clIoProccessor).addCloseList(this);
			clIoProccessor = null;
		}
		
		if (lastRequest != null) {
			lastRequest = null;
		}
		
		if (protocolHandler != null) {
			protocolHandler.recycle();
			protocolHandler = null;
		}
	}
	
	public int read(ByteBuffer dst) throws IOException {
		return client.read(dst);
	}

	public long read(ByteBuffer[] dsts, int offset, int length)
			throws IOException {
		return client.read(dsts, offset, length);
	}

	public final long read(ByteBuffer[] dsts) throws IOException {
		return client.read(dsts);
	}
	
	public void exceptionEnd(RouteException setErrorCode, boolean needDecReq) {
		exceptionEnd(setErrorCode, setErrorCode.getSendBack(), setErrorCode.getCloseClient(), needDecReq);
	}

	public void exceptionEnd(RouteException setErrorCode, boolean needSendBack, boolean needCloseClient, boolean needDecReq) {
		logger.error(setErrorCode);
		
		RedisRequest request = setErrorCode.getRequest();
		NIOSession session = request.getSession();
		
		if (request !=null) {
			if (needSendBack) {
				if (request.getClientReqId() != -1) {
					request.setNeedClose(needCloseClient);
					sendexeceptionMsg(setErrorCode,request);
					long exceptionCnt = exceptionWriteBack.incrementAndGet();
					logger.info("normal write back :" + normalWriteBack.get() + ", exception write back :" + exceptionCnt);
					needCloseClient = false;
				} else {
					needCloseClient = true;
				}
			}
			
			if (needCloseClient) {
				if (needDecReq) {
					HealthMonitor.getMonitor().decReqInQueue();
				}
				logger.info("exceptionEnd close session, remote address:" + getClientIP() + ":" + getClientPort());
				
				if (session != null) {
					session.destroy();
				}
				
				requestPool.recycle(request);
			}
		}
	}

	public SelectionKey getClSelectKey() {
		return clSelectKey;
	}


	public void setClSelectKey(SelectionKey clSelectKey) {
		this.clSelectKey = clSelectKey;
	}

	public IoProcessor getClIoProccessor() {
		return clIoProccessor;
	}

	public ProtocolHandler getProtocolHandler() {
		return protocolHandler;
	}

	public Object getClientRespond() {
		return clientRespond;
	}

	public SocketChannel getClient() {
		return client;
	}

	public ByteBuffer getClientReadBuffer() {
		return clientRespBuffer;
	}

	public SocketChannel getRouteConn() {
		return routeConn;
	}

	public long getLastTimeForClientRead() {
		return lastTimeForClientRead;
	}
	
	public void refreshLastTimeForClientRead() {
		lastTimeForClientRead = SystemTimer.currentTimeMillis();
	}

	public long getLastTimeForClientWrite() {
		return lastTimeForClientWrite;
	}
	
	public void refreshLastTimeForClientWrite() {
		lastTimeForClientWrite = SystemTimer.currentTimeMillis();
	}

	public long getLastTimeForRouteSend() {
		return lastTimeForRouteSend;
	}
	
	public void refreshLastTimeForRouteSend() {
		lastTimeForRouteSend = SystemTimer.currentTimeMillis();
	}

	/**
	 * 重新注册读事件
	 */
	public void intrestClientRead() {
		this.clIoProccessor.addSession(this);
	}

	public void setProtocolHandler(ProtocolHandler protocolHandler) {
		this.protocolHandler = protocolHandler;
	}

	public void recycle() {
		if (protocolHandler != null)
			protocolHandler.recycle();
	}
	
	public RedisRequest getLastRequest() {
		return lastRequest;
	}
	
	public void setLastRequest(RedisRequest request) {
		lastRequest = request;
	}
	
	public void clearLastRequest() {
		lastRequest = null;
	}
	
	public String getClientIP() {
		return this.clientIP;
	}
	
	public String getClientPort() {
		return this.clientPort;
	}
	
	public static final Logger sessionReaderLogger = Logger.getLogger(Reader.class);
	private class Reader implements Runnable {

		private NIOSession session;
		private ClientIoPrecessor processor;
		
		public Reader(NIOSession session, ClientIoPrecessor processor) {
			super();
			
			this.session = session;
			this.processor = processor;
		}
		
		@Override
		public void run() {
			RedisRequest request = session.getLastRequest();
			ProtocolHandler handler = session.getProtocolHandler();
			
			try {
				boolean canRead = handler != null && handler.getInputReador() != null;
				if (request == null) {  // 上次收到了完整的包
					if (canRead) {
						request = requestPool.allocate();
						request.setSession(session);
						
						request.setReador((NioRedisInputReador) handler.getInputReador());
						
						request.setRevTs(SystemTimer.currentTimeMillis());
						request.setRevNs(SystemTimer.currentTimeNano());
						session.setLastRequest(request);
					}
				}
				
				if (canRead) {
					handler.readFromClient(request);
					
					if (request.isFinishedClientRead()) {
						session.clearLastRequest();
						
						long revCnt = monitor.incRevReqTotalCntAndGet();
//						monitor.incGroupReqCnt(request.getGroupId());
						if (revCnt % 200000 == 0) {
							sessionReaderLogger.info("total received request:" + revCnt);
						}
						processor.addRequest(request);
					}
				}
				
			} catch (BufferOverflowException e) {
				// 没读完下次READ事件继续读
				sessionReaderLogger.info("continue READ ......");
			} catch (IOException e) {
				// maybe the remote client closed the connetion actively
				if (session != null) {
					sessionReaderLogger.warn("IOException from "+ session.clientIP+":"+session.clientPort);
					session.exceptionEnd(new RouteException("remote client connection closed ROUTERRERINFO.e11 ......", ROUTERRERINFO.e11, request, e), false);
					session = null;  // 置NULL, 防止进入 finally 重新注册读事件
				}
			} catch (RouteException e) {
				sessionReaderLogger.error(e.getMessage(), e);
				if (session != null) {
					session.exceptionEnd(e, false);
				}
			} catch (NullPointerException e) {
				sessionReaderLogger.error("NIOSession Reader NullPointerException caused by remote closed,  remote address:" + getClientIP() + ":" + getClientPort());
				session.clearLastRequest();
				requestPool.recycle(request);
				request = null;
				session = null;  // 置NULL, 防止进入 finally 重新注册读事件
			} catch (Throwable e) {
				sessionReaderLogger.error(e);
				if (session != null) {
					session.exceptionEnd(new RouteException("remote client connection closed ROUTERRERINFO.e21 ......", ROUTERRERINFO.e21, request, e), false);
				}
			} finally {
				if (session != null) {
					processor.addReadRegistList(session);
				}
			}
		}
	}
}
