package ibsp.cache.access.route;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

import ibsp.cache.access.Config;
import ibsp.cache.access.configure.CacheProxyServiceImpl;
import ibsp.cache.access.configure.ICacheProxyService;
import ibsp.cache.access.exception.RouteException;
import ibsp.cache.access.exception.RouteException.ROUTERRERINFO;
import ibsp.cache.access.redis.processor.IRedisProcessor;
import ibsp.cache.access.request.RedisRequest;
import ibsp.cache.access.route.GroupInfo.OperateType;
import ibsp.cache.access.session.NIOSession;
import ibsp.cache.access.util.RedisUtil.SupportCommand;

public class RouterProcessor {
	
	private int routerCnt = 1;
	private int seed = 0;
	private RouterRuner[] runers;
	private static RouterProcessor ROUTER;
	
	public RouterProcessor() {
		routerCnt = Config.getConfig().getRouter_cnt();
		runers = new RouterRuner[routerCnt];
		for (int i = 0; i < routerCnt; i++) {
			runers[i] = new RouterRuner();
		}
	}
	
	public static RouterProcessor getRouter() {
		if (ROUTER == null)
			ROUTER = new RouterProcessor();
		
		return ROUTER;
	}
	
	public void addRequest(RedisRequest request) {
		if (routerCnt == 1) {
			runers[0].addRequest(request);
		} else if (routerCnt > 1) {
			runers[seed++ % routerCnt].addRequest(request);
		}
	}
	
	public void Stop() {
		if (runers == null)
			return;
		
		for (RouterRuner runer : runers) {
			if (runer == null)
				continue;
			
			runer.StopRunning();
		}
	}
	
	private static final Logger logger = Logger.getLogger(RouterRuner.class);
	private static class RouterRuner implements Runnable {
		private BlockingQueue<RedisRequest> taskQueue;
		private volatile boolean bRunning;
		
		private Thread routerThread;
		private ICacheProxyService proxySvr;
		
		public RouterRuner() {
			this.taskQueue = new ArrayBlockingQueue<RedisRequest>(Config.getConfig().getRouterq_max_len());
			
			this.routerThread = new Thread(this);
			this.routerThread.setDaemon(false);
			this.routerThread.setName("RouterRuner");
			this.routerThread.start();
		}
		
		public void StopRunning() {
			bRunning = false;
		}
		
		public void addRequest(RedisRequest request) {
			if (request != null) {
				taskQueue.offer(request);
			}
		}
	
		@Override
		public void run() {
			this.bRunning = true;
			this.proxySvr = CacheProxyServiceImpl.getInstance();
			
			RedisRequest request = null;
			while (bRunning) {
				try {
					request = taskQueue.take();
					
					// "PING" 命令不带key, 而且只是客户端和接入机间维持连接的作用, 不需要往后端redis推, 直接返回给客户端"PONG"
					if (request.getSupportCmd() == SupportCommand.PING) {
						NIOSession session = request.getSession();
						session.sendBackPong(request);
					} else {
						String groupId = request.getGroupId();
						OperateType opType = request.getOpType();
						int slot = request.getSlot();
						
						IRedisProcessor processor = null;
						CacheNode node = null;
						NIOSession session = request.getSession();
						try {
							node = proxySvr.getDestNode(groupId, slot, opType);
							if (node == null) {
								logger.error("groupId:" + groupId + " not exist ......");
								request.setRedisUrl("0.0.0.0:0");  // 找不到路由时, 设置0.0.0.0:0 防止后续统计监控取不到
								session.exceptionEnd(new RouteException("groupid:" + groupId + " not exist ......", ROUTERRERINFO.e13, request), true);
								request = null;
							} else {
								processor = node.getProcessor();
							}
						} catch (Exception e) {
							processor = null;
							logger.error(e);
							request.setRedisUrl("0.0.0.0:0");  // 找不到路由时, 设置0.0.0.0:0 防止后续统计监控取不到
							session.exceptionEnd(new RouteException("请求找不到目标redis,groupid:"+groupId+",slot:"+slot, ROUTERRERINFO.e13, request, e), true);
							request = null;
						}
						
						if (processor == null) {
							if (request != null) {
								request.setRedisUrl("0.0.0.0:0");  // 找不到路由时, 设置0.0.0.0:0 防止后续统计监控取不到
								session.exceptionEnd(new RouteException("请求找不到目标redis,groupid:"+groupId+",slot:"+slot, ROUTERRERINFO.e13, request, new Exception()), true);
							}
						} else {
							try {
								request.setRedisUrl(processor.getConnUrl());
								processor.dispatchRequest(request);
							} catch (RouteException e) {
								logger.info("RouterProcessor RouteException ......");
								session.exceptionEnd(e, true);
							}
						}
					}
				} catch (InterruptedException e) {
					logger.error(e);
				}
			}
		}
	
	}
	
}
