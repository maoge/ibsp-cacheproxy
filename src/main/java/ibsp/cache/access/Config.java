package ibsp.cache.access;

import ibsp.cache.access.util.PropertiesUtils;

public class Config {

	private static Config config;

	public static void setConfig(Config config){
		Config.config = config;
	}

	public static Config getConfig(){
		return Config.config;
	}
	
	private boolean auto_refresh = true;
	private int auto_refresh_interval = 10;      // 10 s
	private int max_clients = 10000;             // 单台接入机最大允许连接数
	private int redis_input_reador_buffsize = 32 * 1024;  // NioRedisInputReador bTmp bytebuff initial size
	private int max_waiting_acceptor = 0;
	private int select_wait_time = 10000;        // 默认10s
	
	private int monitor_upload_interval = 60*1000; // 监控数据上报间隔
	private long pool_borrow_timeout = 1;          // ByteArrayPool borrow timeout值
	
	// 后面优化点，根据客户端报文的最大值动态调整这个缓冲值
	private int bytebuffer_level0 = 128;
	private int bytebuffer_level1 = 2176;
	private int bytebuffer_level2 = 4224;
	private int bytebuffer_level3 = 10368;
	private int bytebuffer_level4 = 20608;
	private int bytebuffer_level5 = 32768;
	private int bytebuffer_level6 = 1048576;
	private int buffer_pool_level0_size = 200000;    // 一级  ByteBuffer 数量
	private int buffer_pool_level1_size = 200000;    // 二级  ByteBuffer 数量
	private int buffer_pool_level2_size = 40000;     // 三级  ByteBuffer 数量
	private int buffer_pool_level3_size = 16000;     // 四级  ByteBuffer 数量
	private int buffer_pool_level4_size = 10000;     // 五级  ByteBuffer 数量
	private int buffer_pool_level5_size = 8000;      // 六级  ByteBuffer 数量
	private int buffer_pool_level6_size = 110;       // 七级  ByteBuffer 数量    (应对收发大包的情况)
	
	private int bytearray_level0=128;
	private int bytearray_level1=2048;
	private int bytearray_level2=32768;
	private int byte_arr_pool_level0_size = 1000;    // 一级  ByteArray 数量
	private int byte_arr_pool_level1_size = 100;     // 二级  ByteArray 数量
	private int byte_arr_pool_level2_size = 10;      // 三级  ByteArray 数量
	
	private int redis_request_pool_size = 80000;
	
	private int thread_pool_coresize = 60;
	private int thread_pool_maxsize = 100;
	private int thread_pool_keepalivetime = 3;
	private int thread_pool_workqueue_len = 10000; // 线程池队列初始长度
	
	private int client_max_read_count = 100;       // 客户端连续读最大次数
	private int client_redis_request_queue_maxlen = 5000;  // DestRedisProcessor clientRedisRequestQueue 最大长度
	private int backq_max_len = 1000;              // NioSession backQ max len
	private int routerq_max_len = 50000;           // route dispatch taskQueue max len
	private long redis_check_interval = 10000;     // redis 连接测试间隔
	private int default_head_len = 128;            // 扩展头默认总长度
	private int default_head_attr_len = 32;        // 扩展头字段默认长度
	private int fixed_conn_per_redis = 1;          // 每redis实例固定连接数
	private int router_cnt = 1;                    // router porceesor 数量
	private int respond_writer_cnt = 2;            // 回写客户端并行数
	private int client_processor_cnt = 12;         // ClientIoProcessor 数目
	private int so_revbuf_size = 32 * 1024;        // redis对接部分revbuf
	
