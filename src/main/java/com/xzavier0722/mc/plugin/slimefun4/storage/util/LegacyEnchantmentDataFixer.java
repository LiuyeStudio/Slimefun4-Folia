package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nonnull;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/**
 * 移除旧版物品数据里当前服务端不存在的附魔。
 *
 * <p>物品的 NBT 里会记录附魔, 例如 {@code minecraft:enchantments}。当这些附魔来自后来被卸载、改名或不再注册
 * 到附魔注册表的插件(例如某些自定义附魔插件的 {@code xxx:soulbound})时, Paper 反序列化物品会直接抛错:
 * <pre>
 * IllegalStateException: Failed to get element excellentenchants:soulbound
 *   at io.papermc.paper.util.MCUtil.deserializeItem
 *   at org.bukkit.inventory.ItemStack.deserialize
 * </pre>
 * 随后 {@code ConfigurationSerialization.deserializeObject} 会吞掉异常并返回 null, 最终让整个物品变成空气。
 * 这里在交给 Paper 之前把这些无法解析的附魔条目从 NBT 中剔除, 让物品(以及其它仍然有效的附魔)可以正常读取,
 * 并把被剔除的附魔写进日志。
 */
final class LegacyEnchantmentDataFixer {

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    /** 新版组件格式(1.20.5+)里的附魔字段名。 */
    private static final List<String> ENCHANTMENT_COMPONENT_KEYS =
            List.of("minecraft:enchantments", "minecraft:stored_enchantments");

    private LegacyEnchantmentDataFixer() {}

    /**
     * 修复一段 Base64(GZIP(NBT)) 数据, 剔除附魔注册表里不存在的附魔。
     *
     * @param encodedNbt Base64 编码的 GZIP NBT
     * @return 修复后的数据, 没有变化时返回原值
     */
    @Nonnull
    static String fix(@Nonnull String encodedNbt) {
        return fix(encodedNbt, LegacyEnchantmentDataFixer::isRegistered, LegacyEnchantmentDataFixer::logDropped);
    }

    @Nonnull
    static String fix(
            @Nonnull String encodedNbt, @Nonnull Predicate<String> isRegistered, @Nonnull Consumer<String> onDropped) {
        try {
            var decoded = Base64.getDecoder().decode(encodedNbt);
            var fixed = new ByteArrayOutputStream(decoded.length);
            boolean changed;

            try (var input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(decoded)));
                    var gzip = new GZIPOutputStream(fixed);
                    var output = new DataOutputStream(gzip)) {
                var dropped = new ArrayList<String>();
                var rootType = input.readUnsignedByte();
                output.writeByte(rootType);
                output.writeUTF(input.readUTF());
                changed = copyPayload(rootType, input, output, false, isRegistered, dropped);
                dropped.forEach(onDropped);
            }

