package cat.nyaa.yasui.hook;

import java.util.Collections;
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
    private static final Map<Object, CacheEntry> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final LongAdder cacheHits = new LongAdder();
    private static final LongAdder cacheMisses = new LongAdder();
    private static final LongAdder cacheStores = new LongAdder();
    private static volatile boolean enabled = true;
    private static volatile int ttlTicks = 1;
    private static volatile int ttlJitterTicks = 0;
    private static volatile boolean hookActive = false;

    private PathfindingCache() {}

    public static void configure(boolean enabled, int ttlTicks, int ttlJitterTicks) {
        PathfindingCache.enabled = enabled;
        PathfindingCache.ttlTicks = Math.max(0, ttlTicks);
        PathfindingCache.ttlJitterTicks = Math.max(0, ttlJitterTicks);
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
        if (!enabled || ttlTicks == 0 || navigation == null || targets == null) {
            return null;
        }
        int tick = NmsReflect.getCurrentTick();
        CacheEntry entry = CACHE.get(navigation);
        if (entry == null) {
            cacheMisses.increment();
            return null;
        }
        long key = computeKey(targets, target, regionOffset, offsetUpward, accuracy, followRange);
        if (entry.matches(key, tick)) {
            cacheHits.increment();
            return NmsReflect.copyPath(entry.path());
        }
        cacheMisses.increment();
        return null;
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
        if (!enabled || ttlTicks == 0 || navigation == null || targets == null || path == null) {
            return path;
        }
        int tick = NmsReflect.getCurrentTick();
        long key = computeKey(targets, target, regionOffset, offsetUpward, accuracy, followRange);
        int expiryTick = tick + ttlTicks + computeJitter(key, ttlJitterTicks);
        Object pathCopy = NmsReflect.copyPath(path);
        if (pathCopy != null) {
            CACHE.put(navigation, new CacheEntry(key, expiryTick, pathCopy));
            cacheStores.increment();
        }
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
        hash = mix(hash, targets.hashCode());
        hash = mix(hash, target != null ? NmsReflect.getEntityId(target) : 0);
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

    private record CacheEntry(long key, int expiryTick, Object path) {
        private boolean matches(long otherKey, int currentTick) {
            return this.key == otherKey && currentTick <= this.expiryTick;
        }
    }
}
