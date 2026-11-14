package city.norain.slimefun4.timings;

import city.norain.slimefun4.timings.entry.TimingEntry;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;

public class SlowSqlCheckTask implements Runnable {
    private final Supplier<Map<TimingEntry, Long>> statusChecker;

    public SlowSqlCheckTask(Supplier<Map<TimingEntry, Long>> statusChecker) {
        this.statusChecker = statusChecker;
    }

    @Override
    public void run() {
        for (Map.Entry<TimingEntry, Long> mapEntry : statusChecker.get().entrySet()) {
            var entry = mapEntry.getKey();
            var startTime = mapEntry.getValue();

            // 记录用的是 System.nanoTime(), 这里必须用同一种时钟, 否则差值没有意义
            long elapsedTime = (System.nanoTime() - startTime) / 1_000_000L;

            if (elapsedTime > 5000) {
                Slimefun.logger().log(Level.WARNING, "检测到慢 SQL: {0}", entry.normalize());
                break;
            }
        }
    }
}
