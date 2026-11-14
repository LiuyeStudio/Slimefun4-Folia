package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the saturation handling of {@link CoolerListener}.
 * <p>
 * A {@link Cooler} used to call {@code setSaturation(6F)} unconditionally. Because reading a Cooler
 * is asynchronous, several Coolers could be drained within a single tick, and each of them
 * overwrote the previous value, so only a single portion of saturation was ever added.
 */
class CoolerListenerTest {

    @Test
    void saturationIsAddedToTheCurrentValue() {
        Assertions.assertEquals(6F, CoolerListener.applyJuiceSaturation(0F, 20));
        Assertions.assertEquals(12F, CoolerListener.applyJuiceSaturation(6F, 20));
    }

    @Test
    void saturationDoesNotExceedTheFoodLevel() {
        Assertions.assertEquals(20F, CoolerListener.applyJuiceSaturation(18F, 20));
        Assertions.assertEquals(20F, CoolerListener.applyJuiceSaturation(20F, 20));
    }

    @Test
    void saturationIsCappedWhenThePlayerIsStarving() {
        // A starving player has a low food level, which is the vanilla saturation cap
        Assertions.assertEquals(2F, CoolerListener.applyJuiceSaturation(0F, 2));
    }

    @Test
    void consecutiveConsumptionsKeepAccumulating() {
        float saturation = 0F;

        for (int i = 0; i < 3; i++) {
            saturation = CoolerListener.applyJuiceSaturation(saturation, 20);
        }

        Assertions.assertEquals(18F, saturation, "Each juice should add its own saturation");
    }

    @Test
    void saturationNeverGoesNegativeForFullPlayers() {
        // A player at full saturation must not drop below the food level
        Assertions.assertEquals(20F, CoolerListener.applyJuiceSaturation(20F, 20));
        Assertions.assertEquals(7F, CoolerListener.applyJuiceSaturation(1F, 7));
    }
}
