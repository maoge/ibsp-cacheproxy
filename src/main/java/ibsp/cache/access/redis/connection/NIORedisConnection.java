package ibsp.cache.access.redis.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import ibsp.cache.access.Config;
import ibsp.cache.access.exception.RouteException;
import ibsp.cache.access.exception.RouteException.ROUTERRERINFO;
import ibsp.cache.access.pool.mempool.BufferPool;
import ibsp.cache.access.pool.mempool.BufferProxy;
import ibsp.cache.access.pool.mempool.ByteArrayPool;
import ibsp.cache.access.pool.mempool.RedisRequestPool;
import ibsp.cache.access.pool.threadpool.WorkerPool;
import ibsp.cache.access.redis.processor.NIORedisProcessor;
import ibsp.cache.access.request.RedisRequest;
import ibsp.cache.access.request.Request;
import ibsp.cache.access.respond.RespondWriter;
import ibsp.cache.access.session.NIOSession;
import ibsp.cache.access.util.CONSTS;
import ibsp.cache.access.util.SystemTimer;

public class NIORedisConnection implements IRedisConnection {
	
	private static final Logger logger = Logger.getLogger(NIORedisConnection.class);
	private SocketChannel socketChannel;
	private SelectionKey selectkey;
	
	private String ip;
	private int port;
	private long lastUseTime;
	
	private NIORedisProcessor processor;
	
	private ArrayBlockingQueue<RedisRequest> requestQueue;
	private ArrayBlockingQueue<BufferProxy> parseQueue;
	private ConcurrentHashMap<Long, RedisRequest> requestMap;  // redisRequestId <-> RedisRequest
	
	private volatile boolean isConnected = false;
	
	private static long REDIS_WRITER_SLEEP_INTERVAL_IF_DISCONNECTED = 20;  // 连接发生中断时,写线程sleep interval
	
	private RedisWriter writer;
	private Thread writerThread;
	
	private RespondParser parser;
	private Thread parserThread;
	
	private RespondWriter respondWriter;
	private BufferPool byteBuffPool;
	private RedisRequestPool requestPool;
	
	private int revBuffSize;
	
	
	public NIORedisConnection(String ip, int port, NIORedisProcessor processor) {
		
		this.ip = ip;
		this.port = port;
		this.processor = processor;
		
		requestQueue = new ArrayBlockingQueue<RedisRequest>(Config.getConfig().getClient_redis_request_queue_maxlen());
		parseQueue = new ArrayBlockingQueue<BufferProxy>(Config.getConfig().getClient_redis_request_queue_maxlen()*5);
		requestMap = new ConcurrentHashMap<Long, RedisRequest>();
		
		lastUseTime = SystemTimer.currentTimeMillis();
		revBuffSize = Config.getConfig().getSo_revbuf_size();
		
		respondWriter = RespondWriter.getInstance();
		byteBuffPool = BufferPool.getPool();
		requestPool = RedisRequestPool.getPool();
		
		writer = new RedisWriter();
		writerThread = new Thread(writer);
		writerThread.setDaemon(false);
		writerThread.setName("RedisWriter_" + ip + "_" + "port");
		writerThread.start();
		
		parser = new RespondParser();
		parserThread = new Thread(parser);
		parserThread.setDaemon(false);
		parserThread.setName("RespondParser_" + ip + "_" + port);
		parserThread.start();
		
	}
	
