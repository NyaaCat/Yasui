package cat.nyaa.yasui.hook;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * NMS-side cache for PoiCompetitorScan POI type lookups.
 *
 * Loaded by the agent classloader and referenced from transformed NMS bytecode.
 * Uses only JDK types to avoid classloader issues - NMS access via reflection.
 */
public final class PoiCompetitorCache {
    private static final Map<Object, LongLruCache<CacheEntry>> CACHE =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, MemoryEntry> MEMORY_CACHE =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final LongAdder cacheHits = new LongAdder();
    private static final LongAdder cacheMisses = new LongAdder();
    private static final LongAdder cacheStores = new LongAdder();
    private static volatile boolean enabled = true;
    private static volatile int ttlTicks = 2;
    private static volatile int ttlJitterTicks = 0;
    private static volatile int maxEntries = 10000;
    private static volatile boolean cacheEmptyResults = false;
    private static volatile boolean renewOnHit = false;
    private static volatile boolean hotChunksEnabled = false;
    private static volatile int hotTtlTicks = 2;
    private static volatile int hotTtlJitterTicks = 0;
    private static volatile boolean hotRenewOnHit = false;
    private static volatile boolean hookActive = false;

    private PoiCompetitorCache() {}

    public static void configure(boolean enabled, int ttlTicks, int ttlJitterTicks, int maxEntries,
                                 boolean cacheEmptyResults, boolean renewOnHit) {
        PoiCompetitorCache.enabled = enabled;
        PoiCompetitorCache.ttlTicks = Math.max(0, ttlTicks);
        PoiCompetitorCache.ttlJitterTicks = Math.max(0, ttlJitterTicks);
        PoiCompetitorCache.maxEntries = Math.max(0, maxEntries);
        PoiCompetitorCache.cacheEmptyResults = cacheEmptyResults;
        PoiCompetitorCache.renewOnHit = renewOnHit;
        applyCacheLimit();
    }

    public static void configureHotChunks(boolean enabled, int ttlTicks, int ttlJitterTicks, boolean renewOnHit) {
        PoiCompetitorCache.hotChunksEnabled = enabled;
        PoiCompetitorCache.hotTtlTicks = Math.max(0, ttlTicks);
        PoiCompetitorCache.hotTtlJitterTicks = Math.max(0, ttlJitterTicks);
        PoiCompetitorCache.hotRenewOnHit = renewOnHit;
    }

    public static boolean isHookActive() {
        return hookActive;
    }

    public static void markHookActive() {
        hookActive = true;
    }

    /**
     * Cached PoiManager.getType lookup for PoiCompetitorScan.
     *
     * @param poiManager NMS PoiManager
     * @param blockPos NMS BlockPos
     * @return Optional<Holder<PoiType>> (as Optional)
     */
    public static Optional<?> getTypeCached(Object poiManager, Object blockPos) {
        hookActive = true;
        if (poiManager == null || blockPos == null) {
            return Optional.empty();
        }
        if (!NmsReflect.init(poiManager)) {
            return asOptional(NmsReflect.getPoiType(poiManager, blockPos));
        }
        if (!enabled) {
            return asOptional(NmsReflect.getPoiType(poiManager, blockPos));
        }

        LongLruCache<CacheEntry> managerCache = getManagerCache(poiManager);
        long key = NmsReflect.blockPosAsLong(blockPos);
        int effectiveTtl = ttlTicks;
        int effectiveJitter = ttlJitterTicks;
        boolean canRenew = renewOnHit;
        if (hotChunksEnabled) {
            float heat = HotChunkMap.getHeat(poiManager, HotChunkUtil.chunkKeyFromBlockPos(key));
            if (heat > 0f) {
                effectiveTtl = scaleInt(ttlTicks, hotTtlTicks, heat);
                effectiveJitter = scaleInt(ttlJitterTicks, hotTtlJitterTicks, heat);
                if (hotRenewOnHit) {
                    canRenew = true;
                }
            }
        }
        if (effectiveTtl == 0) {
            return asOptional(NmsReflect.getPoiType(poiManager, blockPos));
        }
        int tick = NmsReflect.getCurrentTick();
        CacheEntry entry = managerCache.get(key);
        int epoch = ChunkEpochMap.getEpoch(poiManager, HotChunkUtil.chunkKeyFromBlockPos(key));
        if (entry != null) {
            if (entry.isValid(tick) && entry.epoch() == epoch) {
                cacheHits.increment();
                if (canRenew) {
                    int expiryTick = tick + effectiveTtl + computeJitter(key, effectiveJitter);
                    managerCache.put(key, new CacheEntry(expiryTick, epoch, entry.value()));
                }
                return entry.value();
            }
            managerCache.remove(key);
        }

        cacheMisses.increment();
        Optional<?> result = asOptional(NmsReflect.getPoiType(poiManager, blockPos));
        if (result.isPresent() || cacheEmptyResults) {
            int expiryTick = tick + effectiveTtl + computeJitter(key, effectiveJitter);
            managerCache.put(key, new CacheEntry(expiryTick, epoch, result));
            cacheStores.increment();
        }
        return result;
    }

