package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import city.norain.slimefun4.utils.SlimefunPoolExecutor;
import city.norain.slimefun4.utils.TaskTimer;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.IDataSourceAdapter;
import com.xzavier0722.mc.plugin.slimefun4.storage.callback.IAsyncReadCallback;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.task.DatabaseThreadFactory;
import com.xzavier0722.mc.plugin.slimefun4.storage.task.QueuedWriteTask;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ADataController} 是 Slimefun 数据库控制器的抽象类，
 * 提供了对数据源适配器的访问和数据操作的基本方法。
 * <br/>
 * 该类提供了对数据库的增删查改操作以及异步读写的支持。
 */
@Slf4j
public abstract class ADataController {
    /**
     * The maximum amount of time {@link #shutdown()} waits for pending writes before it gives up.
     * Without an upper bound a stuck write task would hang the whole server shutdown.
     */
    private static final long MAX_SHUTDOWN_WAIT_MILLIS = TimeUnit.MINUTES.toMillis(2);

    private final DataType dataType;
    private final Map<ScopeKey, QueuedWriteTask> scheduledWriteTasks;
    private final ScopedLock lock;

    private volatile IDataSourceAdapter<?> dataAdapter;
    /**
     * 数据库读取调度器
     */
    protected ExecutorService readExecutor;
    /**
     * 数据库写入调度器
     */
    protected ExecutorService writeExecutor;

    protected ExecutorService serialWriteExecutor;

    /**
     * 数据库回调调度器
     */
    @Getter
    protected ExecutorService callbackExecutor;
    /**
     * 标记当前控制器是否已被关闭
     */
    private volatile boolean destroyed = false;

    /**
     * The logger for this data controller.
     */
    protected final Logger logger;

    /**
     * Constructs a new ADataController.
     *
     * @param dataType The data type this controller manages
     */
    protected ADataController(DataType dataType) {
        this.dataType = dataType;
        scheduledWriteTasks = new ConcurrentHashMap<>();
        lock = new ScopedLock();
        logger = Logger.getLogger("SF-" + dataType.name() + "-Controller");
    }

    /**
     * 初始化 {@link ADataController}
     *
     * @param dataAdapter   The data source adapter
     * @param maxReadThread Maximum number of read threads
     * @param maxWriteThread Maximum number of write threads
     */
    @OverridingMethodsMustInvokeSuper
    public void init(IDataSourceAdapter<?> dataAdapter, int maxReadThread, int maxWriteThread) {
        this.dataAdapter = dataAdapter;
        dataAdapter.initStorage(dataType);
        dataAdapter.patch();
        readExecutor = new SlimefunPoolExecutor(
                "SF-" + dataType.name() + "-Read-Executor",
                maxReadThread,
                maxReadThread,
                10,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new DatabaseThreadFactory("SF-" + dataType.name() + "-Read-Thread #"));

        writeExecutor = new SlimefunPoolExecutor(
                "SF-" + dataType.name() + "-Write-Executor",
                Math.max(maxWriteThread - 1, 1),
                Math.max(maxWriteThread - 1, 1),
                10,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new DatabaseThreadFactory("SF-" + dataType.name() + "-Write-Thread #"));

        if (maxWriteThread > 1) {
            serialWriteExecutor = new SlimefunPoolExecutor(
                    "SF-" + dataType.name() + "-SerialWrite-Executor",
                    1,
                    1,
                    10,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    new DatabaseThreadFactory("SF-" + dataType.name() + "-SerialWrite-Thread #"));
        }

        callbackExecutor = new SlimefunPoolExecutor(
                "SF-" + dataType.name() + "-Callback-Executor",
                1,
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                10,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new DatabaseThreadFactory("SF-" + dataType.name() + "-Callback-Thread #"));
    }

    /**
     * 正常关闭 {@link ADataController}
     */
    @OverridingMethodsMustInvokeSuper
    public void shutdown() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        readExecutor.shutdownNow();
        callbackExecutor.shutdownNow();

        try {
            /*
             * 先关闭写入调度器 (shutdown 只拒绝新任务, 已排队的任务仍会执行完毕),
             * 否则关闭期间仍然可以提交新任务, 循环可能永远等不到 pendingTask 归零。
             */
            writeExecutor.shutdown();

            // The serial write pool is a second write queue; it has to be drained as well, otherwise
            // its threads (and queued writes) survive the shutdown.
            if (serialWriteExecutor != null) {
                serialWriteExecutor.shutdown();
            }

            float totalTask = scheduledWriteTasks.size();
            var pendingTask = scheduledWriteTasks.size();
            var timer = new TaskTimer();
            long waitStart = System.currentTimeMillis();

            while (pendingTask > 0) {
                if (totalTask > 0) {
                    var doneTaskPercent = String.format("%.1f", (totalTask - pendingTask) / totalTask * 100);
                    logger.log(
                            Level.INFO, "数据保存中，请稍候... 剩余 {0} 个任务 ({1}%)", new Object[] {pendingTask, doneTaskPercent});
                } else {
                    logger.log(Level.INFO, "数据保存中，请稍候... 剩余 {0} 个任务", pendingTask);
                }

                TimeUnit.SECONDS.sleep(1);
                var currentTask = scheduledWriteTasks.size();

                if (pendingTask == currentTask) {
                    if (timer.peek() / 1000 > 10) {
                        Slimefun.logger().log(Level.WARNING, "检测到耗时保存任务, 请将下面的线程堆栈 完整 发送给开发者以便定位问题: ");
                        Slimefun.logger()
                                .log(Level.WARNING, Slimefun.getProfiler().snapshotThreads());
                    }
                } else {
                    timer.reset();
                }

                pendingTask = scheduledWriteTasks.size();

                if (System.currentTimeMillis() - waitStart > MAX_SHUTDOWN_WAIT_MILLIS) {
                    logger.log(Level.SEVERE, "等待数据保存超时 ({0} 个任务仍未完成), 为避免服务器卡死将强制关闭。", pendingTask);
                    break;
                }
            }

            logger.info("数据保存完成.");
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Exception thrown while saving data: ", e);
            Thread.currentThread().interrupt();
        }
        writeExecutor.shutdownNow();

