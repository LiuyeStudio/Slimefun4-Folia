package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

/**
 * 反序列化旧版 {@link ItemStack} 时, 先把当前服务端不存在的附魔从物品组件里剔除。
 *
 * <p>旧版 Bukkit 对象流里的 {@link ItemStack} 会被还原成一个 Map(其中可能直接带有
 * {@code minecraft:enchantments} 之类的组件), 这些组件最终会交给 Paper 的
 * {@code MCUtil.deserializeItem} 解析。只要其中有任何一个附魔不在附魔注册表里, 整个物品就会解析失败并变成空气。
 * 这里在每个组件上做一次过滤, 把无法解析的附魔剔除掉, 再交回 {@link ItemStack#deserialize(Map)}。
 */
public final class LegacyItemStackDeserializer implements ConfigurationSerializable {

    static final String ITEM_STACK_ALIAS = "ItemStack";

    private static final List<String> ENCHANTMENT_KEYS =
            List.of("minecraft:enchantments", "minecraft:stored_enchantments", "Enchantments", "StoredEnchantments");

    private LegacyItemStackDeserializer() {}

    @SuppressWarnings("unchecked")
    public static ItemStack deserialize(Map<String, Object> serialized) {
        stripUnknownEnchantments(
                serialized, LegacyEnchantmentDataFixer::isRegistered, LegacyEnchantmentDataFixer::logDropped);
        return ItemStack.deserialize(serialized);
    }

    @Override
    public Map<String, Object> serialize() {
        throw new UnsupportedOperationException("LegacyItemStackDeserializer is only used for deserialization");
    }

    static void stripUnknownEnchantments(Object value, Predicate<String> isRegistered, Consumer<String> onDropped) {
        if (value instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            var map = (Map<String, Object>) rawMap;

            for (var entry : new ArrayList<>(map.entrySet())) {
                if (entry.getKey() != null && ENCHANTMENT_KEYS.contains(entry.getKey())) {
                    filterEnchantments(entry.getValue(), isRegistered, onDropped);
                } else {
                    stripUnknownEnchantments(entry.getValue(), isRegistered, onDropped);
                }
            }
        } else if (value instanceof List<?> list) {
            for (var element : list) {
                stripUnknownEnchantments(element, isRegistered, onDropped);
            }
        }
    }

    private static void filterEnchantments(Object value, Predicate<String> isRegistered, Consumer<String> onDropped) {
        if (value instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            var map = (Map<String, Object>) rawMap;

            for (var entry : new ArrayList<>(map.entrySet())) {
                var key = entry.getKey();

                // 新版组件里等级可能包在 "levels" 之类的复合字段里
                if (entry.getValue() instanceof Map<?, ?> || entry.getValue() instanceof List<?>) {
                    filterEnchantments(entry.getValue(), isRegistered, onDropped);
                    continue;
                }

                if (key != null
                        && key.indexOf(':') > 0
                        && entry.getValue() instanceof Number
                        && !isRegistered.test(key)) {
                    map.remove(key);
                    onDropped.accept(key);
                }
            }
        } else if (value instanceof List<?> list) {
            // 旧版格式: [{id: "xxx:yyy", lvl: 1}, ...]
            for (Iterator<?> iterator = list.iterator(); iterator.hasNext(); ) {
                var element = iterator.next();

                if (element instanceof Map<?, ?> elementMap
                        && elementMap.get("id") instanceof String id
                        && id.indexOf(':') > 0
                        && !isRegistered.test(id)) {
                    iterator.remove();
                    onDropped.accept(id);
                    continue;
                }

                stripUnknownEnchantments(element, isRegistered, onDropped);
            }
        }
    }
}
