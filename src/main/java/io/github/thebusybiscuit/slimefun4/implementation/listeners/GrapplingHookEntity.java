package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

final class GrapplingHookEntity {

    private final boolean dropItem;
    private final boolean wasConsumed;
    private final Arrow arrow;
    private final Entity leashTarget;

    @ParametersAreNonnullByDefault
    GrapplingHookEntity(Player p, Arrow arrow, Entity leashTarget, boolean dropItem, boolean wasConsumed) {
        this.arrow = arrow;
        this.wasConsumed = wasConsumed;
        this.leashTarget = leashTarget;
        this.dropItem = p.getGameMode() != GameMode.CREATIVE && dropItem;
    }

    @Nonnull
    public Arrow getArrow() {
        return arrow;
    }

    public void drop(@Nonnull Location l) {
        // If a grappling hook was consumed, drop one grappling hook on the floor
        if (dropItem && wasConsumed) {
            Item item = l.getWorld().dropItem(l, SlimefunItems.GRAPPLING_HOOK.clone());
            item.setPickupDelay(16);
        }
    }

    public void remove() {
        // Folia: 箭与拴绳目标可能属于不同区域, 各自投递到自己的区域线程再移除
        if (arrow.isValid()) {
            if (Bukkit.isOwnedByCurrentRegion(arrow)) {
                arrow.remove();
            } else {
                arrow.getScheduler().run(Slimefun.instance(), task -> arrow.remove(), null);
            }
        }

        if (leashTarget.isValid()) {
            if (Bukkit.isOwnedByCurrentRegion(leashTarget)) {
                leashTarget.remove();
            } else {
                leashTarget.getScheduler().run(Slimefun.instance(), task -> leashTarget.remove(), null);
            }
        }
    }

    public Entity getLeashTarget() {
        return leashTarget;
    }
}
