package cat.nyaa.yasui.nms;

import cat.nyaa.yasui.Yasui;

public final class PoiSearchNmsHook {
    private static volatile boolean active = false;
    private static volatile String errorMessage;

    private PoiSearchNmsHook() {}

    public static boolean install(Yasui plugin) {
        if (!NmsAgentInstaller.install(plugin)) {
            errorMessage = NmsAgentInstaller.getErrorMessage();
            active = false;
            return false;
        }
        active = PoiSearchCacheBridge.isHookActive();
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
                                 boolean cacheEmptyResults, boolean predicateAware) {
        PoiSearchCacheBridge.configure(enabled, ttlTicks, ttlJitterTicks, maxEntries, cacheEmptyResults,
            predicateAware);
    }

    public static long[] drainStats() {
        return PoiSearchCacheBridge.drainStats();
    }

    public static int getCacheSize() {
        return PoiSearchCacheBridge.getCacheSize();
    }

    public static boolean isHookActive() {
        return PoiSearchCacheBridge.isHookActive();
    }
}
