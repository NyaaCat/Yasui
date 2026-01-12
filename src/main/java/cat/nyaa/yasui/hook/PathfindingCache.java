package cat.nyaa.yasui.hook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * NMS-side cache for pathfinding results.
 *
 * Loaded by the agent classloader and referenced from transformed NMS bytecode.
 * Uses only JDK types to avoid classloader issues - NMS access via reflection.
 */
public final class PathfindingCache {
    private static final Map<Object, LruCache<Long, CacheEntry>> CACHE =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final LongAdder cacheHits = new LongAdder();
    private static final LongAdder cacheMisses = new LongAdder();
    private static final LongAdder cacheStores = new LongAdder();
    private static volatile boolean enabled = true;
    private static volatile int ttlTicks = 1;
    private static volatile int ttlJitterTicks = 0;
    private static volatile int maxEntriesPerNav = 4;
    private static volatile int mobMoveThreshold = 0;
    private static volatile int targetMoveThreshold = 1;
    private static volatile int negativeTtlTicks = 0;
    private static volatile boolean hotChunksEnabled = false;
    private static volatile int hotTtlTicks = 1;
    private static volatile int hotTtlJitterTicks = 0;
    private static volatile int hotMobMoveThreshold = 0;
    private static volatile int hotTargetMoveThreshold = 1;
    private static volatile int hotNegativeTtlTicks = 0;
    private static volatile boolean hookActive = false;

    private PathfindingCache() {}

    public static void configure(boolean enabled, int ttlTicks, int ttlJitterTicks, int maxEntriesPerNav,
                                 int mobMoveThreshold, int targetMoveThreshold, int negativeTtlTicks) {
        PathfindingCache.enabled = enabled;
        PathfindingCache.ttlTicks = Math.max(0, ttlTicks);
        PathfindingCache.ttlJitterTicks = Math.max(0, ttlJitterTicks);
        PathfindingCache.maxEntriesPerNav = Math.max(1, maxEntriesPerNav);
        PathfindingCache.mobMoveThreshold = Math.max(0, mobMoveThreshold);
        PathfindingCache.targetMoveThreshold = Math.max(0, targetMoveThreshold);
        PathfindingCache.negativeTtlTicks = Math.max(0, negativeTtlTicks);
    }

    public static void configureHotChunks(boolean enabled, int ttlTicks, int ttlJitterTicks,
                                          int mobMoveThreshold, int targetMoveThreshold, int negativeTtlTicks) {
        PathfindingCache.hotChunksEnabled = enabled;
        PathfindingCache.hotTtlTicks = Math.max(0, ttlTicks);
        PathfindingCache.hotTtlJitterTicks = Math.max(0, ttlJitterTicks);
        PathfindingCache.hotMobMoveThreshold = Math.max(0, mobMoveThreshold);
        PathfindingCache.hotTargetMoveThreshold = Math.max(0, targetMoveThreshold);
        PathfindingCache.hotNegativeTtlTicks = Math.max(0, negativeTtlTicks);
    }

    public static boolean isHookActive() {
        return hookActive;
    }

    public static void markHookActive() {
        hookActive = true;
    }

