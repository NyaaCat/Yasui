package cat.nyaa.yasui.nms;

import cat.nyaa.yasui.Yasui;

public final class SpawnCheckNmsHook {
    private static volatile boolean active = false;
    private static volatile String errorMessage;

    private SpawnCheckNmsHook() {}

    public static boolean install(Yasui plugin) {
        if (!NmsAgentInstaller.install(plugin)) {
            errorMessage = NmsAgentInstaller.getErrorMessage();
            active = false;
            return false;
        }
        active = SpawnCheckCacheBridge.isHookActive();
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

    public static void configure(boolean enabled, int ttlTicks, int maxEntries) {
        SpawnCheckCacheBridge.configure(enabled, ttlTicks, maxEntries);
    }

    public static void configureHotChunks(boolean enabled, int hotTtlTicks) {
        SpawnCheckCacheBridge.configureHotChunks(enabled, hotTtlTicks);
    }

    public static long[] drainStats() {
        return SpawnCheckCacheBridge.drainStats();
    }

    public static int getCacheSize() {
        return SpawnCheckCacheBridge.getCacheSize();
    }

    public static boolean isHookActive() {
        return SpawnCheckCacheBridge.isHookActive();
    }
}