	public void addRequest(Request request) throws RouteException {
		try {
			requestQueue.offer((RedisRequest)request, 10L, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			logger.error("NIORedisConnection addRequestData error ......");
			throw new RouteException("目标服务器繁忙,稍后再试 ......", ROUTERRERINFO.e12, (RedisRequest)request, e);
		}
	}
	
	public void addParseData(BufferProxy data) {
		parseQueue.offer(data);
	}
	
	public void addRequestMap(RedisRequest request) throws RouteException {
		if (requestMap.containsKey(request.getRedisReqId())) {
			throw new RouteException("redisReqId 重复", ROUTERRERINFO.e23, request);
		} else {
			this.requestMap.put(request.getRedisReqId(), request);
		}
	}
	
	public void removeRequestMap(RedisRequest request) {
		this.requestMap.remove(request.getRedisReqId());
	}
	
	public SelectionKey getSelKey() {
		return selectkey;
	}
	
	public void setSelKey(SelectionKey selectkey) {
		this.selectkey = selectkey;
	}
	
	public SocketChannel getChannel() {
		return this.socketChannel;
	}
	
	public long getLastUseTime() {
		return this.lastUseTime;
	}
	
	public int read(ByteBuffer dst) throws IOException {
		return socketChannel.read(dst);
	}

	public void connect() throws IOException {
		try {
			socketChannel = SocketChannel.open(new InetSocketAddress(ip, port));
			socketChannel.configureBlocking(false);
			setSocketOpt(socketChannel);
			
			this.isConnected = true;
			processor.addRegist(this);
		} catch (IOException e) {
			throw e;
		}
	}
	
	private void setSocketOpt(SocketChannel socketChannel) throws IOException {
		socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, revBuffSize);
	}

	public void reconnect() throws IOException {
		close();
		
		connect();
	}
	
	public void close() throws IOException {
		if (selectkey != null)
			selectkey.cancel();
		
		if (socketChannel != null && socketChannel.isConnected())
			socketChannel.close();
	}
	
	public void distroy() throws IOException {
		this.writer.StopRunning();
		this.parser.StopRunning();
		
		close();
	}
	
	public void setDisConnected() {
		this.isConnected = false;
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
		//String ss = s.replaceAll("\\r\\\n", "\\\\r\\\\n");
		
		System.out.println("Buffer len:" + buflen);
		System.out.println("Buffer context:" + s);
	}
	
	private static final Logger writerLogger = Logger.getLogger(RedisWriter.class);
	private class RedisWriter implements Runnable {
		private boolean bRunning;
		
		public RedisWriter() {
			super();
			
			bRunning = true;
		}
		
		public void StopRunning() {
			this.bRunning = false;
		}
		
		@Override
		public void run() {
			RedisRequest request = null;
			NIOSession session = null;
			
			while (bRunning) {
				try {
					if (isConnected) {
						request = requestQueue.take();
						session = request.getSession();
						BufferProxy resp = request.getResp();
						
						addRequestMap(request);
						
						if (!send(resp.getBuffer(), resp.limit())) {
							removeRequestMap(request);
							
							printBuffer(resp.getBuffer());
							
							if (session != null) {
								session.exceptionEnd(new RouteException("send to redis:" + ip + " " + port + " not complete!", ROUTERRERINFO.e21, request), true);
							}
						} else {
							// 大包先回收, 防止池不够用, 大包往redis写完就立即回收
							if (request.getRespLen() > CONSTS.LARGE_PACK_LEN) {
								byteBuffPool.recycle(resp);
								request.setResp(null);
							}
						}
					} else {
						Thread.sleep(REDIS_WRITER_SLEEP_INTERVAL_IF_DISCONNECTED);
					}
				} catch (InterruptedException e) {
					writerLogger.error(e.getStackTrace(), e);
					
					if (session != null)
						session.exceptionEnd(new RouteException("redis write io exception", ROUTERRERINFO.e17, request), true);
				} catch (RouteException e) {
					if (session != null)
						session.exceptionEnd(e, true);
				}
				
			}
			
		}
		
		private boolean send(ByteBuffer buf, int len) {
			int nWrite = 0;
			int nHaveWrite = 0;
			
			int pos = buf.position();
			int limit = buf.limit();
			int cap = buf.capacity();
			
			while (buf.hasRemaining()) {
				try {
					nWrite = socketChannel.write(buf);
					
					if (nWrite < 0) {
						writerLogger.error("send to redis socketChannel.write: ......" + nWrite);
						break;
					} else {
						nHaveWrite += nWrite;
					}
					
					if (nWrite == len)
						break;
				} catch (IOException e) {
					writerLogger.error("send to redis IOException ......");
					isConnected = false;
					processor.setDisConnected();
				} catch (Exception e) {
					writerLogger.error("send to redis Exception ......", e);
				}
			}
			
			if (nHaveWrite != len) {
				System.out.println("nHaveWrite:" + nHaveWrite + ", len:" + len);
				System.out.println("pos:" + pos + ", limit:" + limit + ", cap:" + cap);
			}
			
			return nHaveWrite == len;
		}
		
	}
	
