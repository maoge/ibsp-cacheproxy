package ibsp.cache.access.request;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import ibsp.cache.access.Config;
import ibsp.cache.access.exception.RouteException;
import ibsp.cache.access.exception.RouteException.ROUTERRERINFO;
import ibsp.cache.access.pool.mempool.BufferPool;
import ibsp.cache.access.pool.mempool.BufferProxy;
import ibsp.cache.access.pool.mempool.ByteArray;
import ibsp.cache.access.protocal.reader.NioRedisInputReador;
import ibsp.cache.access.route.GroupInfo.OperateType;
import ibsp.cache.access.session.NIOSession;
import ibsp.cache.access.util.CONSTS;
import ibsp.cache.access.util.CommUtil;
import ibsp.cache.access.util.IDGenerator;
import ibsp.cache.access.util.IntCounter;
import ibsp.cache.access.util.KeyHash;
import ibsp.cache.access.util.RedisUtil;
import ibsp.cache.access.util.SystemTimer;
import ibsp.cache.access.util.RedisUtil.SupportCommand;

public class RedisRequest extends Request {
	protected NioRedisInputReador reador;
	
	private READ_STATUS status;
	
	private long redisReqId;
	
	private ByteArray byteArrHead;
	private byte[] byteHead;
	
	private ByteArray command;
	private byte[] byteCommand;
	
	private ByteArray redisID;         // 送到redis的扩展头中的ID序列化缓冲区
	private byte[] byteRedisID;
	
	private SupportCommand supportCmd;
	
	private ByteArray key;
	private byte[] byteKey;
	
	private String groupId;
	private long clientReqId = -1;
	private int headLen;
	private int respLen = 0;
	private IntCounter headRead;
	private IntCounter respRead;
	private BufferProxy resp;
	
	private int slot = -1;
	private OperateType opType;
	private int retCode = 0;
	private String redisUrl;
	
	private long revTs;
	private long sendBkTs;
	
	private long revNs;
	private long sendBkNs;
	
	private int backRespLen;
	private BufferProxy backResp;
	private ByteBuffer byteBuffResp;
	private BufferPool byteBufferPool;
	private IDGenerator reqIDGen;
	
	private boolean needClose;
	
	public RedisRequest() {
		status = READ_STATUS.INIT;
		byteArrHead = new ByteArray(Config.getConfig().getDefault_head_len());
		byteHead = byteArrHead.getBytes();
		
		command = new ByteArray(Config.getConfig().getDefault_head_attr_len());
		byteCommand = command.getBytes();
		
		redisID = new ByteArray(CONSTS.PROTO_ID_MAXLEN);
		byteRedisID = redisID.getBytes();
		
		key = new ByteArray(Config.getConfig().getDefault_head_attr_len());
		byteKey = key.getBytes();
		
		this.respRead = new IntCounter(0);
		this.headRead = new IntCounter(0);
		
		byteBufferPool = BufferPool.getPool();
		reqIDGen = IDGenerator.getReqIDGenerator();
		
		needClose = false;
	}
	
	public byte[] getByteHead() {
		return byteHead;
	}
	
	public RedisRequest(NioRedisInputReador reador, NIOSession session) {
		super(session);
		status = READ_STATUS.INIT;
		this.reador = reador;
		this.respRead = new IntCounter(0);
		this.headRead = new IntCounter(0);
	}
	
	public void setReador(NioRedisInputReador reador) {
		this.reador = reador;
	}
	
	public void setSession(NIOSession session) {
		this.session = session;
	}

	public void read() throws BufferOverflowException, IOException, RouteException {
		switch (status) {
		case INIT:
			readDollar();
			
			headLen = reador.readIntCrLf();
			if (byteArrHead.getCapacity() < headLen) {
				byteArrHead = new ByteArray(headLen);
				byteHead = byteArrHead.getBytes();
			}
			
			byteArrHead.setLen(headLen);
			
			this.status = READ_STATUS.CMD_HEAD;
			readHead(headLen, headRead);
			if (headRead.get() == headLen) {
				parseHead();
				
				readCrLf();
				this.status = READ_STATUS.CMD_DATA;
				
				readResp(respLen, respRead);
				if (respRead.get() == respLen) {
					byteBuffResp.flip();
					this.status = READ_STATUS.CLIENT_READ_FINISHED;
				}
			}
			break;
		case CMD_HEAD:
			readHead(headLen, headRead);
			if (headRead.get() == headLen) {
				parseHead();
				
				readCrLf();
				this.status = READ_STATUS.CMD_DATA;
				
				readResp(respLen, respRead);
				if (respRead.get() == respLen) {
					byteBuffResp.flip();
					this.status = READ_STATUS.CLIENT_READ_FINISHED;
				}
			}
			break;
		case CMD_DATA:
			readResp(respLen, respRead);
			if (respRead.get() == respLen) {
				byteBuffResp.flip();
				this.status = READ_STATUS.CLIENT_READ_FINISHED;
			}
			break;
		default:
			throw new RouteException("协议读取异常:"+status, ROUTERRERINFO.e8, this, new Exception());
		}

	}
	
