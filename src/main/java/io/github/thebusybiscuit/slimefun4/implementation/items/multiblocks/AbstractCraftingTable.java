package io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks;

import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.ConsumeContext;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.SlimefunBackpack;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * This abstract super class is responsible for some utility methods for machines which
 * are capable of upgrading backpacks.
 *
 * @author TheBusyBiscuit
 *
 * @see EnhancedCraftingTable
 * @see MagicWorkbench
 * @see ArmorForge
 *
 */
abstract class AbstractCraftingTable extends MultiBlockMachine {

    @ParametersAreNonnullByDefault
    AbstractCraftingTable(ItemGroup itemGroup, SlimefunItemStack item, ItemStack[] recipe, BlockFace trigger) {
        super(itemGroup, item, recipe, trigger);
    }

    protected @Nonnull Inventory createVirtualInventory(@Nonnull Inventory inv) {
        Inventory fakeInv = Bukkit.createInventory(null, 9, "Fake Inventory");

        for (int j = 0; j < inv.getContents().length; j++) {
            ItemStack stack = inv.getContents()[j];

            /*
             * Fixes #2103 - Properly simulating the consumption
             * (which may leave behind empty buckets or glass bottles)
             */
            if (stack != null) {
                stack = stack.clone();

                var consumed = Slimefun.getItemStackService().consume(stack, 1, true, ConsumeContext.VIRTUAL_CRAFTING);
                if (consumed.handled()) {
                    stack = consumed.item();
                } else {
                    ItemUtils.consumeItem(stack, true);
                }
            }

            fakeInv.setItem(j, stack);
        }

        return fakeInv;
    }

    /**
     * This searches the given {@link Inventory} for the {@link SlimefunBackpack} which is about to be
     * upgraded.
     * <p>
     * The {@link ItemStack} has to be resolved <strong>before</strong> the ingredients are consumed,
     * because the upgrade is asynchronous while the crafted product is added afterwards.
     * <p>
     * The returned {@link ItemStack} is a copy: the {@link Inventory#getContents() contents} of a
     * {@link Inventory} are live mirrors, so the ingredients being consumed would blank out the
     * backpack that is about to be upgraded.
     *
     * @param inv
     *            The {@link Inventory} to search
     *
     * @return A copy of the {@link SlimefunBackpack}'s {@link ItemStack} or null if there is none
     */
    protected @Nullable ItemStack findBackpack(@Nonnull Inventory inv) {
        var contents = inv.getContents();

        for (int j = 0; j < 9 && j < contents.length; j++) {
            var item = contents[j];

            if (item != null
                    && item.getType() != Material.AIR
                    && SlimefunItem.getByItem(item) instanceof SlimefunBackpack) {
                return item.clone();
            }
        }

        return null;
    }

    // Return: true if upgrade from existing backpack, else false
    @ParametersAreNonnullByDefault
    protected boolean upgradeBackpack(
            Player p, Inventory inv, SlimefunBackpack backpack, ItemStack output, Runnable onReadyCb) {
        return upgradeBackpack(p, findBackpack(inv), backpack, output, onReadyCb);
    }

    /**
     * Upgrades the given {@link SlimefunBackpack} into the given output {@link ItemStack}.
     *
     * @param p
     *            The {@link Player} who crafted the {@link SlimefunBackpack}
     * @param input
     *            The {@link ItemStack} of the {@link SlimefunBackpack} which is being upgraded
     * @param backpack
     *            The {@link SlimefunBackpack} that is being crafted
     * @param output
     *            The {@link ItemStack} which will be handed to the {@link Player}
     * @param onReadyCb
     *            The callback which adds the output once the backpack data was written
     *
     * @return Whether the callback will be invoked asynchronously
     */
    @ParametersAreNonnullByDefault
    protected boolean upgradeBackpack(
            Player p, @Nullable ItemStack input, SlimefunBackpack backpack, ItemStack output, Runnable onReadyCb) {
        if (input == null) {
            return false;
        }

        // Fixes #2574 - Carry over the Soulbound status
        if (SlimefunUtils.isSoulbound(input)) {
            SlimefunUtils.setSoulbound(output, true);
        }

        int size = backpack.getSize();
        /*
         * Folia: 回调不能落在全局区域线程上, 背包写入与打开界面都要回到玩家所在区域。
         * 用 whenComplete 而不是 thenAccept: 读取背包数据失败时, 回调也必须执行, 否则
         * 原料已经被消耗, 而合成产物永远不会被放进输出栏 (表现就是 "合成没有产物")。
         */
        PlayerBackpack.getAsync(input).whenComplete((result, error) -> {
            if (error != null) {
                Slimefun.logger().log(Level.WARNING, "无法读取背包数据, 本次合成的背包内容不会被继承", error);
            }

            Slimefun.runAtEntity(p, () -> {
                if (result != null) {
                    result.setSize(size);
                    PlayerBackpack.bindItem(output, result);
                }
                onReadyCb.run();
            });
        });

        return true;
    }
}