	// redis 改造完回包结构
	// #define FUJITSU_HEADER "fujitsu#"
	// #define FUJITSU_HEADER_LEN 8
	// request:
	// +--------------+-----------+----+-----------------+
	// |FUJITSU_HEADER|sequence_id|\r\n|original protocol|
	// +--------------+-----------+----+-----------------+
	// response: 
	// +--------------+-----------+-+-----------+----+-----------------+
	// |FUJITSU_HEADER|sequence_id|:|body length|\r\n|original protocol|
	// +--------------+-----------+-+-----------+----+-----------------+
	
	private static enum PARSE_STATUS {
		PARSE_INIT,
		PARSE_HEAD,       // 读取 FUJITSU_HEADER
		PARSE_ID,         // 读取 ID
		PARSE_LEN,        // 读取长度
		PARSE_RESP,       // 按长度截取RESP报文部分
		PARSE_END;
	}
	
	private static enum PARTIAL_STATUS {
		PARTIAL_INIT,
		PARTIAL_HEAD,
		PARTIAL_HEAD_END,
		PARTIAL_ID,
		PARTIAL_LEN,
		PARTIAL_LEN_CR,
		PARTIAL_LEN_LN,
		PARTIAL_RESP;
	}
	
	private static final Logger parserLogger = Logger.getLogger(RespondParser.class);
	private class RespondParser implements Runnable {

		private volatile boolean bRunning;
		
		BufferProxy srcBuffProxy = null;
		ByteBuffer srcByteBuff = null;
		
		BufferProxy desBuffProxy = null;
		ByteBuffer desByteBuff = null;
		
		PARSE_STATUS parseStatus = PARSE_STATUS.PARSE_INIT;
		PARTIAL_STATUS partialStatus = PARTIAL_STATUS.PARTIAL_INIT;
		
		int srcBuffLen = 0;
		int idx = 0;
		int idxMax = 0;
		int respLen = 0;
		int respHaveRead = 0;
		
		int headOffset = 0;      // head 完整情况下的计数
		int headPartOffset = 0;  // head 不完整情况下的计数
		
		long redisReqId = 0;     // respond id 
		int len = 0;             // 记录当前id或respLen已读取长度, 防止异常数据情况
		
		RedisRequest request;
		
		public RespondParser() {
			super();
		}
		