	private void parseHead() throws RouteException {
		int len = byteArrHead.getLen();
		if (len == 0)
			throw new RouteException("协议异常 扩展协议头不完整", ROUTERRERINFO.e8, this, new Exception());
		
		//byte[] buff = byteArrHead.getBytes();
		
		byte splitCnt = 0;
		int nStart = 0;
		int nEnd = 0;
		
		int i = 0;
		for (; i < len; i++) {
			if (byteHead[i] == CONSTS.SPLIT_BYTE) {
				if (byteHead[i+1] == CONSTS.SPLIT_BYTE) {
					splitCnt++;
					nStart = nEnd == 0 ? 0 : nEnd + 2;
					nEnd = i;
					
					int byteLen = nEnd - nStart;
					
					if (splitCnt == 1) {
						if (command.getCapacity() < byteLen) {
							command = new ByteArray(byteLen);
							byteCommand = command.getBytes();
						}
						
						System.arraycopy(byteHead, nStart, byteCommand, 0, byteLen);
						
						supportCmd = RedisUtil.GetCommand(byteCommand, byteLen);
						command.setLen(byteLen);
	
						if (supportCmd == null) {
							throw new RouteException("不支持的command: "+command, ROUTERRERINFO.e8, this, new Exception());
						} else {
							this.opType = (supportCmd.getOperatorType() == RedisUtil.R) ? OperateType.READ : OperateType.WRITE;
						}
					} else if (splitCnt == 2) {
						if (key.getCapacity() < byteLen) {
							key = new ByteArray(byteLen);
							byteKey = key.getBytes();
						}
						
						System.arraycopy(byteHead, nStart, byteKey, 0, byteLen);
						
						key.setLen(byteLen);
						slot = KeyHash.keyHashSlot(byteKey, byteLen);
					} else if (splitCnt == 3) {
						groupId = new String(byteHead, nStart, byteLen);
					} else if (splitCnt == 4) {
						clientReqId = 0;
						
						for (int idx = nStart; idx < nEnd; idx++) {
							byte b = byteHead[idx];
							clientReqId *= 10;
							clientReqId += b - '0';
						}
						break;
					}
				}
			}
		}
		
		if (splitCnt < 4)
			throw new RouteException("协议异常 扩展协议头不完整", ROUTERRERINFO.e8, this, new Exception());

		if (len - i < 2) {
			throw new RouteException("协议异常 扩展协议头不完整", ROUTERRERINFO.e8, this, new Exception());
		} else {
			i = nEnd + 2;
			for (; i < len; i++) {
				respLen *= 10;
				respLen += byteHead[i] - '0';
			}
			
			initResp(respLen);
		}
	}
	
	private void initResp(int respLen) {
		redisReqId = reqIDGen.nextID();
		int idLen = CommUtil.getLongLength(redisReqId);
		
		int headLen = CONSTS.PROTO_HEAD_LEN + idLen + CONSTS.CRLF_LEN;
		int totalLen = respLen + headLen;
		if (resp == null) {
			resp = byteBufferPool.allocate(totalLen);
		} else {
			if (resp.capacity() < totalLen) {
				byteBufferPool.recycle(resp);
				resp = byteBufferPool.allocate(totalLen);
			}
		}
		
		byteBuffResp = resp.getBuffer();
		byteBuffResp.clear();
		
		byteBuffResp.put(CONSTS.PROTO_HEAD);
		
		CommUtil.getLongBytes(redisReqId, idLen, byteRedisID);
		byteBuffResp.put(byteRedisID, 0, idLen);
		
		byteBuffResp.put(CONSTS.CRLF);
	}
	
	private void readDollar() throws BufferOverflowException, IOException, RouteException {
		byte b = reador.readByte();
		if (b != CONSTS.DOLLAR_BYTE) {
			throw new RouteException("协议异常 expect "+Character.valueOf((char) CONSTS.DOLLAR_BYTE), ROUTERRERINFO.e8, this, new Exception());
		}
	}
	
	private void readCrLf() throws BufferOverflowException, IOException, RouteException {
		byte cr = reador.readByte();
		if (cr != '\r') {
			throw new RouteException("协议异常 expect "+Character.valueOf((char) CONSTS.DOLLAR_BYTE), ROUTERRERINFO.e8, this, new Exception());
		}
		
		byte lf = reador.readByte();
		if (lf != '\n') {
			throw new RouteException("协议异常 expect "+Character.valueOf((char) CONSTS.DOLLAR_BYTE), ROUTERRERINFO.e8, this, new Exception());
		}
	}
	
