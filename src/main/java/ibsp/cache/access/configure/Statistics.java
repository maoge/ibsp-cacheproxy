package ibsp.cache.access.configure;

import ibsp.cache.access.util.Metrics;

import java.util.Map;

public class Statistics implements StatisticsMBean{
    private volatile static Statistics instance;

    private Statistics(){}

    public static Statistics get() {
        if(instance == null) {
            synchronized (Statistics.class) {
                if(instance == null){
                    instance = new Statistics();
                }
            }
        }
        return instance;
    }

    @Override
    public long getAccessClientConns() {
        return Metrics.access_client_conns;
    }

    @Override
    public long getAccessRedisConns() {
        return Metrics.access_redis_conns;
    }

    @Override
    public String[] getAccessGroupList() {
        return Metrics.access_group_list;
    }

    @Override
    public Map<String, Long> getAccessGroupTps() {
        return Metrics.access_group_tps;
    }

    @Override
    public long getAccessRequestTps() {
        return Metrics.access_request_tps;
    }

    @Override
    public double getAccessProcessMaxTime() {
        return Metrics.access_process_max_time;
    }

    @Override
    public double getAccessProcessAvTime() {
        return Metrics.access_process_av_time;
    }

    @Override
    public long getAccessRequestExcepts() {
        return Metrics.access_request_excepts;
    }

    @Override
    public Map<String, Long> getAccessGroupOnetimeTps() {
        return Metrics.access_group_onetime_tps;
    }

    @Override
    public void stop() {

    }

    @Override
    public void restart() {

    }

    @Override
    public String execute(String command) {
        System.out.println("hello world " + command);
        return "hello world " + command;
    }


}
