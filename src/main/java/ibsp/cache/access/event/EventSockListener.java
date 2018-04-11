package ibsp.cache.access.event;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ffcs.nio.core.config.Configuration;
import com.ffcs.nio.core.nio.TCPController;

public class EventSockListener {
	
	private static Logger logger = LoggerFactory.getLogger(EventSockListener.class);
	
	private TCPController controller;
	private EventHandler  eventHandler;
	
	private String ip;
	private int    port;

	private boolean isStarted = false;

	public EventSockListener(String ip, int port) {
		this.ip   = ip;
		this.port = port;
	}

	public void start() throws IOException {
		if (isStarted) {
			if (controller != null && controller.isStarted()) {
				return;
			}
		}

		Configuration config = new Configuration();
		config.setDispatchMessageThreadCount(1);
		config.setReadThreadCount(1);
		config.setWriteThreadCount(1);
		
		controller = new TCPController(config);
		controller.setSelectorPoolSize(1);
		
		eventHandler = new EventHandler();
		controller.setHandler(eventHandler);
		
		InetSocketAddress addr = new InetSocketAddress(ip, port);
		controller.bind(addr);
		controller.start();

		isStarted = true;
	}

	public void stop() throws IOException {
		if (isStarted && controller != null) {
			boolean realStatus = controller.isStarted();
			if (!realStatus)
				return;

			controller.stop();

			isStarted = false;
		}
	}
	
	public boolean IsStart() {
		return isStarted && controller.isStarted();
	}

}
