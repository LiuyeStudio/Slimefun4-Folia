package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.gadgets.JetBoots;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.bukkit.Effect;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class JetBootsTask extends AbstractPlayerTask {

    private static final float COST = 0.075F;
    private static final double GRAVITY = 0.04D;

    private final JetBoots boots;

    public JetBootsTask(@Nonnull Player p, @Nonnull JetBoots boots) {
        super(p);
        this.boots = boots;
    }

    @Override
    protected void executeTask() {
        ItemStack equipped = p.getInventory().getBoots();

        if (equipped == null || !this.boots.isItem(equipped)) {
            // The JetBoots were removed or replaced. Stop instead of idling forever.
            cancel();
            return;
        }

        if (p.isInsideVehicle()) {
            // The vehicle owns the passenger's velocity, thrusting would only waste charge
            return;
        }

        if (p.isGliding()) {
            // Thrusting during an elytra flight would overwrite the flight direction and
            // act as an unintended speed boost. Do not cancel: the Player may still be sneaking
            // when the flight ends, and the boots have to keep working after the landing.
            return;
        }

        if (this.boots.removeItemCharge(equipped, COST)) {
            SoundEffect.JETBOOTS_THRUST_SOUND.playAt(p.getLocation(), SoundCategory.PLAYERS);
            p.getWorld().playEffect(p.getLocation(), Effect.SMOKE, 1, 1);
            p.setFallDistance(0F);

            /*
             * The random offset makes lower tier boots less accurate. It must never be larger than
             * the thrust itself: JetBoots with a speed of 0.7 or less used to get an offset of
             * (speed - 0.7), i.e. up to 0.7 blocks/tick sideways for a gadget that only moves
             * 0.35 blocks/tick forward.
             */
            double accuracy = NumberUtils.reparseDouble(Math.max(0D, this.boots.getSpeed() - 0.7D));
            double offset = ThreadLocalRandom.current().nextBoolean() ? accuracy : -accuracy;

            p.setVelocity(createThrustVector(p.getEyeLocation().getDirection(), this.boots.getSpeed(), offset));
        } else {
            cancel();
        }
    }

    /**
     * This computes the velocity a pair of {@link JetBoots} applies to its wearer.
     *
     * @param direction
     *            The direction the {@link Player} is looking in
     * @param speed
     *            The horizontal speed of the {@link JetBoots}
     * @param offset
     *            A random offset which makes lower tier boots less accurate
     *
     * @return The resulting velocity
     */
    @Nonnull
    static Vector createThrustVector(@Nonnull Vector direction, double speed, double offset) {
        return new Vector(direction.getX() * speed + offset, GRAVITY, direction.getZ() * speed - offset);
    }
}
