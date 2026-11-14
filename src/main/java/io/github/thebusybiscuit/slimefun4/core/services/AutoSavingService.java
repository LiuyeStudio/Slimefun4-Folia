package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Iterator;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * This Service is responsible for automatically saving {@link Player} and {@link Block}
 * data.
 *
 * @author TheBusyBiscuit
 *
 */
public class AutoSavingService {

    private int interval;

    /**
     * This method starts the {@link AutoSavingService} with the given interval.
     *
     * @param plugin
     *            The current instance of Slimefun
     * @param interval
     *            The interval in which to run this task
     */
    public void start(@Nonnull Slimefun plugin, int interval) {
        this.interval = interval;

        // interval <= 0 表示关闭自动保存 (关服时仍会保存一次)
        if (interval <= 0) {
            Slimefun.logger().info("自动保存已关闭 (options.auto-save-delay-in-minutes <= 0)");
            return;
        }

        Slimefun.logger().info("自动保存已启用: 每 " + interval + " 分钟");

        /*
         * Folia: 玩家数据保存只涉及内存与磁盘 I/O, 不会访问世界/实体,
         * 因此直接放在异步调度器上执行, 避免把文件写入放到全局区域线程上阻塞所有区域。
         */
        // Folia: interval 的单位是分钟, AsyncScheduler 使用毫秒, 因此换算为 分钟 * 60 * 1000。
        long intervalMillis = interval * 60_000L;

        plugin.getServer()
                .getAsyncScheduler()
                .runAtFixedRate(
                        plugin,
                        task -> this.saveAllPlayers(),
                        2000L,
                        intervalMillis,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
        // Folia: 使用 AsyncScheduler 替代 runTaskTimerAsynchronously
        plugin.getServer()
                .getAsyncScheduler()
                .runAtFixedRate(
                        plugin,
                        task -> {
                            Slimefun.getDatabaseManager()
                                    .getBlockDataController()
                                    .saveAllBlockInventories();

                            Slimefun.getDatabaseManager()
                                    .getBlockDataController()
                                    .saveAllUniversalInventories();
                        },
                        2000L,
                        intervalMillis,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * This method saves every {@link PlayerProfile} in memory and removes profiles
     * that were marked for deletion.
     */
    /**
     * Folia: 此方法在 GlobalRegionScheduler 上运行。
     * 玩家数据的实际保存通过 ProfileDataController 的异步队列完成，
     * 不直接操作 Player 实体，因此是安全的。
     */
    private void saveAllPlayers() {
        Iterator<PlayerProfile> iterator = PlayerProfile.iterator();
        int players = 0;

        while (iterator.hasNext()) {
            PlayerProfile profile = iterator.next();

            if (profile.isDirty()) {
                players++;
                profile.save();
            }

            if (profile.isMarkedForDeletion()) {
                iterator.remove();
            }
        }

        // 自动保存必须静默: 只在 FINE 级别输出, 默认控制台不可见
        if (players > 0) {
            Slimefun.logger().log(Level.FINE, "成功保存了 {0} 个玩家的数据!", players);
        }
    }
}
