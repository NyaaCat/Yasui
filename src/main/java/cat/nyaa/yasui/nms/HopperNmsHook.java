package cat.nyaa.yasui.nms;

import cat.nyaa.yasui.Yasui;
import net.minecraft.world.Container;

public final class HopperNmsHook {
    private static volatile boolean active = false;
    private static volatile String errorMessage;

    private HopperNmsHook() {}

    public static boolean install(Yasui plugin) {
        if (!NmsAgentInstaller.install(plugin)) {
            errorMessage = NmsAgentInstaller.getErrorMessage();
            active = false;
            return false;
        }
        active = HopperFullCacheBridge.isHookActive();
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

    public static void configure(boolean enabled, int ttlTicks, boolean cacheNotFull, int cacheNotFullTtlTicks) {
        HopperFullCacheBridge.configure(enabled, ttlTicks, cacheNotFull, cacheNotFullTtlTicks);
    }

    public static void invalidate(Container container) {
        HopperFullCacheBridge.invalidate(container);
    }

    public static long[] drainStats() {
        return HopperFullCacheBridge.drainStats();
    }

    public static boolean isHookActive() {
        return HopperFullCacheBridge.isHookActive();
    }
}