		@Override
		public void run() {
			this.bRunning = true;
			
			while (bRunning) {
				try {
					srcBuffProxy = parseQueue.take();
					
					srcByteBuff = srcBuffProxy.getBuffer();
					srcBuffLen = srcByteBuff.limit();
					idx = 0;
					idxMax = srcBuffLen - 1;
					
					for (; idx <= srcBuffLen; ) {
						switch (parseStatus) {
						case PARSE_INIT:
							processHead();
							break;
						case PARSE_HEAD:
							// 分包 FUJITSU_HEADER 只匹配到了部分, 还要继续匹配剩下部分
							processHeadLeft();
							break;
						case PARSE_ID:
							processID();
							break;
						case PARSE_LEN:
							processRespLen();
							break;
						case PARSE_RESP:
							processResp();
							break;
						case PARSE_END:
							processEnd();
							break;
						default:
							init();
							break;
						}
					}
					
					if (idx > srcBuffLen) {
						if (srcBuffProxy != null) {
							byteBuffPool.recycle(srcBuffProxy);
							srcBuffProxy = null;
							srcByteBuff = null;
						}
					}
				} catch (InterruptedException e) {
					parserLogger.error(e.getStackTrace(), e);
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
			//String ss = s.replaceAll("\\r\\\n", "\\\\r\\\\n");
			
			parserLogger.error("DownStream len:" + buflen);
			parserLogger.error("DownStream context:" + s);
		}
		
		private void processHead() {
			int srcLeft = srcBuffLen - idx;
			if ((srcLeft) >= CONSTS.PROTO_HEAD_LEN) {
				if (headPartOffset == 0) {
					headOffset = findHead(srcByteBuff, idx, srcBuffLen);
					if (headOffset >= idx) {
						idx = headOffset + CONSTS.PROTO_HEAD_LEN;
						parseStatus = PARSE_STATUS.PARSE_ID;
						partialStatus = PARTIAL_STATUS.PARTIAL_INIT;
					} else {
						// 数据包异常, 自动舍弃这段数据
						parserLogger.error("parse HEAD error, length match but data not match extend head ......");
						System.out.println("idx:" + idx + ", headOffset:" + headOffset);
						printBuffer(srcByteBuff);
						initWithErr();
					}
				} else {
					int headLeft = CONSTS.PROTO_HEAD_LEN - headPartOffset;
					if (matchHeadLeft(srcByteBuff, idx, headPartOffset, headLeft)) {
						idx += headLeft;
						headPartOffset += headLeft;
						parseStatus = PARSE_STATUS.PARSE_ID;
						partialStatus = PARTIAL_STATUS.PARTIAL_INIT;
					} else {
						parserLogger.error("parse HEAD error, left head not match ...... idx:" + idx + ", srcBuffLen:" + srcBuffLen + ", srcLeft:" + srcLeft);
						printBuffer(srcByteBuff);
						initWithErr();
					}
				}
			} else {
				// 剩余字节数不够时, 先匹配一部分, 如不匹配则舍弃掉
				if (matchHeadLeft(srcByteBuff, idx, headPartOffset, srcLeft)) {
					partialStatus = PARTIAL_STATUS.PARTIAL_HEAD;
					idx = srcBuffLen;
					headPartOffset += srcLeft;
				} else {
					parserLogger.error("parse HEAD error, left bytes not match ...... idx:" + idx + ", srcBuffLen:" + srcBuffLen + ", srcLeft:" + srcLeft);
					printBuffer(srcByteBuff);
					initWithErr();
				}
			}
			
			// 防止HEAD分包, 死循环
			if (idx >= srcBuffLen) {
				idx = srcBuffLen + 1;  // 已经到末尾, 跳出取下一条缓冲区数据
			}
		}
		
		private void processHeadLeft() {
			if (partialStatus == PARTIAL_STATUS.PARTIAL_HEAD) {
				int headLeft = CONSTS.PROTO_HEAD_LEN - headPartOffset;
				int srcLeft = srcBuffLen - idx;
				int len = Math.min(headLeft, srcLeft);
				if (matchHeadLeft(srcByteBuff, idx, headPartOffset, len)) {
					if (headLeft > srcLeft) {
						idx = srcBuffLen + 1;  // 已经到末尾, 跳出取下一条缓冲区数据
					} else {
						parseStatus = PARSE_STATUS.PARSE_ID;
						partialStatus = PARTIAL_STATUS.PARTIAL_INIT;
						
						headPartOffset += len;
						idx += len;
					}
				} else {
					parserLogger.error("parse PARTIAL_HEAD error, left bytes not match ......");
					printBuffer(srcByteBuff);
					initWithErr();
				}
			}
		}
		
		private void processID() {
			byte b = 0x00;
			for (; idx < srcBuffLen; idx++) {
				if (len > CONSTS.PROTO_ID_MAXLEN) {
					// id长度超过 long 最大长度
					parserLogger.error("parse ID error, data length exceeds long type max value in alpha bytes ......");
					printBuffer(srcByteBuff);
					initWithErr();
					break;
				}
				
				b = srcByteBuff.get(idx);
				
				// 找到结束分隔符':'
				if (b == CONSTS.PROTO_SPLIT) {
					len = 0;
					parseStatus = PARSE_STATUS.PARSE_LEN;
					partialStatus = PARTIAL_STATUS.PARTIAL_INIT;
					idx++;
					
					request = requestMap.get(redisReqId);
					if (request == null) {
						parserLogger.error("requestMap not found redisReqId:" + redisReqId);
						initWithErr();
					}
					
					break;
				}
				
				if (b < 0x30 || b > 0x39) {
					// id 数据异常 [^0-9]
					parserLogger.error("parse ID error, data not in [0-9] ...... id:" + redisReqId);
					printBuffer(srcByteBuff);
					
					// 数据出现异常时暂时不能回收request 因为无法预知当前已经获取的redisReqId是否完整
					
					initWithErr();
					break;
				}
				redisReqId *= 10;
				redisReqId += b - '0';
				len++;
			}
			
			// 防止ID分包, 死循环
			if (idx >= srcBuffLen) {
				idx = srcBuffLen + 1;  // 已经到末尾, 跳出取下一条缓冲区数据
			}
		}
		
		private void processRespLen() {
			byte b = 0x00;
			boolean endFlag = false;
			for (; idx < srcBuffLen; idx++) {
				if (len > CONSTS.PROTO_RESP_MAXLEN) {
					// resp长度超过 int 最大长度
					parserLogger.error("parse RESP_LEN error, resp length exceeds int type max value in alpha bytes ......");
					printBuffer(srcByteBuff);
					initWithErr();
					break;
				}
				
				b = srcByteBuff.get(idx);
				if (idx == idxMax) {
					idx++;  // 防止srcByteBuff.get(idx + 1)越界, 已经到末尾, 跳出取下一条缓冲区数据
					
					if (b == CONSTS.PROTO_CR) {
						partialStatus = PARTIAL_STATUS.PARTIAL_LEN_CR;
						break;
					} else {
						partialStatus = PARTIAL_STATUS.PARTIAL_LEN;
					}
				}
				
				// 找到结束分隔符'\r\n'
				// 注意'\r\n'有可能分包
				if (partialStatus == PARTIAL_STATUS.PARTIAL_INIT
						|| partialStatus == PARTIAL_STATUS.PARTIAL_LEN) {

					if (b == CONSTS.PROTO_CR) {
						if (srcByteBuff.get(idx + 1) == CONSTS.PROTO_LN) {
							endFlag = true;
						}
						idx++;
					}
					
				} else if (partialStatus == PARTIAL_STATUS.PARTIAL_LEN_CR) {
					if (b == CONSTS.PROTO_LN) {
						endFlag = true;
						//parserLogger.error("enter occur, next new line occur ...... idx:" + idx + ", srcBuffLen:" + srcBuffLen);
					} else {
						parserLogger.error("parse RESP_LEN error, resp length end flag not found ......");
						printBuffer(srcByteBuff);
						initWithErr();
						break;
					}
				}
				
				if (endFlag) {
					len = 0;
					idx++;
					
					parseStatus = PARSE_STATUS.PARSE_RESP;
					partialStatus = PARTIAL_STATUS.PARTIAL_INIT;
					
					desBuffProxy = request.getBackResp();
					int totalBackResp = respLen + CONSTS.RESPOND_HEAD_LEN;
					if (desBuffProxy == null) {
						desBuffProxy = byteBuffPool.allocate(totalBackResp);
//						request.setBackResp(desBuffProxy);
					} else {
						ByteBuffer tmpBuf = desBuffProxy.getBuffer();
						if (tmpBuf.capacity() < totalBackResp) {
							byteBuffPool.recycle(desBuffProxy);
							desBuffProxy = byteBuffPool.allocate(totalBackResp);
//							request.setBackResp(desBuffProxy);
						}
					}
					desByteBuff = desBuffProxy.getBuffer();
					desByteBuff.position(CONSTS.RESPOND_HEAD_LEN);
					
					break;
				}
				
				if (b < 0x30 || b > 0x39) {
					// id 数据异常 [^0-9]
					parserLogger.error("parse RESP_LEN error, data not in [0-9] ...... redisReqId:" + redisReqId + ", idx:" + idx + ", srcBuffLen:" + srcBuffLen + ", error data: " + (int) b);
					printBuffer(srcByteBuff);
					initWithErr();
					break;
				}
				respLen *= 10;
				respLen += b - '0';
				len++;
			}
			
			// 防止respLen分包, 死循环
			if (idx >= srcBuffLen) {
				idx = srcBuffLen + 1;  // 已经到末尾, 跳出取下一条缓冲区数据
			}
		}
		
		private void processResp() {
			boolean err = false;
			int respLeft = respLen - respHaveRead;
			int srcLeft = srcBuffLen - idx;
			ByteBuffer slice = srcByteBuff.slice();
			
			if (srcLeft >= respLeft) {
				try {
					boolean match = false;
					if (respLeft >= 2)
						match = srcByteBuff.get(idx+respLeft-2) == CONSTS.PROTO_CR && srcByteBuff.get(idx+respLeft-1) == CONSTS.PROTO_LN;
					else if (respLeft == 1)
						match = srcByteBuff.get(idx+respLeft-1) == CONSTS.PROTO_LN;
					
					if (match) {
						slice.position(idx);
						slice.limit(idx+respLeft);
						
						parseStatus = PARSE_STATUS.PARSE_END;
						partialStatus = PARTIAL_STATUS.PARTIAL_INIT;
					} else {
						parserLogger.error("parse resp data error, end flag not found ...... idx:" + idx + ", srcBuffLen:" + srcBuffLen);
						printBuffer(srcByteBuff);
						initWithErr();
						err = true;
					}
					idx += respLeft;
				} catch (IndexOutOfBoundsException e) {
					parserLogger.error("parse resp data error, IndexOutOfBoundsException ...... idx:" + idx + 
							", srcBuffLen:" + srcBuffLen + ", respLeft:" + respLeft + 
							", respLen:" + respLen + ", respHaveRead:" + respHaveRead);
					printBuffer(srcByteBuff);
					initWithErr();
					err = true;
				}
			} else {
				// 未完待续
				slice.position(idx);
				slice.limit(srcBuffLen);
				respHaveRead += srcLeft;
				partialStatus = PARTIAL_STATUS.PARTIAL_RESP;
				
				idx = srcBuffLen + 1;  // 本条已经取到末尾, 跳出循环取下一条缓冲数据
			}
			
			if (!err)
				desByteBuff.put(slice);
		}
		
		private void processEnd() {
			desByteBuff.flip();
			
			// 写扩展头
//			RedisRequest request = requestMap.get(redisReqId);
			if (request != null) {
				desByteBuff.position(0);
				desByteBuff.put(CONSTS.DOLLAR_BYTE);
				desByteBuff.putLong(request.getClientReqId());
				desByteBuff.put(CONSTS.CRLF);
				desByteBuff.put(CONSTS.DOLLAR_BYTE);
				desByteBuff.putInt(respLen);
				desByteBuff.put(CONSTS.CRLF);
				desByteBuff.position(0);
				
				request.setBackResp(desBuffProxy);
				request.setBackRespLen(respLen);
				respondWriter.addBack(request);
				desBuffProxy = null;
				
				requestMap.remove(redisReqId);
			} else {
				parserLogger.error("redis request id:" + redisReqId + " not found ......");
			}
			
			if (idx >= srcBuffLen) {
				// 尾巴还有数据则继续处理, 已经到头了则回收
				if (srcBuffProxy != null) {
					byteBuffPool.recycle(srcBuffProxy);
					srcBuffProxy = null;
					srcByteBuff = null;
				}
			}
			
			parseStatus = PARSE_STATUS.PARSE_INIT;
			partialStatus = PARTIAL_STATUS.PARTIAL_INIT;
			
			respLen = 0;
			redisReqId = 0;
			headPartOffset = 0;
			len = 0;
			respHaveRead = 0;
			
			request = null;
			
			// 如经处理到为srcByteBuff的结尾则idx++进入processEnd, 否则继续处理下一个完整包(多个请求打到一个包一起返回的情况)
			if (idx >= srcBuffLen)
				idx++;
		}
		
		// HEAD = { 'f', 'u', 'j', 'i', 't', 's', 'u', '#' };
		// HEAD_LEN = HEAD.length;
		private int findHead(ByteBuffer buff, int offset, int buffLen) {
			int i = offset, j = 0;
			int result = -1;
			for (; i < buffLen; i++) {
				if (buff.get(i) == CONSTS.PROTO_HEAD[0]) {
					int tmp = i;
					boolean match = true;
					
					for ( ; j < CONSTS.PROTO_HEAD_LEN; ) {
						if (buff.get(tmp++) != CONSTS.PROTO_HEAD[j++]) {
							j = 0;
							match = false;
							break;
						}
					}
					
					if (match) {
						result = i;
						break;
					}
				}
			}
			
			return result;
		}
		
		private boolean matchHeadLeft(ByteBuffer buff, int srcOffset, int headOffset, int len) {
			int i = 0;
			boolean match = true;
			for (; i < len; i++) {
				if (buff.get(srcOffset++) != CONSTS.PROTO_HEAD[headOffset++]) {
					match = false;
					break;
				}
			}
			return match;
		}
		
		private void init() {
			idx += CONSTS.PROTO_HEAD_LEN;
			clear();
		}
		
		private void initWithErr() {
			idx += 1;
			clear();
		}
		
		private void clear() {
			parseStatus = PARSE_STATUS.PARSE_INIT;
			partialStatus = PARTIAL_STATUS.PARTIAL_INIT;
			
			respLen = 0;
			respHaveRead = 0;
			redisReqId = 0;
			headPartOffset = 0;
			len = 0;
			
			if (request != null) {
				requestPool.recycle(request);
				request = null;
			}
			
			if (idx >= srcBuffLen) {
				if (srcBuffProxy != null) {
					byteBuffPool.recycle(srcBuffProxy);
					srcBuffProxy = null;
					srcByteBuff = null;
				}
				
				if (desBuffProxy != null) {
					byteBuffPool.recycle(desBuffProxy);
					desBuffProxy = null;
					desByteBuff = null;
				}
			}
			
		}
		
		public void StopRunning() {
			this.bRunning = false;
		}
		
	}
	
	private static void InitPool() {
		BufferPool.setPool(new BufferPool());
		ByteArrayPool.setPool(new ByteArrayPool());
		RedisRequestPool.setPool(new RedisRequestPool());
		WorkerPool.setPool(new WorkerPool());
	}
	
	public static void main(String[] args) {
		
		Config.setConfig(new Config());
		InitPool();
		
		NIORedisConnection conn = new NIORedisConnection("127.0.0.1", 6379, null);
		
		BufferProxy buf1 = BufferPool.getPool().allocate(4096);
		//BufferProxy buf2 = BufferPool.getPool().allocate(4096);
		
		//String s1 = new String("fujitsu#14600:11\r\n$5\r\nAAAAA\r");
		//String s2 = new String("\nfujitsu#14601:11\r\n$5\r\nAAAAA\r\n");
		String s1 = new String("fujitsu#14600:11\r\n$5\r\nAAAAA\r\n:0\r\n");
		
		buf1.put(s1.getBytes());
		buf1.flip();
		
		//buf2.put(s2.getBytes());
		//buf2.flip();
		
		RedisRequest req1= new RedisRequest();
		req1.setRedisReqId(14600);
		
		RedisRequest req2= new RedisRequest();
		req2.setRedisReqId(14601);
		
		try {
			conn.addRequestMap(req1);
			conn.addRequestMap(req2);
		} catch (RouteException e) {
			e.printStackTrace();
		}
		
		conn.addParseData(buf1);
		//conn.addParseData(buf2);
		
	}
	
}
