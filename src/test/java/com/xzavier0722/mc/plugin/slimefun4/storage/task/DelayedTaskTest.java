package com.xzavier0722.mc.plugin.slimefun4.storage.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DelayedTask}.
 * <p>
 * A delayed task must run at most once, no matter whether it is run by the saving looper or forced
 * by {@code BlockDataController#move(...)}. Running it twice re-wrote block data of a location whose
 * data had already been moved, which could recreate the old block record.
 */
class DelayedTaskTest {

    @Test
    void runUnsafelyRunsTheTaskOnlyOnce() {
        var runs = new AtomicInteger();
        var task = new DelayedTask(1, TimeUnit.HOURS, runs::incrementAndGet);

        assertTrue(task.runUnsafely());
        assertFalse(task.runUnsafely());
        assertEquals(1, runs.get());
    }

    @Test
    void tryRunDoesNothingAfterTheTaskExecuted() {
        var runs = new AtomicInteger();
        var task = new DelayedTask(1, TimeUnit.HOURS, runs::incrementAndGet);

        task.runUnsafely();
        assertFalse(task.tryRun(), "An already executed task must not run again");
        assertEquals(1, runs.get());
    }

    @Test
    void tryRunRespectsTheDelay() {
        var runs = new AtomicInteger();
        var task = new DelayedTask(1, TimeUnit.HOURS, runs::incrementAndGet);

        assertFalse(task.tryRun());
        assertEquals(0, runs.get());

        // Once the delay passed the task may run exactly once
        task.setRunAfter(0, TimeUnit.MILLISECONDS);
        assertTrue(task.tryRun());
        assertFalse(task.tryRun());
        assertEquals(1, runs.get());
    }

    @Test
    void executedFlagIsVisibleForRescheduling() {
        var task = new DelayedTask(0, TimeUnit.MILLISECONDS, () -> {});

        assertFalse(task.isExecuted());
        task.runUnsafely();
        assertTrue(task.isExecuted());
    }
}
