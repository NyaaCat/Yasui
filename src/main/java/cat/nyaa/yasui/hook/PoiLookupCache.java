package cat.nyaa.yasui.hook;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

/**
 * NMS-side cache for PoiAccess findAny/findClosest lookups.
 *
 * Loaded by the agent classloader and referenced from transformed NMS bytecode.
 * Uses only JDK types to avoid classloader issues - NMS access via reflection.
 */
public final class PoiLookupCache {
    private static final int MODE_ANY = 0;
    private static final int MODE_CLOSEST = 1;
    private static final int MODE_CLOSEST_WITH_TYPE = 2;

    private static final Map<Object, LruCache<CacheKey, CacheEntry>> CACHE =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final LongAdder cacheHits = new LongAdder();
    private static final LongAdder cacheMisses = new LongAdder();
    private static final LongAdder cacheStores = new LongAdder();
    private static volatile boolean enabled = true;
    private static volatile int ttlTicks = 100;
    private static volatile int ttlJitterTicks = 0;
    private static volatile int maxEntries = 20000;
    private static volatile boolean cacheEmptyResults = false;
    private static volatile boolean predicateAware = true;
    private static volatile int sourceBucketSize = 2;
    private static volatile boolean renewOnHit = false;
    private static volatile boolean hotChunksEnabled = false;
    private static volatile int hotTtlTicks = 100;
    private static volatile int hotTtlJitterTicks = 0;
    private static volatile boolean hotRenewOnHit = false;
    private static volatile boolean hookActive = false;
    private static final MethodHandle NULL_PAIR_SECOND = MethodHandles.dropArguments(
        MethodHandles.constant(Object.class, null), 0, Object.class);
    private static final ClassValue<MethodHandle> PAIR_SECOND_GETTER = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            try {
                return MethodHandles.publicLookup().findVirtual(
                    type, "getSecond", MethodType.methodType(Object.class));
            } catch (Throwable ignored) {
                return NULL_PAIR_SECOND;
            }
        }
    };

    private PoiLookupCache() {}

    public static void configure(boolean enabled, int ttlTicks, int ttlJitterTicks, int maxEntries,
                                 boolean cacheEmptyResults, boolean predicateAware, int sourceBucketSize,
                                 boolean renewOnHit) {
        PoiLookupCache.enabled = enabled;
        PoiLookupCache.ttlTicks = Math.max(0, ttlTicks);
        PoiLookupCache.ttlJitterTicks = Math.max(0, ttlJitterTicks);
        PoiLookupCache.maxEntries = maxEntries;
        PoiLookupCache.cacheEmptyResults = cacheEmptyResults;
        PoiLookupCache.predicateAware = predicateAware;
        PoiLookupCache.sourceBucketSize = Math.max(1, sourceBucketSize);
        PoiLookupCache.renewOnHit = renewOnHit;
    }

    public static void configureHotChunks(boolean enabled, int ttlTicks, int ttlJitterTicks, boolean renewOnHit) {
        PoiLookupCache.hotChunksEnabled = enabled;
        PoiLookupCache.hotTtlTicks = Math.max(0, ttlTicks);
        PoiLookupCache.hotTtlJitterTicks = Math.max(0, ttlJitterTicks);
        PoiLookupCache.hotRenewOnHit = renewOnHit;
    }

    public static boolean isHookActive() {
        return hookActive;
    }

    public static void markHookActive() {
        hookActive = true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object findAnyPoiPosition(Object poiManager,
                                            Predicate villagePlaceType,
                                            Predicate positionPredicate,
                                            Object sourcePosition,
                                            int range,
                                            Object occupancy,
                                            boolean load) {
        return findCached(MODE_ANY, poiManager, villagePlaceType, positionPredicate, sourcePosition, range, 0.0d, occupancy, load);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object findClosestPoiDataPosition(Object poiManager,
                                                    Predicate villagePlaceType,
                                                    Predicate positionPredicate,
                                                    Object sourcePosition,
                                                    int range,
                                                    double maxDistanceSquared,
                                                    Object occupancy,
                                                    boolean load) {
        return findCached(MODE_CLOSEST, poiManager, villagePlaceType, positionPredicate, sourcePosition, range,
            maxDistanceSquared, occupancy, load);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object findClosestPoiDataTypeAndPosition(Object poiManager,
                                                           Predicate villagePlaceType,
                                                           Predicate positionPredicate,
                                                           Object sourcePosition,
                                                           int range,
                                                           double maxDistanceSquared,
                                                           Object occupancy,
                                                           boolean load) {
        return findCached(MODE_CLOSEST_WITH_TYPE, poiManager, villagePlaceType, positionPredicate, sourcePosition, range,
            maxDistanceSquared, occupancy, load);
    }

    private static Object findCached(int mode,
                                     Object poiManager,
                                     Predicate villagePlaceType,
                                     Predicate positionPredicate,
                                     Object sourcePosition,
                                     int range,
                                     double maxDistanceSquared,
                                     Object occupancy,
                                     boolean load) {
        hookActive = true;
        if (!NmsReflect.init(poiManager)) {
            return findDirect(mode, poiManager, villagePlaceType, positionPredicate, sourcePosition, range,
                maxDistanceSquared, occupancy, load);
        }
        if (!enabled || poiManager == null || sourcePosition == null) {
            return findDirect(mode, poiManager, villagePlaceType, positionPredicate, sourcePosition, range,
                maxDistanceSquared, occupancy, load);
        }

        long sourceKey = NmsReflect.blockPosAsLong(sourcePosition);
        long bucketKey = bucketSourceKey(sourceKey);
        long maxDistanceBits = mode == MODE_ANY ? 0L : Double.doubleToLongBits(maxDistanceSquared);
        CacheKey key = new CacheKey(
            mode,
            bucketKey,
            range,
            maxDistanceBits,
            occupancy,
            load,
            villagePlaceType,
            predicateAware ? positionPredicate : null,
            sourceBucketSize,
            predicateAware
        );

        int effectiveTtl = ttlTicks;
        int effectiveJitter = ttlJitterTicks;
        boolean canRenew = renewOnHit;
        long chunkKey = HotChunkUtil.chunkKeyFromBlockPos(sourceKey);
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
            return findDirect(mode, poiManager, villagePlaceType, positionPredicate, sourcePosition, range,
                maxDistanceSquared, occupancy, load);
        }

        int tick = NmsReflect.getCurrentTick();
        int epoch = ChunkEpochMap.getEpoch(poiManager, chunkKey);
        LruCache<CacheKey, CacheEntry> managerCache = getManagerCache(poiManager);
        CacheEntry entry = managerCache.get(key);
        if (entry != null) {
            if (entry.isValid(tick) && entry.epoch() == epoch) {
                Object result = entry.result();
                if (!predicateAware && positionPredicate != null) {
                    Object pos = extractPos(mode, result);
                    if (pos == null || !positionPredicate.test(pos)) {
                        managerCache.remove(key);
                        entry = null;
                    }
                }
                if (entry != null && result != null) {
                    Object pos = extractPos(mode, result);
                    if (pos == null || !isWithinRange(sourceKey, NmsReflect.blockPosAsLong(pos), range, maxDistanceSquared, mode)) {
                        managerCache.remove(key);
                        entry = null;
                    }
                }
                if (entry != null) {
                    cacheHits.increment();
                    if (canRenew) {
                        int expiryTick = tick + effectiveTtl + computeJitter(key.hashCode(), effectiveJitter);
                        managerCache.put(key, new CacheEntry(expiryTick, epoch, result));
                    }
                    return result;
                }
            }
            managerCache.remove(key);
        }

        cacheMisses.increment();
        Object result = findDirect(mode, poiManager, villagePlaceType,
            predicateAware ? positionPredicate : null,
            sourcePosition, range, maxDistanceSquared, occupancy, load);
        if (!predicateAware && positionPredicate != null && result != null) {
            Object pos = extractPos(mode, result);
            if (pos == null || !positionPredicate.test(pos)) {
                result = null;
            }
        }
        if (result != null) {
            Object pos = extractPos(mode, result);
            if (pos == null || !isWithinRange(sourceKey, NmsReflect.blockPosAsLong(pos), range, maxDistanceSquared, mode)) {
                result = null;
            }
        }
        if (result != null || cacheEmptyResults) {
            int expiryTick = tick + effectiveTtl + computeJitter(key.hashCode(), effectiveJitter);
            managerCache.put(key, new CacheEntry(expiryTick, epoch, result));
            cacheStores.increment();
        }
        return result;
    }

    private static Object findDirect(int mode,
                                     Object poiManager,
                                     Object villagePlaceType,
                                     Object positionPredicate,
                                     Object sourcePosition,
                                     int range,
                                     double maxDistanceSquared,
                                     Object occupancy,
                                     boolean load) {
        if (mode == MODE_ANY) {
            return NmsReflect.findAnyPoiPosition(poiManager, villagePlaceType, positionPredicate, sourcePosition, range, occupancy, load);
        }
        if (mode == MODE_CLOSEST_WITH_TYPE) {
            return NmsReflect.findClosestPoiDataTypeAndPosition(
                poiManager, villagePlaceType, positionPredicate, sourcePosition, range, maxDistanceSquared, occupancy, load);
        }
        return NmsReflect.findClosestPoiDataPosition(
            poiManager, villagePlaceType, positionPredicate, sourcePosition, range, maxDistanceSquared, occupancy, load);
    }

    private static Object extractPos(int mode, Object result) {
        if (result == null) {
            return null;
        }
        if (mode == MODE_CLOSEST_WITH_TYPE) {
            return getPairSecond(result);
        }
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
            for (LruCache<CacheKey, CacheEntry> entries : CACHE.values()) {
                total += entries.size();
            }
        }
        return total;
    }

    private static LruCache<CacheKey, CacheEntry> getManagerCache(Object manager) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(manager, key -> new LruCache<>());
        }
    }

    private static Object getPairSecond(Object pair) {
        try {
            return PAIR_SECOND_GETTER.get(pair.getClass()).invoke(pair);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int computeJitter(int seed, int jitterTicks) {
        if (jitterTicks <= 0) {
            return 0;
        }
        return Math.floorMod(seed, jitterTicks + 1);
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

    private static long bucketSourceKey(long sourcePosKey) {
        int bucket = sourceBucketSize;
        if (bucket <= 1) {
            return sourcePosKey;
        }
        int x = unpackX(sourcePosKey);
        int y = unpackY(sourcePosKey);
        int z = unpackZ(sourcePosKey);
        int bx = Math.floorDiv(x, bucket) * bucket;
        int bz = Math.floorDiv(z, bucket) * bucket;
        return packBlockPos(bx, y, bz);
    }

    private static boolean isWithinRange(long sourcePosKey, long candidatePosKey, int range, double maxDistanceSquared, int mode) {
        int dx = unpackX(candidatePosKey) - unpackX(sourcePosKey);
        int dy = unpackY(candidatePosKey) - unpackY(sourcePosKey);
        int dz = unpackZ(candidatePosKey) - unpackZ(sourcePosKey);
        if (Math.abs(dx) > range || Math.abs(dy) > range || Math.abs(dz) > range) {
            return false;
        }
        long distanceSq = (long) dx * dx + (long) dy * dy + (long) dz * dz;
        long limit = mode == MODE_ANY ? (long) range * range : (long) maxDistanceSquared;
        return distanceSq <= limit;
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    private static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    private static long packBlockPos(int x, int y, int z) {
        return ((x & 67108863L) << 38) | ((y & 4095L)) | ((z & 67108863L) << 12);
    }

    private record CacheKey(int mode,
                            long posKey,
                            int range,
                            long maxDistanceBits,
                            Object occupancy,
                            boolean load,
                            Object typePredicate,
                            Object positionPredicate,
                            int bucketSize,
                            boolean predicateAware) {}

    private record CacheEntry(int expiryTick, int epoch, Object result) {
        private boolean isValid(int currentTick) {
            return currentTick <= this.expiryTick;
        }
    }

    private static final class LruCache<K, V> {
        private final LinkedHashMap<K, V> map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                int limit = PoiLookupCache.maxEntries;
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
