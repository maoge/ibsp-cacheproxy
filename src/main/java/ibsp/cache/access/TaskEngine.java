package ibsp.cache.access;

import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskEngine {
	private static final Logger Log = LoggerFactory.getLogger(TaskEngine.class);
    private static TaskEngine instance = null;
    private ExecutorService executor;
    private Timer timer;
    private Map<TimerTask, TimerTaskWrapper> wrappedTasks = new ConcurrentHashMap<TimerTask, TimerTaskWrapper>();
    private static final ReentrantLock monitor = new ReentrantLock();
    
    public static TaskEngine getInstance() {
    	monitor.lock();
    	try {
            if(instance==null) instance = new TaskEngine();
    	}finally {
    		monitor.unlock();
    	}
    	return instance;
    }

    private TaskEngine() {
        timer = new Timer("TaskEngine-timer", true);
        executor = Executors.newFixedThreadPool(1);
        Log.info("TaskEngine new ......");
        /**
        executor = Executors.newCachedThreadPool(new ThreadFactory() {
            final AtomicInteger threadNumber = new AtomicInteger(1);
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(Thread.currentThread().getThreadGroup(), runnable, "TaskEngine-pool-" + threadNumber.getAndIncrement(), 0);
                thread.setDaemon(true);
                if (thread.getPriority() != Thread.NORM_PRIORITY) {
                    thread.setPriority(Thread.NORM_PRIORITY);
                }
                return thread;
            }
        });
        **/        
    }

    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    public Future<?> submit(Callable<?> task) {
        return executor.submit(task);
    }

    public void schedule(TimerTask task, long delay) {
        timer.schedule(new TimerTaskWrapper(task), delay);
    }

    public void schedule(TimerTask task, Date time) {
        timer.schedule(new TimerTaskWrapper(task), time);
    }

    public void schedule(TimerTask task, long delay, long period) {
        TimerTaskWrapper taskWrapper = new TimerTaskWrapper(task);
        wrappedTasks.put(task, taskWrapper);
        timer.schedule(taskWrapper, delay, period);
    }

    public void schedule(TimerTask task, Date firstTime, long period) {
        TimerTaskWrapper taskWrapper = new TimerTaskWrapper(task);
        wrappedTasks.put(task, taskWrapper);
        timer.schedule(taskWrapper, firstTime, period);
    }

    public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
        TimerTaskWrapper taskWrapper = new TimerTaskWrapper(task);
        wrappedTasks.put(task, taskWrapper);
        timer.scheduleAtFixedRate(taskWrapper, delay, period);
    }

    public void scheduleAtFixedRate(TimerTask task, Date firstTime, long period) {
        TimerTaskWrapper taskWrapper = new TimerTaskWrapper(task);
        wrappedTasks.put(task, taskWrapper);
        timer.scheduleAtFixedRate(taskWrapper, firstTime, period);
    }

    public void cancelScheduledTask(TimerTask task) {
        TaskEngine.TimerTaskWrapper taskWrapper = wrappedTasks.remove(task);
        if (taskWrapper != null) {
            taskWrapper.cancel();
        }
    }

    public void shutdown() {
        if (executor != null) {
        	executor.shutdownNow();
        	executor = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        monitor.lock();
        try {
            if(instance!=null)	instance = null;
        }finally {
        	monitor.unlock();
        }
        Log.info("TaskEngine shutdown ......");
    }

    private class TimerTaskWrapper extends TimerTask {
        private TimerTask task;
        public TimerTaskWrapper(TimerTask task) {
            this.task = task;
        }
		public void run() {
			executor.submit(task);
        }
    }
}