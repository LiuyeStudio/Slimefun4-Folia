package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.LocationUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Regression tests for the deletion tombstone of {@link SlimefunChunkData}.
 * <p>
 * Removing a block only leaves an {@link SlimefunChunkData#INVALID_BLOCK_DATA} tombstone behind and
 * queues the actual database delete asynchronously. The chunk's data-loaded flag used to clear all
 * tombstones, so a forced re-read could reload a machine that had already been broken (a so called
 * ghost machine, which in the worst case duplicated its inventory).
 */
class SlimefunChunkDataTombstoneTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void markingDataAsLoadedKeepsTombstones() {
        World world = server.addSimpleWorld("world");
        var chunkData = new SlimefunChunkData(world, 0, 0);
        Location location = new Location(world, 1, 2, 3);
        var key = LocationUtils.getLocKey(location);

        var blockData = new SlimefunBlockData(location, "TEST_ITEM");
        chunkData.addBlockCacheInternal(blockData, false);
        assertSame(blockData, chunkData.getBlockCacheInternal(key));

        // Tombstone the location directly: this is what happens while the queued delete runs
        chunkData.removeBlockDataCacheInternal(key);
        assertTrue(chunkData.hasBlockCache(key), "The tombstone must be present after removal");
        assertNull(chunkData.getBlockCacheInternal(key));

        // Re-reading the chunk data must not resurrect a location that is still being deleted
        chunkData.setIsDataLoaded(true);

        assertTrue(chunkData.hasBlockCache(key), "Loading chunk data must not discard the tombstone");
        assertNull(chunkData.getBlockCacheInternal(key));
    }

    @Test
    void tombstoneCanBeClearedOnceTheDatabaseConfirmedTheRowIsGone() {
        World world = server.addSimpleWorld("world");
        var chunkData = new SlimefunChunkData(world, 0, 0);
        Location location = new Location(world, 4, 5, 6);
        var key = LocationUtils.getLocKey(location);

        chunkData.addBlockCacheInternal(new SlimefunBlockData(location, "TEST_ITEM"), false);
        chunkData.removeBlockDataCacheInternal(key);
        assertNull(chunkData.getBlockCacheInternal(key));

        chunkData.clearTombstone(key);

        // The tombstone is gone, so a later (valid) record may occupy the location again
        chunkData.addBlockCacheInternal(new SlimefunBlockData(location, "TEST_ITEM"), false);
        assertNotNull(chunkData.getBlockCacheInternal(key));
    }
}
