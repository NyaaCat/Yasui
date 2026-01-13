package cat.nyaa.yasui.hook;

/**
 * Compatibility wrapper for the shared block state cache.
 */
public final class SpawnCheckCache {
    private SpawnCheckCache() {}

    public static void configure(boolean enabled, int ttlTicks, int maxEntries) {
        BlockStateCache.configure(enabled, ttlTicks, maxEntries);
    }

    public static void configureHotChunks(boolean enabled, int hotTtlTicks) {
        BlockStateCache.configureHotChunks(enabled, hotTtlTicks);
    }

    public static boolean isHookActive() {
        return BlockStateCache.isHookActive();
    }

    public static boolean isBlockWriteHookActive() {
        return BlockStateCache.isBlockWriteHookActive();
    }

    public static void markHookActive() {
        BlockStateCache.markHookActive();
    }

    public static void markBlockWriteHookActive() {
        BlockStateCache.markBlockWriteHookActive();
    }

    public static void invalidateBlockState(Object level, Object pos) {
        BlockStateCache.invalidateBlockState(level, pos);
    }

    public static Object getBlockStateIfLoadedAndInBounds(Object level, Object pos, Object chunk) {
        return BlockStateCache.getBlockStateIfLoadedAndInBounds(level, pos, chunk);
    }

    public static boolean isLoadedAndInBounds(Object level, Object pos, Object chunk) {
        return BlockStateCache.isLoadedAndInBounds(level, pos, chunk);
    }

    public static long[] drainStats() {
        return BlockStateCache.drainStats();
    }

    public static int getCacheSize() {
        return BlockStateCache.getCacheSize();
    }
}
