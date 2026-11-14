package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.Parachute;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.gadgets.JetBoots;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.gadgets.Jetpack;
import io.github.thebusybiscuit.slimefun4.implementation.items.magical.InfusedMagnet;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.player.InfusedMagnetTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.player.JetBootsTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.player.JetpackTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.player.ParachuteTask;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * This {@link Listener} is responsible for listening to the {@link PlayerToggleSneakEvent}
 * to start tasks for various gadgets that are activated by pressing shift,
 * like the {@link Jetpack} or {@link JetBoots}
 *
 * @author TheBusyBiscuit
 *
 * @see JetpackTask
 * @see JetBootsTask
 * @see ParachuteTask
 * @see InfusedMagnetTask
 *
 */
public class GadgetsListener implements Listener {

    public GadgetsListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) {
            return;
        }

        Player p = e.getPlayer();

        if (p.getGameMode() == GameMode.SPECTATOR) {
            // Spectators cannot use gadgets, and their inventory is not accessible
            return;
        }

        if (p.getInventory().getChestplate() != null) {
            SlimefunItem chestplate = SlimefunItem.getByItem(p.getInventory().getChestplate());
            handleChestplate(p, chestplate);
        }

        if (p.getInventory().getBoots() != null) {
            SlimefunItem boots = SlimefunItem.getByItem(p.getInventory().getBoots());
            handleBoots(p, boots);
        }

        if (SlimefunUtils.containsSimilarItem(p.getInventory(), SlimefunItems.INFUSED_MAGNET, true)
                && SlimefunItems.INFUSED_MAGNET.getItem() instanceof InfusedMagnet magnet
                && magnet.canUse(p, true)) {
            // The initial delay has to be at least one tick, Folia rejects a delay of zero
            new InfusedMagnetTask(p, magnet.getRadius()).scheduleRepeating(1, 8);
        }
    }

    private void handleChestplate(@Nonnull Player p, @Nullable SlimefunItem chestplate) {
        if (chestplate == null || !chestplate.canUse(p, true)) {
            return;
        }

        if (chestplate instanceof Jetpack jetpack) {
            /*
             * Do not hard-code a minimum thrust here: an addon may register a Jetpack with a
             * lower thrust than the ones shipped with Slimefun. Only a Jetpack without any
             * thrust at all cannot do anything useful.
             */
            if (jetpack.getThrust() > 0) {
                // The initial delay has to be at least one tick, Folia rejects a delay of zero
                new JetpackTask(p, jetpack).scheduleRepeating(1, 3);
            }
        } else if (chestplate instanceof Parachute) {
            new ParachuteTask(p).scheduleRepeating(1, 3);
        }
    }

    private void handleBoots(@Nonnull Player p, @Nullable SlimefunItem boots) {
        if (boots instanceof JetBoots jetBoots && boots.canUse(p, true)) {
            if (jetBoots.getSpeed() > 0) {
                // The initial delay has to be at least one tick, Folia rejects a delay of zero
                new JetBootsTask(p, jetBoots).scheduleRepeating(1, 2);
            }
        }
    }
}
