package io.github.thebusybiscuit.slimefun4.utils.itemstack;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.bakedlibs.dough.data.persistent.PersistentDataAPI;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * This is just a helper {@link ItemStack} class for the {@link SlimefunGuide} {@link ItemStack}.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunGuide
 * @see SlimefunGuideImplementation
 *
 */
public class SlimefunGuideItem extends ItemStack {

    /**
     * The alternative mode may override the localized name, used when a
     * translation did not provide this alternative yet.
     */
    private static final String SURVIVAL_MODE_FALLBACK_NAME = "&a%slimefun% 指南 &7(箱子界面)";

    private static final String CHEAT_MODE_FALLBACK_NAME = "&c%slimefun% 指南 &4(作弊模式)";

    public SlimefunGuideItem(@Nonnull SlimefunGuideImplementation implementation) {
        super(Material.ENCHANTED_BOOK);

        ItemMeta meta = getItemMeta();
        SlimefunGuideMode type = implementation.getMode();
        String path = type == SlimefunGuideMode.CHEAT_MODE ? "guide.item.cheat-mode" : "guide.item.survival-mode";

        meta.setDisplayName(ChatColors.color(getLocalizedName(path, type)));

        List<String> lore = new ArrayList<>();
        lore.add(type == SlimefunGuideMode.CHEAT_MODE ? ChatColors.color("&4&l仅限管理员使用") : "");
        lore.add(ChatColors.color("&e右键 &8\u21E8 &7浏览物品"));
        lore.add(ChatColors.color("&eShift + 右键 &8\u21E8 &7打开 设置 / 关于"));

        meta.setLore(lore);

        PersistentDataAPI.setString(meta, Slimefun.getRegistry().getGuideDataKey(), type.name());
        Slimefun.getItemTextureService().setTexture(meta, "SLIMEFUN_GUIDE");

        setItemMeta(meta);
    }

    @Nonnull
    private static String getLocalizedName(@Nonnull String path, @Nonnull SlimefunGuideMode mode) {
        String fallback = mode == SlimefunGuideMode.CHEAT_MODE ? CHEAT_MODE_FALLBACK_NAME : SURVIVAL_MODE_FALLBACK_NAME;

        try {
            String name = Slimefun.getLocalization().getMessage(path);

            if (name != null && !name.isBlank() && !name.startsWith("! Missing string")) {
                return SlimefunGuide.applyMenuName(name);
            }
        } catch (Exception | LinkageError x) {
            // Fall through to the hardcoded default
        }

        return SlimefunGuide.applyMenuName(fallback);
    }
}
