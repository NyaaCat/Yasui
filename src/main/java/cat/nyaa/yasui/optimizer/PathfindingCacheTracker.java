package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import cat.nyaa.yasui.nms.PathfindingNmsHook;
import org.bukkit.scheduler.BukkitTask;

/**
 * Rolling stats tracker for pathfinding cache.
 */
public class PathfindingCacheTracker {
    private static final long ROLLING_BUCKET_MS = 60 * 1000L;

    private final Yasui plugin;
    private final YasuiConfig config;
    private final long[] rollingHits = new long[60];
    private final long[] rollingMisses = new long[60];
    private final long[] rollingStores = new long[60];
    private int rollingIndex = 0;
    private long rollingBucketStart = alignToMinute(System.currentTimeMillis());
    private BukkitTask statsTask;

    public PathfindingCacheTracker(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public record RollingStats(long hits, long misses, long stores) {}

    public void start() {
        statsTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushStats, 20L, 20L);
    }

    public void shutdown() {
        if (statsTask != null) {
            statsTask.cancel();
        }
        clearRollingBuckets();
    }

    public RollingStats getRollingStats() {
        flushStats();
        updateRollingBuckets(System.currentTimeMillis());
        return new RollingStats(sumRolling(rollingHits), sumRolling(rollingMisses), sumRolling(rollingStores));
    }

    private void flushStats() {
        if (!config.isPathfindingCacheEnabled()) {
            return;
        }
        updateRollingBuckets(System.currentTimeMillis());
        long[] stats = PathfindingNmsHook.drainStats();
        rollingHits[rollingIndex] += stats[0];
        rollingMisses[rollingIndex] += stats[1];
        rollingStores[rollingIndex] += stats[2];
    }

    private void updateRollingBuckets(long now) {
        long elapsed = now - rollingBucketStart;
        if (elapsed < ROLLING_BUCKET_MS) {
            return;
        }
        int steps = (int) Math.min(rollingHits.length, elapsed / ROLLING_BUCKET_MS);
        for (int i = 0; i < steps; i++) {
            rollingIndex = (rollingIndex + 1) % rollingHits.length;
            rollingHits[rollingIndex] = 0;
            rollingMisses[rollingIndex] = 0;
            rollingStores[rollingIndex] = 0;
        }
        rollingBucketStart += (long) steps * ROLLING_BUCKET_MS;
    }

    private void clearRollingBuckets() {
        for (int i = 0; i < rollingHits.length; i++) {
            rollingHits[i] = 0;
            rollingMisses[i] = 0;
            rollingStores[i] = 0;
        }
        rollingIndex = 0;
        rollingBucketStart = alignToMinute(System.currentTimeMillis());
    }

    private long sumRolling(long[] buckets) {
        long total = 0;
        for (long bucket : buckets) {
            total += bucket;
        }
        return total;
    }

    private static long alignToMinute(long now) {
        return (now / ROLLING_BUCKET_MS) * ROLLING_BUCKET_MS;
    }
}
