package io.github.thebusybiscuit.slimefun4.implementation.items.electric.reactors;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineFuel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The {@link NetherStarReactor} is an implementation of {@link Reactor} that consumes
 * Nether Stars and adds Withering to any nearby {@link LivingEntity}
 *
 * @author John000708
 *
 * @see NuclearReactor
 *
 */
public abstract class NetherStarReactor extends Reactor {

    @ParametersAreNonnullByDefault
    protected NetherStarReactor(
            ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    protected void registerDefaultFuelTypes() {
        registerFuel(new MachineFuel(1800, new ItemStack(Material.NETHER_STAR)));
    }

    @Override
    public void extraTick(@Nonnull Location l) {
        // Folia: 附近实体的药水效果只能在拥有该位置的区域线程上施加
        Slimefun.runAtRegion(l, () -> {
            for (Entity entity :
                    l.getWorld().getNearbyEntities(l, 5, 5, 5, n -> n instanceof LivingEntity && n.isValid())) {
                // Folia: 跨越区域边界的实体由另一个区域持有, 不能直接修改
                if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(entity)) {
                    continue;
                }
                if (entity instanceof LivingEntity livingEntity) {
                    livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                }
            }
        });
    }

    @Override
    public ItemStack getCoolant() {
        return SlimefunItems.NETHER_ICE_COOLANT_CELL;
    }

    @Override
    public ItemStack getFuelIcon() {
        return new ItemStack(Material.NETHER_STAR);
    }

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.NETHER_STAR);
    }
}
