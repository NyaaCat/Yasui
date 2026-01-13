package cat.nyaa.yasui.hook;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * NMS-side cache for block state lookups.
 *
 * Loaded by the agent classloader and referenced from transformed NMS bytecode.
 * Uses only JDK types to avoid classloader issues - NMS access via reflection.
 */
public final class BlockStateCache {
    private static final Map<Object, LongLruCache<CacheEntry>> CACHE = new WeakHashMap<>();
    private static final LongAdder cacheHits = new LongAdder();
    private static final LongAdder cacheMisses = new LongAdder();
    private static final LongAdder cacheStores = new LongAdder();
    private static volatile boolean enabled = true;
    private static volatile int ttlTicks = 1;
    private static volatile int maxEntries = 20000;
    private static volatile boolean hookActive = false;
    private static volatile boolean exactInvalidationActive = false;
    private static volatile boolean hotChunksEnabled = false;
    private static volatile int hotTtlTicks = 1;

    private BlockStateCache() {}

    public static void configure(boolean enabled, int ttlTicks, int maxEntries) {
        BlockStateCache.enabled = enabled;
        BlockStateCache.ttlTicks = Math.max(0, ttlTicks);
        BlockStateCache.maxEntries = Math.max(0, maxEntries);
        applyCacheLimit();
    }

    public static void configureHotChunks(boolean enabled, int hotTtlTicks) {
        BlockStateCache.hotChunksEnabled = enabled;
        BlockStateCache.hotTtlTicks = Math.max(0, hotTtlTicks);
    }

    public static boolean isHookActive() {
        return hookActive;
    }

    public static boolean isBlockWriteHookActive() {
        return exactInvalidationActive;
    }

    public static void markHookActive() {
        hookActive = true;
    }

    public static void markBlockWriteHookActive() {
        exactInvalidationActive = true;
    }

    public static void invalidateBlockState(Object level, Object pos) {
        if (!enabled || level == null || pos == null) {
            return;
        }
        if (!NmsReflect.init(level)) {
            return;
        }
        long posKey = NmsReflect.blockPosAsLong(pos);
        LongLruCache<CacheEntry> levelCache = getLevelCacheOrNull(level);
        if (levelCache != null) {
            levelCache.remove(posKey);
        }
        bumpEpochs(level, posKey);
    }

    public static Object getBlockStateIfLoadedAndInBounds(Object level, Object pos, Object chunk) {
        hookActive = true;
        if (level == null || pos == null) {
            return null;
        }
        if (!NmsReflect.init(level)) {
            return null;
        }
        if (!enabled) {
            return NmsReflect.getBlockStateIfLoadedAndInBounds(level, pos);
        }
        Boolean withinBorder = NmsReflect.isWithinWorldBorder(level, pos);
        if (withinBorder == null) {
            return NmsReflect.getBlockStateIfLoadedAndInBounds(level, pos);
        }
        if (!withinBorder) {
            return null;
        }
        long posKey = NmsReflect.blockPosAsLong(pos);
        if (!isSameChunk(posKey, chunk)) {
            return NmsReflect.getBlockStateIfLoadedAndInBounds(level, pos);
        }
        boolean cacheEnabled = maxEntries > 0 && (ttlTicks > 0 || (hotChunksEnabled && hotTtlTicks > 0));
        int effectiveTtl = cacheEnabled ? getEffectiveTtl(level, posKey) : 0;
        if (effectiveTtl <= 0 || maxEntries <= 0) {
            Object state = NmsReflect.getBlockState(chunk, pos);
            return state != null ? state : NmsReflect.getBlockStateIfLoadedAndInBounds(level, pos);
        }
        LongLruCache<CacheEntry> levelCache = getLevelCache(level);
        CacheEntry entry = levelCache.get(posKey);
        int tick = NmsReflect.getCurrentTick();
        boolean checkEpoch = !exactInvalidationActive;
        int epoch = checkEpoch ? ChunkEpochMap.getEpoch(level, HotChunkUtil.chunkKeyFromBlockPos(posKey)) : 0;
        if (entry != null) {
            if (entry.isValid(tick, epoch, checkEpoch)) {
                cacheHits.increment();
                return entry.state();
            }
            levelCache.remove(posKey);
        }
        cacheMisses.increment();
        Object state = NmsReflect.getBlockState(chunk, pos);
        if (state == null) {
            return NmsReflect.getBlockStateIfLoadedAndInBounds(level, pos);
        }
        levelCache.put(posKey, new CacheEntry(state, tick + effectiveTtl, epoch));
        cacheStores.increment();
        return state;
    }