	public Config() {
		this.auto_refresh = Boolean.valueOf( PropertiesUtils.getInstance("init").get("auto_refresh") );
		this.auto_refresh_interval = Integer.valueOf( PropertiesUtils.getInstance("init").get("auto_refresh_interval") );
		this.select_wait_time = Integer.valueOf( PropertiesUtils.getInstance("init").get("select_wait_time") );
		this.max_waiting_acceptor = Integer.valueOf( PropertiesUtils.getInstance("init").get("max_waiting_acceptor") );
		this.max_clients = Integer.valueOf( PropertiesUtils.getInstance("init").get("max_clients") );
		
		this.pool_borrow_timeout = Long.valueOf( PropertiesUtils.getInstance("init").get("pool_borrow_timeout") );
		
		this.bytebuffer_level0 = Integer.valueOf( PropertiesUtils.getInstance("init").get("bytebuffer.level0") );
		this.bytebuffer_level1 = Integer.valueOf( PropertiesUtils.getInstance("init").get("bytebuffer.level1") );
		this.bytebuffer_level2 = Integer.valueOf( PropertiesUtils.getInstance("init").get("bytebuffer.level2") );
		this.bytebuffer_level3 = Integer.valueOf( PropertiesUtils.getInstance("init").get("bytebuffer.level3") );
		this.bytebuffer_level4 = Integer.valueOf( PropertiesUtils.getInstance("init").get("bytebuffer.level4") );
		this.bytebuffer_level5 = Integer.valueOf( PropertiesUtils.getInstance("init").get("bytebuffer.level5") );
		this.bytebuffer_level6 = Integer.valueOf( PropertiesUtils.getInstance("init").get("bytebuffer.level6") );
		this.buffer_pool_level0_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("buffer_pool_level0_size") );
		this.buffer_pool_level1_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("buffer_pool_level1_size") );
		this.buffer_pool_level2_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("buffer_pool_level2_size") );
		this.buffer_pool_level3_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("buffer_pool_level3_size") );
		this.buffer_pool_level4_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("buffer_pool_level4_size") );
		this.buffer_pool_level5_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("buffer_pool_level5_size") );
		this.buffer_pool_level6_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("buffer_pool_level6_size") );
		
		this.bytearray_level0 = Integer.valueOf( PropertiesUtils.getInstance("init").get("bytearray.level0") );
		this.bytearray_level1 = Integer.valueOf( PropertiesUtils.getInstance("init").get("bytearray.level1") );
		this.bytearray_level2 = Integer.valueOf( PropertiesUtils.getInstance("init").get("bytearray.level2") );
		this.byte_arr_pool_level0_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("byte_arr_pool_level0_size") );
		this.byte_arr_pool_level1_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("byte_arr_pool_level1_size") );
		this.byte_arr_pool_level2_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("byte_arr_pool_level2_size") );
		
		this.thread_pool_coresize = Integer.valueOf( PropertiesUtils.getInstance("init").get("thread_pool_coresize") );
		this.thread_pool_maxsize  = Integer.valueOf( PropertiesUtils.getInstance("init").get("thread_pool_maxsize") );
		this.thread_pool_keepalivetime = Integer.valueOf( PropertiesUtils.getInstance("init").get("thread_pool_keepalivetime") );
		this.thread_pool_workqueue_len = Integer.valueOf( PropertiesUtils.getInstance("init").get("thread_pool_workqueue_len") );
		
		this.client_max_read_count = Integer.valueOf( PropertiesUtils.getInstance("init").get("client_max_read_count") );
		this.client_redis_request_queue_maxlen = Integer.valueOf( PropertiesUtils.getInstance("init").get("client_redis_request_queue_maxlen") );
		this.monitor_upload_interval = Integer.valueOf( PropertiesUtils.getInstance("init").get("monitor_upload_interval") );
		this.backq_max_len = Integer.valueOf( PropertiesUtils.getInstance("init").get("backq_max_len") );
		this.routerq_max_len = Integer.valueOf( PropertiesUtils.getInstance("init").get("routerq_max_len") );
		this.redis_request_pool_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("redis_request_pool_size") );
		this.default_head_len = Integer.valueOf( PropertiesUtils.getInstance("init").get("default_head_len") );
		this.default_head_attr_len = Integer.valueOf( PropertiesUtils.getInstance("init").get("default_head_attr_len") );
		this.redis_input_reador_buffsize = Integer.valueOf( PropertiesUtils.getInstance("init").get("redis_input_reador_buffsize") );
		this.fixed_conn_per_redis = Integer.valueOf( PropertiesUtils.getInstance("init").get("fixed_conn_per_redis") );
		this.redis_check_interval = Long.valueOf( PropertiesUtils.getInstance("init").get("redis_check_interval") );
		
		this.respond_writer_cnt = Integer.valueOf( PropertiesUtils.getInstance("init").get("respond_writer_cnt") );
		this.client_processor_cnt = Integer.valueOf( PropertiesUtils.getInstance("init").get("client_processor_cnt") );
		this.so_revbuf_size = Integer.valueOf( PropertiesUtils.getInstance("init").get("so_revbuf_size") );
		this.router_cnt = Integer.valueOf( PropertiesUtils.getInstance("init").get("router_cnt") );
	}
	
	public boolean isAuto_refresh() {
		return auto_refresh;
	}
	
	public int getAuto_refresh_interval() {
		return auto_refresh_interval;
	}
	
	public int getSelect_wait_time() {
		return select_wait_time;
	}

	public int getMax_waiting_acceptor() {
		return max_waiting_acceptor;
	}

	public int getMax_clients() {
		return max_clients;
	}
	
	public int getBuffer_pool_level_size(int level) {
		if (level == 0)
			return buffer_pool_level0_size;
		else if (level == 1)
			return buffer_pool_level1_size;
		else if (level ==2)
			return buffer_pool_level2_size;
		else if (level == 3)
			return buffer_pool_level3_size;
		else if (level == 4)
			return buffer_pool_level4_size;
		else if (level == 5)
			return buffer_pool_level5_size;
		else if (level == 6)
			return buffer_pool_level6_size;
		else
			return 0;
	}
	
	public int getByte_arr_pool_level_size(int level) {
		if (level == 0)
			return byte_arr_pool_level0_size;
		else if (level == 1)
			return byte_arr_pool_level1_size;
		else if (level ==2)
			return byte_arr_pool_level2_size;
		else
			return 0;
	}

	public int getThread_pool_coresize() {
		return thread_pool_coresize;
	}

	public int getThread_pool_maxsize() {
		return thread_pool_maxsize;
	}

	public int getThread_pool_keepalivetime() {
		return thread_pool_keepalivetime;
	}
	
	public int getThread_pool_workqueue_len() {
		return thread_pool_workqueue_len;
	}
	
	public int getClient_max_read_count() {
		return client_max_read_count;
	}
	
	public int getClient_redis_request_queue_maxlen() {
		return client_redis_request_queue_maxlen;
	}
	
	public int getMonitor_upload_interval() {
		return monitor_upload_interval;
	}
	
	public int getBackq_max_len() {
		return backq_max_len;
	}
	
	public int getRouterq_max_len() {
		return routerq_max_len;
	}

	public int getRedis_request_pool_size() {
		return redis_request_pool_size;
	}

	public int getDefault_head_len() {
		return default_head_len;
	}

	public int getDefault_head_attr_len() {
		return default_head_attr_len;
	}

	public int getRedis_input_reador_buffsize() {
		return redis_input_reador_buffsize;
	}

	public int getFixed_conn_per_redis() {
		return fixed_conn_per_redis;
	}

	public long getRedis_check_interval() {
		return redis_check_interval;
	}

	public int getRespond_writer_cnt() {
		return respond_writer_cnt;
	}

	public long getPool_borrow_timeout() {
		return pool_borrow_timeout;
	}

	public int getClient_processor_cnt() {
		return client_processor_cnt;
	}

	public int getSo_revbuf_size() {
		return so_revbuf_size;
	}

	public int getRouter_cnt() {
		return router_cnt;
	}

	public int getBytebuffer_level0() {
		return bytebuffer_level0;
	}

	public int getBytebuffer_level1() {
		return bytebuffer_level1;
	}

	public int getBytebuffer_level2() {
		return bytebuffer_level2;
	}

	public int getBytebuffer_level3() {
		return bytebuffer_level3;
	}
	
	public int getBytebuffer_level4() {
		return bytebuffer_level4;
	}
	
	public int getBytebuffer_level5() {
		return bytebuffer_level5;
	}
	
	public int getBytebuffer_level6() {
		return bytebuffer_level6;
	}

	public int getBytearray_level0() {
		return bytearray_level0;
	}

	public int getBytearray_level1() {
		return bytearray_level1;
	}

	public int getBytearray_level2() {
		return bytearray_level2;
	}

}
