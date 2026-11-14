package com.xzavier0722.mc.plugin.slimefun4.storage.migrator;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import lombok.Getter;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class BlockStorageMigrator implements IMigrator {
    @Getter
    private static final BlockStorageMigrator instance = new BlockStorageMigrator();

    private static final File invFolder = new File("data-storage/Slimefun/stored-inventories/");
    private static final File chunk = new File("data-storage/Slimefun/stored-chunks/chunks.sfc");
    private static final File blockFolder = new File("data-storage/Slimefun/stored-blocks/");
    private static final Gson gson = new Gson();
    private static volatile boolean migrateLock = false;

    private BlockStorageMigrator() {}

    @Override
    public String getName() {
        return "BlockStorage";
    }

    @Override
    public boolean hasOldData() {
        return !MigratorUtil.checkMigrateMark() && (hasBlockData() || chunk.exists());
    }

    @Override
    public MigrateStatus migrateData() {
        /*
         * 先检查锁再修改 ticker / 延迟保存状态: 以前是先暂停 ticker 再判断 migrateLock,
         * 并发调用时后到的那次会直接返回, 却把 ticker 永久留在暂停状态。
         */
        if (migrateLock) {
            return MigrateStatus.MIGRATING;
        }

        Slimefun.getTickerTask().setPaused(true);

        var controller = Slimefun.getDatabaseManager().getBlockDataController();
        var isDelayedSavingEnabled = controller.isDelayedSavingEnabled();
        if (isDelayedSavingEnabled) {
            controller.setDelayedSavingEnable(false);
        }

        var status = MigrateStatus.SUCCESS;
        migrateLock = true;

        try {
            if (chunk.isFile()) {
                if (!migrateChunks()) {
                    /*
                     * Folia: 区块数据的写入发生在区块所属区域的线程上, 是异步的。
                     * 只要有一个区块没能写完, 就不能删除旧文件, 否则这部分数据会永久丢失。
                     */
                    Slimefun.logger().log(Level.WARNING, "区块数据未能全部迁移, 已保留旧文件 " + chunk.getName() + " 以便下次重试。");
                    status = MigrateStatus.FAILED;
                } else {
                    try {
                        var chunkBak = Files.createFile(Path.of("data-storage/Slimefun/old_data/chunks.sfc"));
                        Files.copy(chunk.toPath(), chunkBak, StandardCopyOption.REPLACE_EXISTING);
                        Files.delete(chunk.toPath());
                    } catch (Exception e) {
                        Slimefun.logger().log(Level.WARNING, "备份旧数据 " + chunk.getName() + " 时出现问题", e);
                        status = MigrateStatus.FAILED;
                    }
                }
            } else {
                Slimefun.logger().log(Level.WARNING, "未检测到区块数据，跳过迁移。");
            }

            Bukkit.getWorlds().forEach(this::migrateWorld);

            if (MigratorUtil.createDirBackup(invFolder)) {
                MigratorUtil.deleteOldFolder(invFolder);
            }

            if (MigratorUtil.createDirBackup(blockFolder)) {
                MigratorUtil.deleteOldFolder(blockFolder);
            }
        } finally {
            migrateLock = false;

            if (isDelayedSavingEnabled) {
                controller.setDelayedSavingEnable(true);
            }
            Slimefun.getTickerTask().setPaused(false);
        }

        return status;
    }

    private boolean hasBlockData() {
        for (var world : Bukkit.getWorlds()) {
            var f = new File(blockFolder, world.getName());
            var fList = f.listFiles();
            if (fList != null && fList.length > 0) {
                return true;
            }
        }
        return false;
    }

    private void migrateWorld(World w) {
        Slimefun.logger().log(Level.INFO, "开始迁移方块数据: " + w.getName());
        var fList = new File(blockFolder, w.getName()).listFiles();
        if (fList == null) {
            return;
        }

        var count = 0;
        var total = fList.length;
        for (var f : fList) {
            var id = f.getName();
            id = id.substring(0, id.length() - 4);
            Slimefun.logger().log(Level.INFO, "正在迁移方块数据: " + id + "(" + ++count + "/" + total + ")");

            if (SlimefunItem.getById(id) == null) {
                Slimefun.logger().log(Level.WARNING, "检测到不存在的方块 ID (" + id + "), 已跳过迁移.");
                continue;
            }

            var cfg = new Config(f);
            for (var key : cfg.getKeys()) {
                migrateBlock(w, id, key, cfg.getString(key));
            }
        }
    }

    private void migrateBlock(World world, String sfId, String locStr, String jsonStr) {
        try {
            var arr = locStr.split(";");
            var x = Integer.parseInt(arr[1]);
            var y = Integer.parseInt(arr[2]);
            var z = Integer.parseInt(arr[3]);

            var loc = new Location(world, x, y, z);
            var sfData = Slimefun.getDatabaseManager().getBlockDataController().createBlock(loc, sfId);
            Map<String, String> data = gson.fromJson(jsonStr, new TypeToken<Map<String, String>>() {}.getType());
            for (var each : data.entrySet()) {
                var key = each.getKey();
                if ("id".equals(key)) {
                    continue;
                }
                sfData.setData(key, each.getValue());
            }

            DirtyChestMenu menu = sfData.getBlockMenu();

            if (menu != null) {
                var f = new File(invFolder, world.getName() + ";" + x + ";" + y + ";" + z + ".sfi");
                if (!f.isFile()) {
                    return;
                }
                migrateInv(menu, f);
            }
        } catch (Throwable e) {
            Slimefun.logger().log(Level.SEVERE, "迁移方块时发生错误: " + locStr, e);
        }
    }

    private void migrateInv(DirtyChestMenu menu, File f) {
        var cfg = new Config(f);
        var preset = menu.getPreset().getPresetSlots();
        for (var key : cfg.getKeys()) {
            if ("preset".equals(key)) {
                continue;
            }

            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (Throwable e) {
                continue;
            }

            if (preset.contains(slot)) {
                continue;
            }

            var item = cfg.getItem(key);
            if (item == null) {
                continue;
            }

            menu.replaceExistingItem(slot, item);
        }
    }

    /**
     * 迁移所有区块数据, 并等待写入真正完成。
     *
     * @return 是否全部迁移成功
     */
    private boolean migrateChunks() {
        var cfg = new Config(chunk);
        var keys = cfg.getKeys();
        var total = keys.size();
        var count = 0;
        var futures = new ArrayList<CompletableFuture<Void>>();

        for (var key : keys) {
            Slimefun.logger().log(Level.INFO, "正在迁移区块数据: " + ++count + "/" + total);
            var arr = key.split(";");
            try {
                var w = Bukkit.getWorld(arr[0]);
                if (w == null) {
                    Slimefun.logger().log(Level.WARNING, "区块所在世界未加载，忽略: " + arr[0]);
                    continue;
                }

                int chunkX = Integer.parseInt(arr[2]);
                int chunkZ = Integer.parseInt(arr[3]);
                Map<String, String> data =
                        gson.fromJson(cfg.getString(key), new TypeToken<Map<String, String>>() {}.getType());

                /*
                 * Folia: 同步加载区块只能在拥有该区块区域的线程上进行。
                 * 这里先异步加载区块, 回调会在其归属区域的线程上执行, 然后再写入区块数据。
                 */
                futures.add(w.getChunkAtAsync(chunkX, chunkZ, true).thenAccept(c -> {
                    if (c == null) {
                        return;
                    }
                    try {
                        var chunkData = Slimefun.getDatabaseManager()
                                .getBlockDataController()
                                .getChunkData(c);
                        data.forEach(chunkData::setData);
                    } catch (Throwable e) {
                        Slimefun.logger().log(Level.SEVERE, "迁移区块数据时发生错误: " + key, e);
                    }
                }));
            } catch (Throwable e) {
                Slimefun.logger().log(Level.SEVERE, "迁移区块数据时发生错误: " + key, e);
            }
        }

        /*
         * 必须等所有区块都写完才能让调用方删除旧文件: 以前这里是"发射后不管",
         * 迁移命令返回成功后紧接着就删掉了 chunks.sfc, 尚未写入的区块数据会永久丢失。
         */
        var success = true;

        for (var future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Throwable e) {
                success = false;
                Slimefun.logger().log(Level.SEVERE, "等待区块数据写入完成时超时或出错", e);
            }
        }

        return success;
    }
}
