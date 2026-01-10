package cat.nyaa.yasui.hook;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

/**
 * NMS-side cache for AcquirePoi searches.
 *
 * Loaded by the agent classloader and referenced from transformed NMS bytecode.
 * Uses only JDK types to avoid classloader issues - NMS access via reflection.
 */
public final class PoiSearchCache {
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
    private static volatile boolean predicateAware = false;
    private static volatile int sourceBucketSize = 2;
    private static volatile boolean fallbackOnInsufficient = false;
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

    private PoiSearchCache() {}

    public static void configure(boolean enabled, int ttlTicks, int ttlJitterTicks, int maxEntries,
                                 boolean cacheEmptyResults, boolean predicateAware, int sourceBucketSize,
                                 boolean fallbackOnInsufficient) {
        PoiSearchCache.enabled = enabled;
        PoiSearchCache.ttlTicks = Math.max(0, ttlTicks);
        PoiSearchCache.ttlJitterTicks = Math.max(0, ttlJitterTicks);
        PoiSearchCache.maxEntries = maxEntries;
        PoiSearchCache.cacheEmptyResults = cacheEmptyResults;
        PoiSearchCache.predicateAware = predicateAware;
        PoiSearchCache.sourceBucketSize = Math.max(1, sourceBucketSize);
        PoiSearchCache.fallbackOnInsufficient = fallbackOnInsufficient;
    }

    public static boolean isHookActive() {
        return hookActive;
    }

    public static void markHookActive() {
        hookActive = true;
    }

    /**
     * Find nearest POI positions, using cache when possible.
     *
     * All NMS types are passed as Object to avoid classloader issues.
     *
     * @param poiManager NMS PoiManager
     * @param villagePlaceType Predicate for POI type filtering
     * @param positionPredicate Predicate for position filtering
     * @param sourcePosition NMS BlockPos
     * @param range search range
     * @param maxDistanceSquared maximum distance squared
     * @param occupancy NMS PoiManager.Occupancy
     * @param load whether to load chunks
     * @param max maximum results
     * @param ret List to populate with Pair<Holder<PoiType>, BlockPos> results
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void findNearestPoiPositions(Object poiManager,
                                               Predicate villagePlaceType,
                                               Predicate positionPredicate,
                                               Object sourcePosition,
                                               int range,
                                               double maxDistanceSquared,
                                               Object occupancy,
                                               boolean load,
                                               int max,
                                               List ret) {
        hookActive = true;
        if (!NmsReflect.init(poiManager)) {
            NmsReflect.findNearestPoiPositions(
                poiManager, villagePlaceType, positionPredicate, sourcePosition,
                range, maxDistanceSquared, occupancy, load, max, ret);
            return;
        }
        if (!enabled || ttlTicks == 0 || poiManager == null || sourcePosition == null) {
            NmsReflect.findNearestPoiPositions(
                poiManager, villagePlaceType, positionPredicate, sourcePosition,
                range, maxDistanceSquared, occupancy, load, max, ret);
            return;
        }

        int maxResults = Math.max(0, max);
        long sourceKey = NmsReflect.blockPosAsLong(sourcePosition);
        long bucketKey = bucketSourceKey(sourceKey);
        LruCache<CacheKey, CacheEntry> managerCache = getManagerCache(poiManager);
        CacheKey key = new CacheKey(
            bucketKey,
            range,
            Double.doubleToLongBits(maxDistanceSquared),
            occupancy,
            load,
            villagePlaceType,
            sourceBucketSize,
            predicateAware ? maxResults : 0,
            predicateAware
        );

        int tick = NmsReflect.getCurrentTick();
        CacheEntry entry = managerCache.get(key);
        if (entry != null) {
            if (entry.isValid(tick)) {
                if (!fallbackOnInsufficient || maxResults == 0) {
                    cacheHits.increment();
                    fillResults(entry.results(), positionPredicate, maxResults, ret, sourceKey, range, maxDistanceSquared);
                    return;
                }
                List cachedResults = new ArrayList();
                fillResults(entry.results(), positionPredicate, maxResults, cachedResults, sourceKey, range, maxDistanceSquared);
                if (cachedResults.size() >= maxResults) {
                    cacheHits.increment();
                    ret.addAll(cachedResults);
                    return;
                }
            }
            managerCache.remove(key);
        }

        cacheMisses.increment();
        List results = new ArrayList();
        Predicate searchPredicate = predicateAware ? positionPredicate : null;
        int searchMax = predicateAware ? maxResults : Integer.MAX_VALUE;
        NmsReflect.findNearestPoiPositions(
            poiManager, villagePlaceType, searchPredicate, sourcePosition,
            range, maxDistanceSquared, occupancy, load, searchMax, results);
        Predicate fillPredicate = (predicateAware && positionPredicate != null) ? null : positionPredicate;
        fillResults(results, fillPredicate, maxResults, ret, sourceKey, range, maxDistanceSquared);

        if (!results.isEmpty() || cacheEmptyResults) {
            int expiryTick = tick + ttlTicks + computeJitter(key.hashCode(), ttlJitterTicks);
            managerCache.put(key, new CacheEntry(expiryTick, results));
            cacheStores.increment();
        }
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

    private record CacheKey(long posKey,
                            int range,
                            long maxDistanceBits,
                            Object occupancy,
                            boolean load,
                            Object typePredicate,
                            int bucketSize,
                            int max,
                            boolean predicateAware) {}

    private record CacheEntry(int expiryTick, List<Object> results) {
        private boolean isValid(int currentTick) {
            return currentTick <= this.expiryTick;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void fillResults(List candidates,
                                    Predicate positionPredicate,
                                    int max,
                                    List ret,
                                    long sourcePosKey,
                                    int range,
                                    double maxDistanceSquared) {
        if (candidates.isEmpty()) {
            return;
        }
        int remaining = Math.max(0, max);
        for (Object candidate : candidates) {
            if (remaining == 0) {
                break;
            }
            // candidate is Pair<Holder<PoiType>, BlockPos>
            // Use reflection to get the second element (BlockPos)
            Object blockPos = getPairSecond(candidate);
            if (blockPos == null) {
                continue;
            }
            long candidateKey = NmsReflect.blockPosAsLong(blockPos);
            if (!isWithinRange(sourcePosKey, candidateKey, range, maxDistanceSquared)) {
                continue;
            }
            if (positionPredicate != null && !positionPredicate.test(blockPos)) {
                continue;
            }
            ret.add(candidate);
            remaining--;
        }
    }

    private static Object getPairSecond(Object pair) {
        if (pair == null) {
            return null;
        }
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

    private static boolean isWithinRange(long sourcePosKey, long candidatePosKey, int range, double maxDistanceSquared) {
        int dx = unpackX(candidatePosKey) - unpackX(sourcePosKey);
        int dy = unpackY(candidatePosKey) - unpackY(sourcePosKey);
        int dz = unpackZ(candidatePosKey) - unpackZ(sourcePosKey);
        if (Math.abs(dx) > range || Math.abs(dy) > range || Math.abs(dz) > range) {
            return false;
        }
        long distanceSq = (long) dx * dx + (long) dy * dy + (long) dz * dz;
        return distanceSq <= maxDistanceSquared;
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

    private static final class LruCache<K, V> {
        private final LinkedHashMap<K, V> map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                int limit = PoiSearchCache.maxEntries;
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