    /**
     * Get cached path if available.
     *
     * @param navigation NMS PathNavigation object (used as cache key)
     * @param targets Set of target positions
     * @param target NMS Entity object (nullable)
     * @param regionOffset region offset parameter
     * @param offsetUpward offset direction
     * @param accuracy pathfinding accuracy
     * @param followRange follow range
     * @return cached Path object or null if not cached
     */
    public static Object getCached(Object navigation,
                                   Set<?> targets,
                                   Object target,
                                   int regionOffset,
                                   boolean offsetUpward,
                                   int accuracy,
                                   float followRange) {
        hookActive = true;
        if (!NmsReflect.init(navigation)) {
            return null;
        }
        if (!enabled || navigation == null || targets == null) {
            return null;
        }
        int tick = NmsReflect.getCurrentTick();
        long key = computeKey(targets, target, regionOffset, offsetUpward, accuracy, followRange);
        LruCache<Long, CacheEntry> navCache = getNavigationCache(navigation);
        CacheEntry entry = navCache.get(key);
        if (entry == null) {
            cacheMisses.increment();
            return null;
        }
        if (!entry.isValid(tick)) {
            navCache.remove(key);
            cacheMisses.increment();
            return null;
        }
        Object mob = NmsReflect.getNavigationMob(navigation);
        Object mobPos = NmsReflect.getEntityBlockPos(mob);
        if (mobPos == null) {
            cacheMisses.increment();
            return null;
        }
        long mobPosKey = NmsReflect.blockPosAsLong(mobPos);
        int effectiveMobThreshold = mobMoveThreshold;
        int effectiveTargetThreshold = targetMoveThreshold;
        float heat = getHotChunkHeat(mob, mobPosKey);
        if (heat > 0f) {
            effectiveMobThreshold = scaleInt(mobMoveThreshold, hotMobMoveThreshold, heat);
            effectiveTargetThreshold = scaleInt(targetMoveThreshold, hotTargetMoveThreshold, heat);
        }
        Object level = NmsReflect.getEntityLevel(mob);
        if (level != null) {
            int mobEpoch = ChunkEpochMap.getEpoch(level, HotChunkUtil.chunkKeyFromBlockPos(mobPosKey));
            if (mobEpoch != entry.mobEpoch()) {
                navCache.remove(key);
                cacheMisses.increment();
                return null;
            }
            if (entry.hasTargetPos()) {
                int targetEpoch = ChunkEpochMap.getEpoch(level, HotChunkUtil.chunkKeyFromBlockPos(entry.targetPosKey()));
                if (targetEpoch != entry.targetEpoch()) {
                    navCache.remove(key);
                    cacheMisses.increment();
                    return null;
                }
            }
        }
        if (!isWithinThreshold(entry.mobPosKey(), mobPosKey, effectiveMobThreshold)) {
            navCache.remove(key);
            cacheMisses.increment();
            return null;
        }
        if (entry.hasTargetPos()) {
            Object targetPos = NmsReflect.getEntityBlockPos(target);
            if (targetPos == null) {
                cacheMisses.increment();
                return null;
            }
            long targetPosKey = NmsReflect.blockPosAsLong(targetPos);
            if (!isWithinThreshold(entry.targetPosKey(), targetPosKey, effectiveTargetThreshold)) {
                navCache.remove(key);
                cacheMisses.increment();
                return null;
            }
        }
        cacheHits.increment();
        if (entry.path() == null) {
            return null;
        }
        return NmsReflect.copyPath(entry.path());
    }

    /**
     * Store path in cache and return it.
     *
     * @param path NMS Path object to cache
     * @param navigation NMS PathNavigation object (used as cache key)
     * @param targets Set of target positions
     * @param target NMS Entity object (nullable)
     * @param regionOffset region offset parameter
     * @param offsetUpward offset direction
     * @param accuracy pathfinding accuracy
     * @param followRange follow range
     * @return the same path object passed in
     */
    public static Object storeAndReturn(Object path,
                                        Object navigation,
                                        Set<?> targets,
                                        Object target,
                                        int regionOffset,
                                        boolean offsetUpward,
                                        int accuracy,
                                        float followRange) {
        if (!enabled || navigation == null || targets == null) {
            return path;
        }
        if (!NmsReflect.init(navigation)) {
            return path;
        }
        Object mob = NmsReflect.getNavigationMob(navigation);
        Object mobPos = NmsReflect.getEntityBlockPos(mob);
        if (mobPos == null) {
            return path;
        }
        long mobPosKey = NmsReflect.blockPosAsLong(mobPos);
        float heat = getHotChunkHeat(mob, mobPosKey);
        int effectiveTtl = ttlTicks;
        int effectiveJitter = ttlJitterTicks;
        int effectiveNegativeTtl = negativeTtlTicks;
        if (heat > 0f) {
            effectiveTtl = scaleInt(ttlTicks, hotTtlTicks, heat);
            effectiveJitter = scaleInt(ttlJitterTicks, hotTtlJitterTicks, heat);
            effectiveNegativeTtl = scaleInt(negativeTtlTicks, hotNegativeTtlTicks, heat);
        }
        boolean cachePositive = effectiveTtl > 0 && path != null;
        boolean cacheNegative = effectiveNegativeTtl > 0 && path == null;
        if (!cachePositive && !cacheNegative) {
            return path;
        }
        boolean hasTargetPos = target != null;
        long targetPosKey = 0L;
        if (hasTargetPos) {
            Object targetPos = NmsReflect.getEntityBlockPos(target);
            if (targetPos == null) {
                return path;
            }
            targetPosKey = NmsReflect.blockPosAsLong(targetPos);
        }
        Object level = NmsReflect.getEntityLevel(mob);
        int mobEpoch = 0;
        int targetEpoch = 0;
        if (level != null) {
            mobEpoch = ChunkEpochMap.getEpoch(level, HotChunkUtil.chunkKeyFromBlockPos(mobPosKey));
            if (hasTargetPos) {
                targetEpoch = ChunkEpochMap.getEpoch(level, HotChunkUtil.chunkKeyFromBlockPos(targetPosKey));
            }
        }
        int tick = NmsReflect.getCurrentTick();
        long key = computeKey(targets, target, regionOffset, offsetUpward, accuracy, followRange);
        int ttl = cachePositive ? effectiveTtl : effectiveNegativeTtl;
        int expiryTick = tick + ttl + computeJitter(key, effectiveJitter);
        Object pathCopy = null;
        if (cachePositive) {
            pathCopy = NmsReflect.copyPath(path);
            if (pathCopy == null) {
                return path;
            }
        }
        LruCache<Long, CacheEntry> navCache = getNavigationCache(navigation);
        navCache.put(key, new CacheEntry(key, expiryTick, pathCopy, mobPosKey, targetPosKey, hasTargetPos, mobEpoch, targetEpoch));
        cacheStores.increment();
        return path;
    }

