package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the velocity calculation of a {@link JetpackTask}.
 */
class JetpackTaskTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    void thrustIsAppliedVertically() {
        Vector velocity = JetpackTask.createThrustVector(new Vector(0, 0, 1), 0.5);

        Assertions.assertEquals(0, velocity.getX(), EPSILON);
        Assertions.assertEquals(0.5, velocity.getY(), EPSILON);
        Assertions.assertEquals(0.2, velocity.getZ(), EPSILON);
    }

    @Test
    void lookingUpAddsTheForwardMomentumToTheThrust() {
        Vector velocity = JetpackTask.createThrustVector(new Vector(0, 1, 0), 0.35);

        Assertions.assertEquals(0, velocity.getX(), EPSILON);
        Assertions.assertEquals(0.55, velocity.getY(), EPSILON);
        Assertions.assertEquals(0, velocity.getZ(), EPSILON);
    }

    @Test
    void theLookDirectionIsNotMutated() {
        Vector direction = new Vector(0, 0, 1);
        JetpackTask.createThrustVector(direction, 0.4);

        Assertions.assertEquals(new Vector(0, 0, 1), direction);
    }
}
