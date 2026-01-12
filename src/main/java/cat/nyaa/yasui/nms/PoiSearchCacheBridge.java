package cat.nyaa.yasui.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class PoiSearchCacheBridge {
    private static final String CLASS_NAME = "cat.nyaa.yasui.hook.PoiSearchCache";
    private static volatile boolean resolved = false;
    private static volatile Class<?> cacheClass;
    private static volatile MethodHandle configureHandle;
    private static volatile MethodHandle configureHotHandle;
    private static volatile MethodHandle drainStatsHandle;
    private static volatile MethodHandle cacheSizeHandle;
    private static volatile MethodHandle hookActiveHandle;

    private PoiSearchCacheBridge() {}

    static void configure(boolean enabled, int ttlTicks, int ttlJitterTicks, int maxEntries, boolean cacheEmptyResults,
                          boolean predicateAware, int sourceBucketSize, boolean fallbackOnInsufficient,
                          boolean renewOnHit) {
        if (!resolve()) {
            return;
        }
        try {
            configureHandle.invokeWithArguments(
                enabled, ttlTicks, ttlJitterTicks, maxEntries, cacheEmptyResults, predicateAware, sourceBucketSize,
                fallbackOnInsufficient, renewOnHit
            );
        } catch (Throwable ignored) {
        }
    }

    static void configureHotChunks(boolean enabled, int ttlTicks, int ttlJitterTicks, boolean renewOnHit) {
        if (!resolve()) {
            return;
        }
        try {
            configureHotHandle.invokeWithArguments(enabled, ttlTicks, ttlJitterTicks, renewOnHit);
        } catch (Throwable ignored) {
        }
    }

    static long[] drainStats() {
        if (!resolve()) {
            return new long[] {0L, 0L, 0L};
        }
        try {
            Object value = drainStatsHandle.invokeWithArguments();
            if (value instanceof long[] stats && stats.length >= 3) {
                return stats;
            }
        } catch (Throwable ignored) {
        }
        return new long[] {0L, 0L, 0L};
    }

    static int getCacheSize() {
        if (!resolve()) {
            return 0;
        }
        try {
            Object value = cacheSizeHandle.invokeWithArguments();
            if (value instanceof Integer size) {
                return size;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    static boolean isHookActive() {
        if (!resolve()) {
            return false;
        }
        try {
            Object value = hookActiveHandle.invokeWithArguments();
            if (value instanceof Boolean active) {
                return active;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean resolve() {
        if (resolved) {
            return cacheClass != null;
        }
        synchronized (PoiSearchCacheBridge.class) {
            if (resolved) {
                return cacheClass != null;
            }
            try {
                ClassLoader system = ClassLoader.getSystemClassLoader();
                cacheClass = Class.forName(CLASS_NAME, true, system);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                configureHandle = lookup.findStatic(cacheClass, "configure",
                    MethodType.methodType(void.class, boolean.class, int.class, int.class, int.class, boolean.class,
                        boolean.class, int.class, boolean.class, boolean.class));
                configureHotHandle = lookup.findStatic(cacheClass, "configureHotChunks",
                    MethodType.methodType(void.class, boolean.class, int.class, int.class, boolean.class));
                drainStatsHandle = lookup.findStatic(cacheClass, "drainStats",
                    MethodType.methodType(long[].class));
                cacheSizeHandle = lookup.findStatic(cacheClass, "getCacheSize",
                    MethodType.methodType(int.class));
                hookActiveHandle = lookup.findStatic(cacheClass, "isHookActive",
                    MethodType.methodType(boolean.class));
                resolved = true;
                return true;
            } catch (Throwable ignored) {
                resolved = true;
                cacheClass = null;
                return false;
            }
        }
    }
}
