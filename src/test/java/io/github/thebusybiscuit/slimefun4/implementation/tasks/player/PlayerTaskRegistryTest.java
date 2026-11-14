package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlayerTaskRegistry}.
 * <p>
 * The registry makes sure that only one task of a given type is active per player. Without it,
 * quickly releasing and pressing shift would start a second gadget task while the first one is
 * still running, which doubles the charge consumption.
 */
class PlayerTaskRegistryTest {

    private final PlayerTaskRegistry registry = new PlayerTaskRegistry();
    private final UUID uuid = UUID.randomUUID();

    @Test
    void replaceReturnsThePreviousTask() {
        Runnable first = () -> {};
        Runnable second = () -> {};

        Assertions.assertNull(registry.replace(uuid, JetpackTask.class, first));
        Assertions.assertSame(first, registry.replace(uuid, JetpackTask.class, second));
        Assertions.assertTrue(registry.contains(uuid, JetpackTask.class, second));
        Assertions.assertFalse(registry.contains(uuid, JetpackTask.class, first));
    }

    @Test
    void differentTaskTypesDoNotReplaceEachOther() {
        Runnable jetpack = () -> {};
        Runnable boots = () -> {};

        registry.replace(uuid, JetpackTask.class, jetpack);
        Assertions.assertNull(registry.replace(uuid, JetBootsTask.class, boots));

        Assertions.assertTrue(registry.contains(uuid, JetpackTask.class, jetpack));
        Assertions.assertTrue(registry.contains(uuid, JetBootsTask.class, boots));
    }

    @Test
    void playersAreIsolated() {
        UUID otherPlayer = UUID.randomUUID();
        Runnable first = () -> {};
        Runnable second = () -> {};

        registry.replace(uuid, JetpackTask.class, first);
        Assertions.assertNull(registry.replace(otherPlayer, JetpackTask.class, second));

        Assertions.assertTrue(registry.contains(uuid, JetpackTask.class, first));
        Assertions.assertTrue(registry.contains(otherPlayer, JetpackTask.class, second));
    }

    @Test
    void removingAReplacedTaskKeepsItsSuccessor() {
        Runnable first = () -> {};
        Runnable second = () -> {};

        registry.replace(uuid, JetpackTask.class, first);
        registry.replace(uuid, JetpackTask.class, second);

        // The already replaced task finishing late must not unregister the new one
        registry.remove(uuid, JetpackTask.class, first);
        Assertions.assertTrue(registry.contains(uuid, JetpackTask.class, second));

        registry.remove(uuid, JetpackTask.class, second);
        Assertions.assertFalse(registry.contains(uuid, JetpackTask.class, second));
    }

    @Test
    void entryIsDroppedWhenTheLastTaskIsRemoved() {
        Runnable task = () -> {};

        registry.replace(uuid, JetpackTask.class, task);
        Assertions.assertEquals(1, registry.size());

        registry.remove(uuid, JetpackTask.class, task);
        Assertions.assertEquals(0, registry.size());
    }
}
