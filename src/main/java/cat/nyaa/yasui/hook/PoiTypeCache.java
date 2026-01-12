package cat.nyaa.yasui.hook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * NMS-side cache for PoiManager.getType/exists lookups.
 *
 * Loaded by the agent classloader and referenced from transformed NMS bytecode.
 * Uses only JDK types to avoid classloader issues - NMS access via reflection.
 */
public final class PoiTypeCache {
    private static final Map<Object, LruCache<Long, TypeEntry>> TYPE_CACHE =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, LruCache<ExistsKey, ExistsEntry>> EXISTS_CACHE =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final LongAdder typeHits = new LongAdder();
    private static final LongAdder typeMisses = new LongAdder();
    private static final LongAdder typeStores = new LongAdder();
    private static final LongAdder existsHits = new LongAdder();
    private static final LongAdder existsMisses = new LongAdder();
    private static final LongAdder existsStores = new LongAdder();
    private static volatile boolean enabled = true;
    private static volatile int ttlTicks = 100;
    private static volatile int ttlJitterTicks = 0;
    private static volatile int maxEntries = 20000;
    private static volatile boolean cacheEmptyResults = true;
    private static volatile boolean predicateAware = true;
    private static volatile boolean renewOnHit = false;
    private static volatile boolean hotChunksEnabled = false;
    private static volatile int hotTtlTicks = 100;
    private static volatile int hotTtlJitterTicks = 0;
    private static volatile boolean hotRenewOnHit = false;
    private static volatile boolean hookActive = false;

    private PoiTypeCache() {}

    public static void configure(boolean enabled, int ttlTicks, int ttlJitterTicks, int maxEntries,
                                 boolean cacheEmptyResults, boolean predicateAware, boolean renewOnHit) {
        PoiTypeCache.enabled = enabled;
        PoiTypeCache.ttlTicks = Math.max(0, ttlTicks);
        PoiTypeCache.ttlJitterTicks = Math.max(0, ttlJitterTicks);
        PoiTypeCache.maxEntries = maxEntries;
        PoiTypeCache.cacheEmptyResults = cacheEmptyResults;
        PoiTypeCache.predicateAware = predicateAware;
        PoiTypeCache.renewOnHit = renewOnHit;
    }

    public static void configureHotChunks(boolean enabled, int ttlTicks, int ttlJitterTicks, boolean renewOnHit) {
        PoiTypeCache.hotChunksEnabled = enabled;
        PoiTypeCache.hotTtlTicks = Math.max(0, ttlTicks);
        PoiTypeCache.hotTtlJitterTicks = Math.max(0, ttlJitterTicks);
        PoiTypeCache.hotRenewOnHit = renewOnHit;
    }

    public static boolean isHookActive() {
        return hookActive;
    }

    public static void markHookActive() {
        hookActive = true;
    }

    public static Object getTypeCached(Object poiManager, Object blockPos) {
        hookActive = true;
        if (!NmsReflect.init(poiManager)) {
            return null;
        }
        if (!enabled || poiManager == null || blockPos == null) {
            return null;
        }

        long key = NmsReflect.blockPosAsLong(blockPos);
        long chunkKey = HotChunkUtil.chunkKeyFromBlockPos(key);
        int effectiveTtl = ttlTicks;
        int effectiveJitter = ttlJitterTicks;
        boolean canRenew = renewOnHit;
        if (hotChunksEnabled) {
            float heat = HotChunkMap.getHeat(poiManager, chunkKey);
            if (heat > 0f) {
                effectiveTtl = scaleInt(ttlTicks, hotTtlTicks, heat);
                effectiveJitter = scaleInt(ttlJitterTicks, hotTtlJitterTicks, heat);
                if (hotRenewOnHit) {
                    canRenew = true;
                }
            }
        }
        if (effectiveTtl == 0) {
            return null;
        }

        int tick = NmsReflect.getCurrentTick();
        int epoch = ChunkEpochMap.getEpoch(poiManager, chunkKey);
        LruCache<Long, TypeEntry> managerCache = getTypeCache(poiManager);
        TypeEntry entry = managerCache.get(key);
        if (entry != null) {
            if (entry.isValid(tick) && entry.epoch() == epoch) {
                typeHits.increment();
                if (canRenew) {
                    int expiryTick = tick + effectiveTtl + computeJitter(key, effectiveJitter);
                    managerCache.put(key, new TypeEntry(expiryTick, epoch, entry.value()));
                }
                return entry.value();
            }
            managerCache.remove(key);
        }

        typeMisses.increment();
        return null;
    }

