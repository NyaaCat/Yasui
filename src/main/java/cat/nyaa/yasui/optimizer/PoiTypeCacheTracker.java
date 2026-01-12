package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import cat.nyaa.yasui.nms.PoiTypeNmsHook;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tracks rolling stats for PoiManager getType/exists caching.
 */
public class PoiTypeCacheTracker {
    private static final long ROLLING_BUCKET_MS = 60 * 1000L;

    private final Yasui plugin;
    private final YasuiConfig config;
    private final long[] rollingTypeHits = new long[60];
    private final long[] rollingTypeMisses = new long[60];
    private final long[] rollingTypeStores = new long[60];
    private final long[] rollingExistsHits = new long[60];
    private final long[] rollingExistsMisses = new long[60];
    private final long[] rollingExistsStores = new long[60];
    private int rollingIndex = 0;
    private long rollingBucketStart = alignToMinute(System.currentTimeMillis());
    private BukkitTask statsTask;

    public record RollingStats(long typeHits, long typeMisses, long typeStores,
                               long existsHits, long existsMisses, long existsStores) {}

    public PoiTypeCacheTracker(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        statsTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushStats, 20L, 20L);
        flushStats();
    }

    public void shutdown() {
        if (statsTask != null) {
            statsTask.cancel();
        }
        clearRollingBuckets();
    }

    public RollingStats getRollingStats() {
        updateRollingBuckets(System.currentTimeMillis());
        long typeHits = 0;
        long typeMisses = 0;
        long typeStores = 0;
        long existsHits = 0;
        long existsMisses = 0;
        long existsStores = 0;
        for (int i = 0; i < rollingTypeHits.length; i++) {
            typeHits += rollingTypeHits[i];
            typeMisses += rollingTypeMisses[i];
            typeStores += rollingTypeStores[i];
            existsHits += rollingExistsHits[i];
            existsMisses += rollingExistsMisses[i];
            existsStores += rollingExistsStores[i];
        }
        return new RollingStats(typeHits, typeMisses, typeStores, existsHits, existsMisses, existsStores);
    }

    public int getCacheSize() {
        return PoiTypeNmsHook.getCacheSize();
    }

    private void flushStats() {
        if (!config.isVillagerPOIEnabled() || !config.isPoiTypeCacheEnabled()) {
            return;
        }
        updateRollingBuckets(System.currentTimeMillis());
        long[] stats = PoiTypeNmsHook.drainStats();
        if (stats.length < 6) {
            return;
        }
        rollingTypeHits[rollingIndex] += stats[0];
        rollingTypeMisses[rollingIndex] += stats[1];
        rollingTypeStores[rollingIndex] += stats[2];
        rollingExistsHits[rollingIndex] += stats[3];
        rollingExistsMisses[rollingIndex] += stats[4];
        rollingExistsStores[rollingIndex] += stats[5];
    }

    private void updateRollingBuckets(long now) {
        long elapsed = now - rollingBucketStart;
        if (elapsed < ROLLING_BUCKET_MS) {
            return;
        }
        int steps = (int) Math.min(rollingTypeHits.length, elapsed / ROLLING_BUCKET_MS);
        for (int i = 0; i < steps; i++) {
            rollingIndex = (rollingIndex + 1) % rollingTypeHits.length;
            rollingTypeHits[rollingIndex] = 0;
            rollingTypeMisses[rollingIndex] = 0;
            rollingTypeStores[rollingIndex] = 0;
            rollingExistsHits[rollingIndex] = 0;
            rollingExistsMisses[rollingIndex] = 0;
            rollingExistsStores[rollingIndex] = 0;
        }
        rollingBucketStart += (long) steps * ROLLING_BUCKET_MS;
    }

    private void clearRollingBuckets() {
        for (int i = 0; i < rollingTypeHits.length; i++) {
            rollingTypeHits[i] = 0;
            rollingTypeMisses[i] = 0;
            rollingTypeStores[i] = 0;
            rollingExistsHits[i] = 0;
            rollingExistsMisses[i] = 0;
            rollingExistsStores[i] = 0;
        }
        rollingIndex = 0;
        rollingBucketStart = alignToMinute(System.currentTimeMillis());
    }

    private static long alignToMinute(long now) {
        return (now / ROLLING_BUCKET_MS) * ROLLING_BUCKET_MS;
    }
}
