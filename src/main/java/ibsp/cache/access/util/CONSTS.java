package ibsp.cache.access.util;

public final class CONSTS {
	
	public static final byte SPLIT_BYTE = 0x15;
	
	public static final int LARGE_PACK_LEN = 32*1024;
	
	// IBSP
	public static final String CONF_PATH             = "conf";
	
	public static final String JSON_HEADER_RET_CODE    = "RET_CODE";
	public static final String JSON_HEADER_RET_INFO    = "RET_INFO";
	
	public static final int REVOKE_OK                  = 0;
	public static final int REVOKE_NOK                 = -1;
	
	public static final String HTTP_PROTOCAL           = "http";
	public static final String HTTP_METHOD_GET         = "GET";
	public static final String HTTP_METHOD_POST        = "POST";
	
	public static final String META_SERVICE            = "metasvr";
	public static final String CACHE_SERVICE           = "cachesvr";
	
	public static final String FUN_URL_TEST            = "test";
	public static final String FUN_GET_PROXY_INFO      = "getProxyInfoByID";
	public static final String FUN_GET_CLUSTER_INFO    = "getNodeClusterInfo";
	
	//original
	public static final String CONS_ZOOKEEPER_HOST = "zookeeper.host";
	public static final String CONS_ZOOKEEPER_ROOT_PATH = "zookeeper.root.path";
	public static final String CONS_ZOOKEEPER_ACCESS_NAME = "proxyName";
	//IBSP
	public static final String CONS_SERVICE_ID = "service.id";
	public static final String CONS_PROXY_ID = "proxy.id";
	public static final String CONS_METASVR_ROOTURL = "metasvr.rooturl";
	
	// monitor
	public static int NORMAL_RETCODE = 0;                          // 正常返回retCode=0
	public static String VAL_SPLITER = "&";                        // value分隔符
	
	public static int RESPOND_HEAD_LEN = 18;
	public static byte[] CRLF = { 0x0D, 0x0A };  // \r\n
	public static char[] CHAR_CRLF = { 0x0D, 0x0A };  // \r\n
	public static int CRLF_LEN = CRLF.length;
	
	// redis protocal
	public static final byte DOLLAR_BYTE = '$';
	public static final byte PLUS_BYTE = '+';
	public static final char[] CHAR_ERROR = { '-', 'E', 'R', 'R', ' ' };
	public static final String CHARSET = "UTF-8";
	  
	// extend protocal
	public static byte[] PROTO_HEAD = { 'f', 'u', 'j', 'i', 't', 's', 'u', '#' };
	public static int PROTO_HEAD_LEN = PROTO_HEAD.length;
	
	public static byte PROTO_SPLIT = 0x3A;    // ':'
	public static byte PROTO_CR = 0x0D;       // '\r'
	public static byte PROTO_LN = 0x0A;       // '\n'
	public static int PROTO_ID_MAXLEN = 19;   // long MAX_VALUE = 0x7fffffffffffffffL
	public static int PROTO_RESP_MAXLEN = 10; // int MAX_VALUE = 0x7fffffff
	
	public static byte[] CMD_PING = { '*', '1', '\r', '\n', '$', '4', '\r', '\n', 'P', 'I', 'N', 'G', '\r', '\n' };
	public static int CMD_PING_LEN = CMD_PING.length + PROTO_HEAD_LEN + PROTO_ID_MAXLEN + CRLF.length;
	
}