    public static boolean isLoadedAndInBounds(Object level, Object pos, Object chunk) {
        hookActive = true;
        if (level == null || pos == null) {
            return false;
        }
        if (!NmsReflect.init(level)) {
            return false;
        }
        if (!enabled) {
            return NmsReflect.isLoadedAndInBounds(level, pos);
        }
        Boolean withinBorder = NmsReflect.isWithinWorldBorder(level, pos);
        if (withinBorder == null) {
            return NmsReflect.isLoadedAndInBounds(level, pos);
        }
        if (!withinBorder) {
            return false;
        }
        long posKey = NmsReflect.blockPosAsLong(pos);
        if (isSameChunk(posKey, chunk)) {
            return true;
        }
        return NmsReflect.isLoadedAndInBounds(level, pos);
    }

    public static Object getBlockStateFromLevel(Object level, Object pos) {
        hookActive = true;
        if (level == null || pos == null) {
            return null;
        }
        if (!NmsReflect.init(level)) {
            return null;
        }
        if (!enabled || maxEntries <= 0) {
            return NmsReflect.getBlockState(level, pos);
        }
        long posKey = NmsReflect.blockPosAsLong(pos);
        int effectiveTtl = getEffectiveTtl(level, posKey);
        if (effectiveTtl <= 0) {
            return NmsReflect.getBlockState(level, pos);
        }
        LongLruCache<CacheEntry> levelCache = getLevelCache(level);
        CacheEntry entry = levelCache.get(posKey);
        int tick = NmsReflect.getCurrentTick();
        boolean checkEpoch = !exactInvalidationActive;
        int epoch = checkEpoch ? ChunkEpochMap.getEpoch(level, HotChunkUtil.chunkKeyFromBlockPos(posKey)) : 0;
        if (entry != null) {
            if (entry.isValid(tick, epoch, checkEpoch)) {
                cacheHits.increment();
                return entry.state();
            }
            levelCache.remove(posKey);
        }
        cacheMisses.increment();
        Object state = NmsReflect.getBlockState(level, pos);
        if (state == null) {
            return null;
        }
        levelCache.put(posKey, new CacheEntry(state, tick + effectiveTtl, epoch));
        cacheStores.increment();
        return state;
    }

    public static Object getBlockStateIfLoadedFromLevel(Object level, Object pos) {
        hookActive = true;
        if (level == null || pos == null) {
            return null;
        }
        if (!NmsReflect.init(level)) {
            return null;
        }
        if (!enabled || maxEntries <= 0) {
            return NmsReflect.getBlockStateIfLoaded(level, pos);
        }
        long posKey = NmsReflect.blockPosAsLong(pos);
        int effectiveTtl = getEffectiveTtl(level, posKey);
        if (effectiveTtl <= 0) {
            return NmsReflect.getBlockStateIfLoaded(level, pos);
        }
        LongLruCache<CacheEntry> levelCache = getLevelCache(level);
        CacheEntry entry = levelCache.get(posKey);
        int tick = NmsReflect.getCurrentTick();
        boolean checkEpoch = !exactInvalidationActive;
        int epoch = checkEpoch ? ChunkEpochMap.getEpoch(level, HotChunkUtil.chunkKeyFromBlockPos(posKey)) : 0;
        if (entry != null) {
            if (entry.isValid(tick, epoch, checkEpoch)) {
                cacheHits.increment();
                return entry.state();
            }
            levelCache.remove(posKey);
        }
        cacheMisses.increment();
        Object state = NmsReflect.getBlockStateIfLoaded(level, pos);
        if (state == null) {
            return null;
        }
        levelCache.put(posKey, new CacheEntry(state, tick + effectiveTtl, epoch));
        cacheStores.increment();
        return state;
    }

