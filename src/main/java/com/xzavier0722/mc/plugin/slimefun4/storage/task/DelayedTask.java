package com.xzavier0722.mc.plugin.slimefun4.storage.task;

import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelayedTask {
    private final Runnable task;
    private long runAfter = 0;
    private boolean executed = false;

    public DelayedTask(long delay, TimeUnit unit, Runnable task) {
        this.task = task;
        setRunAfter(delay, unit);
    }

    public synchronized void setRunAfter(long delay, TimeUnit unit) {
        runAfter = System.currentTimeMillis() + unit.toMillis(delay);
    }

    public synchronized boolean tryRun() {
        if (executed || System.currentTimeMillis() < runAfter) {
            return false;
        }

        // 先标记再执行: 同一个任务被强制保存与轮询线程同时取走时只能执行一次
        executed = true;

        try {
            task.run();
            return true;
        } catch (Exception e) {
            log.warn("An error occurred while running delayed task", e);
            return false;
        }
    }

    public synchronized boolean isExecuted() {
        return executed;
    }

    /**
     * This runs the delayed task immediately, regardless of its delay.
     * <p>
     * Folia/Slimefun: the delayed task used to be executed without checking the executed flag,
     * while the saving looper iterates over a snapshot of the same map. A task that was force-run
     * by {@code BlockDataController#move(...)} could therefore be executed a second time by the
     * looper, re-saving the old location after the block data had already been moved.
     */
    public synchronized boolean runUnsafely() {
        if (executed) {
            return false;
        }

        executed = true;

        try {
            task.run();
            return true;
        } catch (Exception e) {
            log.warn("An error occurred while running delayed task", e);
            return false;
        }
    }
}
