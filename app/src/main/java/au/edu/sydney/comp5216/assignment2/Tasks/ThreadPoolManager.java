package au.edu.sydney.comp5216.assignment2.Tasks;

import android.os.Process;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ThreadPoolManager.java
 * <p>
 * Executor wrapper, maintains a thread pool to run Callable
 *
 * @author Huashuai Cai
 * @version 1.0
 * @since 2020-10-10
 */
public class ThreadPoolManager {

    // default settings
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    private static final int KEEP_ALIVE_TIME = 1;
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;

    /**
     * Executor
     */
    private final ExecutorService mExecutorService;

    // Jobs
    private final BlockingQueue<Runnable> mTaskQueue;
    private List<Future> mRunningTaskList;

    private static ThreadPoolManager mInstance;


    // The class is used as a singleton
    static {
        mInstance = new ThreadPoolManager();
    }

    // Made constructor private to avoid the class being initiated from outside
    private ThreadPoolManager() {
        // initialize a queue for the thread pool. New tasks will be added to this queue
        mTaskQueue = new LinkedBlockingQueue<Runnable>();
        mRunningTaskList = new ArrayList<>();
        mExecutorService = new ThreadPoolExecutor(NUMBER_OF_CORES, NUMBER_OF_CORES * 2,
                KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, mTaskQueue, new MyThreadFactory());
    }

    public static ThreadPoolManager getInstance() {
        return mInstance;
    }

    // Add a callable to the queue, which will be executed by the next available thread in the pool
    public void addCallable(Callable callable) {
        Future future = mExecutorService.submit(callable);
        mRunningTaskList.add(future);
    }

    /**
     * Cancel tasks in the queue and stop all running threads
     */
    public void cancelAllTasks() {
        synchronized (this) {
            mTaskQueue.clear();
            for (Future task : mRunningTaskList) {
                if (!task.isDone()) {
                    task.cancel(true);
                }
            }
            mRunningTaskList.clear();
        }
    }

    /**
     * MyThreadFactory.java
     * <p>
     * a ThreadFactory implementation
     *
     * @author Huashuai Cai
     * @version 1.0
     * @since 2020-10-10
     */
    private static class MyThreadFactory implements ThreadFactory {
        private static int sTag = 1;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("MyThread" + sTag);
            thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);

            // A exception handler is created to log the exception from threads
            thread.setUncaughtExceptionHandler((exceptionThread, ex) -> Log.e("LOG",
                    exceptionThread.getName() + " encountered an error: " + ex.getMessage()));
            return thread;
        }
    }
}