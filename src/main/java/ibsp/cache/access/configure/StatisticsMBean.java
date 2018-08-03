package ibsp.cache.access.configure;

import java.util.Map;

public interface StatisticsMBean {
    /**
     * 接入机当前客户端连接数
     * @return
     */
    public long getAccessClientConns();

    /**
     * 接入机当前REDIS连接数
     * @return
     */
    public long  getAccessRedisConns();

    /**
     * 接入机当前服务的分组
     * @return
     */
    public String[]  getAccessGroupList();

    /**
     * 接入机分组对应TPS
     * @return
     */
    public Map<String, Long> getAccessGroupTps();

    /**
     * 接入机请求时段内平均tps
     * @return
     */
    public long getAccessRequestTps();

    /**
     * 接入机时段内请求处理最大耗时
     * @return
     */
    public double  getAccessProcessMaxTime();

    /**
     * 接入机时段内请求处理平均耗时
     * @return
     */
    public double  getAccessProcessAvTime();

    /**
     * 接入机异常请求次数
     * @return
     */
    public long getAccessRequestExcepts();

    /**
     * 分组某个时刻的tps
     * @return
     */
    public Map<String, Long> getAccessGroupOnetimeTps();

    /**
     * 停止接入机运行
     */
    void stop();

    /**
     * 重启接入机运行
     */
    void restart();

    /**
     * 执行接入机命令
     * @param command
     */
    String execute(String command);


}
