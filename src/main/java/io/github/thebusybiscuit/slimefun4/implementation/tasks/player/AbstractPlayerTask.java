package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import javax.annotation.Nonnull;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

abstract class AbstractPlayerTask implements Runnable {

    /**
     * Tracks the currently active task of each type for every {@link Player}.
     */
    private static final PlayerTaskRegistry ACTIVE_TASKS = new PlayerTaskRegistry();

    protected final Player p;
    private ScheduledTask task;

    AbstractPlayerTask(@Nonnull Player p) {
        this.p = p;
    }

    public final void schedule(long delay) {
        cancelPreviousTask();
        task = p.getScheduler()
                .runDelayed(Slimefun.instance(), scheduledTask -> this.run(), this::onRetired, normalizeTicks(delay));

        if (task == null) {
            // The entity was removed before the task could be scheduled, no callback is invoked
            onRetired();
        }
    }

    public final void scheduleRepeating(long delay, long interval) {
        cancelPreviousTask();
        task = p.getScheduler()
                .runAtFixedRate(
                        Slimefun.instance(),
                        scheduledTask -> this.run(),
                        this::onRetired,
                        normalizeTicks(delay),
                        normalizeTicks(interval));

        if (task == null) {
            // The entity was removed before the task could be scheduled, no callback is invoked
            onRetired();
        }
    }

    /**
     * This turns a delay or interval given in ticks into a value that the scheduler actually accepts.
     * <p>
     * Folia's {@code EntityScheduler} rejects non-positive values: {@code runDelayed} and
     * {@code runAtFixedRate} throw an {@link IllegalArgumentException} ("Delay ticks may not be <= 0"
     * resp. "Initial delay ticks may not be <= 0") instead of treating zero as "the next tick" like the
     * old Bukkit scheduler did. All gadgets used to be started with a delay of {@code 0}, so on Folia
     * the exception was thrown from the event handler, no task was ever created and the Jetpack - as
     * well as the Jet Boots, the Parachute and the Infused Magnet - did nothing at all.
     *
     * @param ticks
     *            The delay or interval in ticks
     *
     * @return The given amount of ticks, but at least one tick
     */
    static long normalizeTicks(long ticks) {
        return Math.max(1L, ticks);
    }

    /**
     * This is called when the {@link Player} is removed before this task could run.
     * The retired callback runs in critical code, so it must only clean up this task.
     */
    private void onRetired() {
        task = null;
        ACTIVE_TASKS.remove(p.getUniqueId(), getClass(), this);
    }

    /**
     * This cancels the previously running task of this type, if there is one.
     * <p>
     * A task only discovers that it is no longer needed when it runs. If a {@link Player} triggers the same
     * gadget again before the old task had a chance to run, both tasks would be active at once. This would
     * double the charge consumption and let both tasks overwrite the velocity of the {@link Player}.
     */
    private void cancelPreviousTask() {
        // Scheduling the same instance twice would otherwise leak the previously scheduled task
        ScheduledTask currentTask = task;
        task = null;

        if (currentTask != null) {
            currentTask.cancel();
        }

        Runnable previousTask = ACTIVE_TASKS.replace(p.getUniqueId(), getClass(), this);

        if (previousTask instanceof AbstractPlayerTask other && other != this) {
            other.cancel();
        }
    }

    @Override
    public final void run() {
        if (isValid()) {
            executeTask();
        }
    }

    /**
     * This method cancels this {@link AbstractPlayerTask}.
     */
    public final void cancel() {
        ScheduledTask currentTask = task;
        task = null;

        if (currentTask != null) {
            currentTask.cancel();
        }

        // Only unregister if this task is still the active one, otherwise a replaced task
        // would remove its successor from the registry.
        ACTIVE_TASKS.remove(p.getUniqueId(), getClass(), this);
    }

    /**
     * This method checks if this {@link AbstractPlayerTask} should be continued or cancelled.
     * It will also cancel this {@link AbstractPlayerTask} if it became invalid.
     *
     * @return Whether this {@link AbstractPlayerTask} is still valid
     */
    protected boolean isValid() {
        if (!p.isOnline() || !p.isValid() || p.isDead() || !p.isSneaking() || p.getGameMode() == GameMode.SPECTATOR) {
            cancel();
            return false;
        }

        return true;
    }

    protected abstract void executeTask();
}
