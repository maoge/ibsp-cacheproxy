package ibsp.cache.access.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Proxy {
	public static final Logger logger = LoggerFactory.getLogger(Proxy.class);
	private int         jmxport = -1;
	private String      proxyName;
	private String      Address;
	private String      groups;
	private HostAndPort hostAndPort;

	public String getProxyName() {
		return proxyName;
	}

	public void setProxyName(String proxyName) {
		this.proxyName = proxyName;
	}

	public void setAddress(String address) {
		Address = address;
		if(Address!=null) {
			String[] hostInfo = address.split(":");
			setHostAndPort(new HostAndPort(hostInfo[0], Integer.parseInt(hostInfo[1])));
		}
	}
	
	public String getGroups() {
		return groups;
	}

	public void setGroups(String groups) {
		this.groups = groups;
	}

	public HostAndPort getHostAndPort() {
		return hostAndPort;
	}

	public void setHostAndPort(HostAndPort hostAndPort) {
		this.hostAndPort = hostAndPort;
	}

	public int getJmxport() {
		return jmxport;
	}

	public void setJmxport(int jmxport) {
		this.jmxport = jmxport;
	}
}
