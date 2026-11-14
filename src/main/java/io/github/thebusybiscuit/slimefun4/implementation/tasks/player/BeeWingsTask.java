package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.magical.BeeWings;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BeeWingsListener;
import javax.annotation.Nonnull;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * This task is responsible for the repeating checks for our {@link BeeWings}.
 *
 * @author beSnow
 * @author TheBusyBiscuit
 *
 * @see BeeWings
 * @see BeeWingsListener
 *
 */
public class BeeWingsTask extends AbstractPlayerTask {

    private static final int MIN_ALTITUDE = 4;
    private static final int SLOW_FALLING_DURATION = 60;
    private static final int MAX_GROUND_CHECK_DISTANCE = 6;

    private Location lastLocation;

    public BeeWingsTask(@Nonnull Player p) {
        super(p);
        lastLocation = p.getLocation();
    }

    @Override
    protected void executeTask() {
        Location location = p.getLocation();

        if (location.getY() < lastLocation.getY()) {
            int distanceToHighestBlock =
                    location.getBlockY() - location.getWorld().getHighestBlockYAt(location, HeightMap.WORLD_SURFACE);

            /*
             * getDistanceToGround will only fire when distanceToHighestBlock is negative
             * (which happens when a player flies beneath an existing structure)
             */
            if (distanceToHighestBlock < 0) {
                int distanceToGround = getDistanceToGround(location.getBlock(), MAX_GROUND_CHECK_DISTANCE);

                /*
                 * A distance of zero means that there is no solid ground within the checked range.
                 * There is nothing to protect the Player from, so the last location must still be
                 * updated, otherwise the next descent would be measured against a stale location.
                 */
                if (distanceToGround > 0) {
                    slowDown();
                }
            } else if (distanceToHighestBlock <= MIN_ALTITUDE) {
                slowDown();
            }
        }

        lastLocation = location;
    }

    private void slowDown() {
        p.setFallDistance(0);

        /*
         * The task stays active while the Player is gliding, so a slow falling effect may have to be
         * refreshed after it expired. Only send the message when the effect is actually applied for
         * the first time, otherwise the Player would receive one message per tick.
         */
        if (p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            return;
        }

        Slimefun.getLocalization().sendMessage(p, "messages.bee-suit-slow-fall");
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, SLOW_FALLING_DURATION, 0));
    }

    /**
     * Calculates the distance of the given {@link Block} from the ground.
     *
     * @param b
     *            The {@link Block} to calculate from.
     * @param limit
     *            The limit of {@link Block blocks} to check under the given {@link Block b}.
     *
     */
    private int getDistanceToGround(@Nonnull Block b, int limit) {
        for (int i = 1; i <= limit; i++) {
            Block relative = b.getRelative(0, -i, 0);

            if (relative.getType().isSolid()) {
                return i;
            }
        }

        return 0;
    }

    @Override
    protected boolean isValid() {
        /*
         * The task is only valid as long as the Player is alive and gliding.
         * The presence of the slow falling effect must not invalidate the task: the effect only
         * lasts for three seconds, so cancelling here would remove the protection while the
         * Player is still gliding downwards.
         */
        if (!p.isOnline() || !p.isValid() || p.isDead() || !p.isGliding()) {
            cancel();
            return false;
        }

        if (!(SlimefunItem.getByItem(p.getInventory().getChestplate()) instanceof BeeWings)) {
            // The Bee Wings were removed or replaced while the Player is still gliding
            cancel();
            return false;
        }

        return true;
    }
}
