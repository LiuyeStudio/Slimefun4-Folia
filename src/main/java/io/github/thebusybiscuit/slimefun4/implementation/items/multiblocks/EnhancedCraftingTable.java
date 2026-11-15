package io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks;

import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.ConsumeContext;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.SlimefunBackpack;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class EnhancedCraftingTable extends AbstractCraftingTable {

    @ParametersAreNonnullByDefault
    public EnhancedCraftingTable(ItemGroup itemGroup, SlimefunItemStack item) {
        super(
                itemGroup,
                item,
                new ItemStack[] {
                    null,
                    null,
                    null,
                    null,
                    new ItemStack(Material.CRAFTING_TABLE),
                    null,
                    null,
                    new ItemStack(Material.DISPENSER),
                    null
                },
                BlockFace.SELF);
    }

    @Override
    public void onInteract(Player p, Block b) {
        Block possibleDispenser = b.getRelative(BlockFace.DOWN);
        BlockState state = possibleDispenser.getState(false);

        if (state instanceof Dispenser dispenser) {
            Inventory inv = dispenser.getInventory();
            List<ItemStack[]> inputs = RecipeType.getRecipeInputList(this);

            for (ItemStack[] input : inputs) {
                if (isCraftable(inv, input)) {
                    ItemStack output =
                            RecipeType.getRecipeOutputList(this, input).clone();
                    MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, input, output);

                    Bukkit.getPluginManager().callEvent(event);
                    if (!event.isCancelled() && SlimefunUtils.canPlayerUseItem(p, output, true)) {
                        craft(inv, possibleDispenser, p, b, event.getOutput());
                    }

                    return;
                }
            }

            if (inv.isEmpty()) {
                Slimefun.getLocalization().sendMessage(p, "machines.inventory-empty", true);
            } else {
                Slimefun.getLocalization().sendMessage(p, "machines.pattern-not-found", true);
            }
        }
    }

    private void craft(Inventory inv, Block dispenser, Player p, Block b, ItemStack output) {
        Inventory fakeInv = createVirtualInventory(inv);
        Inventory outputInv = findOutputInventory(output, dispenser, inv, fakeInv);

        if (outputInv == null) {
            Slimefun.getLocalization().sendMessage(p, "machines.full-inventory", true);
            return;
        }

        SlimefunItem sfItem = SlimefunItem.getByItem(output);
        SlimefunBackpack backpack = sfItem instanceof SlimefunBackpack slimefunBackpack ? slimefunBackpack : null;

        /*
         * The backpack has to be looked up before the ingredients are consumed.
         */
        ItemStack oldBackpack = backpack == null ? null : findBackpack(inv);

        /*
         * The ingredients are consumed first and the product is only added afterwards.
         * Upgrading a backpack may complete synchronously (an unbound backpack or one whose
         * data is already cached), and the product would then be placed into the dispenser
         * before the removal below runs - which consumes the freshly crafted backpack again.
         */
        consumeIngredients(inv);

        Runnable deliver = () -> {
            SoundEffect.ENHANCED_CRAFTING_TABLE_CRAFT_SOUND.playAt(b);
            handleCraftedItem(output, dispenser, inv);
        };

        if (oldBackpack == null || !upgradeBackpack(p, oldBackpack, backpack, output, deliver)) {
            deliver.run();
        }
    }

    private static void consumeIngredients(@Nonnull Inventory inv) {
        ItemStack[] contents = inv.getContents();

        for (int j = 0; j < 9 && j < contents.length; j++) {
            ItemStack item = contents[j];

            if (item != null && item.getType() != Material.AIR) {
                var consumed = Slimefun.getItemStackService().consume(item, 1, true, ConsumeContext.VIRTUAL_CRAFTING);
                if (consumed.handled()) {
                    inv.setItem(j, consumed.item());
                } else {
                    ItemUtils.consumeItem(item, true);
                }
            }
        }
    }

    private boolean isCraftable(Inventory inv, ItemStack[] recipe) {
        for (int j = 0; j < inv.getContents().length; j++) {
            if (!SlimefunUtils.isItemSimilar(inv.getContents()[j], recipe[j], true, true, false, false)) {
                if (SlimefunItem.getByItem(recipe[j]) instanceof SlimefunBackpack) {
                    if (!SlimefunUtils.isItemSimilar(inv.getContents()[j], recipe[j], false, true, false, false)) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }
}