        if (serialWriteExecutor != null) {
            serialWriteExecutor.shutdownNow();
        }

        dataAdapter = null;
    }

    protected void scheduleDeleteTask(ScopeKey scopeKey, RecordKey key, boolean forceScopeKey) {
        scheduleWriteTask(
                scopeKey,
                key,
                () -> {
                    dataAdapter.deleteData(key);
                },
                forceScopeKey);
    }

    protected void scheduleWriteTask(ScopeKey scopeKey, RecordKey key, RecordSet data, boolean forceScopeKey) {
        scheduleWriteTask(scopeKey, key, () -> dataAdapter.setData(key, data), forceScopeKey);
    }

    protected void scheduleWriteTask(ScopeKey scopeKey, RecordKey key, Runnable task, boolean forceScopeKey) {
        lock.lock(scopeKey);

        // log.info("schedule write scope [{}], key [{}]", scopeKey, key);

        try {
            var scopeToUse = forceScopeKey ? scopeKey : key;
            var queuedTask = scheduledWriteTasks.get(scopeKey);
            if (queuedTask == null && scopeKey != scopeToUse) {
                queuedTask = scheduledWriteTasks.get(scopeToUse);
            }

            if (queuedTask != null && queuedTask.queue(key, task)) {
                return;
            }

            queuedTask = new QueuedWriteTask() {
                @Override
                protected void onSuccess() {
                    // Only unregister this exact task: a newer write for the same scope may have
                    // replaced it while this one was finishing, and removing by key alone would
                    // orphan that replacement (two writers for the same rows).
                    scheduledWriteTasks.remove(scopeToUse, this);
                }

                @Override
                protected void onError(Throwable e) {
                    Slimefun.logger()
                            .log(
                                    Level.SEVERE,
                                    "[" + Thread.currentThread().getName()
                                            + "] Exception thrown while executing write task: ",
                                    e);
                }
            };
            queuedTask.queue(key, task);
            scheduledWriteTasks.put(scopeToUse, queuedTask);

            if (serialWriteExecutor != null && key.getScope().isSerial()) {
                serialWriteExecutor.submit(queuedTask);
            } else {
                writeExecutor.submit(queuedTask);
            }
        } finally {
            lock.unlock(scopeKey);
        }
    }

    protected void checkDestroy() {
        if (destroyed) {
            throw new IllegalStateException("Controller cannot be accessed after destroyed.");
        }
    }

    protected <T> void invokeCallback(IAsyncReadCallback<T> callback, T result) {
        if (callback == null) {
            return;
        }

        Runnable cb;
        if (result == null) {
            cb = callback::onResultNotFound;
        } else {
            cb = () -> callback.onResult(result);
        }

        if (callback.runOnMainThread()) {
            // Folia: "主线程" 并不拥有任何世界/方块/实体, 直接投递到全局区域线程会让
            // 回调里的方块操作全部越线程执行。这里按数据所属位置投递到对应区域线程。
            dispatchToOwnerRegion(result, cb);
        } else {
            callbackExecutor.submit(cb);
        }
    }

    private static void dispatchToOwnerRegion(Object result, Runnable runnable) {
        org.bukkit.Location location = null;

        if (result instanceof SlimefunBlockData blockData) {
            location = blockData.getLocation();
        } else if (result instanceof SlimefunUniversalBlockData universalBlockData) {
            var lastPresent = universalBlockData.getLastPresent();
            if (lastPresent != null) {
                location = lastPresent.toLocation();
            }
        }

        if (location != null && location.getWorld() != null) {
            Slimefun.runAtRegion(location, runnable);
        } else {
            Slimefun.runSync(runnable);
        }
    }

    protected void scheduleReadTask(Runnable run) {
        checkDestroy();
        readExecutor.submit(run);
    }

    protected void scheduleWriteTask(Runnable run) {
        checkDestroy();
        writeExecutor.submit(run);
    }

    protected List<RecordSet> getData(RecordKey key) {
        return getData(key, false);
    }

    protected List<RecordSet> getData(RecordKey key, boolean distinct) {
        return dataAdapter.getData(key, distinct);
    }

    protected void setData(RecordKey key, RecordSet data) {
        dataAdapter.setData(key, data);
    }

    protected void deleteData(RecordKey key) {
        dataAdapter.deleteData(key);
    }

    protected void abortScopeTask(ScopeKey key) {
        var task = scheduledWriteTasks.remove(key);
        if (task != null) {
            task.abort();
        }
    }

    public final DataType getDataType() {
        return dataType;
    }
}