            return changed ? Base64.getEncoder().encodeToString(fixed.toByteArray()) : encodedNbt;
        } catch (IOException | IllegalArgumentException ignored) {
            return encodedNbt;
        }
    }

    /**
     * 判断某个附魔在当前服务端是否存在。注册表不可用时按"存在"处理, 避免误删数据。
     */
    static boolean isRegistered(@Nonnull String id) {
        try {
            var key = NamespacedKey.fromString(id);
            return key == null || Registry.ENCHANTMENT.get(key) != null;
        } catch (RuntimeException e) {
            return true;
        }
    }

    static void logDropped(@Nonnull String id) {
        var plugin = Slimefun.instance();
        if (plugin == null) {
            return;
        }

        plugin.getLogger().log(Level.WARNING, "旧版物品数据中的附魔 " + id + " 在当前服务端不存在, 已忽略该附魔以保证物品能够正常读取");
    }

    private static boolean copyPayload(
            int type,
            DataInputStream input,
            DataOutputStream output,
            boolean insideEnchantments,
            Predicate<String> isRegistered,
            List<String> dropped)
            throws IOException {
        return switch (type) {
            case TAG_BYTE -> {
                output.writeByte(input.readByte());
                yield false;
            }
            case TAG_SHORT -> {
                output.writeShort(input.readShort());
                yield false;
            }
            case TAG_INT -> {
                output.writeInt(input.readInt());
                yield false;
            }
            case TAG_LONG -> {
                output.writeLong(input.readLong());
                yield false;
            }
            case TAG_FLOAT -> {
                output.writeFloat(input.readFloat());
                yield false;
            }
            case TAG_DOUBLE -> {
                output.writeDouble(input.readDouble());
                yield false;
            }
            case TAG_BYTE_ARRAY -> copyByteArray(input, output);
            case TAG_STRING -> {
                output.writeUTF(input.readUTF());
                yield false;
            }
            case TAG_LIST -> copyList(input, output, insideEnchantments, isRegistered, dropped);
            case TAG_COMPOUND -> copyCompound(input, output, insideEnchantments, isRegistered, dropped);
            case TAG_INT_ARRAY -> copyIntArray(input, output);
            case TAG_LONG_ARRAY -> copyLongArray(input, output);
            default -> throw new IOException("Unsupported NBT tag type: " + type);
        };
    }

    private static boolean copyCompound(
            DataInputStream input,
            DataOutputStream output,
            boolean insideEnchantments,
            Predicate<String> isRegistered,
            List<String> dropped)
            throws IOException {
        var changed = false;

        while (true) {
            var type = input.readUnsignedByte();
            if (type == TAG_END) {
                output.writeByte(TAG_END);
                return changed;
            }

            var name = input.readUTF();

            // 附魔组件里的条目形如 <附魔 id> -> <等级>, 不认识的附魔直接丢掉
            if (insideEnchantments && isNumeric(type) && name.indexOf(':') > 0 && !isRegistered.test(name)) {
                skipNumericPayload(type, input);
                dropped.add(name);
                changed = true;
                continue;
            }

            output.writeByte(type);
            output.writeUTF(name);
            changed |= copyPayload(
                    type,
                    input,
                    output,
                    insideEnchantments || ENCHANTMENT_COMPONENT_KEYS.contains(name),
                    isRegistered,
                    dropped);
        }
    }

    private static boolean copyList(
            DataInputStream input,
            DataOutputStream output,
            boolean insideEnchantments,
            Predicate<String> isRegistered,
            List<String> dropped)
            throws IOException {
        var elementType = input.readUnsignedByte();
        var length = input.readInt();
        output.writeByte(elementType);
        output.writeInt(length);

        var changed = false;
        for (var i = 0; i < length; i++) {
            changed |= copyPayload(elementType, input, output, insideEnchantments, isRegistered, dropped);
        }

        return changed;
    }

    private static boolean copyByteArray(DataInputStream input, DataOutputStream output) throws IOException {
        var length = input.readInt();
        output.writeInt(length);
        output.write(input.readNBytes(length));
        return false;
    }

    private static boolean copyIntArray(DataInputStream input, DataOutputStream output) throws IOException {
        var length = input.readInt();
        output.writeInt(length);
        for (var i = 0; i < length; i++) {
            output.writeInt(input.readInt());
        }
        return false;
    }

    private static boolean copyLongArray(DataInputStream input, DataOutputStream output) throws IOException {
        var length = input.readInt();
        output.writeInt(length);
        for (var i = 0; i < length; i++) {
            output.writeLong(input.readLong());
        }
        return false;
    }

    private static void skipNumericPayload(int type, DataInputStream input) throws IOException {
        switch (type) {
            case TAG_BYTE -> input.readByte();
            case TAG_SHORT -> input.readShort();
            case TAG_INT -> input.readInt();
            case TAG_LONG -> input.readLong();
            case TAG_FLOAT -> input.readFloat();
            case TAG_DOUBLE -> input.readDouble();
            default -> throw new IOException("Unexpected numeric NBT tag type: " + type);
        }
    }

    private static boolean isNumeric(int type) {
        return type >= TAG_BYTE && type <= TAG_DOUBLE;
    }
}
