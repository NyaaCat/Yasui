package cat.nyaa.yasui.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class TickGroupGateBridge {
    private static final String CLASS_NAME = "cat.nyaa.yasui.hook.TickGroupGate";
    private static volatile boolean resolved = false;
    private static volatile Class<?> gateClass;
    private static volatile MethodHandle configureHandle;
    private static volatile MethodHandle hookActiveHandle;

    private TickGroupGateBridge() {}

    static void configure(boolean enabled, int tickGroups) {
        if (!resolve()) {
            return;
        }
        try {
            configureHandle.invokeWithArguments(enabled, tickGroups);
        } catch (Throwable ignored) {
        }
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
            return gateClass != null;
        }
        synchronized (TickGroupGateBridge.class) {
            if (resolved) {
                return gateClass != null;
            }
            try {
                ClassLoader system = ClassLoader.getSystemClassLoader();
                gateClass = Class.forName(CLASS_NAME, true, system);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                configureHandle = lookup.findStatic(gateClass, "configure",
                    MethodType.methodType(void.class, boolean.class, int.class));
                hookActiveHandle = lookup.findStatic(gateClass, "isHookActive",
                    MethodType.methodType(boolean.class));
                resolved = true;
                return true;
            } catch (Throwable ignored) {
                resolved = true;
                gateClass = null;
                return false;
            }
        }
    }
}
