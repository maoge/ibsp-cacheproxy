package ibsp.cache.access.exception;

import ibsp.cache.access.request.RedisRequest;

/**
 * 50010000
 * 50010001 路由队列堵塞
 * 50010002 添加路由队列异常
 * 50010003 路由处理异常
 * 50010004 注册客户端读失败
 * 50010005 超过最大允许的客户端连接数
 * 50010006 注册目标路由读事件异常
 * 50010007 单个客户端连接请求数大于规定值
 * 50010008 协议异常
 * 50010009 不完整的报文
 * 50010010 route 程序bug
 * 50010011 网络io异常
 * 50010012 目标服务器繁忙,稍后再试
 * 50010013 获取目标路由主机失败
 * 50010014 路由主机配置端口不合法
 * 50010015 连接目标主机失败
 * 50010016 目标主机网络繁忙
 * 50010017 路由发送给目标主机时网络错误
 * 50010018 读取解析客户端请求失败
 * 50010019 注册redis服务端返回失败
 * 50010020 redis read io exception
 * 50010021 接入机内部异常
 * 50010022 内存池不够
 * 50010023 redisReqId 重复
 * 50010024 redis respond data error
 */

public class RouteException extends Exception {
	private static final long serialVersionUID = -5525423385719978388L;

	public static enum ROUTERRERINFO {
		DEFAULT(50010000, false, false),//默认错误码
		e1(50010001, true, false),// 路由队列堵塞
		e2(50010002, true, false),// 添加路由队列异常
		e3(50010003, true, false),// 路由处理异常
		e4(50010004, true, true),// 注册客户端读失败
		e5(50010005, true, true),// 超过最大允许的客户端连接数
		e6(50010006, true, false),// 注册目标路由读事件异常
		e7(50010007, true, false),// 单个客户端连接请求数大于规定值
		e8(50010008, true, true),// 协议异常
		e9(50010009, true, false),// 不完整的报文
		e10(50010010, true, false),// route 程序bug
		e11(50010011, false, true),// 网络io异常
		e12(50010012, true, false),// 目标服务器繁忙,稍后再试
		e13(50010013, true, false),// 获取目标路由主机失败
		e14(50010014, true, false),// 路由主机配置端口不合法
		e15(50010015, true, false),// 连接目标主机失败
		e16(50010016, true, false),// 目标主机网络繁忙
		e17(50010017, true, false),// 路由发送给目标主机时网络错误
		e18(50010018, true, false),// 读取解析客户端请求失败
		e19(50010019, true, false),// 注册redis服务端返回失败
		e20(50010020, true, false),// redis read io exception
		e21(50010021, true, false),// 接入机内部异常
		e22(50010022, true, false),// 内存池不够
		e23(50010023, true, false),// redisReqId 重复
		e24(50010024, true, false),// redis respond data error
;
		private int value;     //
		private boolean bSendBack;  // 是否往客户端回写错误信息
		private boolean bCloseClient;  // 是否关闭对端连接


		private ROUTERRERINFO(int s, boolean bBack, boolean bClose) {
			// 定义枚举的构造函数
			value = s;
			bSendBack = bBack;
			bCloseClient = bClose;
		}
		
		public int getValue() {
			// 得到枚举值代表的字符串。
			return value;
		}
		
		public boolean getSendBack() {
			return bSendBack;
		}
		
		public boolean getCloseClient() {
			return bCloseClient;
		}
	}
	
	private int errorCode;
	private boolean bSendBack;  // 是否往客户端回写错误信息
	private boolean bCloseClient;  // 是否关闭对端连接
	private RedisRequest request;
	
	public RouteException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
	
	public RouteException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public RouteException(String message) {
		super(message);
	}
	
	public RouteException(Throwable cause) {
		super(cause);
	}
	
	public RouteException(String message, ROUTERRERINFO errorInfo, RedisRequest request, Throwable cause) {
		super(message, cause);
		
		this.errorCode = errorInfo.value;
		this.bSendBack = errorInfo.bSendBack;
		this.bCloseClient = errorInfo.bCloseClient;
		this.request = request;
	}
	
	public RouteException(String message, ROUTERRERINFO errorInfo, RedisRequest request) {
		super(message);
		
		this.errorCode = errorInfo.value;
		this.bSendBack = errorInfo.bSendBack;
		this.bCloseClient = errorInfo.bCloseClient;
		this.request = request;
	}
	
	public RouteException(String message, ROUTERRERINFO errorInfo) {
		super(message);
		
		this.errorCode = errorInfo.value;
		this.bSendBack = errorInfo.bSendBack;
		this.bCloseClient = errorInfo.bCloseClient;
	}
	
	public int getErrorCode() {
		return errorCode;
	}
	public RouteException setErrorCode(ROUTERRERINFO errorInfo) {
		this.errorCode = errorInfo.value;
		return this;
	}
	
	public RouteException setErrorCode(int errorCode) {
		this.errorCode = errorCode;
		return this;
	}
	
	public boolean getSendBack() {
		return bSendBack;
	}
	
	public boolean getCloseClient() {
		return bCloseClient;
	}
	
	public RedisRequest getRequest() {
		return request;
	}
	
	public RouteException setRequest(RedisRequest request) {
		this.request = request;
		return this;
	}
	
}
