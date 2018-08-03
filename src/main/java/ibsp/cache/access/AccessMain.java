package ibsp.cache.access;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.rmi.registry.LocateRegistry;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.*;
import javax.security.auth.Subject;

import ibsp.cache.access.configure.*;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibsp.cache.access.client.acceptor.ClientAcceptor;
import ibsp.cache.access.event.EventController;
import ibsp.cache.access.pool.mempool.BufferPool;
import ibsp.cache.access.pool.mempool.ByteArrayPool;
import ibsp.cache.access.pool.mempool.RedisRequestPool;
import ibsp.cache.access.pool.threadpool.WorkerPool;
import ibsp.cache.access.util.CONSTS;
import ibsp.cache.access.util.HealthMonitor;
import ibsp.cache.access.util.Metrics;
import ibsp.cache.access.util.PropertiesUtils;

public class AccessMain {
	private static final Logger Log = LoggerFactory.getLogger(AccessMain.class);
	private static final String MBEAN_PROXY = "ibsp.metaserver.cache.access:name=Proxy";
	private static final CountDownLatch runLatch = new CountDownLatch(1);
	private static ClientAcceptor acceptor = null;
	private static MBeanServer mbs = null;

	private static void registerUnregisterJMX(boolean doRegister) throws Exception {

		if (mbs == null ){
			mbs = ManagementFactory.getPlatformMBeanServer();
		}
		ObjectName name = new ObjectName(MBEAN_PROXY);
		if (doRegister){
			if (!mbs.isRegistered(name)){
				mbs.registerMBean(Statistics.get(), name);
			}
		} else {
			if (mbs.isRegistered(name)){
				mbs.unregisterMBean(name);
			}
		}

	}

	private static void startJMX(ICacheProxyService cacheProxyService) throws Exception {
		registerUnregisterJMX(true);

		int rmiPort = cacheProxyService.getProxyInfo().getJmxport();
		LocateRegistry.createRegistry(rmiPort);

		Map<String, Object> props = new HashMap<>();
		props.put(JMXConnectorServer.AUTHENTICATOR, createJMXAuthenticator("admin", "admin"));

		JMXServiceURL url = new JMXServiceURL(String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", "127.0.0.1",
				String.valueOf(rmiPort)));
		JMXConnectorServer jmxConnector = JMXConnectorServerFactory.newJMXConnectorServer(url, props, mbs);
		jmxConnector.start();
	}

	private static JMXAuthenticator createJMXAuthenticator(String credentialUserName, String credentialPassword) {
		return new JMXAuthenticator() {
			public Subject authenticate(Object credentials) {
				String[] sCredentials = (String[]) credentials;
				if (null == sCredentials || sCredentials.length != 2) {
					throw new SecurityException("Authentication failed!");
				}
				String userName = sCredentials[0];
				String pValue = sCredentials[1];

				if (credentialUserName.equals(userName) && credentialPassword.equals(pValue)) {
					Set<Principal> principals = new HashSet<>();
					principals.add(new JMXPrincipal(userName));
					return new Subject(true, principals, Collections.EMPTY_SET,
							Collections.EMPTY_SET);
				}
				throw new SecurityException("Authentication failed!");
			}
		};
	}

	private static void InitPool() {
		BufferPool.setPool(new BufferPool());
		ByteArrayPool.setPool(new ByteArrayPool());
		RedisRequestPool.setPool(new RedisRequestPool());
		WorkerPool.setPool(new WorkerPool());
	}
	
	public static void main(String[] args) {
		PropertyConfigurator.configure(PropertiesUtils.getInstance("log4j").getProperties());		
		String proxyID = PropertiesUtils.getInstance("init").get(CONSTS.CONS_PROXY_ID);
     	String metasvrUrl = PropertiesUtils.getInstance("init").get(CONSTS.CONS_METASVR_ROOTURL);

		Config.setConfig(new Config());
		InitPool();
		
		final ICacheProxyService cacheProxyService = CacheProxyServiceImpl.getInstance();
		cacheProxyService.setConfigProxyService(MetadataConfigProxyService.getInstance(proxyID, metasvrUrl));
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
			
			//启动事件监听
			EventController.getInstance();
			
			//启动JMX服务
			Log.info("启动JMX服务...");
			startJMX(cacheProxyService);
	    	
			Log.info("接入机启动成功，绑定端口:" + localPort + ", JMX端口:" +
					cacheProxyService.getProxyInfo().getJmxport());
		} catch (Exception e) {
			Log.error("接入机启动失败！", e);
			System.exit(0);
		}
	}

}
