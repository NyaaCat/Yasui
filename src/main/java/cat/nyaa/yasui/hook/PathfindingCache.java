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
        if (!isWithinThreshold(entry.mobPosKey(), mobPosKey, mobMoveThreshold)) {
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
            if (!isWithinThreshold(entry.targetPosKey(), targetPosKey, targetMoveThreshold)) {
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
        boolean cachePositive = ttlTicks > 0 && path != null;
        boolean cacheNegative = negativeTtlTicks > 0 && path == null;
        if (!cachePositive && !cacheNegative) {
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
        boolean hasTargetPos = target != null;
        long targetPosKey = 0L;
        if (hasTargetPos) {
            Object targetPos = NmsReflect.getEntityBlockPos(target);
            if (targetPos == null) {
                return path;
            }
            targetPosKey = NmsReflect.blockPosAsLong(targetPos);
        }
        int tick = NmsReflect.getCurrentTick();
        long key = computeKey(targets, target, regionOffset, offsetUpward, accuracy, followRange);
        int ttl = cachePositive ? ttlTicks : negativeTtlTicks;
        int expiryTick = tick + ttl + computeJitter(key, ttlJitterTicks);
        Object pathCopy = null;
        if (cachePositive) {
            pathCopy = NmsReflect.copyPath(path);
            if (pathCopy == null) {
                return path;
            }
        }
        LruCache<Long, CacheEntry> navCache = getNavigationCache(navigation);
        navCache.put(key, new CacheEntry(key, expiryTick, pathCopy, mobPosKey, targetPosKey, hasTargetPos));
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
                              boolean hasTargetPos) {
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