    public static long[] drainStats() {
        return new long[] {
            cacheHits.sumThenReset(),
            cacheMisses.sumThenReset(),
            cacheStores.sumThenReset()
        };
    }

    private static long computeKey(Set<?> targets,
                                   Object target,
                                   int regionOffset,
                                   boolean offsetUpward,
                                   int accuracy,
                                   float followRange) {
        long hash = 1469598103934665603L;
        boolean hasTarget = target != null;
        hash = mix(hash, hasTarget ? 1 : 0);
        if (hasTarget) {
            hash = mix(hash, NmsReflect.getEntityId(target));
        } else {
            hash = mix(hash, targets.hashCode());
        }
        hash = mix(hash, regionOffset);
        hash = mix(hash, offsetUpward ? 1 : 0);
        hash = mix(hash, accuracy);
        hash = mix(hash, Float.floatToIntBits(followRange));
        return hash;
    }

    private static long mix(long hash, int value) {
        hash ^= value;
        return hash * 1099511628211L;
    }

    private static int computeJitter(long seed, int jitterTicks) {
        if (jitterTicks <= 0) {
            return 0;
        }
        int hash = (int) (seed ^ (seed >>> 32));
        return Math.floorMod(hash, jitterTicks + 1);
    }

    private static LruCache<Long, CacheEntry> getNavigationCache(Object navigation) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(navigation, key -> new LruCache<>());
        }
    }

    private static boolean isWithinThreshold(long posKey, long otherPosKey, int threshold) {
        if (threshold <= 0) {
            return posKey == otherPosKey;
        }
        int dx = Math.abs(unpackX(posKey) - unpackX(otherPosKey));
        int dy = Math.abs(unpackY(posKey) - unpackY(otherPosKey));
        int dz = Math.abs(unpackZ(posKey) - unpackZ(otherPosKey));
        return dx <= threshold && dy <= threshold && dz <= threshold;
    }

    private static float getHotChunkHeat(Object mob, long mobPosKey) {
        if (!hotChunksEnabled || mob == null) {
            return 0f;
        }
        Object level = NmsReflect.getEntityLevel(mob);
        if (level == null) {
            return 0f;
        }
        long chunkKey = HotChunkUtil.chunkKeyFromBlockPos(mobPosKey);
        return HotChunkMap.getHeat(level, chunkKey);
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

    private static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    private static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    private record CacheEntry(long key, int expiryTick, Object path, long mobPosKey, long targetPosKey,
                              boolean hasTargetPos, int mobEpoch, int targetEpoch) {
        private boolean isValid(int currentTick) {
            return currentTick <= this.expiryTick;
        }
    }

    private static final class LruCache<K, V> {
        private final LinkedHashMap<K, V> map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                int limit = PathfindingCache.maxEntriesPerNav;
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
    }
}
