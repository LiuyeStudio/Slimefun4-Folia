package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the tick normalization of an {@link AbstractPlayerTask}.
 * <p>
 * Folia's entity scheduler throws an {@link IllegalArgumentException} for a delay or a period that is
 * not positive, while the old Bukkit scheduler simply ran the task on the next tick. Every gadget was
 * started with a delay of zero, so on Folia the exception was thrown before the task could be created
 * and the Jetpack never applied any thrust.
 */
class AbstractPlayerTaskTest {

    @Test
    void aZeroDelayIsRaisedToTheNextTick() {
        Assertions.assertEquals(1L, AbstractPlayerTask.normalizeTicks(0L));
    }

    @Test
    void negativeTicksAreRaisedToTheMinimum() {
        Assertions.assertEquals(1L, AbstractPlayerTask.normalizeTicks(-20L));
    }

    @Test
    void validTicksAreKept() {
        Assertions.assertEquals(3L, AbstractPlayerTask.normalizeTicks(3L));
        Assertions.assertEquals(8L, AbstractPlayerTask.normalizeTicks(8L));
    }
}
