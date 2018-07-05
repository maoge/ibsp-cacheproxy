package ibsp.cache.access.util;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class RedisUtil {
	
	public static final int W = 0;
	public static final int R = 1;
	public static final int WR = 2;
	public static final Map<Long, SupportCommand> cmdMap;
	
	static {
		cmdMap = new HashMap<Long, SupportCommand>();
		for (SupportCommand cmd : EnumSet.allOf(SupportCommand.class)) {
			cmdMap.put(cmd.getID(), cmd);
		}
	}
	
	public static enum SupportCommand {
		SET(W), GET(R), DEL(W), HSET(W), HGET(R), HDEL(W), HSETNX(W), HMSET(W), HMGET(R), HGETALL(R), EXISTS(R), TYPE(R), 
		INCRBY(W), INCR(W), APPEND(W), EXPIRE(W), TTL(W), GETSET(W),  
		HINCRBY(W), HEXISTS(R), LINDEX(R), LSET(W), LREM(W), SETNX(W), SETEX(W), DECRBY(W), DECR(W), HLEN(R), HKEYS(R), 
		ZSCORE(R), HVALS(R),  RPUSH(W), LPUSH(W), LLEN(R), LRANGE(R), LTRIM(W), LPOP(W), RPOP(W), BLPOP(W), BRPOP(W), SADD(W), 
		SMEMBERS(R), SREM(W), SCARD(W), SISMEMBER(R), ZADD(W), ZRANGE(R), ZREM(W), ZINCRBY(W), ZCARD(W), 
		ZCOUNT(R), ZRANGEBYSCORE(W), ZREMRANGEBYRANK(W), ZREMRANGEBYSCORE(W), STRLEN(R), 
		LPUSHX(W), PERSIST(W), RPUSHX(W), LINSERT(W), SETRANGE(W), GETRANGE(R), PEXPIRE(W), PTTL(W), 
		HSCAN(R), PING(R);
		
		// RENAME(WR), RENAMENX(WR)  接入机不好支持, 通过在客户端拆成多条指令来实现
		// SUBSTR 命令就是GETRANGE
		// TTL PTTL 由于主从复制有时差, 所以设置为W用以从主节点获取较准确
		
		private final byte[] value;
		private final String cmd;
		private final int OperatorType;
		private final Long id;

		SupportCommand(int operatorType) {
		    value = this.name().getBytes();
		    cmd = new String(value);
		    OperatorType = operatorType;
		    id = hash(value, 0, value.length);
		}

		public byte[] getValue() {
		    return value;
		}

		public int getOperatorType() {
		    return OperatorType;	
		}
		    
		public String getCmd() {
		    return cmd;
		}
		
		public long getID() {
			return id;
		}
		    
		public static SupportCommand lookup(final byte[] value, final int length) {
		    for (SupportCommand cmd : EnumSet.allOf(SupportCommand.class)) {
		        if(RedisUtil.equals(value, length, cmd.value)) {
                    return cmd;		        		  
		        }
		    }
		    return null;		    	
		}
	}
	
	private static long hash(byte[] arr, int offset, int len) {
		long result = 0L;
		if (arr == null)
			return result;
		
		int end = offset + len;
		for (int i = offset; i < end; i++) {
			result = (result << 8) | arr[i];
		}
		
		return result;      
	}

    private static boolean equals(final byte[] src, final int length, final byte[] dest) {
        if (src==dest)
            return true;
        if (src==null || dest==null)
            return false;
        if (length != dest.length)
            return false;
        for (int i=0; i<length; i++)
            if (src[i] != dest[i])
                return false;
        return true;
    }
    
	public static boolean CommandCheck(final byte[] cmd, final int length) {
		boolean checkResult = false;
		checkResult = SupportCommand.lookup(cmd, length) != null;
		return checkResult;
	}
	
	public static SupportCommand GetCommand(final byte[] cmd, final int length) {
		//return SupportCommand.lookup(cmd, length);
		long id = hash(cmd, 0, length);
		return cmdMap.get(id);
	}
	
	public static void main(String[] args) {
		String cmd = "ZREMRANGEBYRANK";
		long start = System.currentTimeMillis();
		long total = 10000000;
		
		for (int i = 0; i < total; i++) {
			GetCommand(cmd.getBytes(), cmd.length());
		}
		long end = System.currentTimeMillis();
		long diff = end - start;
		long tps = (total*1000)/diff;
		
		System.out.println("diff:" + diff + ", tps:" + tps);
		
	}
	
}