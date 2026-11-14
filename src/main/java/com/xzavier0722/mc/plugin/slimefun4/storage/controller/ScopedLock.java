package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

class ScopedLock {
    private final Map<ScopeKey, ReentrantLock> locks;

    ScopedLock() {
        locks = new ConcurrentHashMap<>();
    }

    void lock(ScopeKey scopeKey) {
        var lock = locks.computeIfAbsent(scopeKey, k -> new ReentrantLock());
        lock.lock();
    }

    void unlock(ScopeKey scopeKey) {
        var lock = locks.get(scopeKey);
        if (lock == null) {
            return;
        }

        lock.unlock();
        /*
         * 注意: 这里不能因为 !isLocked() 就把锁从 map 中移除。
         * 移除后另一个线程的 computeIfAbsent 会创建一把全新的锁, 于是两个线程会同时
         * "持有" 同一个 scope 的锁, 互斥彻底失效 (会导致重复的 BlockData / BlockMenu)。
         * per-scope 锁的数量与 scope 数量同级, 保留它们的内存开销可以忽略。
         */
    }

    boolean hasLock(ScopeKey scopeKey) {
        return locks.containsKey(scopeKey);
    }
}
