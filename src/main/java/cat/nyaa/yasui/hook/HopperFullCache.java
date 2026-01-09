package cat.nyaa.yasui.hook;

import java.util.Collections;
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
    private static final Map<Object, CacheEntry> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final LongAdder cacheHits = new LongAdder();
    private static final LongAdder cacheMisses = new LongAdder();
    private static final LongAdder cacheStores = new LongAdder();
    private static final LongAdder cacheInvalidations = new LongAdder();
    private static volatile boolean enabled = true;
    private static volatile int ttlTicks = 2;
    private static volatile boolean hookActive = false;

    private HopperFullCache() {}

    public static void configure(boolean enabled, int ttlTicks) {
        HopperFullCache.enabled = enabled;
        HopperFullCache.ttlTicks = Math.max(0, ttlTicks);
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
        if (!enabled || ttlTicks == 0) {
            return computeFull(container, directionOrdinal);
        }

        int tick = NmsReflect.getCurrentTick();
        Object key = getKey(container);
        CacheEntry entry = CACHE.get(key);
        if (entry != null && entry.isValid(tick, ttlTicks, directionOrdinal)) {
            cacheHits.increment();
            return entry.full();
        }

        boolean full = computeFull(container, directionOrdinal);
        cacheMisses.increment();
        if (full) {
            CACHE.put(key, new CacheEntry(true, tick, directionOrdinal));
            cacheStores.increment();
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
            if (item == null || NmsReflect.isItemEmpty(item)) {
                return false;
            }
            if (NmsReflect.getItemCount(item) < NmsReflect.getItemMaxStackSize(item)) {
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

    private record CacheEntry(boolean full, int tick, int directionOrdinal) {
        private boolean isValid(int currentTick, int ttl, int direction) {
            return directionOrdinal == direction
                && currentTick - tick <= ttl;
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
