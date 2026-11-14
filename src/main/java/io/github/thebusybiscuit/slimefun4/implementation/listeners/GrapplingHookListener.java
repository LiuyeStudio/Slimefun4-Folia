package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.GrapplingHook;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * This {@link Listener} is responsible for the mechanics behind the {@link GrapplingHook}.
 *
 * @author TheBusyBiscuit
 * @author Linox
 * @author BlackBeltPanda
 *
 * @see GrapplingHook
 *
 */
public class GrapplingHookListener implements Listener {

    private GrapplingHook grapplingHook;

    // Folia: 这两个集合会被不同区域的线程访问, 必须使用线程安全的实现
    private final Map<UUID, GrapplingHookEntity> activeHooks = new ConcurrentHashMap<>();
    private final Set<UUID> invulnerability = ConcurrentHashMap.newKeySet();

    public void register(@Nonnull Slimefun plugin, @Nonnull GrapplingHook grapplingHook) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        this.grapplingHook = grapplingHook;
    }

    @EventHandler
    public void onArrowHitEntity(EntityDamageByEntityEvent e) {
        if (grapplingHook.isDisabled()) {
            return;
        }

        if (e.getDamager() instanceof Arrow arrow) {
            handleGrapplingHook(arrow);
        }
    }

    @EventHandler
    public void onArrowHitSurface(ProjectileHitEvent e) {
        if (grapplingHook.isDisabled()) {
            return;
        }

        if (!(e.getEntity() instanceof Arrow arrow)) {
            return;
        }

        var plugin = Slimefun.instance();
        if (plugin == null) {
            return;
        }

        // Folia: 弹射物命中事件发生在拥有该箭的区域线程上, 延迟处理也必须调度回该区域,
        // 否则读取箭/射手会触发 "Accessing entity state off owning region's thread"。
        var target = arrow.getLocation();
        Bukkit.getRegionScheduler().runDelayed(plugin, target, task -> handleGrapplingHook(arrow), 2L);
    }

    @EventHandler
    public void onArrowHitHanging(HangingBreakByEntityEvent e) {
        if (grapplingHook.isDisabled()) {
            return;
        }

        // This is called when the arrow shoots off a painting or an item frame
        if (e.getRemover() instanceof Player player) {
            GrapplingHookEntity hook = activeHooks.get(player.getUniqueId());
            if (hook == null) return;
            handleGrapplingHook(hook.getArrow());
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        if (grapplingHook.isDisabled()) {
            return;
        }

        UUID uuid = e.getPlayer().getUniqueId();
        activeHooks.remove(uuid);
        invulnerability.remove(uuid);
    }

    @EventHandler
    public void onLeave(PlayerKickEvent e) {
        if (grapplingHook.isDisabled()) {
            return;
        }

        UUID uuid = e.getPlayer().getUniqueId();
        activeHooks.remove(uuid);
        invulnerability.remove(uuid);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (grapplingHook.isDisabled()) {
            return;
        }

        if (e.getEntity() instanceof Player
                && e.getCause() == DamageCause.FALL
                && invulnerability.remove(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPortalEnter(EntityPortalEnterEvent e) {
        if (grapplingHook.isDisabled()) {
            return;
        }

        if (e.getEntity() instanceof Arrow arrow) {
            handleGrapplingHook(arrow);
        }
    }

    // Fixing Issue #2351
    @EventHandler
    public void onLeash(PlayerLeashEntityEvent e) {
        if (grapplingHook.isDisabled()) {
            return;
        }

        Player p = e.getPlayer();

        ItemStack item = p.getInventory().getItemInMainHand();
        SlimefunItem slimeItem = SlimefunItem.getByItem(item);

        if (slimeItem instanceof GrapplingHook) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeashBreak(EntityUnleashEvent e) {
        if (grapplingHook.isDisabled()) {
            return;
        }

        for (GrapplingHookEntity hook : activeHooks.values()) {
            if (hook.getLeashTarget() == e.getEntity()) {
                e.setDropLeash(false);
                return;
            }
        }
    }

    private void handleGrapplingHook(@Nullable Arrow arrow) {
        if (arrow == null) {
            return;
        }

        var plugin = Slimefun.instance();
        if (plugin == null) {
            return;
        }

        // Folia: 读取箭的状态(isValid/getShooter/getLocation)必须在拥有该箭的区域线程上执行。
        // onArrowHitHanging / onPortalEnter / onArrowHitEntity 都可能在非箭所在区域的线程上触发,
        // 这里统一投递到箭自己的实体调度器, 避免越线程访问实体状态。
        arrow.getScheduler().run(plugin, task -> doHandleGrapplingHook(arrow), null);
    }

    private void doHandleGrapplingHook(@Nonnull Arrow arrow) {
        if (!arrow.isValid() || !(arrow.getShooter() instanceof Player player)) {
            return;
        }

        GrapplingHookEntity hook = activeHooks.get(player.getUniqueId());
        if (hook == null) {
            return;
        }

        var plugin = Slimefun.instance();
        if (plugin == null) {
            return;
        }

        Location target = arrow.getLocation();
        hook.drop(target);
        hook.remove();

        // Folia: 玩家的位置/传送/速度只能在玩家所在区域的线程上读写
        player.getScheduler().run(plugin, task -> applyHookMovement(player, target), null);

        // 20 tick 后再移除, 保持 isGrappling() 在这段时间内仍然为 true (见 issue #253)
        // 只移除“当前这个 hook”, 避免玩家在 20 tick 内重新发射后, 旧定时器误删新 hook。
        Slimefun.runSync(() -> activeHooks.remove(player.getUniqueId(), hook), 20L);
    }

    private void applyHookMovement(@Nonnull Player player, @Nonnull Location target) {
        var playerLocation = player.getLocation();
        var velocity = new Vector(0.0, 0.2, 0.0);

        if (playerLocation.distance(target) < 3.0) {
            if (target.getY() <= playerLocation.getY()) {
                velocity = target.toVector().subtract(playerLocation.toVector());
            }
        } else {
            var l = playerLocation.clone();
            l.setY(l.getY() + 0.5);

            double g = -0.08;
            double d = target.distance(l);
            double t = d;
            double vX = (1.0 + 0.08 * t) * (target.getX() - l.getX()) / t;
            double vY = (1.0 + 0.04 * t) * (target.getY() - l.getY()) / t - 0.5D * g * t;
            double vZ = (1.0 + 0.08 * t) * (target.getZ() - l.getZ()) / t;

            velocity = player.getVelocity();
            velocity.setX(vX);
            velocity.setY(vY);
            velocity.setZ(vZ);

            // Folia: Entity#teleport 在区域化世界中会抛 UnsupportedOperationException,
            // 必须使用 teleportAsync; 其回调不在玩家区域线程上, 所以速度要再投递回实体调度器。
            var finalVelocity = velocity;
            player.teleportAsync(l)
                    .thenRun(() -> Slimefun.runAtEntity(player, () -> player.setVelocity(finalVelocity)));
            return;
        }

        player.setVelocity(velocity);
    }

    public boolean isGrappling(@Nonnull UUID uuid) {
        return activeHooks.containsKey(uuid);
    }

    @ParametersAreNonnullByDefault
    public void addGrapplingHook(
            Player p, Arrow arrow, Bat bat, boolean dropItem, long despawnTicks, boolean wasConsumed) {
        GrapplingHookEntity hook = new GrapplingHookEntity(p, arrow, bat, dropItem, wasConsumed);
        UUID uuid = p.getUniqueId();

        activeHooks.put(uuid, hook);

        // To fix issue #253
        // Folia: 到期清理时需要移除实体, 必须在拥有该箭所在区域的线程上执行。
        // 同时不能直接把 despawnTicks 交给 RegionScheduler#runDelayed:
        // 该配置项允许为 0 (despawn-seconds), 而 Folia 会拒绝非正数的延迟并抛
        // IllegalArgumentException, 清理任务因此永远不会被调度, 钩爪与 activeHooks 会永久残留。
        // Slimefun.runAtRegion 已经正确处理 delay <= 0 (立即/下一 tick 执行)。
        var arrowLocation = arrow.getLocation();
        var arrowUuid = arrow.getUniqueId();
        Slimefun.runAtRegion(
                arrowLocation,
                () -> {
                    GrapplingHookEntity entity = activeHooks.get(uuid);

                    if (entity != null) {
                        // 射弹映射以箭的 UUID 为键, 而不是玩家的 UUID
                        Slimefun.getBowListener().getProjectileData().remove(arrowUuid);
                        entity.remove();

                        Slimefun.runSync(
                                () -> {
                                    activeHooks.remove(uuid);
                                    invulnerability.remove(uuid);
                                },
                                20L);
                    }
                },
                despawnTicks);
    }
}
