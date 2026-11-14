package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyItemStackDeserializerTest {

    @Test
    void stripsUnknownEnchantmentFromComponentMap() {
        var enchantments = new HashMap<String, Object>();
        enchantments.put("excellentenchants:soulbound", 1);
        enchantments.put("excellentenchants:curse_of_fragility", 1);
        enchantments.put("minecraft:mending", 1);

        var serialized = new HashMap<String, Object>();
        serialized.put("type", "DIAMOND_SWORD");
        serialized.put("minecraft:enchantments", enchantments);
        serialized.put("name", "my sword");

        var dropped = new ArrayList<String>();
        LegacyItemStackDeserializer.stripUnknownEnchantments(
                serialized, id -> id.startsWith("minecraft:"), dropped::add);

        assertEquals(
                List.of("excellentenchants:curse_of_fragility", "excellentenchants:soulbound"),
                dropped.stream().sorted().toList());
        assertEquals(Map.of("minecraft:mending", 1), enchantments);
        assertEquals("my sword", serialized.get("name"));
    }

    @Test
    void stripsEnchantmentsNestedInLevelsComponent() {
        var levels = new HashMap<String, Object>();
        levels.put("weirdplugin:curse", 3);
        levels.put("minecraft:sharpness", 5);

        var enchantments = new HashMap<String, Object>();
        enchantments.put("levels", levels);

        var serialized = new HashMap<String, Object>();
        serialized.put("minecraft:enchantments", enchantments);

        var dropped = new ArrayList<String>();
        LegacyItemStackDeserializer.stripUnknownEnchantments(
                serialized, id -> id.startsWith("minecraft:"), dropped::add);

        assertEquals(List.of("weirdplugin:curse"), dropped);
        assertEquals(Map.of("minecraft:sharpness", 5), levels);
    }

    @Test
    void stripsEnchantmentsFromLegacyListForm() {
        var unknown = new HashMap<String, Object>(Map.of("id", "excellentenchants:soulbound", "lvl", 1));
        var known = new HashMap<String, Object>(Map.of("id", "minecraft:mending", "lvl", 1));

        var serialized = new HashMap<String, Object>();
        serialized.put("Enchantments", new ArrayList<>(List.of(unknown, known)));

        var dropped = new ArrayList<String>();
        LegacyItemStackDeserializer.stripUnknownEnchantments(
                serialized, id -> id.startsWith("minecraft:"), dropped::add);

        assertEquals(List.of("excellentenchants:soulbound"), dropped);
        var remaining = (List<?>) serialized.get("Enchantments");
        assertEquals(1, remaining.size());
        assertTrue(remaining.contains(known));
        assertFalse(remaining.contains(unknown));
    }
}