    public static Object getBlockStateFromChunk(Object chunk, Object pos, Object level) {
        hookActive = true;
        if (chunk == null || pos == null) {
            return null;
        }
        Object cacheOwner = level;
        if (cacheOwner == null) {
            if (!NmsReflect.init(chunk)) {
                return null;
            }
            return NmsReflect.getBlockState(chunk, pos);
        }
        if (!NmsReflect.init(cacheOwner)) {
            return null;
        }
        if (!enabled || maxEntries <= 0) {
            Object state = NmsReflect.getBlockState(chunk, pos);
            return state != null ? state : NmsReflect.getBlockState(cacheOwner, pos);
        }
        long posKey = NmsReflect.blockPosAsLong(pos);
        int effectiveTtl = getEffectiveTtl(cacheOwner, posKey);
        if (effectiveTtl <= 0) {
            Object state = NmsReflect.getBlockState(chunk, pos);
            return state != null ? state : NmsReflect.getBlockState(cacheOwner, pos);
        }
        LongLruCache<CacheEntry> levelCache = getLevelCache(cacheOwner);
        CacheEntry entry = levelCache.get(posKey);
        int tick = NmsReflect.getCurrentTick();
        boolean checkEpoch = !exactInvalidationActive;
        int epoch = checkEpoch ? ChunkEpochMap.getEpoch(cacheOwner, HotChunkUtil.chunkKeyFromBlockPos(posKey)) : 0;
        if (entry != null) {
            if (entry.isValid(tick, epoch, checkEpoch)) {
                cacheHits.increment();
                return entry.state();
            }
            levelCache.remove(posKey);
        }
        cacheMisses.increment();
        Object state = NmsReflect.getBlockState(chunk, pos);
        if (state == null) {
            return NmsReflect.getBlockState(cacheOwner, pos);
        }
        levelCache.put(posKey, new CacheEntry(state, tick + effectiveTtl, epoch));
        cacheStores.increment();
        return state;
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

    private static LongLruCache<CacheEntry> getLevelCache(Object level) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(level, key -> new LongLruCache<>(maxEntries));
        }
    }

    private static LongLruCache<CacheEntry> getLevelCacheOrNull(Object level) {
        synchronized (CACHE) {
            return CACHE.get(level);
        }
    }

    private static boolean isSameChunk(long posKey, Object chunk) {
        if (chunk == null) {
            return false;
        }
        Object chunkPos = NmsReflect.getChunkPos(chunk);
        Integer chunkX = NmsReflect.getChunkPosX(chunkPos);
        Integer chunkZ = NmsReflect.getChunkPosZ(chunkPos);
        if (chunkX == null || chunkZ == null) {
            return false;
        }
        int posChunkX = unpackX(posKey) >> 4;
        int posChunkZ = unpackZ(posKey) >> 4;
        return chunkX == posChunkX && chunkZ == posChunkZ;
    }

    private static int getEffectiveTtl(Object cacheOwner, long posKey) {
        int baseTtl = ttlTicks;
        if (!hotChunksEnabled || cacheOwner == null) {
            return baseTtl;
        }
        float heat = getHotChunkHeat(cacheOwner, posKey);
        return scaleInt(baseTtl, hotTtlTicks, heat);
    }

    private static float getHotChunkHeat(Object owner, long posKey) {
        if (!hotChunksEnabled || owner == null) {
            return 0f;
        }
        long chunkKey = HotChunkUtil.chunkKeyFromBlockPos(posKey);
        return HotChunkMap.getHeat(owner, chunkKey);
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

    private static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    private record CacheEntry(Object state, int expiryTick, int epoch) {
        private boolean isValid(int currentTick, int currentEpoch, boolean checkEpoch) {
            return currentTick <= expiryTick && (!checkEpoch || currentEpoch == epoch);
        }
    }

    private static void applyCacheLimit() {
        synchronized (CACHE) {
            for (LongLruCache<CacheEntry> entries : CACHE.values()) {
                entries.setLimit(maxEntries);
            }
        }
    }

    private static void bumpEpochs(Object level, long posKey) {
        if (level == null) {
            return;
        }
        long chunkKey = HotChunkUtil.chunkKeyFromBlockPos(posKey);
        ChunkEpochMap.bumpEpoch(level, chunkKey);
        Object poiManager = NmsReflect.getPoiManager(level);
        if (poiManager != null) {
            ChunkEpochMap.bumpEpoch(poiManager, chunkKey);
        }
    }
}
