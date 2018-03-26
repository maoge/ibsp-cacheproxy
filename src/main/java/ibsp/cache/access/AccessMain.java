package ibsp.cache.access;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibsp.cache.access.client.acceptor.ClientAcceptor;
import ibsp.cache.access.configure.CacheProxyServiceImpl;
import ibsp.cache.access.configure.ICacheProxyService;
import ibsp.cache.access.configure.ILoadConfigCallback;
import ibsp.cache.access.configure.IStatisticsMBean;
import ibsp.cache.access.configure.MetadataConfigProxyService;
import ibsp.cache.access.configure.ProxyStatistics;
import ibsp.cache.access.pool.mempool.BufferPool;
import ibsp.cache.access.pool.mempool.ByteArrayPool;
import ibsp.cache.access.pool.mempool.RedisRequestPool;
import ibsp.cache.access.pool.threadpool.WorkerPool;
import ibsp.cache.access.util.CONSTS;
import ibsp.cache.access.util.HealthMonitor;
import ibsp.cache.access.util.JMXManager;
import ibsp.cache.access.util.Metrics;
import ibsp.cache.access.util.PropertiesUtils;

public class AccessMain {
	private static final Logger Log = LoggerFactory.getLogger(AccessMain.class);
	private static final String MBEAN_PROXY = "com.ctg.itrdc.cache.access:type=Proxy";
	private static final CountDownLatch runLatch = new CountDownLatch(1);
	private static ClientAcceptor acceptor = null;
	private static MBeanServer mbs = null;
    private static Server JMXServer = null;

	private static void registerUnregisterJMX(boolean doRegister, IStatisticsMBean statisicsBean) {
		try {
			if (mbs == null ){
				mbs = ManagementFactory.getPlatformMBeanServer();
			}
			ObjectName name = new ObjectName(MBEAN_PROXY);
			if (doRegister){
				if (!mbs.isRegistered(name)){
					mbs.registerMBean(statisicsBean, name);
				}
			} else {
				if (mbs.isRegistered(name)){
					mbs.unregisterMBean(name);
				}
			}
		} catch (Exception e) {
			Log.error("Unable to start/stop JMX", e);
		}
	}
	
	private static void InitPool() {
		BufferPool.setPool(new BufferPool());
		ByteArrayPool.setPool(new ByteArrayPool());
		RedisRequestPool.setPool(new RedisRequestPool());
		WorkerPool.setPool(new WorkerPool());
	}
	
	public static void main(String[] args) {
		PropertyConfigurator.configure(PropertiesUtils.getInstance("log4j").getProperties());		
//        String proxyName = PropertiesUtils.getInstance("init").get(CONSTS.CONS_ZOOKEEPER_ACCESS_NAME);
//        String zkConnectUrl = PropertiesUtils.getInstance("init").get(CONSTS.CONS_ZOOKEEPER_HOST);
//        String zkRootPath = PropertiesUtils.getInstance("init").get(CONSTS.CONS_ZOOKEEPER_ROOT_PATH);
		String serviceID = PropertiesUtils.getInstance("init").get(CONSTS.CONS_SERVICE_ID);
		String proxyID = PropertiesUtils.getInstance("init").get(CONSTS.CONS_PROXY_ID);
     	String metasvrUrl = PropertiesUtils.getInstance("init").get(CONSTS.CONS_METASVR_ROOTURL);
		
		
		Config.setConfig(new Config());
		InitPool();
		
		final ICacheProxyService cacheProxyService = CacheProxyServiceImpl.getInstance();
//		cacheProxyService.setConfigProxyService(ZkConfigProxyService.getInstance(proxyName, zkConnectUrl, zkRootPath));
		cacheProxyService.setConfigProxyService(MetadataConfigProxyService.getInstance(serviceID, proxyID, metasvrUrl));
		Log.info("加载接入机配置...");
		cacheProxyService.loadConfigInfo(Config.getConfig().isAuto_refresh(),
				Config.getConfig().getAuto_refresh_interval(),
				TimeUnit.SECONDS, new ILoadConfigCallback() {
					public void handle() {
						runLatch.countDown();
					}
				});
		
		HealthMonitor.setMonitor(new HealthMonitor());
		Metrics metrics = new Metrics();
		Thread metricThread = new Thread(metrics);
		metricThread.setDaemon(false);
		metricThread.start();
		
		try {
			runLatch.await();
			Log.info("启动接入机代理服务...");
			
			boolean getPort = false;
			int localPort = -1;
			while (!getPort) {
				try {
					localPort = cacheProxyService.getProxyInfo().getHostAndPort().getPort();
					getPort = true;
				} catch (Exception e) {
					Log.error("读取配置失败！");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {}
				}
			}
			
			acceptor = new ClientAcceptor();
			acceptor.bind(new InetSocketAddress("0.0.0.0", localPort));
			Log.info("启动接入机接口服务...");
			
			//启动JMX服务
			ProxyStatistics proxyStatistics = new ProxyStatistics(cacheProxyService);
			registerUnregisterJMX(true, proxyStatistics);
	    	JMXManager jmxManager = JMXManager.getInstance(cacheProxyService);
	    	JMXServer = new Server();
	    	JMXServer.getContainer().addEventListener(jmxManager.getContainer());
	    	JMXServer.addBean(jmxManager.getContainer());
	    	QueuedThreadPool jmxThreadPool = new QueuedThreadPool();
	    	jmxThreadPool.setDaemon(true);
	    	jmxThreadPool.setMaxThreads(0);
	    	jmxThreadPool.setMinThreads(0);
	    	JMXServer.setThreadPool(jmxThreadPool);
	    	JMXServer.start();
	    	
			Log.info("接入机启动成功，绑定端口:" + localPort);
		} catch (Exception e) {
			Log.error("接入机启动失败！", e);
		}
	}

}
