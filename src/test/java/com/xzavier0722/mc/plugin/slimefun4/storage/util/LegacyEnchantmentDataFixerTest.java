package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class LegacyEnchantmentDataFixerTest {

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_INT = 3;
    private static final int TAG_COMPOUND = 10;

    @Test
    void removesEnchantmentsThatAreNoLongerRegistered() throws Exception {
        var nbt = encode(out -> {
            beginRoot(out);
            beginCompound(out, "minecraft:enchantments");
            intEntry(out, "excellentenchants:soulbound", 1);
            intEntry(out, "minecraft:mending", 1);
            byteEntry(out, "show_in_tooltip", 0);
            end(out);
            beginCompound(out, "SomeAddonData");
            intEntry(out, "addon:key", 7);
            end(out);
            end(out);
        });

        var dropped = new ArrayList<String>();
        var fixed = LegacyEnchantmentDataFixer.fix(nbt, id -> id.equals("minecraft:mending"), dropped::add);

        assertEquals(List.of("excellentenchants:soulbound"), dropped);
        assertFalse(contains(fixed, "excellentenchants:soulbound"));
        assertTrue(contains(fixed, "minecraft:mending"));
        assertTrue(contains(fixed, "show_in_tooltip"));
        assertTrue(contains(fixed, "addon:key"));
    }

    @Test
    void removesEnchantmentsInsideNestedLevelsCompound() throws Exception {
        var nbt = encode(out -> {
            beginRoot(out);
            beginCompound(out, "minecraft:enchantments");
            beginCompound(out, "levels");
            intEntry(out, "weirdplugin:curse", 1);
            intEntry(out, "minecraft:sharpness", 5);
            end(out);
            end(out);
            end(out);
        });

        var dropped = new ArrayList<String>();
        var fixed = LegacyEnchantmentDataFixer.fix(nbt, id -> id.startsWith("minecraft:"), dropped::add);

        assertEquals(List.of("weirdplugin:curse"), dropped);
        assertFalse(contains(fixed, "weirdplugin:curse"));
        assertTrue(contains(fixed, "minecraft:sharpness"));
    }

    @Test
    void keepsDataUntouchedWhenEveryEnchantmentIsKnown() throws Exception {
        var nbt = encode(out -> {
            beginRoot(out);
            beginCompound(out, "minecraft:enchantments");
            intEntry(out, "minecraft:mending", 1);
            end(out);
            end(out);
        });

        assertSame(nbt, LegacyEnchantmentDataFixer.fix(nbt, id -> true, id -> {}));
    }

    @Test
    void returnsOriginalDataWhenItIsNotReadableNbt() {
        var broken = Base64.getEncoder().encodeToString("not nbt at all".getBytes(StandardCharsets.UTF_8));

        assertEquals(broken, LegacyEnchantmentDataFixer.fix(broken, id -> false, id -> {}));
    }

    private static void beginRoot(DataOutputStream out) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF("");
    }

    private static void beginCompound(DataOutputStream out, String name) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF(name);
    }

    private static void intEntry(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(TAG_INT);
        out.writeUTF(name);
        out.writeInt(value);
    }

    private static void byteEntry(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(TAG_BYTE);
        out.writeUTF(name);
        out.writeByte(value);
    }

    private static void end(DataOutputStream out) throws IOException {
        out.writeByte(TAG_END);
    }

    private interface NbtWriter {
        void write(DataOutputStream out) throws IOException;
    }

    private static String encode(NbtWriter writer) throws IOException {
        var raw = new ByteArrayOutputStream();

        try (var gzip = new GZIPOutputStream(raw);
                var out = new DataOutputStream(gzip)) {
            writer.write(out);
        }

        return Base64.getEncoder().encodeToString(raw.toByteArray());
    }

    private static byte[] decode(String nbt) throws IOException {
        var raw = Base64.getDecoder().decode(nbt);

        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(raw));
                var in = new DataInputStream(gzip)) {
            return in.readAllBytes();
        }
    }

    private static boolean contains(String nbt, String text) throws IOException {
        return new String(decode(nbt), StandardCharsets.ISO_8859_1).contains(text);
    }
}
