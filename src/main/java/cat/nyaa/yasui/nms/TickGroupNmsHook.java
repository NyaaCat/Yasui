package cat.nyaa.yasui.nms;

import cat.nyaa.yasui.Yasui;

public final class TickGroupNmsHook {
    private static volatile boolean active = false;
    private static volatile String errorMessage;

    private TickGroupNmsHook() {}

    public static boolean install(Yasui plugin) {
        if (!NmsAgentInstaller.install(plugin)) {
            errorMessage = NmsAgentInstaller.getErrorMessage();
            active = false;
            return false;
        }
        active = TickGroupGateBridge.isHookActive();
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

    public static void configure(boolean enabled, int tickGroups) {
        TickGroupGateBridge.configure(enabled, tickGroups);
    }

    public static boolean isHookActive() {
        return TickGroupGateBridge.isHookActive();
    }
}
