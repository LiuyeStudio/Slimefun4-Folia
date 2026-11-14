package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.BowShootHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.weapons.SlimefunBow;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * This {@link Listener} is responsible for tracking {@link Arrow Arrows} fired from a
 * {@link SlimefunBow}.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunBow
 *
 */
public class SlimefunBowListener implements Listener {

    // Folia: 射击/命中事件可能来自不同区域线程
    private final Map<UUID, SlimefunBow> projectiles = new java.util.concurrent.ConcurrentHashMap<>();

    public void register(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * This returns a {@link HashMap} holding the {@link UUID} of a {@link Arrow} and the
     * associated {@link SlimefunBow} with which this {@link Arrow} was fired from.
     *
     * @return A {@link HashMap} with all actively tracked {@link Arrow Arrows}
     */
    @Nonnull
    public Map<UUID, SlimefunBow> getProjectileData() {
        return projectiles;
    }

    @EventHandler
    public void onBowUse(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player && e.getProjectile() instanceof Arrow) {
            SlimefunItem bow = SlimefunItem.getByItem(e.getBow());

            if (bow instanceof SlimefunBow slimefunBow) {
                projectiles.put(e.getProjectile().getUniqueId(), slimefunBow);
            }
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent e) {
        Arrow arrow = e.getEntity() instanceof Arrow a ? a : null;

        if (arrow == null) {
            return;
        }

        // Folia: 读取/等待箭的状态需要它所在区域的线程, 这里投递到箭自己的实体调度器
        Arrow finalArrow = arrow;
        Slimefun.runAtEntity(
                arrow,
                () -> {
                    if (finalArrow.isValid()) {
                        projectiles.remove(finalArrow.getUniqueId());
                    }
                },
                4L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArrowSuccessfulHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Arrow
                && e.getEntity() instanceof LivingEntity
                && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            SlimefunBow bow = projectiles.remove(e.getDamager().getUniqueId());

            if (!e.isCancelled() && bow != null) {
                bow.callItemHandler(BowShootHandler.class, handler -> handler.onHit(e, (LivingEntity) e.getEntity()));
            }
        }
    }
}
