package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.Parachute;
import javax.annotation.Nonnull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class ParachuteTask extends AbstractPlayerTask {

    public ParachuteTask(@Nonnull Player p) {
        super(p);
    }

    @Override
    protected void executeTask() {
        ItemStack chestplate = p.getInventory().getChestplate();

        if (!(SlimefunItem.getByItem(chestplate) instanceof Parachute)) {
            // The Parachute was removed or replaced. Stop instead of idling forever.
            cancel();
            return;
        }

        if (p.isOnGround()) {
            // Do not push a Player into the ground, the parachute only matters while falling.
            return;
        }

        if (p.isInsideVehicle()) {
            // The vehicle owns the passenger's velocity
            return;
        }

        /*
         * Only slow the fall. The old implementation replaced the whole velocity with (0, -0.1, 0),
         * which wiped all horizontal momentum (walking, knockback, explosions) several times per
         * second while parachuting.
         */
        Vector velocity = p.getVelocity().clone();
        velocity.setY(-0.1D);
        p.setVelocity(velocity);
        p.setFallDistance(0F);
    }
}
