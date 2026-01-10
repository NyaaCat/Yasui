package cat.nyaa.yasui.hook;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * NMS-side cache for hopper full checks.
 *
 * Loaded by the agent classloader and referenced from transformed NMS bytecode.
 * Uses only JDK types to avoid classloader issues - NMS access via reflection.
 */
public final class HopperFullCache {
    private static final int[][] CACHED_SLOTS = new int[55][];
    // Server thread only (NMS hopper tick), so avoid synchronized map overhead.
    private static final Map<Object, CacheEntry> CACHE = new WeakHashMap<>();
    private static final LongAdder cacheHits = new LongAdder();
    private static final LongAdder cacheMisses = new LongAdder();
    private static final LongAdder cacheStores = new LongAdder();
    private static final LongAdder cacheInvalidations = new LongAdder();
    private static volatile boolean enabled = true;
    private static volatile int ttlTicks = 2;
    private static volatile boolean cacheNotFull = false;
    private static volatile int cacheNotFullTtlTicks = 1;
    private static volatile boolean hookActive = false;
    private static volatile Object l1Key;
    private static volatile CacheEntry l1Entry;

    private HopperFullCache() {}

    public static void configure(boolean enabled, int ttlTicks, boolean cacheNotFull, int cacheNotFullTtlTicks) {
        HopperFullCache.enabled = enabled;
        HopperFullCache.ttlTicks = Math.max(0, ttlTicks);
        HopperFullCache.cacheNotFull = cacheNotFull;
        HopperFullCache.cacheNotFullTtlTicks = Math.max(0, cacheNotFullTtlTicks);
        l1Key = null;
        l1Entry = null;
    }

    public static boolean isHookActive() {
        return hookActive;
    }

    public static void markHookActive() {
        hookActive = true;
    }

    /**
     * Check if container is full for the given direction.
     *
     * @param container NMS Container object
     * @param directionOrdinal Direction.ordinal() value
     * @return true if container is full
     */
    public static boolean isFullContainer(Object container, int directionOrdinal) {
        hookActive = true;
        if (!NmsReflect.init(container)) {
            return false;
        }
        if (!enabled || (ttlTicks == 0 && (!cacheNotFull || cacheNotFullTtlTicks == 0))) {
            return computeFull(container, directionOrdinal);
        }

        int tick = NmsReflect.getCurrentTick();
        Object key = getKey(container);
        CacheEntry l1 = l1Entry;
        Object l1KeyLocal = l1Key;
        if (l1 != null && isSameKey(l1KeyLocal, key) && l1.isValid(tick, directionOrdinal)) {
            cacheHits.increment();
            return l1.full();
        }

        CacheEntry entry = CACHE.get(key);
        if (entry != null && entry.isValid(tick, directionOrdinal)) {
            cacheHits.increment();
            setL1(key, entry);
            return entry.full();
        }
        if (entry != null) {
            CACHE.remove(key);
        }

        boolean full = computeFull(container, directionOrdinal);
        cacheMisses.increment();
        int entryTtl = full ? ttlTicks : (cacheNotFull ? cacheNotFullTtlTicks : 0);
        if (entryTtl > 0) {
            CacheEntry created = new CacheEntry(full, tick + entryTtl, directionOrdinal);
            CACHE.put(key, created);
            setL1(key, created);
            cacheStores.increment();
        } else {
            clearL1IfMatch(key);
        }
        return full;
    }

    /**
     * Invalidate cache for a container.
     *
     * @param container NMS Container object
     */
    public static void invalidate(Object container) {
        Object key = getKey(container);
        CacheEntry removed = CACHE.remove(key);
        if (removed != null) {
            cacheInvalidations.increment();
        }
        clearL1IfMatch(key);
    }

    public static long[] drainStats() {
        return new long[] {
            cacheHits.sumThenReset(),
            cacheMisses.sumThenReset(),
            cacheStores.sumThenReset(),
            cacheInvalidations.sumThenReset()
        };
    }

    private static Object getKey(Object container) {
        if (NmsReflect.isCompoundContainer(container)) {
            Object c1 = NmsReflect.getContainer1(container);
            Object c2 = NmsReflect.getContainer2(container);
            return new CompoundKey(c1, c2);
        }
        return container;
    }

    private static boolean computeFull(Object container, int directionOrdinal) {
        int[] slots = getSlots(container, directionOrdinal);
        for (int slot : slots) {
            Object item = NmsReflect.getItem(container, slot);
            if (item == null) {
                return false;
            }
            int count = NmsReflect.getItemCount(item);
            if (count <= 0 || count < NmsReflect.getItemMaxStackSize(item)) {
                return false;
            }
        }
        return true;
    }

    private static int[] getSlots(Object container, int directionOrdinal) {
        if (NmsReflect.isWorldlyContainer(container)) {
            return NmsReflect.getSlotsForFace(container, directionOrdinal);
        }
        int containerSize = NmsReflect.getContainerSize(container);
        if (containerSize < CACHED_SLOTS.length) {
            int[] cached = CACHED_SLOTS[containerSize];
            if (cached != null) {
                return cached;
            }
            int[] created = createFlatSlots(containerSize);
            CACHED_SLOTS[containerSize] = created;
            return created;
        }
        return createFlatSlots(containerSize);
    }

    private static int[] createFlatSlots(int size) {
        int[] slots = new int[size];
        for (int i = 0; i < size; i++) {
            slots[i] = i;
        }
        return slots;
    }

    private static void setL1(Object key, CacheEntry entry) {
        l1Key = key;
        l1Entry = entry;
    }

    private static void clearL1IfMatch(Object key) {
        Object l1KeyLocal = l1Key;
        if (l1KeyLocal != null && isSameKey(l1KeyLocal, key)) {
            l1Key = null;
            l1Entry = null;
        }
    }

    private static boolean isSameKey(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof CompoundKey || right instanceof CompoundKey) {
            return left.equals(right);
        }
        return false;
    }

    private record CacheEntry(boolean full, int expiryTick, int directionOrdinal) {
        private boolean isValid(int currentTick, int direction) {
            return directionOrdinal == direction
                && currentTick <= expiryTick;
        }
    }

    private static final class CompoundKey {
        private final Object left;
        private final Object right;
        private final int hash;

        private CompoundKey(Object left, Object right) {
            this.left = left;
            this.right = right;
            this.hash = System.identityHashCode(left) * 31 + System.identityHashCode(right);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CompoundKey other)) {
                return false;
            }
            return left == other.left && right == other.right;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
