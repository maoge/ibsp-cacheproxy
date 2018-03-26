package ibsp.cache.access.util;

import java.lang.management.ManagementFactory;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXPrincipal;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibsp.cache.access.configure.ICacheProxyService;

public class JMXManager {
    private static final Logger Log = LoggerFactory.getLogger(JMXManager.class);
	private static final int DEFAULT_PORT = Registry.REGISTRY_PORT;
	private static JMXManager instance = null;
	private ICacheProxyService cacheProxyService = null;
    private MBeanContainer mbContainer;
    private ConnectorServer jmxServer;
	
	public static JMXManager getInstance(ICacheProxyService cacheProxyService) {
		if (instance == null) {
			instance = new JMXManager(cacheProxyService);
			instance.start();
		}
		return instance;
	}

	private JMXManager(ICacheProxyService cacheProxyService) {
		this.cacheProxyService = cacheProxyService;
	}
	
	private void start() {
		setContainer(new MBeanContainer(ManagementFactory.getPlatformMBeanServer()));
//		getContainer().addBean(org.eclipse.jetty.util.log.Log.getLog());
		int jmxPort = cacheProxyService.getProxyInfo().getJmxport()==-1 ? DEFAULT_PORT : cacheProxyService.getProxyInfo().getJmxport();
		String jmxUrl = "/jndi/rmi://localhost:" + jmxPort + "/jmxrmi";
		Map<String, Object> env = new HashMap<String, Object>();
		env.put("jmx.remote.authenticator", new JMXAuthenticator() {
			public Subject authenticate(Object credentials) {
	            if (!(credentials instanceof String[])) {
	                if (credentials == null) {
	                    throw new SecurityException("Credentials required");
	                }
	                throw new SecurityException("Credentials should be String[]");
	            }
	            final String[] aCredentials = (String[]) credentials;
	            if (aCredentials.length < 2) {
	                throw new SecurityException("Credentials should have at least two elements");
	            }
	            String username = (String) aCredentials[0];
	            // String password = (String) aCredentials[1];
                return new Subject(true,
                        Collections.singleton(new JMXPrincipal(username)),
                        Collections.EMPTY_SET,
                        Collections.EMPTY_SET);    	            
	        }
		});
		
		try {
			jmxServer = new ConnectorServer(new JMXServiceURL("rmi", null, jmxPort, jmxUrl), env, "org.eclipse.jetty.jmx:name=rmiconnectorserver");
			jmxServer.doStart();
		} catch (Exception e) {
			Log.error("Failed to start JMX connector", e);
		}
	}
	
	public void stop() {
		if (jmxServer!=null) {
			try {
				jmxServer.doStop();
			} catch(Exception e) {
				Log.error("Failed to stop JMX connector", e);
			} finally {
				jmxServer = null;
			}
		}
	}
	
	public MBeanContainer getContainer() {
		return mbContainer;
	}

	public void setContainer(MBeanContainer mbContainer) {
		this.mbContainer = mbContainer;
	}
}
