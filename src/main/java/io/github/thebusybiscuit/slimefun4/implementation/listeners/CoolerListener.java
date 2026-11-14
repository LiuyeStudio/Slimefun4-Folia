package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.api.events.CoolerFeedPlayerEvent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.Cooler;
import io.github.thebusybiscuit.slimefun4.implementation.items.food.Juice;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

/**
 * This {@link Listener} listens for a {@link FoodLevelChangeEvent} or an {@link EntityDamageEvent} for starvation
 * damage
 * and consumes a {@link Juice} from any {@link Cooler} that can be found in the {@link Inventory} of the given
 * {@link Player}.
 *
 * @author TheBusyBiscuit
 * @author Linox
 *
 * @see Cooler
 * @see Juice
 *
 */
public class CoolerListener implements Listener {

    /**
     * The amount of saturation a single {@link Juice} restores.
     */
    private static final float SATURATION_PER_JUICE = 6F;

    /**
     * How long (in milliseconds) a {@link Player} counts as being fed.
     * <p>
     * The lookup of a backpack is asynchronous and the callback is not invoked when the stored data
     * is missing, so this guard must never be able to block a {@link Player} permanently.
     */
    private static final long FEED_TIMEOUT_MILLIS = 5_000L;

    private final Slimefun plugin;
    private final Cooler cooler;