    public static Object storeType(Object result, Object poiManager, Object blockPos) {
        if (!enabled || poiManager == null || blockPos == null) {
            return result;
        }
        if (!NmsReflect.init(poiManager)) {
            return result;
        }
        long key = NmsReflect.blockPosAsLong(blockPos);
        long chunkKey = HotChunkUtil.chunkKeyFromBlockPos(key);
        int effectiveTtl = ttlTicks;
        int effectiveJitter = ttlJitterTicks;
        if (hotChunksEnabled) {
            float heat = HotChunkMap.getHeat(poiManager, chunkKey);
            if (heat > 0f) {
                effectiveTtl = scaleInt(ttlTicks, hotTtlTicks, heat);
                effectiveJitter = scaleInt(ttlJitterTicks, hotTtlJitterTicks, heat);
            }
        }
        if (effectiveTtl == 0) {
            return result;
        }
        if (!isPresent(result) && !cacheEmptyResults) {
            return result;
        }
        int tick = NmsReflect.getCurrentTick();
        int epoch = ChunkEpochMap.getEpoch(poiManager, chunkKey);
        int expiryTick = tick + effectiveTtl + computeJitter(key, effectiveJitter);
        LruCache<Long, TypeEntry> managerCache = getTypeCache(poiManager);
        managerCache.put(key, new TypeEntry(expiryTick, epoch, result));
        typeStores.increment();
        return result;
    }

    public static Object existsCached(Object poiManager, Object blockPos, Object predicate) {
        hookActive = true;
        if (!NmsReflect.init(poiManager)) {
            return null;
        }
        if (!enabled || poiManager == null || blockPos == null) {
            return null;
        }
        if (!predicateAware && predicate != null) {
            return null;
        }

        long key = NmsReflect.blockPosAsLong(blockPos);
        long chunkKey = HotChunkUtil.chunkKeyFromBlockPos(key);
        int effectiveTtl = ttlTicks;
        int effectiveJitter = ttlJitterTicks;
        boolean canRenew = renewOnHit;
        if (hotChunksEnabled) {
            float heat = HotChunkMap.getHeat(poiManager, chunkKey);
            if (heat > 0f) {
                effectiveTtl = scaleInt(ttlTicks, hotTtlTicks, heat);
                effectiveJitter = scaleInt(ttlJitterTicks, hotTtlJitterTicks, heat);
                if (hotRenewOnHit) {
                    canRenew = true;
                }
            }
        }
        if (effectiveTtl == 0) {
            return null;
        }

        int tick = NmsReflect.getCurrentTick();
        int epoch = ChunkEpochMap.getEpoch(poiManager, chunkKey);
        ExistsKey existsKey = new ExistsKey(key, predicateAware ? predicate : null);
        LruCache<ExistsKey, ExistsEntry> managerCache = getExistsCache(poiManager);
        ExistsEntry entry = managerCache.get(existsKey);
        if (entry != null) {
            if (entry.isValid(tick) && entry.epoch() == epoch) {
                existsHits.increment();
                if (canRenew) {
                    int expiryTick = tick + effectiveTtl + computeJitter(existsKey.hashCode(), effectiveJitter);
                    managerCache.put(existsKey, new ExistsEntry(expiryTick, epoch, entry.value()));
                }
                return entry.value();
            }
            managerCache.remove(existsKey);
        }

        existsMisses.increment();
        return null;
    }

