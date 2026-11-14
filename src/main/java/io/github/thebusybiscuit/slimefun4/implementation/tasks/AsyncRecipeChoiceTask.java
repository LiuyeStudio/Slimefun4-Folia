package io.github.thebusybiscuit.slimefun4.implementation.tasks;

import io.github.bakedlibs.dough.collections.LoopIterator;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.guide.SurvivalSlimefunGuide;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nonnull;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;

/**
 * A {@link AsyncRecipeChoiceTask} is an asynchronously repeating task that cycles
 * through the different variants of {@link Material} that a {@link MaterialChoice} or {@link Tag} can represent.
 *
 * It is used in the {@link SurvivalSlimefunGuide} for any {@link ItemStack} from Minecraft
 * that accepts more than one {@link Material} in its {@link Recipe}.
 *
 * @author TheBusyBiscuit
 *
 */
public class AsyncRecipeChoiceTask implements Runnable {

    private static final int UPDATE_INTERVAL = 14;

    private final Map<Integer, LoopIterator<Material>> iterators = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile Inventory inventory;
    private volatile ScheduledTask task;

    /**
     * This will start this task for the given {@link Inventory}.
     *
     * @param inv
     *            The {@link Inventory} to start this task for
     */
    public void start(@Nonnull Inventory inv) {
        Validate.notNull(inv, "Inventory must not be null");

        // Cancel a previous run first, otherwise the old task keeps updating the inventory
        ScheduledTask previous = task;
        if (previous != null) {
            previous.cancel();
        }

        inventory = inv;
        task = Bukkit.getAsyncScheduler()
                .runAtFixedRate(
                        Slimefun.instance(),
                        scheduledTask -> this.run(),
                        0,
                        UPDATE_INTERVAL * 50L,
                        TimeUnit.MILLISECONDS);
    }

    public void add(int slot, @Nonnull MaterialChoice choice) {
        Validate.notNull(choice, "Cannot add a null RecipeChoice");

        lock.writeLock().lock();

        try {
            iterators.put(slot, new LoopIterator<>(choice.getChoices()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void add(int slot, @Nonnull Tag<Material> tag) {
        Validate.notNull(tag, "Cannot add a null Tag");

        lock.writeLock().lock();

        try {
            iterators.put(slot, new LoopIterator<>(tag.getValues()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * This method checks if there are any slots that need to be updated.
     *
     * @return Whether this task has nothing to do
     */
    public boolean isEmpty() {
        lock.readLock().lock();

        try {
            return iterators.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * This method clears the {@link AsyncRecipeChoiceTask} and removes all active
     * iterators.
     */
    public void clear() {
        lock.writeLock().lock();

        try {
            iterators.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void run() {
        // Terminate the task when noone is viewing the Inventory
        Inventory inv = inventory;
        List<HumanEntity> viewers = new ArrayList<>(inv.getViewers());

        if (viewers.isEmpty()) {
            ScheduledTask currentTask = task;

            if (currentTask != null) {
                currentTask.cancel();
            }

            return;
        }

        Map<Integer, ItemStack> updates = new LinkedHashMap<>();
        lock.readLock().lock();

        try {
            for (Map.Entry<Integer, LoopIterator<Material>> entry : iterators.entrySet()) {
                updates.put(entry.getKey(), new ItemStack(entry.getValue().next()));
            }
        } finally {
            lock.readLock().unlock();
        }

        if (updates.isEmpty()) {
            return;
        }

        /*
         * Folia: 这是一个正在展示给玩家的界面, setItem 属于实体/容器操作, 只能在拥有该玩家的
         * 区域线程上执行。本任务的 run() 在 AsyncScheduler 上执行, 以前直接在这里 setItem
         * 会与点击事件所在的区域线程并发修改同一个容器。
         *
         * 该 Inventory 被所有查看者共享, 因此只投递到第一个查看者所在区域线程执行一次,
         * 然后由客户端通过容器更新自动刷新。
         */
        if (!(viewers.get(0) instanceof Entity entity)) {
            return;
        }

        Slimefun.runAtEntity(entity, () -> {
            for (Map.Entry<Integer, ItemStack> entry : updates.entrySet()) {
                inv.setItem(entry.getKey(), entry.getValue());
            }
        });
    }
}
