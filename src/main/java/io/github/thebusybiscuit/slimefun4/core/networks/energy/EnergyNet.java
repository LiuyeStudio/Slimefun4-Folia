package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.ErrorReport;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.network.Network;
import io.github.thebusybiscuit.slimefun4.api.network.NetworkComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * The {@link EnergyNet} is an implementation of {@link Network} that deals with
 * electrical energy being sent from and to nodes.
 *
 * @author meiamsome
 * @author TheBusyBiscuit
 *
 * @see Network
 * @see EnergyNetComponent
 * @see EnergyNetProvider
 * @see EnergyNetComponentType
 *
 */
public class EnergyNet extends Network implements HologramOwner {

    private static final int RANGE = 6;

    // Folia: 能源网络会由调节机所在区域的线程 tick, 而分类变更可能来自其它区域的线程
    private final Map<Location, EnergyNetProvider> generators = new ConcurrentHashMap<>();
    private final Map<Location, EnergyNetComponent> capacitors = new ConcurrentHashMap<>();
    private final Map<Location, EnergyNetComponent> consumers = new ConcurrentHashMap<>();

    /*
     * Folia: 跨区域节点的 runAtRegion 会延迟到下一个 tick 才执行, 因此能量池不能是
     * tickNetwork 的局部变量 (延迟执行的节点会写入一个已经被丢弃的池, 能量凭空消失)。
     *
     * 但同样不能像早先那样 "把池放在实例上, 过几个 tick 再去读它": 那时池里装的已经是
     * 后续 tick 的能量了。电容于是被反复清零又填满, 看起来永远充不满、也不稳定。
     *
     * 现在的做法是: 池由每个 tick 的结算批次 (EnergyTick) 在它投递出去的节点全部执行完毕、
     * 且用电器都取过电之后再取走; 电容的写回也发生在这一刻。跨区域的节点延迟执行时读到的
     * 仍然是同一个池, 而结算会一直等到它执行完。
     */
    private final AtomicLong pool = new AtomicLong();

    /*
     * 已经被某个尚未结算的 tick "接管" 的电容。
     * 这些电容的电量已经计入 pool (由该次结算负责写回), 因此后续 tick 不能再次计入,
     * 否则就会凭空造电。
     */
    private final Set<Location> capturedCapacitors = ConcurrentHashMap.newKeySet();

    /*
     * 结算已经取走归属权、但写回任务还没执行的电容。
     * Folia: 跨区域的写回任务会延后执行, 在它真正落地之前, 电容里读到的仍然是旧的电量;
     * 这段时间内必须跳过它, 否则同一份电量会被算进池子两次。
     */
    private final Set<Location> settlingCapacitors = ConcurrentHashMap.newKeySet();

    protected EnergyNet(@Nonnull Location l) {
        super(Slimefun.getNetworkManager(), l);
    }

    @Override
    public int getRange() {
        return RANGE;
    }

    /**
     * This creates an immutable {@link Map} of {@link EnergyNetProvider}s within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of generators
     */
    public @Nonnull Map<Location, EnergyNetProvider> getGenerators() {
        return Collections.unmodifiableMap(generators);
    }

    /**
     * This creates an immutable {@link Map} of {@link EnergyNetComponentType#CAPACITOR} {@link EnergyNetComponent}s within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of capacitors
     */
    public @Nonnull Map<Location, EnergyNetComponent> getCapacitors() {
        return Collections.unmodifiableMap(capacitors);
    }

    /**
     * This creates an immutable {@link Map} of {@link EnergyNetComponentType#CONSUMER} {@link EnergyNetComponent}s within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of consumers
     */
    public @Nonnull Map<Location, EnergyNetComponent> getConsumers() {
        return Collections.unmodifiableMap(consumers);
    }

    @Override
    public @Nonnull String getId() {
        return "ENERGY_NETWORK";
    }

    @Override
    public NetworkComponent classifyLocation(@Nonnull Location l) {
        if (regulator.equals(l)) {
            return NetworkComponent.REGULATOR;
        }

        EnergyNetComponent component = getComponent(l);

        if (component == null) {
            return null;
        } else {
            return switch (component.getEnergyComponentType()) {
                case CONNECTOR, CAPACITOR -> NetworkComponent.CONNECTOR;
                case CONSUMER, GENERATOR -> NetworkComponent.TERMINUS;
                default -> null;
            };
        }
    }

