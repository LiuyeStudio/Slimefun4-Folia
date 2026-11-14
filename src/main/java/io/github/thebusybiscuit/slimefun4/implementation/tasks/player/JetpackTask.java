package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.gadgets.Jetpack;
import javax.annotation.Nonnull;
import org.bukkit.Effect;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class JetpackTask extends AbstractPlayerTask {

    private static final float COST = 0.08F;
    private static final double FORWARD_THRUST = 0.2D;

    private final Jetpack jetpack;

    public JetpackTask(@Nonnull Player p, @Nonnull Jetpack jetpack) {
        super(p);
        this.jetpack = jetpack;
    }

    @Override
    protected void executeTask() {
        ItemStack chestplate = p.getInventory().getChestplate();

        if (chestplate == null || !jetpack.isItem(chestplate)) {
            // The Jetpack was removed or replaced with another item. Stop instead of idling
            // forever (and potentially draining a different item) until the Player stops sneaking.
            cancel();
            return;
        }

        if (p.isInsideVehicle()) {
            // The vehicle owns the passenger's velocity: setVelocity would not move the Player, but
            // the charge would still be drained (and the sound/particles would suggest otherwise).
            return;
        }

        if (jetpack.removeItemCharge(chestplate, COST)) {
            SoundEffect.JETPACK_THRUST_SOUND.playAt(p.getLocation(), SoundCategory.PLAYERS);
            p.getWorld().playEffect(p.getLocation(), Effect.SMOKE, 1, 1);
            p.setFallDistance(0F);
            p.setVelocity(createThrustVector(p.getEyeLocation().getDirection(), jetpack.getThrust()));
        } else {
            cancel();
        }
    }

    /**
     * This computes the velocity a {@link Jetpack} applies to its wearer.
     *
     * @param direction
     *            The direction the {@link Player} is looking in
     * @param thrust
     *            The vertical thrust of the {@link Jetpack}
     *
     * @return The resulting velocity
     */
    @Nonnull
    static Vector createThrustVector(@Nonnull Vector direction, double thrust) {
        Vector vector = new Vector(0, 1, 0);
        vector.multiply(thrust);
        vector.add(direction.clone().multiply(FORWARD_THRUST));
        return vector;
    }
}
