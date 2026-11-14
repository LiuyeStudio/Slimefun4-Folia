package io.github.thebusybiscuit.slimefun4.utils;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;

/**
 * Folia-safe threading helpers.
 *
 * <p>
 * 这里的 "main thread" 指的是 <b>全局区域线程</b> ({@link Bukkit#isGlobalTickThread()})。
 * 注意: 全局区域线程并不拥有任何世界、方块或实体。任何需要访问方块/实体的工作都必须改用
 * {@link Slimefun#runAtRegion(org.bukkit.Location, Runnable)} 或
 * {@link Slimefun#runAtEntity(org.bukkit.entity.Entity, Runnable)}，否则会触发 Folia 的线程检查。
 * </p>
 */
public class ThreadUtils {

    public static Executor getMainThreadExecutor() {
        return command -> {
            if (Bukkit.isGlobalTickThread() || Bukkit.isPrimaryThread()) {
                command.run();
            } else {
                Slimefun.runSync(command);
            }
        };
    }

    /**
     * 与 {@link #getMainThreadExecutor()} 的区别: 即使已经在全局区域线程上, 也会延后一个 tick 执行,
     * 避免在同一个 tick 内递归触发回调。
     *
     * @return The {@link Executor}
     */
    public static Executor getMainDelayedExecutor() {
        return command -> {
            if (Slimefun.instance() == null) {
                return;
            }

            Bukkit.getGlobalRegionScheduler().runDelayed(Slimefun.instance(), task -> command.run(), 1L);
        };
    }

    /**
     * Executes a task on the global region thread.
     * If the current thread is already the global tick thread, the task runs immediately.
     *
     * @param runnable The task to execute
     */
    public static void executeSync(@Nonnull Runnable runnable) {
        if (Bukkit.isGlobalTickThread() || Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Slimefun.runSync(runnable);
        }
    }

    /**
     * Executes a task on the global region thread, always scheduling it for the next tick when the
     * caller is already on that thread.
     *
     * @param runnable The task to execute
     */
    public static void executeSyncSched(@Nonnull Runnable runnable) {
        CompletableFuture.runAsync(runnable, getMainDelayedExecutor());
    }

    /**
     * Creates a {@link FutureTask} from a {@link Callable}.
     *
     * @param <T>      The type of the result
     * @param callable The callable to wrap
     * @return A FutureTask wrapping the callable
     */
    public static <T> FutureTask<T> getFutureTask(@Nonnull Callable<T> callable) {
        return new FutureTask<>(callable);
    }

    /**
     * Creates a {@link FutureTask} from a {@link Runnable}.
     * If the input is already a FutureTask, it is cast and returned.
     * Otherwise, a new FutureTask is created with the Runnable and null result.
     *
     * @param runnable The runnable to wrap
     * @return A FutureTask wrapping the runnable
     */
    @SuppressWarnings("unchecked")
    public static FutureTask<Void> getFutureTask(@Nonnull Runnable runnable) {
        return runnable instanceof FutureTask<?> future
                ? (FutureTask<Void>) future
                : new FutureTask<>(runnable, (Void) null);
    }

    /**
     * Creates a {@link FutureTask} from a {@link Runnable} with a specific result value.
     *
     * @param <T>      The type of the result
     * @param runnable The runnable to execute
     * @param val      The result value to return
     * @return A FutureTask that executes the runnable and returns the specified value
     */
    public static <T> FutureTask<T> getFutureTask(@Nonnull Runnable runnable, T val) {
        return new FutureTask<>(runnable, val);
    }
}
