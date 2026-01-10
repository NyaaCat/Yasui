package cat.nyaa.yasui.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class AsyncPlayerSaveBridge {
    private static final String CLASS_NAME = "cat.nyaa.yasui.hook.AsyncPlayerSave";
    private static volatile boolean resolved = false;
    private static volatile Class<?> hookClass;
    private static volatile MethodHandle configureHandle;
    private static volatile MethodHandle shutdownHandle;

    private AsyncPlayerSaveBridge() {}

    static void configure(boolean playerSaveEnabled,
                          int workerThreads,
                          boolean asyncStatsEnabled,
                          boolean asyncAdvancementsEnabled,
                          boolean waitOnShutdown,
                          int shutdownTimeoutSeconds) {
        if (!resolve()) {
            return;
        }
        try {
            configureHandle.invokeWithArguments(
                playerSaveEnabled,
                workerThreads,
                asyncStatsEnabled,
                asyncAdvancementsEnabled,
                waitOnShutdown,
                shutdownTimeoutSeconds
            );
        } catch (Throwable ignored) {
        }
    }

    static void shutdown(boolean wait) {
        if (!resolve()) {
            return;
        }
        try {
            shutdownHandle.invokeWithArguments(wait);
        } catch (Throwable ignored) {
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return hookClass != null;
        }
        synchronized (AsyncPlayerSaveBridge.class) {
            if (resolved) {
                return hookClass != null;
            }
            try {
                ClassLoader system = ClassLoader.getSystemClassLoader();
                hookClass = Class.forName(CLASS_NAME, true, system);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                configureHandle = lookup.findStatic(hookClass, "configure",
                    MethodType.methodType(void.class, boolean.class, int.class, boolean.class, boolean.class,
                        boolean.class, int.class));
                shutdownHandle = lookup.findStatic(hookClass, "shutdown",
                    MethodType.methodType(void.class, boolean.class));
                resolved = true;
                return true;
            } catch (Throwable ignored) {
                resolved = true;
                hookClass = null;
                return false;
            }
        }
    }
}
