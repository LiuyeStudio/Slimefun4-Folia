package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 在物品数据反序列化失败时, 把数据库中的原始数据备份到磁盘。
 *
 * <p>读取失败通常不代表数据本身已经损坏, 而是当前服务端读不出来(旧版 NBT 格式、物品来自已卸载的插件、
 * 服务端序列化格式发生变更等)。原先的处理方式是直接把这格背包/机器的槽位当成空气, 一旦该槽位之后被写入
 * 新物品, 或者背包/机器被整体重写, 这份原始数据就永久丢失了。这里在反序列化失败的第一时间把原始数据落盘
 * 保存, 保证它仍然可以被恢复或分析。
 *
 * <p>备份文件保存在 {@code plugins/Slimefun/recovered-items/} 下, 每一份不同的数据只备份一次。记录备份的
 * 日志行会输出真正的错误原因, 紧随其后的报错会给出具体位置(背包 UUID:槽位 或 机器坐标:槽位)。
 */
public final class ItemDataRecovery {

    private static final String FOLDER_NAME = "recovered-items";
    private static final String FILE_PREFIX = "item-";
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final int MAX_TRACKED_DATA = 8192;
    private static final Set<String> SAVED_DATA = ConcurrentHashMap.newKeySet();

    private ItemDataRecovery() {}

    /**
     * 备份一份无法反序列化的物品数据, 并输出真正的错误原因。
     *
     * @param rawData
     *            数据库中保存的原始数据, 可以是 {@code byte[]} 或 {@link String}
     * @param cause
     *            反序列化时抛出的异常
     */
    public static void recover(@Nullable Object rawData, @Nullable Throwable cause) {
        var data = toBytes(rawData);

        if (data.length == 0) {
            log(Level.WARNING, "物品数据反序列化失败, 且数据库中没有可备份的原始数据, 真正的错误: " + describe(cause), null);
            return;
        }

        var digest = sha256(data);

        // 同一份数据只备份一次, 避免每次加载都重复写盘
        if (SAVED_DATA.size() >= MAX_TRACKED_DATA) {
            SAVED_DATA.clear();
        }

        if (!SAVED_DATA.add(digest)) {
            return;
        }

        File file = null;

        try {
            var folder = getBackupFolder();
            if (folder != null) {
                file = writeFile(folder, data, digest, cause);
            }
        } catch (Exception e) {
            log(Level.WARNING, "备份损坏的物品数据时出现错误", e);
        }

        var message = "物品数据反序列化失败, 原始数据(" + data.length + " 字节, sha256=" + digest + ")";
        message += file == null ? ", 无法写入备份文件" : ", 已备份到 " + file.getPath();
        log(Level.WARNING, message + ", 真正的错误: " + describe(cause), null);
    }

    /**
     * 把原始物品数据写入备份文件, 内容包含完整错误链与原始数据的 Base64。
     *
     * @param folder
     *            备份目录
     * @param data
     *            数据库中保存的原始数据
     * @param digest
     *            {@code data} 的摘要, 用于生成不重复的文件名
     * @param cause
     *            反序列化时抛出的异常
     * @return 写入的备份文件
     * @throws IOException
     *             写入失败
     */
    static File writeFile(@Nonnull File folder, @Nonnull byte[] data, @Nonnull String digest, @Nullable Throwable cause)
            throws IOException {
        var fileName = FILE_PREFIX + LocalDateTime.now().format(FILE_TIME_FORMAT) + '-' + shortDigest(digest) + ".txt";
        var file = new File(folder, fileName);

        var content = new StringBuilder();
        content.append("# Slimefun 损坏物品数据备份\n");
        content.append("# 时间: ").append(LocalDateTime.now()).append('\n');
        content.append("# 原始数据长度: ").append(data.length).append(" 字节\n");
        content.append("# sha256: ").append(digest).append('\n');
        content.append("# 真正的错误: ").append(describe(cause)).append('\n');

        if (cause != null) {
            var stackTrace = new StringWriter();
            cause.printStackTrace(new PrintWriter(stackTrace));
            for (var line : stackTrace.toString().split("\n")) {
                content.append("# ").append(line).append('\n');
            }
        }

        content.append("# 说明: DATA 之后的单行 Base64 即数据库中保存的原始物品数据\n");
        content.append("DATA:\n");
        content.append(Base64.getEncoder().encodeToString(data)).append('\n');

        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return file;
    }

    @Nullable private static File getBackupFolder() {
        var plugin = Slimefun.instance();
        if (plugin == null) {
            return null;
        }

        var folder = new File(plugin.getDataFolder(), FOLDER_NAME);
        if (folder.isDirectory() || folder.mkdirs()) {
            return folder;
        }

        return null;
    }

    private static void log(@Nonnull Level level, @Nonnull String message, @Nullable Throwable cause) {
        var plugin = Slimefun.instance();
        if (plugin == null) {
            return;
        }

        if (cause == null) {
            plugin.getLogger().log(level, message);
        } else {
            plugin.getLogger().log(level, message, cause);
        }
    }

    /**
     * 取出错误链最底层的异常, 也就是 Paper 包装之前真正的错误。
     */
    @Nonnull
    private static String describe(@Nullable Throwable cause) {
        if (cause == null) {
            return "未知";
        }

        var root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        var message = root.getMessage();
        return message == null ? root.getClass().getName() : root.getClass().getName() + ": " + message;
    }

    @Nonnull
    private static byte[] toBytes(@Nullable Object rawData) {
        if (rawData instanceof byte[] bytes) {
            return bytes;
        }

        if (rawData instanceof String str) {
            return str.getBytes(StandardCharsets.UTF_8);
        }

        return new byte[0];
    }

    @Nonnull
    private static String sha256(@Nonnull byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(Arrays.hashCode(data));
        }
    }

    @Nonnull
    private static String shortDigest(@Nonnull String digest) {
        return digest.length() <= 8 ? digest : digest.substring(0, 8);
    }
}
