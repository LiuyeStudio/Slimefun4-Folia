package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.LocationUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * {@link SlimefunChunkData} 是 Slimefun 中用于存储区块内所有方块数据的容器类。
 */
public class SlimefunChunkData extends ADataContainer {
    private static final SlimefunBlockData INVALID_BLOCK_DATA = new SlimefunBlockData(
            new Location(Bukkit.getWorlds().get(0), Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE),
            "INVALID_BLOCK_DATA_SF_KEY");
    private final UUID worldId;
    private final int chunkX;
    private final int chunkZ;
    private final Map<String, SlimefunBlockData> sfBlocks;

    @ParametersAreNonnullByDefault
    SlimefunChunkData(Chunk chunk) {
        super(LocationUtils.getChunkKey(chunk));
        worldId = chunk.getWorld().getUID();
        chunkX = chunk.getX();
        chunkZ = chunk.getZ();
        sfBlocks = new ConcurrentHashMap<>();
    }

    /**
     * Folia: 创建一个不持有 {@link Chunk} 句柄的区块数据容器。
     * <p>
     * {@link Chunk} 只能在拥有该区块区域的线程上获取, 而存储层需要在任意线程上按坐标创建缓存,
     * 因此这里只需要 {@link World} 与区块坐标。
     */
    @ParametersAreNonnullByDefault
    SlimefunChunkData(World world, int chunkX, int chunkZ) {
        super(LocationUtils.getChunkKey(world, chunkX, chunkZ));
        worldId = world.getUID();
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        sfBlocks = new ConcurrentHashMap<>();
    }

    /**
     * Resolves this data container's world on demand without retaining it.
     *
     * @return the currently loaded world that owns this container
     * @throws IllegalStateException if the world that owns this container is no longer loaded
     */
    @Nonnull
    public World getWorld() {
        var world = Bukkit.getWorld(worldId);
        if (world == null) {
            throw new IllegalStateException("The world for chunk data " + getKey() + " is no longer loaded");
        }
        return world;
    }

    /**
     * Resolves this data container's chunk on demand. Calling this method may synchronously load the chunk, so it must
     * only be called from the thread that owns this chunk's region.
     *
     * @return the currently loaded world's chunk at this container's coordinates
     * @throws IllegalStateException if the calling thread does not own this chunk's region, or if the owning world is
     *         no longer loaded
     */
    @Nonnull
    public Chunk getChunk() {
        World world = getWorld();

        // Folia: 同步加载区块只能在拥有该区块区域的线程上进行。
        // 没有该区域时也一并拒绝, 以免误触发阻塞式加载。
        if (world.isChunkLoaded(chunkX, chunkZ) && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
            return world.getChunkAt(chunkX, chunkZ);
        }

        throw new IllegalStateException(
                "Chunk data must be resolved from the region thread that owns chunk " + chunkX + ":" + chunkZ);
    }

    @Nonnull
    @ParametersAreNonnullByDefault
    public SlimefunBlockData createBlockData(Location l, String sfId) {
        var lKey = LocationUtils.getLocKey(l);
        if (getBlockCacheInternal(lKey) != null) {
            throw new IllegalStateException("There already a block in this location: " + lKey);
        }
        var re = new SlimefunBlockData(l, sfId);
        re.setIsDataLoaded(true);
        sfBlocks.put(lKey, re);

        var preset = BlockMenuPreset.getPreset(sfId);
        if (preset != null) {
            re.setBlockMenu(new BlockMenu(preset, l));
        }

        Slimefun.getDatabaseManager().getBlockDataController().saveNewBlock(l, sfId);

        return re;
    }

    @Nullable @ParametersAreNonnullByDefault
    public SlimefunBlockData getBlockData(Location l) {
        checkData();
        return getBlockCacheInternal(LocationUtils.getLocKey(l));
    }

    @Nullable @ParametersAreNonnullByDefault
    public SlimefunBlockData removeBlockData(Location l) {
        var lKey = LocationUtils.getLocKey(l);
        var re = removeBlockDataCacheInternal(lKey);
        if (re == null) {
            if (isDataLoaded()) {
                return null;
            }
            sfBlocks.put(lKey, INVALID_BLOCK_DATA);
        }
        Slimefun.getDatabaseManager().getBlockDataController().removeBlockDirectly(l);
        return re;
    }

    void addBlockCacheInternal(SlimefunBlockData data, boolean override) {
        if (override) {
            sfBlocks.put(data.getKey(), data);
        } else {
            sfBlocks.putIfAbsent(data.getKey(), data);
        }
    }

    SlimefunBlockData getBlockCacheInternal(String lKey) {
        var re = sfBlocks.get(lKey);
        return re == INVALID_BLOCK_DATA ? null : re;
    }

    Set<SlimefunBlockData> getAllCacheInternal() {
        var re = new HashSet<>(sfBlocks.values());
        re.removeIf(v -> v == INVALID_BLOCK_DATA);
        return re;
    }

    void removeAllCacheInternal() {
        sfBlocks.clear();
    }

    boolean hasBlockCache(String lKey) {
        return sfBlocks.containsKey(lKey);
    }

    /**
     * Clears the deletion tombstone at the given location.
     * <p>
     * This may only be called once it has been confirmed from the database that the block record
     * does not exist (anymore). Any other caller would reintroduce the ghost-machine problem.
     *
     * @param lKey
     *            The location key of the tombstone to clear
     */
    void clearTombstone(String lKey) {
        sfBlocks.remove(lKey, INVALID_BLOCK_DATA);
    }

    SlimefunBlockData removeBlockDataCacheInternal(String lKey) {
        /*
         * 始终保留 INVALID_BLOCK_DATA 墓碑, 即使数据已经加载。
         * 数据库删除是异步的, 如果这里只是 remove, 紧接着的 loadBlockData 可能又从库里
         * 读到尚未删除的行并重新创建方块数据 —— 也就是“幽灵机器”。
         */
        var re = sfBlocks.put(lKey, INVALID_BLOCK_DATA);
        return re == INVALID_BLOCK_DATA ? null : re;
    }

    @ParametersAreNonnullByDefault
    public void setData(String key, String val) {
        checkData();
        setCacheInternal(key, val, true);
        Slimefun.getDatabaseManager().getBlockDataController().scheduleDelayedChunkDataUpdate(this, key);
    }

    @ParametersAreNonnullByDefault
    public void removeData(String key) {
        if (removeCacheInternal(key) != null) {
            Slimefun.getDatabaseManager().getBlockDataController().scheduleDelayedChunkDataUpdate(this, key);
        }
    }

    public Set<SlimefunBlockData> getAllBlockData() {
        return getAllCacheInternal();
    }

    /*
     * 注意: 这里绝对不能因为 isDataLoaded 变为 true 就清除 INVALID_BLOCK_DATA 墓碑。
     *
     * 墓碑表示“该位置的数据已被删除, 只是删除语句还在异步队列里”。如果重新加载区块数据时
     * 把它清掉, 紧接着的方块记录查询仍可能读到尚未删除的旧行, 从而在原地重新创建一个
     * 幽灵机器 (玩家已经拆掉机器, 数据却又回来了, 可能造成物品复制)。
     * 墓碑的唯一失效途径是从数据库确认该行不存在 —— 见 BlockDataController#loadBlockData。
     */
}
