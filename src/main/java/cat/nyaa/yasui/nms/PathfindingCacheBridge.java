package cat.nyaa.yasui.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class PathfindingCacheBridge {
    private static final String CLASS_NAME = "cat.nyaa.yasui.hook.PathfindingCache";
    private static volatile boolean resolved = false;
    private static volatile Class<?> cacheClass;
    private static volatile MethodHandle configureHandle;
    private static volatile MethodHandle configureHotHandle;
    private static volatile MethodHandle drainStatsHandle;
    private static volatile MethodHandle hookActiveHandle;

    private PathfindingCacheBridge() {}

    static void configure(boolean enabled, int ttlTicks, int ttlJitterTicks, int maxEntriesPerNav,
                          int mobMoveThreshold, int targetMoveThreshold, int negativeTtlTicks) {
        if (!resolve()) {
            return;
        }
        try {
            configureHandle.invokeWithArguments(
                enabled, ttlTicks, ttlJitterTicks, maxEntriesPerNav, mobMoveThreshold, targetMoveThreshold,
                negativeTtlTicks
            );
        } catch (Throwable ignored) {
        }
    }

    static void configureHotChunks(boolean enabled, int ttlTicks, int ttlJitterTicks,
                                   int mobMoveThreshold, int targetMoveThreshold, int negativeTtlTicks) {
        if (!resolve()) {
            return;
        }
        try {
            configureHotHandle.invokeWithArguments(
                enabled, ttlTicks, ttlJitterTicks, mobMoveThreshold, targetMoveThreshold, negativeTtlTicks
            );
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
        synchronized (PathfindingCacheBridge.class) {
            if (resolved) {
                return cacheClass != null;
            }
            try {
                ClassLoader system = ClassLoader.getSystemClassLoader();
                cacheClass = Class.forName(CLASS_NAME, true, system);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                configureHandle = lookup.findStatic(cacheClass, "configure",
                    MethodType.methodType(void.class, boolean.class, int.class, int.class, int.class, int.class,
                        int.class, int.class));
                configureHotHandle = lookup.findStatic(cacheClass, "configureHotChunks",
                    MethodType.methodType(void.class, boolean.class, int.class, int.class, int.class, int.class,
                        int.class));
                drainStatsHandle = lookup.findStatic(cacheClass, "drainStats",
                    MethodType.methodType(long[].class));
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
