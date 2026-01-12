package cat.nyaa.yasui.nms;

import cat.nyaa.yasui.Yasui;

public final class PoiTypeNmsHook {
    private static volatile boolean active = false;
    private static volatile String errorMessage;

    private PoiTypeNmsHook() {}

    public static boolean install(Yasui plugin) {
        if (!NmsAgentInstaller.install(plugin)) {
            errorMessage = NmsAgentInstaller.getErrorMessage();
            active = false;
            return false;
        }
        active = PoiTypeCacheBridge.isHookActive();
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
                                 boolean cacheEmptyResults, boolean predicateAware, boolean renewOnHit) {
        PoiTypeCacheBridge.configure(
            enabled, ttlTicks, ttlJitterTicks, maxEntries, cacheEmptyResults, predicateAware, renewOnHit
        );
    }

    public static void configureHotChunks(boolean enabled, int ttlTicks, int ttlJitterTicks, boolean renewOnHit) {
        PoiTypeCacheBridge.configureHotChunks(enabled, ttlTicks, ttlJitterTicks, renewOnHit);
    }

    public static long[] drainStats() {
        return PoiTypeCacheBridge.drainStats();
    }

    public static int getCacheSize() {
        return PoiTypeCacheBridge.getCacheSize();
    }

    public static boolean isHookActive() {
        return PoiTypeCacheBridge.isHookActive();
    }
}
