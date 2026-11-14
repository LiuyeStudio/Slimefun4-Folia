package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * This {@link Listener} registers a shapeless crafting recipe which allows players to craft
 * the {@link SlimefunGuide} out of one {@link Material#BOOK} and one {@link Material#SLIME_BALL}.
 * <p>
 * The recipe is a real vanilla {@link ShapelessRecipe} whose result is the actual guide
 * {@link ItemStack}, so the recipe book and any recipe viewer will display it correctly.
 * <p>
 * Vanilla does not retain {@link ItemStack} metadata of a registered recipe on its own,
 * the stored result is nothing but a {@link Material#ENCHANTED_BOOK} without any meta.
 * Therefore {@link #onPrepareCraft(PrepareItemCraftEvent)} and {@link #onCraft(CraftItemEvent)}
 * replace the result with a fresh guide, so the name always reflects
 * {@code guide.menu-name} from our {@code config.yml}.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunGuide
 */
public class SlimefunGuideRecipeListener implements Listener {

    public static final NamespacedKey RECIPE_KEY = new NamespacedKey(Slimefun.instance(), "slimefun_guide");

    public SlimefunGuideRecipeListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registerRecipe(plugin);
    }

    /**
     * (Re)registers the "Book + Slimeball" recipe if it is enabled in the config.
     *
     * @param plugin
     *            The {@link Slimefun} instance
     */
    public static void registerRecipe(@Nonnull Slimefun plugin) {
        try {
            plugin.getServer().removeRecipe(RECIPE_KEY);
        } catch (Exception | LinkageError ignored) {
            // The recipe was simply not registered yet
        }

        if (!Slimefun.getConfigManager().isGuideCraftingEnabled()) {
            return;
        }

        try {
            /*
             * This result is only used for display purposes, vanilla discards the
             * ItemMeta of a recipe result. The actual outcome is set in onPrepareCraft().
             */
            ItemStack displayResult =
                    SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE).clone();

            ShapelessRecipe recipe = new ShapelessRecipe(RECIPE_KEY, displayResult);
            recipe.addIngredient(Material.BOOK);
            recipe.addIngredient(Material.SLIME_BALL);
            plugin.getServer().addRecipe(recipe);
        } catch (Exception | LinkageError x) {
            plugin.getLogger().log(Level.WARNING, "Could not register the Slimefun Guide crafting recipe", x);
        }
    }

    /**
     * Replaces the result of our recipe with a freshly created guide, so it carries
     * the display name, lore and texture of the current configuration.
     *
     * @param e
     *            The {@link PrepareItemCraftEvent}
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (e.isRepair()) {
            return;
        }

        ItemStack result = e.getInventory().getResult();

        /*
         * A null result is not necessarily an error, but we only ever touch
         * our own recipe and never any other vanilla recipe.
         */
        if (result == null || !isGuideResult(result) || !isGuideRecipe(e.getInventory())) {
            return;
        }

        ItemStack guide = getGuide();

        if (guide != null) {
            guide.setAmount(result.getAmount());
            e.getInventory().setResult(guide);
        }
    }

    /**
     * Ensures the crafted item is an actual guide and not a plain book.
     *
     * @param e
     *            The {@link CraftItemEvent}
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getInventory() instanceof CraftingInventory craftingInventory)) {
            return;
        }

        if (craftingInventory.getType() != InventoryType.WORKBENCH) {
            return;
        }

        ItemStack result = e.getCurrentItem();

        if (result == null || !isGuideResult(result) || !isGuideRecipe(e.getRecipe())) {
            return;
        }

        ItemStack guide = getGuide();

        if (guide == null) {
            e.setCancelled(true);
            return;
        }

        guide.setAmount(Math.max(1, Math.min(result.getAmount(), guide.getMaxStackSize())));
        e.setCurrentItem(guide);
    }

    /**
     * Checks whether the given {@link ItemStack} is the result of our guide recipe.
     * <p>
     * A {@link Material#BOOK} is accepted as well, because vanilla may return a plain book
     * for a registered recipe whose result metadata was stripped.
     *
     * @param item
     *            The result {@link ItemStack}
     *
     * @return Whether this could be our guide recipe result
     */
    private boolean isGuideResult(@Nonnull ItemStack item) {
        Material type = item.getType();
        return type == Material.ENCHANTED_BOOK || type == Material.BOOK;
    }

    /**
     * Checks whether the given {@link Recipe} is our registered guide recipe.
     *
     * @param recipe
     *            The {@link Recipe} to check, may be null
     *
     * @return Whether this is our "Book + Slimeball" recipe
     */
    private boolean isGuideRecipe(@Nullable Recipe recipe) {
        return recipe instanceof Keyed keyed && RECIPE_KEY.equals(keyed.getKey());
    }

    /**
     * Checks whether the given {@link CraftingInventory} contains exactly our recipe.
     * Used by {@link PrepareItemCraftEvent}, where the matching {@link Recipe} is not exposed.
     *
     * @param inventory
     *            The {@link CraftingInventory}
     *
     * @return Whether this is the "Book + Slimeball" guide recipe
     */
    private boolean isGuideRecipe(@Nonnull CraftingInventory inventory) {
        int books = 0;
        int slimeballs = 0;

        for (ItemStack item : inventory.getMatrix()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            if (item.getType() == Material.BOOK && item.getAmount() == 1) {
                books++;
            } else if (item.getType() == Material.SLIME_BALL && item.getAmount() == 1) {
                slimeballs++;
            } else {
                return false;
            }
        }

        return books == 1 && slimeballs == 1;
    }

    /**
     * Creates a fresh copy of the survival guide {@link ItemStack}.
     *
     * @return A copy of the guide, or null if it could not be created
     */
    private @Nullable ItemStack getGuide() {
        try {
            return SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE).clone();
        } catch (Exception | LinkageError x) {
            Slimefun.logger().log(Level.WARNING, "Could not create a Slimefun Guide item", x);
            return null;
        }
    }
}