    @Override
    public void onClassificationChange(Location l, NetworkComponent from, NetworkComponent to) {
        if (from == NetworkComponent.TERMINUS) {
            generators.remove(l);
            consumers.remove(l);
        }

        EnergyNetComponent component = getComponent(l);

        if (component != null) {
            switch (component.getEnergyComponentType()) {
                case CAPACITOR:
                    capacitors.put(l, component);
                    break;
                case CONSUMER:
                    consumers.put(l, component);
                    break;
                case GENERATOR:
                    if (component instanceof EnergyNetProvider provider) {
                        generators.put(l, provider);
                    } else if (component instanceof SlimefunItem item) {
                        item.warn("This Item is marked as a GENERATOR but does not implement the interface"
                                + " EnergyNetProvider!");
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public void tick(@Nonnull Block b, SlimefunBlockData blockData) {
        AtomicLong timestamp = new AtomicLong(Slimefun.getProfiler().newEntry());
        try {
            if (!regulator.equals(b.getLocation())) {
                updateHologram(b, "&4检测到附近有其他调节器", blockData::isPendingRemove);

                return;
            }

            super.tick();

            if (connectorNodes.isEmpty() && terminusNodes.isEmpty()) {
                updateHologram(b, "&4找不到能源网络", blockData::isPendingRemove);
            } else {
                tickNetwork(blockData, timestamp::getAndAdd);
            }
        } finally {
            // We have subtracted the timings from Generators, so they do not show up twice.
            Slimefun.getProfiler()
                    .closeEntry(b.getLocation(), SlimefunItems.ENERGY_REGULATOR.getItem(), timestamp.get());
        }
    }

    /**
     * Folia: 能源网络的节点可能分散在多个区域中。
     * <p>
     * 旧实现把整个网络放在调节机所在区域的线程上遍历, 于是只有同区域的节点能工作,
     * 其它区域的发电机 (例如太阳能) 会在读取自己方块时抛出线程检查异常并被移出网络。
     * <p>
     * 现在改为「每个节点投递到它自己的区域」, 并通过一个原子能量池在区域之间传递能量:
     * 发电机在自己的区域把电量放进池子, 用电器再从池子里原子地取走。
     * <p>
     * 结算时机是关键: 投递出去的任务可能晚一两个 tick 才执行, 所以不能在固定的延迟之后
     * 去读池子 —— 那时池里装的已经是后续 tick 的能量了。这里由 {@link EnergyTick} 统计
     * 本 tick 投递出去的全部任务, 最后一个任务执行完才结算, 此时池里恰好是本 tick 的能量。
     */
    private void tickNetwork(@Nonnull SlimefunBlockData blockData, @Nonnull LongConsumer timings) {
        EnergyTick tick = new EnergyTick(blockData);

        /*
         * 兜底: 若某个跨区域任务因为区块卸载等原因永远不执行, 上面的计数就到不了零,
         * 电容会一直被标记为已接管而无法再供电。这里先挂一个延迟结算,
         * 正常情况下它只会被 settle() 里的 CAS 挡掉。
         */
        Slimefun.runSync(tick::settle, 3L);

        /*
         * 阶段一: 所有发电机在自己所在区域产出能量。
         * 同一区域的节点会就地执行 (runAtRegion 在已拥有区域时直接运行), 行为与旧版一致。
         */
        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            Location loc = entry.getKey();
            EnergyNetProvider provider = entry.getValue();
            tick.expect();

            Slimefun.runAtRegion(loc, () -> {
                try {
                    long produced = tickSingleGenerator(loc, provider, timings);
                    if (produced > 0) {
                        tick.generated.addAndGet(produced);
                        pool.addAndGet(produced);
                    }
                } finally {
                    tick.finished();
                }
            });
        }

        /*
         * 阶段二: 电容既作为供电源, 也用于存放剩余能量。
         * 这里只把电容里的电量并入池子, 不再先把电容清零:
         * 清零会让玩家每 tick 都看到一次空电容, 而且一旦结算晚到, 上一 tick 的电量就丢了。
         * 电量真正的写回发生在结算阶段, 未结算的电容也绝不会被重复计入 (见 capturedCapacitors)。
         */
        for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
            Location loc = entry.getKey();
            EnergyNetComponent component = entry.getValue();
            tick.expect();

            Slimefun.runAtRegion(loc, () -> {
                try {
                    var data = StorageCacheUtils.getDataContainer(loc);
                    if (data == null || data.isPendingRemove() || !data.isDataLoaded()) {
                        return;
                    }

                    long charge = component.getChargeLong(loc);

                    // 上一次结算还没写回的电容, 它的电量已经在池子里了, 不能再算一次
                    if (charge > 0 && !settlingCapacitors.contains(loc) && capturedCapacitors.add(loc)) {
                        pool.addAndGet(charge);
                    }
                } finally {
                    tick.finished();
                }
            });
        }

        /*
         * 阶段三: 用电器从池子里原子地取电。
         * 用电器可能晚于发电机执行, 但池子在结算之前不会清零, 所以晚到的用电器仍能拿到电。
         */
        for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet()) {
            Location loc = entry.getKey();
            EnergyNetComponent component = entry.getValue();
            tick.expect();

            Slimefun.runAtRegion(loc, () -> {
                try {
                    tickSingleConsumer(loc, component, pool, tick.demand);
                } finally {
                    tick.finished();
                }
            });
        }

        /*
         * 阶段四: 结算。同区域网络在这里立即结算; 跨区域网络会等到最后一个延迟任务
         * 执行完由该任务触发结算, 因此不会出现「池子还没被填满就被清零」的情况。
         */
        tick.finished();
    }

    /**
     * Folia: 该方法必须在 {@code loc} 所在区域线程上执行。
     *
     * @return 该发电机本 tick 产出的能量
     */
    private long tickSingleGenerator(
            @Nonnull Location loc, @Nonnull EnergyNetProvider provider, @Nonnull LongConsumer timings) {
        if (!Bukkit.isOwnedByCurrentRegion(loc)) {
            return 0;
        }

        long timestamp = Slimefun.getProfiler().newEntry();
        long produced = 0;

        try {
            var data = StorageCacheUtils.getDataContainer(loc);
            if (data == null || data.isPendingRemove()) {
                return 0;
            }

            var actualProvider = provider;

            if (!((SlimefunItem) actualProvider).getId().equals(data.getSfId())) {
                var newItem = SlimefunItem.getById(data.getSfId());
                if (!(newItem instanceof EnergyNetProvider newProvider)) {
                    return 0;
                }
                generators.put(loc, newProvider);
                actualProvider = newProvider;
            }

            if (!data.isDataLoaded()) {
                StorageCacheUtils.requestLoad(data);
                return 0;
            }

            produced = actualProvider.getGeneratedOutputLong(loc, data);

            if (actualProvider.isChargeable()) {
                produced = NumberUtils.flowSafeAddition(produced, actualProvider.getChargeLong(loc));
                actualProvider.setCharge(loc, 0L);
            }

            if (actualProvider.willExplode(loc, data)) {
                generators.remove(loc);
                Slimefun.getDatabaseManager().getBlockDataController().removeBlock(loc);
                loc.getBlock().setType(Material.LAVA);
                loc.getWorld().createExplosion(loc, 0F, false);
                produced = 0;
            }
        } catch (Exception | LinkageError throwable) {
            generators.remove(loc);
            new ErrorReport<>(throwable, loc, (SlimefunItem) provider);
            produced = 0;
        } finally {
            timings.accept(Slimefun.getProfiler().closeEntry(loc, (SlimefunItem) provider, timestamp));
        }

        return produced;
    }

    /**
     * Folia: 该方法必须在 {@code loc} 所在区域线程上执行。
     */
    private void tickSingleConsumer(
            @Nonnull Location loc,
            @Nonnull EnergyNetComponent component,
            @Nonnull AtomicLong pool,
            @Nonnull AtomicLong demand) {
        if (!Bukkit.isOwnedByCurrentRegion(loc)) {
            return;
        }

        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null || data.isPendingRemove()) {
            return;
        }

        var actualComponent = component;

        if (!((SlimefunItem) actualComponent).getId().equals(data.getSfId())) {
            var newItem = SlimefunItem.getById(data.getSfId());
            if (!(newItem instanceof EnergyNetComponent newComponent)
                    || newComponent.getEnergyComponentType() != EnergyNetComponentType.CONSUMER) {
                return;
            }
            consumers.put(loc, newComponent);
            actualComponent = newComponent;
        }

        if (!data.isDataLoaded()) {
            StorageCacheUtils.requestLoad(data);
            return;
        }

        long capacity = actualComponent.getCapacityLong();
        long charge = actualComponent.getChargeLong(loc);

        if (charge >= capacity) {
            return;
        }

        long availableSpace = capacity - charge;
        demand.addAndGet(availableSpace);

        long gained = takeFromPool(pool, availableSpace);

        if (gained > 0) {
            actualComponent.setCharge(loc, NumberUtils.flowSafeAddition(charge, gained));
        }
    }

