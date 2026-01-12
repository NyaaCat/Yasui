package cat.nyaa.yasui.nms;

import cat.nyaa.yasui.Yasui;

public final class PoiLookupNmsHook {
    private static volatile boolean active = false;
    private static volatile String errorMessage;

    private PoiLookupNmsHook() {}

    public static boolean install(Yasui plugin) {
        if (!NmsAgentInstaller.install(plugin)) {
            errorMessage = NmsAgentInstaller.getErrorMessage();
            active = false;
            return false;
        }
        active = PoiLookupCacheBridge.isHookActive();
        if (!active) {
            errorMessage = "Agent installed, hook not active";
        }
        return active;
    }

    public static boolean isActive() {
        return active;
    }

    public static String getErrorMessage() {
        return errorMessage;
    }

    public static void configure(boolean enabled, int ttlTicks, int ttlJitterTicks, int maxEntries,
                                 boolean cacheEmptyResults, boolean predicateAware, int sourceBucketSize,
                                 boolean renewOnHit) {
        PoiLookupCacheBridge.configure(
            enabled, ttlTicks, ttlJitterTicks, maxEntries, cacheEmptyResults, predicateAware, sourceBucketSize,
            renewOnHit
        );
    }

    public static void configureHotChunks(boolean enabled, int ttlTicks, int ttlJitterTicks, boolean renewOnHit) {
        PoiLookupCacheBridge.configureHotChunks(enabled, ttlTicks, ttlJitterTicks, renewOnHit);
    }

    public static long[] drainStats() {
        return PoiLookupCacheBridge.drainStats();
    }

    public static int getCacheSize() {
        return PoiLookupCacheBridge.getCacheSize();
    }

    public static boolean isHookActive() {
        return PoiLookupCacheBridge.isHookActive();
    }
}