    /**
     * The {@link Player Players} that are currently being fed, mapped to the time they started.
     */
    private final Map<UUID, Long> feeding = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public CoolerListener(Slimefun plugin, Cooler cooler) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        this.plugin = plugin;
        this.cooler = cooler;
    }

    @EventHandler
    public void onHungerLoss(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player player && e.getFoodLevel() < player.getFoodLevel()) {
            checkAndConsume(player);
        }
    }

    @EventHandler
    public void onHungerDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player player && e.getCause() == DamageCause.STARVATION) {
            checkAndConsume(player);
        }
    }

    private void checkAndConsume(@Nonnull Player p) {
        if (cooler == null || cooler.isDisabled()) {
            // Do not proceed if the Cooler was disabled
            return;
        }

        // Starving triggers both a FoodLevelChangeEvent and starvation damage, sometimes within
        // the same tick. Feeding from both would drain two Coolers at once, so one feed at a time.
        if (!tryAcquireFeedLock(p)) {
            return;
        }

        ItemStack item = findFirstCooler(p);

        if (item == null || !cooler.canUse(p, true)) {
            releaseFeedLock(p);
            return;
        }

        takeJuiceFromCooler(p, item);
    }

    /**
     * This returns the first {@link Cooler} in the {@link Inventory} of the given {@link Player}.
     * <p>
     * Only a single {@link Cooler} is ever returned: reading the contents of a {@link Cooler} is
     * asynchronous, so feeding from every {@link Cooler} at once made all of them consume a
     * {@link Juice} in the same tick while only a single saturation value ended up being applied.
     *
     * @param p
     *            The {@link Player} to search
     *
     * @return The first {@link Cooler} {@link ItemStack} or null if the {@link Player} carries none
     */
    @Nullable public ItemStack findFirstCooler(@Nonnull Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (cooler.isItem(item)) {
                return item;
            }
        }

        return null;
    }

    /**
     * This takes a {@link Juice} from the given {@link Cooler} and consumes it in order
     * to restore hunger for the given {@link Player}.
     *
     * @param p
     *            The {@link Player}
     * @param cooler
     *            The {@link Cooler} {@link ItemStack} to take the {@link Juice} from
     */
    private void takeJuiceFromCooler(@Nonnull Player p, @Nonnull ItemStack cooler) {
        if (!PlayerBackpack.isOwnerOnline(cooler.getItemMeta())) {
            releaseFeedLock(p);
            return;
        }

        // Folia: 回调不能落在全局/数据库线程上, 药水效果与背包写入必须在玩家所在区域线程执行
        PlayerBackpack.getAsync(
                cooler,
                backpack -> Slimefun.runAtEntity(p, () -> {
                    try {
                        // The lookup is asynchronous: the Player may have left or died in the meantime
                        if (p.isOnline() && !p.isDead()) {
                            consumeJuice(p, cooler, backpack);
                        }
                    } finally {
                        releaseFeedLock(p);
                    }
                }),
                false);
    }

    /**
     * This tries to mark the given {@link Player} as currently being fed.
     * <p>
     * An entry that is older than {@link #FEED_TIMEOUT_MILLIS} is considered stale and is replaced,
     * so a lost callback can never stop a {@link Player} from being fed again.
     *
     * @param p
     *            The {@link Player} that wants to be fed
     *
     * @return Whether the {@link Player} may be fed now
     */
    private boolean tryAcquireFeedLock(@Nonnull Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long[] previous = new Long[1];

        feeding.compute(uuid, (key, startedAt) -> {
            if (startedAt != null && now - startedAt < FEED_TIMEOUT_MILLIS) {
                // Still being fed, this is the duplicate event of the same hunger tick
                previous[0] = startedAt;
                return startedAt;
            }

            return now;
        });

        return previous[0] == null;
    }

    /**
     * This releases the feed lock of the given {@link Player}.
     *
     * @param p
     *            The {@link Player} that was fed
     */
    private void releaseFeedLock(@Nonnull Player p) {
        feeding.remove(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Do not leak a feed lock when the Player leaves mid-lookup
        feeding.remove(e.getPlayer().getUniqueId());
    }

    private boolean consumeJuice(@Nonnull Player p, @Nonnull ItemStack coolerItem, @Nonnull PlayerBackpack backpack) {
        if (backpack.isInvalid()) {
            // The backpack was removed in the meantime, do not write into a stale inventory
            return false;
        }

        if (!isStillCarried(p, coolerItem)) {
            // The Cooler was dropped, stashed or vaulted while the lookup was in flight
            return false;
        }

        Inventory inv = backpack.getInventory();
        int slot = -1;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);

            if (isJuice(stack)) {
                slot = i;
                break;
            }
        }

        if (slot >= 0) {
            ItemStack item = inv.getItem(slot);

            if (item == null) {
                // The slot was emptied while we were looking at it
                return false;
            }

            CoolerFeedPlayerEvent event = new CoolerFeedPlayerEvent(p, cooler, coolerItem, item);
            plugin.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return false;
            }

            ItemStack consumed = event.getConsumedItem();

            if (consumed != null && consumed.getItemMeta() instanceof PotionMeta im) {
                for (PotionEffect effect : im.getCustomEffects()) {
                    p.addPotionEffect(effect);
                }
            }

            // Saturation is capped at the food level, so it has to be added rather than
            // overwritten, otherwise a second consumption in the same tick would be lost.
            p.setSaturation(applyJuiceSaturation(p.getSaturation(), p.getFoodLevel()));
            SoundEffect.COOLER_CONSUME_SOUND.playFor(p);
            inv.setItem(slot, null);
            Slimefun.getDatabaseManager().getProfileDataController().saveBackpackInventory(backpack);

            return true;
        }

        return false;
    }

    /**
     * This checks whether the given {@link Cooler} {@link ItemStack} is still inside the
     * {@link Inventory} of the given {@link Player}.
     *
     * @param p
     *            The {@link Player} to inspect
     * @param coolerItem
     *            The {@link Cooler} {@link ItemStack} that was found earlier
     *
     * @return Whether the {@link Player} still carries that {@link Cooler}
     */
    private boolean isStillCarried(@Nonnull Player p, @Nonnull ItemStack coolerItem) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (coolerItem.equals(item)) {
                return true;
            }
        }

        return false;
    }

    /**
     * This computes the saturation a {@link Player} should end up with after drinking one
     * {@link Juice}. Saturation is added rather than overwritten so that consecutive consumptions
     * are not lost, and it is clamped to the food level because vanilla does not allow a higher
     * saturation than that.
     *
     * @param currentSaturation
     *            The current saturation of the {@link Player}
     * @param foodLevel
     *            The current food level of the {@link Player}
     *
     * @return The saturation the {@link Player} should have afterwards
     */
    public static float applyJuiceSaturation(float currentSaturation, int foodLevel) {
        return Math.min(foodLevel, currentSaturation + SATURATION_PER_JUICE);
    }

    /**
     * This checks whether the given {@link ItemStack} is a {@link Juice} that can be consumed.
     * <p>
     * A {@link Cooler} only accepts {@link Juice Juices}, so the {@link SlimefunItem} is looked up
     * instead of merely checking for a named potion.
     *
     * @param item
     *            The {@link ItemStack} to check, may be null
     *
     * @return Whether the given {@link ItemStack} is a consumable {@link Juice}
     */
    private boolean isJuice(@Nullable ItemStack item) {
        if (item == null || item.getType() != Material.POTION) {
            return false;
        }

        SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
        return slimefunItem instanceof Juice;
    }
}
