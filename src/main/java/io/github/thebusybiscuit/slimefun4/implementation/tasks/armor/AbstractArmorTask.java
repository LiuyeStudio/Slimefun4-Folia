package io.github.thebusybiscuit.slimefun4.implementation.tasks.armor;

import com.google.common.base.Preconditions;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * This is a base class for any ArmorTask, it checks every online player
 * and handles any armor functionality.
 *
 * @author TheBusyBiscuit
 * @author martinbrom
 *
 * @see SlimefunArmorTask
 * @see RainbowArmorTask
 */
public abstract class AbstractArmorTask implements Runnable {

    @Override
    public final void run() {
        try {
            tickPlayers();
        } catch (UnsupportedOperationException x) {
            /*
             * Folia: Server#getOnlinePlayers() may only be called from the global region thread, but
             * this task is scheduled on the AsyncScheduler. The exception used to abort the loop on
             * every interval, which silently disabled every armor effect (Slimefun armor, radiation,
             * rainbow armor and the solar helmet). Retry the iteration on the global region instead.
             */
            Slimefun.runSync(this::tickPlayers);
        }
    }

    private void tickPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerProfile.get(p, profile -> scheduleOnPlayerThread(p, profile));
        }

        onTick();
    }

    /**
     * Schedules the actual work for a single {@link Player} on the thread that ticks the region
     * this entity belongs to.
     * <p>
     * On Folia (and its forks such as Canvas) entity access - changing potion effects, reading or
     * modifying the inventory, sending action bars - is only allowed from the thread owning that
     * entity's region. The global region thread is <b>not</b> such a thread, using it throws a
     * {@code ThreadViolationException} ("Cannot add potion effect async").
     *
     * @param p
     *            The {@link Player} to tick
     * @param profile
     *            The {@link PlayerProfile} of that {@link Player}
     */
    private void scheduleOnPlayerThread(@Nonnull Player p, @Nonnull PlayerProfile profile) {
        Slimefun plugin = Slimefun.instance();

        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        p.getScheduler()
                .run(
                        plugin,
                        task -> {
                            if (!p.isValid() || p.isDead()) {
                                return;
                            }

                            /*
                             * 区域线程上的异常会直接打挂整个区域, 因此必须就地捕获。
                             * 以前这里没有 try/catch, 任何一件护甲处理出错都会让该区域崩溃并反复刷屏。
                             */
                            try {
                                onPlayerTick(p, profile);
                            } catch (Exception | LinkageError x) {
                                plugin.getLogger()
                                        .log(
                                                Level.SEVERE,
                                                x,
                                                () -> "An Exception was caught while ticking armor of " + p.getName());
                            }
                        },
                        null);
    }

    /**
     * Schedules this {@link AbstractArmorTask} to run every {@code tickInterval} ticks
     *
     * @param plugin
     *            The {@link Slimefun}
     * @param tickInterval
     *            Delay between two "runs" of this task in ticks
     */
    public final void schedule(@Nonnull Slimefun plugin, long tickInterval) {
        Preconditions.checkNotNull(plugin, "The plugin instance cannot be null!");

        if (tickInterval < 1) {
            tickInterval = 1;
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "The interval for {0} is misconfigured! Received {1}, expected at least 1.",
                            new Object[] {getClass().getSimpleName(), tickInterval});
        }

        Bukkit.getAsyncScheduler()
                .runAtFixedRate(
                        plugin,
                        scheduledTask -> this.run(),
                        0L,
                        tickInterval * 50L,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Method to handle things related to the task itself.
     * Called once per tick (per schedule interval).
     */
    protected void onTick() {
        // Can be overridden as necessary.
    }

    /**
     * Method to handle behavior for player's armor as a whole.
     * It is called once per player.
     *
     * @param p
     *            The {@link Player} wearing the armor
     * @param profile
     *            The {@link Player}'s {@link PlayerProfile}
     */
    @ParametersAreNonnullByDefault
    protected abstract void onPlayerTick(Player p, PlayerProfile profile);
}
