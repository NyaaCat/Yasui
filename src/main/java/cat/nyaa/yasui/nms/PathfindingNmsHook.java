package cat.nyaa.yasui.nms;

import cat.nyaa.yasui.Yasui;

public final class PathfindingNmsHook {
    private static volatile boolean active = false;
    private static volatile String errorMessage;

    private PathfindingNmsHook() {}

    public static boolean install(Yasui plugin) {
        if (!NmsAgentInstaller.install(plugin)) {
            errorMessage = NmsAgentInstaller.getErrorMessage();
            active = false;
            return false;
        }
        active = PathfindingCacheBridge.isHookActive();
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

    public static void configure(boolean enabled, int ttlTicks, int ttlJitterTicks) {
        PathfindingCacheBridge.configure(enabled, ttlTicks, ttlJitterTicks);
    }

    public static long[] drainStats() {
        return PathfindingCacheBridge.drainStats();
    }

    public static boolean isHookActive() {
        return PathfindingCacheBridge.isHookActive();
    }
}