    public static boolean storeExists(boolean result, Object poiManager, Object blockPos, Object predicate) {
        if (!enabled || poiManager == null || blockPos == null) {
            return result;
        }
        if (!NmsReflect.init(poiManager)) {
            return result;
        }
        if (!predicateAware && predicate != null) {
            return result;
        }
        long key = NmsReflect.blockPosAsLong(blockPos);
        long chunkKey = HotChunkUtil.chunkKeyFromBlockPos(key);
        int effectiveTtl = ttlTicks;
        int effectiveJitter = ttlJitterTicks;
        if (hotChunksEnabled) {
            float heat = HotChunkMap.getHeat(poiManager, chunkKey);
            if (heat > 0f) {
                effectiveTtl = scaleInt(ttlTicks, hotTtlTicks, heat);
                effectiveJitter = scaleInt(ttlJitterTicks, hotTtlJitterTicks, heat);
            }
        }
        if (effectiveTtl == 0) {
            return result;
        }
        if (!result && !cacheEmptyResults) {
            return result;
        }
        int tick = NmsReflect.getCurrentTick();
        int epoch = ChunkEpochMap.getEpoch(poiManager, chunkKey);
        ExistsKey existsKey = new ExistsKey(key, predicateAware ? predicate : null);
        int expiryTick = tick + effectiveTtl + computeJitter(existsKey.hashCode(), effectiveJitter);
        LruCache<ExistsKey, ExistsEntry> managerCache = getExistsCache(poiManager);
        managerCache.put(existsKey, new ExistsEntry(expiryTick, epoch, result));
        existsStores.increment();
        return result;
    }

    public static void onPoiChanged(Object poiManager, Object blockPos) {
        if (poiManager == null || blockPos == null) {
            return;
        }
        long key = NmsReflect.blockPosAsLong(blockPos);
        ChunkEpochMap.bumpEpoch(poiManager, HotChunkUtil.chunkKeyFromBlockPos(key));
    }

    public static long[] drainStats() {
        return new long[] {
            typeHits.sumThenReset(),
            typeMisses.sumThenReset(),
            typeStores.sumThenReset(),
            existsHits.sumThenReset(),
            existsMisses.sumThenReset(),
            existsStores.sumThenReset()
        };
    }

    public static int getCacheSize() {
        int total = 0;
        synchronized (TYPE_CACHE) {
            for (LruCache<Long, TypeEntry> entries : TYPE_CACHE.values()) {
                total += entries.size();
            }
        }
        synchronized (EXISTS_CACHE) {
            for (LruCache<ExistsKey, ExistsEntry> entries : EXISTS_CACHE.values()) {
                total += entries.size();
            }
        }
        return total;
    }

    private static boolean isPresent(Object optional) {
        if (optional instanceof Optional<?> opt) {
            return opt.isPresent();
        }
        return false;
    }

    private static LruCache<Long, TypeEntry> getTypeCache(Object manager) {
        synchronized (TYPE_CACHE) {
            return TYPE_CACHE.computeIfAbsent(manager, key -> new LruCache<>());
        }
    }

    private static LruCache<ExistsKey, ExistsEntry> getExistsCache(Object manager) {
        synchronized (EXISTS_CACHE) {
            return EXISTS_CACHE.computeIfAbsent(manager, key -> new LruCache<>());
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

    private record TypeEntry(int expiryTick, int epoch, Object value) {
        private boolean isValid(int currentTick) {
            return currentTick <= this.expiryTick;
        }
    }

    private record ExistsKey(long posKey, Object predicate) {}

    private record ExistsEntry(int expiryTick, int epoch, boolean value) {
        private boolean isValid(int currentTick) {
            return currentTick <= this.expiryTick;
        }
    }

    private static final class LruCache<K, V> {
        private final LinkedHashMap<K, V> map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                int limit = PoiTypeCache.maxEntries;
                return limit > 0 && size() > limit;
            }
        };

        synchronized V get(K key) {
            return map.get(key);
        }

        synchronized void put(K key, V value) {
            map.put(key, value);
        }

        synchronized void remove(K key) {
            map.remove(key);
        }

        synchronized int size() {
            return map.size();
        }
    }
}
