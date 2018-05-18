package ibsp.cache.access.client.acceptor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.Logger;

import ibsp.cache.access.Config;
import ibsp.cache.access.client.processor.ClientIoPrecessor;
import ibsp.cache.access.client.processor.IoProcessor;
import ibsp.cache.access.handler.HandlerFactory;
import ibsp.cache.access.handler.RedisHandlerFactory;
import ibsp.cache.access.session.NIOSession;

public class ClientAcceptor implements Runnable {

	public static  final Logger logger = Logger.getLogger(ClientAcceptor.class);
	private volatile Selector acceptSelect;
	private SelectorProvider selectorProvider;
	private ServerSocketChannel ssChannel;
	private final IoProcessor[] ioProcessors;
	
	private HandlerFactory handlerFactoy;
	
	Thread thread = null;
	
	private boolean bRunning = false;
	
	int processorCnt = 1;
	long seed = 0;
	
	public ClientAcceptor() throws IOException {
		super();
		
		this.acceptSelect = Selector.open();
		
		processorCnt = Config.getConfig().getClient_processor_cnt();
		processorCnt = Config.getConfig().getClient_processor_cnt();
		ioProcessors = new ClientIoPrecessor[processorCnt];
		for (int i=0; i<processorCnt; i++) {
			ioProcessors[i] = new ClientIoPrecessor();
		}
		
		this.handlerFactoy = new RedisHandlerFactory();
	}

	public void bind(SocketAddress localAddress) throws IOException{
		if (selectorProvider != null) {
			ssChannel = selectorProvider.openServerSocketChannel();
		} else {
			ssChannel = ServerSocketChannel.open();
		}

		boolean success = false;

		try {
			ssChannel.configureBlocking(false);
			ServerSocket socket = ssChannel.socket();

			try {
				socket.bind(localAddress, Config.getConfig().getMax_waiting_acceptor());
			} catch (IOException ioe) {
				String newMessage = "Error while binding on " + localAddress + "\n" + "original message : "
						+ ioe.getMessage();
				IOException e = new IOException(newMessage);
				e.initCause(ioe.getCause());
				ssChannel.close();
				throw e;
			}

			ssChannel.register(acceptSelect, SelectionKey.OP_ACCEPT);
			success = true;

			if (thread == null) {
				synchronized (this) {
					if (thread == null ) {
						thread = new Thread(this);
						thread.setDaemon(false);
						thread.setName("ClientAcceptor");
						thread.start();
					}
				}
			}
		} finally {
			if (!success) {
				close();
			}
		}
	}

	protected void close() throws IOException {
		if (ssChannel != null) {
			if (ssChannel.isRegistered()) {
				SelectionKey key = ssChannel.keyFor(acceptSelect);
				if (key != null) {
					key.cancel();
				}
			}
			
			if (ssChannel.isOpen()) {
				ssChannel.close();
			}
			
			ssChannel = null;
		}
		
		if (acceptSelect != null && acceptSelect.isOpen()) {
			acceptSelect.close();
			acceptSelect = null;
		}
	}

	public void process(SelectionKey key){
		if (!key.isValid())
			return;
		
		if (key.isAcceptable()) {
			ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
			SocketChannel sc = null;
			try
			{	
				sc = ssc.accept();
				sc.configureBlocking(false); // 一定要将它设置为非阻塞，这样才可以使用Selector。
				
				// 	触发接受连接事件
				IoProcessor ioProcessor = getRandomProcessor();
				NIOSession session = new NIOSession(sc, ioProcessor);
				handlerFactoy.createHandler(session);
				
				session.refreshLastTimeForClientRead();  // 连接上来要更新时间, 防止先连接上不发送数据导致接入机主动切断连接
				key.interestOps(SelectionKey.OP_ACCEPT);
				ioProcessor.addSession(session);
				
			}
			catch(Exception ex){
				logger.error("processAcceptEvent ERROR", ex);
				if(sc != null) {
					try {
						sc.close();
					} catch (Exception e) {
						logger.error("close client", ex);
					}
				}
			}
		}

	}
	
	private IoProcessor getRandomProcessor() {
		IoProcessor ioProcessor = ioProcessors[(int) (seed++ % processorCnt)];
		if (seed == Integer.MAX_VALUE) {
			seed = 0;
		}
		return ioProcessor;
	}

	public void run() {
		bRunning = true;
		
		// 监听
		while (bRunning) {
			try{
				int num = 0;

				num = acceptSelect.select(Config.getConfig().getSelect_wait_time());
				if (num > 0) {
					Set<SelectionKey> selectedKeys = acceptSelect.selectedKeys();
					Iterator<SelectionKey> it = selectedKeys.iterator();
					while (it.hasNext()) {
						SelectionKey key = (SelectionKey) it.next();
						it.remove();
						
						process(key);
					}
				}
			} catch (RuntimeException ex) {
				logger.error("主监听线程运行时异常！",ex);
			} catch (Exception ex) {
				logger.error("主监听线程异常！",ex);
			}
		}
		
		try {
			close();
		} catch (IOException e) {
			logger.error(e.getStackTrace(), e);
		}
	}

}
