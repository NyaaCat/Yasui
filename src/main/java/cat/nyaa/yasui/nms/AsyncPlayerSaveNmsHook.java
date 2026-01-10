package cat.nyaa.yasui.nms;

import cat.nyaa.yasui.Yasui;

public final class AsyncPlayerSaveNmsHook {
    private static volatile boolean active = false;
    private static volatile String errorMessage;

    private AsyncPlayerSaveNmsHook() {}

    public static boolean install(Yasui plugin) {
        if (!NmsAgentInstaller.install(plugin)) {
            errorMessage = NmsAgentInstaller.getErrorMessage();
            active = false;
            return false;
        }
        active = true;
        return true;
    }

    public static boolean isActive() {
        return active;
    }

    public static String getErrorMessage() {
        return errorMessage;
    }

    public static void configure(boolean playerSaveEnabled,
                                 int workerThreads,
                                 boolean asyncStatsEnabled,
                                 boolean asyncAdvancementsEnabled,
                                 boolean waitOnShutdown,
                                 int shutdownTimeoutSeconds) {
        AsyncPlayerSaveBridge.configure(
            playerSaveEnabled,
            workerThreads,
            asyncStatsEnabled,
            asyncAdvancementsEnabled,
            waitOnShutdown,
            shutdownTimeoutSeconds
        );
    }

    public static void shutdown(boolean wait) {
        AsyncPlayerSaveBridge.shutdown(wait);
    }
}
