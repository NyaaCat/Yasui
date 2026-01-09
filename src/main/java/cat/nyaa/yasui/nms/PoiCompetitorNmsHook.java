package cat.nyaa.yasui.nms;

import cat.nyaa.yasui.Yasui;

public final class PoiCompetitorNmsHook {
    private static volatile boolean active = false;
    private static volatile String errorMessage;

    private PoiCompetitorNmsHook() {}

    public static boolean install(Yasui plugin) {
        if (!NmsAgentInstaller.install(plugin)) {
            errorMessage = NmsAgentInstaller.getErrorMessage();
            active = false;
            return false;
        }
        active = PoiCompetitorCacheBridge.isHookActive();
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

    public static void configure(boolean enabled, int ttlTicks, int ttlJitterTicks, int maxEntries, boolean cacheEmptyResults) {
        PoiCompetitorCacheBridge.configure(enabled, ttlTicks, ttlJitterTicks, maxEntries, cacheEmptyResults);
    }

    public static long[] drainStats() {
        return PoiCompetitorCacheBridge.drainStats();
    }

    public static int getCacheSize() {
        return PoiCompetitorCacheBridge.getCacheSize();
    }

    public static boolean isHookActive() {
        return PoiCompetitorCacheBridge.isHookActive();
    }
}
