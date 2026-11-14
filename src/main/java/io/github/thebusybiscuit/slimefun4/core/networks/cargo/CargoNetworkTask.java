package io.github.thebusybiscuit.slimefun4.core.networks.cargo;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSpawnReason;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import io.github.thebusybiscuit.slimefun4.core.networks.NetworkManager;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.ItemStackWrapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link CargoNetworkTask} is the actual {@link Runnable} responsible for moving {@link ItemStack ItemStacks}
 * around the {@link CargoNet}.
 *
 * Inbefore this was just a method in the {@link CargoNet} class.
 * However for aesthetic reasons but mainly to prevent the Cargo Task from showing up as
 * "lambda:xyz-123" in timing reports... this was moved.
 *
 * @see CargoNet
 * @see CargoUtils
 * @see AbstractItemNetwork
 *
 */
class CargoNetworkTask implements Runnable {

    private final NetworkManager manager;
    private final CargoNet network;

    // Folia: 每个输入节点会在自己的区域线程上执行, 该缓存会被多个线程共享
    private final Map<Location, Inventory> inventories = new java.util.concurrent.ConcurrentHashMap<>();

    private final Map<Location, Integer> inputs;
    private final Map<Integer, List<Location>> outputs;

    @ParametersAreNonnullByDefault
    CargoNetworkTask(CargoNet network, Map<Location, Integer> inputs, Map<Integer, List<Location>> outputs) {
        this.network = network;
        this.manager = Slimefun.getNetworkManager();

        this.inputs = inputs;
        this.outputs = outputs;
    }

    @Override
    public void run() {
        long timestamp = System.nanoTime();

        /*
         * Folia: 货运网络的输入节点可能分散在多个区域中。
         * 以前整个网络都在调节机所在区域的线程上运行, 于是只有调节机所在区域里的节点会工作,
         * 其它区域的节点会被 getAttachedBlock() 的归属检查直接跳过 (既不搬运也不报错)。
         *
         * 现在改为「每个输入节点投递到它自己的区域」:
         * - 抽取物品、读取过滤器、容器写入都发生在该节点自己的区域线程上;
         * - 输出节点若位于别的区域, getAttachedBlock() 会返回 empty 从而跳过,
         *   物品会被放回源容器, 不会丢失也不会复制。
         */
        SlimefunItem inputNodeItem = SlimefunItems.CARGO_INPUT_NODE.getItem();

        for (Map.Entry<Location, Integer> entry : inputs.entrySet()) {
            Location input = entry.getKey();
            int frequency = entry.getValue();
            long nodeTimestamp = System.nanoTime();

            Slimefun.runAtRegion(input, () -> {
                try {
                    if (network.getRegulator().getWorld() == null) {
                        return;
                    }
                    Optional<Block> attachedBlock = network.getAttachedBlock(input);
                    attachedBlock.ifPresent(block -> routeItems(input, block, frequency, outputs));
                } catch (Exception | LinkageError x) {
                    Slimefun.logger()
                            .log(
                                    Level.SEVERE,
                                    x,
                                    () -> "An Exception was caught while ticking a Cargo node @ "
                                            + new BlockPosition(input));
                } finally {
                    // This will prevent this timings from showing up for the Cargo Manager
                    Slimefun.getProfiler().closeEntry(input, inputNodeItem, nodeTimestamp);
                }
            });
        }

        // Submit a timings report
        Slimefun.getProfiler().closeEntry(network.getRegulator(), SlimefunItems.CARGO_MANAGER.getItem(), timestamp);
    }

    @ParametersAreNonnullByDefault
    private void routeItems(
            Location inputNode, Block inputTarget, int frequency, Map<Integer, List<Location>> outputNodes) {
        ItemStackAndInteger slot = CargoUtils.withdraw(network, inventories, inputNode.getBlock(), inputTarget);

        if (slot == null) {
            return;
        }

        ItemStack stack = slot.getItem();
        int previousSlot = slot.getInt();
        List<Location> destinations = outputNodes.get(frequency);

        if (destinations != null) {
            stack = distributeItem(stack, inputNode, destinations);
        }

        if (stack != null) {
            insertItem(inputTarget, previousSlot, stack);
        }
    }