    /**
     * Cached Brain.getMemory for PoiCompetitorScan.
     *
     * Cache is scoped to the current tick to minimize behavior differences.
     *
     * @param brain NMS Brain
     * @param memoryType NMS MemoryModuleType
     * @return Optional memory value
     */
    public static Optional<?> getMemoryCached(Object brain, Object memoryType) {
        hookActive = true;
        if (brain == null || memoryType == null) {
            return Optional.empty();
        }
        if (!NmsReflect.init(brain)) {
            return asOptional(NmsReflect.getBrainMemory(brain, memoryType));
        }
        if (!enabled) {
            return asOptional(NmsReflect.getBrainMemory(brain, memoryType));
        }

        int tick = NmsReflect.getCurrentTick();
        MemoryEntry entry = MEMORY_CACHE.get(brain);
        if (entry != null && entry.isValid(tick, memoryType)) {
            return entry.value();
        }

        Optional<?> result = asOptional(NmsReflect.getBrainMemory(brain, memoryType));
        MEMORY_CACHE.put(brain, new MemoryEntry(tick, memoryType, result));
        return result;
    }

    public static long[] drainStats() {
        return new long[] {
            cacheHits.sumThenReset(),
            cacheMisses.sumThenReset(),
            cacheStores.sumThenReset()
        };
    }

    public static int getCacheSize() {
        int total = 0;
        synchronized (CACHE) {
            for (LongLruCache<CacheEntry> entries : CACHE.values()) {
                total += entries.size();
            }
        }
        return total;
    }

    private static Optional<?> asOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional;
        }
        return Optional.empty();
    }

    private static LongLruCache<CacheEntry> getManagerCache(Object manager) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(manager, key -> new LongLruCache<>(maxEntries));
        }
    }

    private static int computeJitter(long seed, int jitterTicks) {
        if (jitterTicks <= 0) {
            return 0;
        }
        int hash = (int) (seed ^ (seed >>> 32));
        return Math.floorMod(hash, jitterTicks + 1);
    }

    private static int scaleInt(int base, int hot, float heat) {
        if (heat <= 0f) {
            return base;
        }
        int target = Math.max(base, hot);
        int delta = target - base;
        if (delta == 0) {
            return base;
        }
        return base + Math.round(delta * heat);
    }

    private record CacheEntry(int expiryTick, int epoch, Optional<?> value) {
        private boolean isValid(int currentTick) {
            return currentTick <= this.expiryTick;
        }
    }

    private record MemoryEntry(int tick, Object memoryType, Optional<?> value) {
        private boolean isValid(int currentTick, Object currentMemoryType) {
            return this.tick == currentTick && this.memoryType == currentMemoryType;
        }
    }

    private static void applyCacheLimit() {
        synchronized (CACHE) {
            for (LongLruCache<CacheEntry> entries : CACHE.values()) {
                entries.setLimit(maxEntries);
            }
        }
    }
}
