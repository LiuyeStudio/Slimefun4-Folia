package city.norain.slimefun4.utils;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.Location;

/**
 * A minimal, Folia-safe replacement for dough's {@code TaskQueue}.
 * <p>
 * dough's {@code TaskQueue} is backed by the legacy {@code BukkitScheduler} and therefore throws
 * {@code UnsupportedOperationException} on Folia (and its forks such as Canvas). This queue instead
 * dispatches every step to the region that owns the given {@link Location}, so each step is
 * guaranteed to run on the thread that owns that position.
 * <p>
 * The semantics match dough's queue: the delay of a step is relative to the previous step, so all
 * delays are cumulative.
 *
 * @author Slimefun4
 */
public final class FoliaTaskQueue {

    private final List<Step> steps = new ArrayList<>();
    private long cursor = 0L;

    /**
     * Appends a step which runs without any additional delay.
     *
     * @param action
     *            The {@link Runnable} to run
     *
     * @return This {@link FoliaTaskQueue}
     */
    @Nonnull
    public FoliaTaskQueue thenRun(@Nonnull Runnable action) {
        return thenRun(0L, action);
    }

    /**
     * Appends a step which runs {@code delayTicks} after the previous step.
     *
     * @param delayTicks
     *            The delay in ticks, relative to the previous step
     * @param action
     *            The {@link Runnable} to run
     *
     * @return This {@link FoliaTaskQueue}
     */
    @Nonnull
    public FoliaTaskQueue thenRun(long delayTicks, @Nonnull Runnable action) {
        cursor += Math.max(0L, delayTicks);
        steps.add(new Step(cursor, action));
        return this;
    }

    /**
     * Appends {@code times} steps, each one {@code periodTicks} after the previous step.
     *
     * @param periodTicks
     *            The delay in ticks between two executions
     * @param times
     *            How many times the action should run
     * @param action
     *            The {@link Runnable} to run
     *
     * @return This {@link FoliaTaskQueue}
     */
    @Nonnull
    public FoliaTaskQueue thenRepeatEvery(long periodTicks, int times, @Nonnull Runnable action) {
        long period = Math.max(1L, periodTicks);

        for (int i = 0; i < times; i++) {
            cursor += period;
            steps.add(new Step(cursor, action));
        }

        return this;
    }

    /**
     * Schedules every step of this queue on the thread owning the given {@link Location}.
     *
     * @param location
     *            The {@link Location} whose region should run these steps
     */
    public void execute(@Nonnull Location location) {
        for (Step step : steps) {
            Slimefun.runAtRegion(location, step.action(), step.delay());
        }
    }

    private record Step(long delay, Runnable action) {}
}