    @ParametersAreNonnullByDefault
    private void insertItem(Block inputTarget, int previousSlot, ItemStack item) {
        Inventory inv = inventories.get(inputTarget.getLocation());

        if (inv != null) {
            ItemStack rest;

            // Check if the original slot hasn't been occupied in the meantime
            if (inv.getItem(previousSlot) == null) {
                rest = Slimefun.getItemStackService().addItem(inv, item, InventoryContext.CARGO_INSERT, previousSlot);
                if (rest != null) {
                    rest = Slimefun.getItemStackService().addItem(inv, rest, InventoryContext.CARGO_INSERT);
                }
            } else {
                // Try to add the item into another available slot then
                rest = Slimefun.getItemStackService().addItem(inv, item, InventoryContext.CARGO_INSERT);
            }

            if (rest != null && !manager.isItemDeletionEnabled()) {
                // If the item still couldn't be inserted, simply drop it on the ground
                SlimefunUtils.spawnItem(inputTarget.getLocation().add(0, 1, 0), rest, ItemSpawnReason.CARGO_OVERFLOW);
            }
        } else {
            DirtyChestMenu menu = CargoUtils.getChestMenu(inputTarget);

            if (menu != null) {
                if (menu.getItemInSlot(previousSlot) == null) {
                    menu.replaceExistingItem(previousSlot, item);
                } else if (!manager.isItemDeletionEnabled()) {
                    SlimefunUtils.spawnItem(
                            inputTarget.getLocation().add(0, 1, 0), item, ItemSpawnReason.CARGO_OVERFLOW);
                }
            }
        }
    }

    @Nullable @ParametersAreNonnullByDefault
    private ItemStack distributeItem(ItemStack stack, Location inputNode, List<Location> outputNodes) {
        ItemStack item = stack;

        var blockData = StorageCacheUtils.getBlock(inputNode);
        boolean roundrobin = Objects.equals(blockData.getData("round-robin"), "true");
        boolean smartFill = Objects.equals(blockData.getData("smart-fill"), "true");

        int index = 0;
        Collection<Location> destinations;
        if (roundrobin) {
            // The current round-robin index of the (unsorted) outputNodes list,
            // or the index at which to start searching for valid output nodes
            index = network.roundRobin.getOrDefault(inputNode, 0);
            // Use an ArrayDeque to perform round-robin sorting
            // Since the impl for roundRobinSort just does Deque.addLast(Deque#removeFirst)
            // An ArrayDequeue is preferable as opposed to a LinkedList:
            // - The number of elements does not change.
            // - ArrayDequeue has better iterative performance
            Deque<Location> tempDestinations = new ArrayDeque<>(outputNodes);
            roundRobinSort(index, tempDestinations);
            destinations = tempDestinations;
        } else {
            // Using an ArrayList here since we won't need to sort the destinations
            // The ArrayList has the best performance for iteration bar a primitive array
            destinations = new ArrayList<>(outputNodes);
        }

        for (Location output : destinations) {
            Optional<Block> target = network.getAttachedBlock(output);

            if (target.isPresent()) {
                ItemStackWrapper wrapper = ItemStackWrapper.wrap(item);
                item = CargoUtils.insert(
                        network, inventories, output.getBlock(), target.get(), smartFill, item, wrapper);

                if (item == null) {
                    if (roundrobin) {
                        // The output was valid, set the round robin index to the node after this one
                        network.roundRobin.put(inputNode, (index + 1) % outputNodes.size());
                    }
                    break;
                }
            }
            index++;
        }

        return item;
    }

    /**
     * This method sorts a given {@link Deque} of output node locations using a semi-accurate
     * round-robin method.
     *
     * @param index
     *            The round-robin index of the input node
     * @param outputNodes
     *            A {@link Deque} of {@link Location Locations} of the output nodes
     */
    private void roundRobinSort(int index, Deque<Location> outputNodes) {
        if (index < outputNodes.size()) {
            // Not ideal but actually not bad performance-wise over more elegant alternatives
            for (int i = 0; i < index; i++) {
                Location temp = outputNodes.removeFirst();
                outputNodes.add(temp);
            }
        }
    }
}