    /**
     * 以 CAS 循环从能量池中取走至多 {@code max} 点能量, 保证跨区域并发时既不丢电也不凭空造电。
     */
    private long takeFromPool(@Nonnull AtomicLong pool, long max) {
        while (true) {
            long current = pool.get();
            if (current <= 0) {
                return 0;
            }
            long taken = Math.min(current, max);
            if (pool.compareAndSet(current, current - taken)) {
                return taken;
            }
        }
    }

    private void storeRemainingEnergy() {
        AtomicLong remaining = new AtomicLong(pool.getAndSet(0));

        /*
         * 归属权必须在投递任务之前同步确定:
         * 某个电容如果在本 tick 把自己的电量并入了池子, 那么只有这一次结算有权改写它
         * (它的新电量 = 它最终分到的电量, 可能为 0)。
         * 没有归属权的电容只做「加法充能」, 绝不覆盖它现有的电量 —— 这正是逐步充满的来源。
         */
        Set<Location> claimed = new HashSet<>();

        for (Location loc : capturedCapacitors) {
            if (capturedCapacitors.remove(loc)) {
                // 电容可能已经被拆掉了, 那就不需要再写回, 也不要让它停留在"结算中"
                if (!capacitors.containsKey(loc)) {
                    continue;
                }

                claimed.add(loc);

                // 写回落地之前, 后续 tick 不能再把它还没更新的电量算进池子
                settlingCapacitors.add(loc);
            }
        }

        /*
         * Folia: 每个 runAtRegion 都是异步投递的, 因此不能给每个节点各发一份剩余电量 -
         * 那样 remaining.get() 会在区域任务真正执行之前就读到未扣减的原始值, 于是每个节点都能
         * 拿走完整的剩余电量 (能量凭空复制)。
         * 这里让所有节点共用同一个 AtomicLong, takeFromPool 的 CAS 循环保证每一焦耳只会
         * 被一个节点取走。
         */
        for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
            Location loc = entry.getKey();
            EnergyNetComponent component = entry.getValue();
            boolean owner = claimed.contains(loc);

            // 没有归属权且池子已空, 投递了也没有意义
            if (!owner && remaining.get() <= 0) {
                continue;
            }

            Slimefun.runAtRegion(loc, () -> {
                try {
                    var data = StorageCacheUtils.getDataContainer(loc);
                    if (data == null || data.isPendingRemove() || !data.isDataLoaded()) {
                        return;
                    }

                    long capacity = component.getCapacityLong();
                    if (capacity <= 0) {
                        return;
                    }

                    if (owner) {
                        // 这个电容的电量已经计入池子, 因此用结算结果覆盖它
                        component.setCharge(loc, takeFromPool(remaining, capacity));
                        return;
                    }

                    long charge = component.getChargeLong(loc);
                    long space = capacity - charge;

                    if (space > 0) {
                        long gained = takeFromPool(remaining, space);
                        if (gained > 0) {
                            component.setCharge(loc, NumberUtils.flowSafeAddition(charge, gained));
                        }
                    }
                } finally {
                    if (owner) {
                        // 无论写回是否成功, 都不能让这个电容永远停留在"结算中"
                        settlingCapacitors.remove(loc);
                    }
                }
            });
        }