	public BufferProxy getBackResp() {
		return this.backResp;
	}
	
	public void setBackResp(BufferProxy buf) {
		this.backResp = buf;
	}
	
	public int getBackRespLen() {
		return this.backRespLen;
	}
	
	public void setBackRespLen(int len) {
		this.backRespLen = len;
	}
	
	protected void readResp(int respLen, IntCounter haveRead) throws BufferOverflowException, IOException {
		reador.readWithLength(byteBuffResp, respLen, haveRead);
	}
	
	protected void readHead(int headLen, IntCounter haveRead) throws BufferOverflowException, IOException {
		reador.readWithLength(byteArrHead, headLen, haveRead);
	}

	void setCommand(ByteArray command) {
		this.command = command;
	}

	public ByteArray getCommand() {
		return command;
	}

	public NIOSession getSession() {
		return session;
	}
	
	public String getGroupId() {
		return groupId;
	}
	
	public ByteArray getKey() {
		return key;
	}
	
	public OperateType getOpType() {
		return opType;
	}
	
	public int getSlot() {
		return slot;
	}
	
	public BufferProxy getResp() {
		return resp;
	}
	
	public void setResp(BufferProxy resp) {
		this.resp = resp;
	}

	public int getRespLen() {
		return respLen;
	}

	public boolean isFinishedClientRead() {
		return status == READ_STATUS.CLIENT_READ_FINISHED;
	}
	
	public void clear() {
		status = READ_STATUS.INIT;
		
		byteArrHead.clear();
		command.clear();
		redisID.clear();
		key.clear();
		
		respRead.set(0);
		headRead.set(0);
		
		supportCmd = null;
		opType = null;
		
		redisReqId = -1;
		clientReqId = -1;
		respLen = 0;
		backRespLen = 0;
		headLen = 0;
		slot = -1;
		retCode = 0;
		
		groupId = "";
		redisUrl = "";
		
		session = null;
		reador = null;
		
		needClose = false;
		
		if (resp != null) {
			resp.clear();
			
			if (respLen >= CONSTS.LARGE_PACK_LEN) {
				byteBufferPool.recycle(resp);       // 大包，池中本来就少，用完就归还否则残留在Request中，Request池很大导致ByteBuffer大包越用越少
				resp = null;
			}
		}
		
		if (backResp != null) {
			backResp.clear();
			
			if ((backRespLen + CONSTS.RESPOND_HEAD_LEN) > CONSTS.LARGE_PACK_LEN) {
				byteBufferPool.recycle(backResp);   // 大包，池中本来就少，用完就归还否则残留在Request中，Request池很大导致ByteBuffer大包越用越少
				backResp = null;
			}
		}
	}
	
	public SupportCommand getSupportCmd() {
		return this.supportCmd;
	}
	
	public void setRetCode(int code) {
		this.retCode = code;
	}
	
	public int getRetCode() {
		return this.retCode;
	}
	
	public int getRequestPackLen() {
		return headLen + respLen;
	}
	
	public int getBackPackLen() {
		return backRespLen + CONSTS.RESPOND_HEAD_LEN;
	}

	public long getRevTs() {
		return revTs;
	}

	public void setRevTs(long revTs) {
		this.revTs = revTs;
	}
	
	public long getRevNs() {
		return revNs;
	}
	
	public void setRevNs(long revNs) {
		this.revNs = revNs;
	}

	public long getSendBkTs() {
		return sendBkTs;
	}

	public void refreshSendBkTs() {
		this.sendBkTs = SystemTimer.currentTimeMillis();
	}

	public long getSendBkNs() {
		return sendBkNs;
	}
	
	public void refreshSendBkNs() {
		this.sendBkNs = SystemTimer.currentTimeNano();
	}
	
	public double getIntervalMs() {
		return (double)(this.sendBkNs-this.revNs)/1000000;
	}
	
	public String getRedisUrl() {
		return redisUrl;
	}

	public void setRedisUrl(String redisUrl) {
		this.redisUrl = redisUrl;
	}
	
	public long getClientReqId() {
		return clientReqId;
	}

	public void setClientReqId(long clientReqId) {
		this.clientReqId = clientReqId;
	}

	public long getRedisReqId() {
		return redisReqId;
	}

	public void setRedisReqId(long redisReqId) {
		this.redisReqId = redisReqId;
	}
	
	public boolean isNeedClose() {
		return needClose;
	}

	public void setNeedClose(boolean needClose) {
		this.needClose = needClose;
	}

	private static enum READ_STATUS {
		INIT,                  // 初始值
		CMD_HEAD,              // 读取扩展头
		CMD_DATA,              // 已从客户端读取完整cmd报文
		CLIENT_READ_FINISHED;  // 已从客户端读取一个完整请求报文
	}

}
