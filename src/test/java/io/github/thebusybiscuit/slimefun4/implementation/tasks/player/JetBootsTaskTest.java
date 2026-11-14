package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the velocity calculation of a {@link JetBootsTask}.
 */
class JetBootsTaskTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    void horizontalSpeedFollowsTheLookDirection() {
        Vector velocity = JetBootsTask.createThrustVector(new Vector(0, 0, 1), 0.5, 0);

        Assertions.assertEquals(0, velocity.getX(), EPSILON);
        Assertions.assertEquals(0.04, velocity.getY(), EPSILON);
        Assertions.assertEquals(0.5, velocity.getZ(), EPSILON);
    }

    @Test
    void theOffsetIsAppliedToBothHorizontalAxes() {
        Vector velocity = JetBootsTask.createThrustVector(new Vector(0, 0, 1), 0.5, 0.1);

        Assertions.assertEquals(0.1, velocity.getX(), EPSILON);
        Assertions.assertEquals(0.4, velocity.getZ(), EPSILON);
    }

    @Test
    void theLookDirectionIsNotMutated() {
        Vector direction = new Vector(0, 0, 1);
        JetBootsTask.createThrustVector(direction, 0.5, 0.1);

        Assertions.assertEquals(new Vector(0, 0, 1), direction);
    }
}