        // 还没存完的部分还给发电机 (它们也可以为自己充电)
        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            Location loc = entry.getKey();
            EnergyNetProvider component = entry.getValue();

            Slimefun.runAtRegion(loc, () -> {
                if (remaining.get() <= 0) {
                    return;
                }

                var data = StorageCacheUtils.getDataContainer(loc);
                if (data == null || data.isPendingRemove() || !data.isDataLoaded()) {
                    return;
                }

                long capacity = component.getCapacityLong();
                long charge = component.getChargeLong(loc);
                long space = capacity - charge;

                if (space > 0) {
                    long gained = takeFromPool(remaining, space);
                    if (gained > 0) {
                        component.setCharge(loc, NumberUtils.flowSafeAddition(charge, gained));
                    }
                }
            });
        }
    }

    /**
     * 一个 tick 的能量结算批次。
     * <p>
     * Folia: 投递到其它区域的任务会被推迟执行, 因此无法用固定的延迟来结算。
     * 每个 tick 创建一份, 由该 tick 投递出去的所有任务共享: 最后一个任务执行完时结算,
     * 此时池里恰好是本 tick 的全部能量。
     */
    private final class EnergyTick {

        private final SlimefunBlockData regulatorData;
        private final AtomicLong generated = new AtomicLong();
        private final AtomicLong demand = new AtomicLong();

        // 初始值 1 代表 tickNetwork 自身, 它投递完所有任务后也会 finished()
        private final AtomicInteger pending = new AtomicInteger(1);
        private final AtomicBoolean settled = new AtomicBoolean();

        private EnergyTick(@Nonnull SlimefunBlockData regulatorData) {
            this.regulatorData = regulatorData;
        }

        private void expect() {
            pending.incrementAndGet();
        }

        private void finished() {
            if (pending.decrementAndGet() == 0) {
                settle();
            }
        }

        /**
         * 结算本 tick 的剩余电量并刷新 hologram。重复调用是安全的。
         */
        private void settle() {
            if (!settled.compareAndSet(false, true)) {
                return;
            }

            storeRemainingEnergy();

            /*
             * hologram 展示的是「本 tick 的供电量与需求量」。
             * 对跨区域网络, 结算发生在其最后一个节点执行完之后, 因此展示的同样是本 tick 的数值。
             */
            updateHologram(regulatorData, generated.get(), demand.get());
        }
    }

    private void updateHologram(@Nonnull SlimefunBlockData data, double supply, double demand) {
        if (demand > supply) {
            String netLoss = NumberUtils.getCompactDouble(demand - supply);
            updateHologram(
                    data.getLocation().getBlock(), "&4&l- &c" + netLoss + " &7J &e\u26A1", data::isPendingRemove);
        } else {
            String netGain = NumberUtils.getCompactDouble(supply - demand);
            updateHologram(
                    data.getLocation().getBlock(), "&2&l+ &a" + netGain + " &7J &e\u26A1", data::isPendingRemove);
        }
    }

    @Nullable private static EnergyNetComponent getComponent(@Nonnull Location l) {
        SlimefunItem item = StorageCacheUtils.getSlimefunItem(l);

        if (item instanceof EnergyNetComponent component) {
            return component;
        }

        return null;
    }

    /**
     * This attempts to get an {@link EnergyNet} from a given {@link Location}.
     * If no suitable {@link EnergyNet} could be found, {@code null} will be returned.
     *
     * @param l
     *            The target {@link Location}
     *
     * @return The {@link EnergyNet} at that {@link Location}, or {@code null}
     */
    @Nullable public static EnergyNet getNetworkFromLocation(@Nonnull Location l) {
        return Slimefun.getNetworkManager()
                .getNetworkFromLocation(l, EnergyNet.class)
                .orElse(null);
    }

    /**
     * This attempts to get an {@link EnergyNet} from a given {@link Location}.
     * If no suitable {@link EnergyNet} could be found, a new one will be created.
     *
     * @param l
     *            The target {@link Location}
     *
     * @return The {@link EnergyNet} at that {@link Location}, or a new one
     */
    @Nonnull
    public static EnergyNet getNetworkFromLocationOrCreate(@Nonnull Location l) {
        return Slimefun.getNetworkManager().getNetworkFromLocationOrCreate(l, EnergyNet.class, EnergyNet::new);
    }
}
