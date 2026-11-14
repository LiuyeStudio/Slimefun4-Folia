package city.norain.slimefun4.utils;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Folia-safe inventory utilities.
 * <p>
 * 所有操作都被路由到拥有该 {@link Entity} 的 {@code EntityScheduler}，
 * 从而保证无论调用点处于哪个线程 / 区域，都会在正确的区域线程上执行。
 * <p>
 * 在 Folia 上 {@code Bukkit.isPrimaryThread()} 只有在拥有该位置的区域线程上
 * 才返回 {@code true}，因此不能作为“是否能打开界面”的判断依据。
 */
@UtilityClass
public class InventoryUtil {
    public void openInventory(Player p, Inventory inventory) {
        if (p == null || inventory == null) {
            return;
        }

        if (Bukkit.isOwnedByCurrentRegion(p)) {
            p.openInventory(inventory);
        } else {
            p.getScheduler().run(Slimefun.instance(), task -> p.openInventory(inventory), null);
        }
    }

    /**
     * Close inventory for all viewers.
     *
     * @param inventory {@link Inventory}
     */
    public void closeInventory(Inventory inventory) {
        closeInventory(inventory, null);
    }

    /**
     * Close the inventory for all viewers, then run the callback once every viewer has actually
     * been closed.
     * <p>
     * Folia: 关闭界面是实体操作, 必须逐个投递到查看者所在区域的线程。
     * 以前的实现在投递完关闭任务后立刻在全局线程上执行回调, 回调必然早于关闭完成,
     * 调用方随后清空/丢弃容器内容就可能造成物品复制或丢失。
     *
     * @param inventory {@link Inventory}
     * @param callback  callback to run after all viewers have been closed (nullable)
     */
    public void closeInventory(Inventory inventory, Runnable callback) {
        if (inventory == null) {
            if (callback != null) {
                callback.run();
            }
            return;
        }

        List<HumanEntity> viewers = new LinkedList<>(inventory.getViewers());

        if (viewers.isEmpty()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }

        AtomicInteger remaining = new AtomicInteger(viewers.size());
        Runnable onClosed = () -> {
            if (remaining.decrementAndGet() == 0 && callback != null) {
                callback.run();
            }
        };

        for (HumanEntity viewer : viewers) {
            if (viewer instanceof Entity entity) {
                if (Bukkit.isOwnedByCurrentRegion(entity)) {
                    closeSilently(viewer);
                    onClosed.run();
                } else {
                    // 第三个参数是“实体已被移除导致任务被放弃”时的回调, 保证计数一定会递减
                    entity.getScheduler()
                            .run(
                                    Slimefun.instance(),
                                    task -> {
                                        closeSilently(viewer);
                                        onClosed.run();
                                    },
                                    onClosed);
                }
            } else {
                // 非实体查看者（理论上不会出现）— 退回全局调度器
                Slimefun.runSync(() -> {
                    closeSilently(viewer);
                    onClosed.run();
                });
            }
        }
    }

    private void closeSilently(HumanEntity viewer) {
        try {
            viewer.closeInventory();
        } catch (Exception ignored) {
            // 玩家可能已经离线, 不再关心关闭结果
        }
    }
}
