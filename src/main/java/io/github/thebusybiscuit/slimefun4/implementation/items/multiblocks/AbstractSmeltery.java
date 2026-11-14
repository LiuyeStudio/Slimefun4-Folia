package io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks;

import io.github.bakedlibs.dough.inventory.InvUtils;
import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * An abstract super class for the {@link Smeltery} and {@link MakeshiftSmeltery}.
 *
 * @author TheBusyBiscuit
 *
 */
abstract class AbstractSmeltery extends MultiBlockMachine {

    @ParametersAreNonnullByDefault
    protected AbstractSmeltery(ItemGroup itemGroup, SlimefunItemStack item, ItemStack[] recipe, BlockFace trigger) {
        super(itemGroup, item, recipe, trigger);
    }

    @Override
    public void onInteract(Player p, Block b) {
        Block possibleDispenser = b.getRelative(BlockFace.DOWN);
        BlockState state = possibleDispenser.getState(false);

        if (state instanceof Dispenser dispenser) {
            Inventory inv = dispenser.getInventory();
            List<ItemStack[]> inputs = RecipeType.getRecipeInputList(this);

            for (int i = 0; i < inputs.size(); i++) {
                if (canCraft(inv, inputs, i)) {
                    ItemStack output =
                            RecipeType.getRecipeOutputList(this, inputs.get(i)).clone();
                    MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, inputs.get(i), output);

                    Bukkit.getPluginManager().callEvent(event);
                    if (!event.isCancelled() && SlimefunUtils.canPlayerUseItem(p, output, true)) {
                        /*
                         * 先确认产物放得下, 再消耗材料。否则 findOutputInventory 只按“整体尺寸”判断,
                         * 输出容器只差一点空间时, 材料被扣光而产物被 addItem 静默丢弃。
                         */
                        Inventory outputInv = findOutputInventory(output, possibleDispenser, inv);

                        if (outputInv != null
                                && !Slimefun.getItemStackService()
                                        .fits(outputInv, event.getOutput(), InventoryContext.MACHINE_OUTPUT)) {
                            outputInv = null;
                        }

                        if (outputInv != null) {
                            craft(p, b, inv, inputs.get(i), event.getOutput(), outputInv);
                        } else {
                            Slimefun.getLocalization().sendMessage(p, "machines.full-inventory", true);
                        }
                    }

                    return;
                }
            }

            Slimefun.getLocalization().sendMessage(p, "machines.unknown-material", true);
        }
    }

    private boolean canCraft(Inventory inv, List<ItemStack[]> inputs, int i) {
        var contents = inv.getContents();
        var claimed = new boolean[contents.length];

        for (ItemStack expectedInput : inputs.get(i)) {
            if (expectedInput == null) {
                continue;
            }

            /*
             * Folia/Slimefun: 必须校验数量, 而不只是“存在”。
             * 以前只检查是否含有该物品, 于是发射器里只有 1 个却需要 n 个时也会判定成功,
             * 随后 InvUtils#removeItem 拿不到足够的物品 (甚至拿不到), 输出却照常发放 —— 凭空产出。
             * 另外这里统计可用数量时要把已经被同一配方前面消耗掉的位置扣除, 否则
             * “2 个配方都要求同一种物品”时会重复计算同一个堆叠。
             */
            int required = Math.max(1, expectedInput.getAmount());
            int available = 0;

            for (int j = 0; j < contents.length; j++) {
                if (!claimed[j] && SlimefunUtils.isItemSimilar(contents[j], expectedInput, true)) {
                    available += contents[j].getAmount();
                }
            }

            if (available < required) {
                return false;
            }

            // Mark the stacks of this ingredient as used so the next ingredient cannot count them twice
            int remaining = required;
            for (int j = 0; j < contents.length && remaining > 0; j++) {
                if (!claimed[j] && SlimefunUtils.isItemSimilar(contents[j], expectedInput, true)) {
                    claimed[j] = true;
                    remaining -= contents[j].getAmount();
                }
            }
        }

        return true;
    }

    /**
     * This checks whether the given {@link Inventory} still holds every {@link ItemStack} of the
     * given recipe in a sufficient amount.
     *
     * @param inv
     *            The {@link Inventory} to check
     * @param recipe
     *            The ingredients of the recipe
     *
     * @return Whether all ingredients are available
     */
    private boolean hasIngredients(@Nonnull Inventory inv, @Nonnull ItemStack[] recipe) {
        var contents = inv.getContents();

        for (ItemStack required : recipe) {
            if (required == null) {
                continue;
            }

            int available = 0;

            for (ItemStack stack : contents) {
                if (SlimefunUtils.isItemSimilar(stack, required, true)) {
                    available += stack.getAmount();
                }
            }

            if (available < Math.max(1, required.getAmount())) {
                return false;
            }
        }

        return true;
    }

    protected void craft(Player p, Block b, Inventory inv, ItemStack[] recipe, ItemStack output, Inventory outputInv) {
        // 先确认发射器里确实还够材料, 再扣除并发放产物, 避免材料被改走时仍然凭空产出
        if (!hasIngredients(inv, recipe)) {
            return;
        }

        for (ItemStack removing : recipe) {
            if (removing != null) {
                InvUtils.removeItem(
                        inv, removing.getAmount(), true, stack -> SlimefunUtils.isItemSimilar(stack, removing, true));
            }
        }

        addOutput(outputInv, output, b);
        SoundEffect.SMELTERY_CRAFT_SOUND.playAt(b);
        p.getWorld().playEffect(b.getLocation(), Effect.MOBSPAWNER_FLAMES, 1);
    }
}
