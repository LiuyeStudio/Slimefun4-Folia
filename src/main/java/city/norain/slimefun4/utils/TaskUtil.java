package city.norain.slimefun4.utils;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;

@UtilityClass
public class TaskUtil {
    @SneakyThrows
    public void runSyncMethod(Runnable runnable) {
        // Folia: 只有全局区域线程才等同于过去的“主线程”
        if (Bukkit.isGlobalTickThread() || Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Slimefun.runSync(runnable);
        }
    }

    /**
     * Folia-safe 同步调用。
     * <p>
     * 旧的 {@code Bukkit.getScheduler().callSyncMethod(...)} API 在 Folia 上
     * 完全不可用；改为把任务投递到 GlobalRegionScheduler 上并阻塞等待其完成。
     * <p>
     * 注意：本方法会阻塞调用线程最多 1 秒，且目标任务运行在全局区域线程上，
     * <b>因此绝对不能在区域线程上用它来访问某个方块的方块/容器数据</b>，
     * 否则既会触发线程检查，也可能与全局线程互相等待而死锁。
     * 访问方块请改用 {@code Slimefun.runAtRegion(...)}。
     */
    @SneakyThrows
    public <T> T runSyncMethod(Callable<T> callable) {
        if (Bukkit.isGlobalTickThread() || Bukkit.isPrimaryThread()) {
            return callable.call();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(Slimefun.instance(), task -> {
            try {
                future.complete(callable.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            Slimefun.logger().log(Level.WARNING, "Timeout when executing sync method", e);
            throw new IllegalStateException("Timeout when executing sync method", e);
        }
    }
}
