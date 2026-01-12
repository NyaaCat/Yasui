package cat.nyaa.yasui.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class ChunkEpochMapBridge {
    private static final String CLASS_NAME = "cat.nyaa.yasui.hook.ChunkEpochMap";
    private static volatile boolean resolved = false;
    private static volatile Class<?> mapClass;
    private static volatile MethodHandle bumpHandle;
    private static volatile MethodHandle bumpBatchHandle;
    private static volatile MethodHandle clearHandle;

    private ChunkEpochMapBridge() {}

    static void bumpEpoch(Object owner, long chunkKey) {
        if (!resolve()) {
            return;
        }
        try {
            bumpHandle.invokeWithArguments(owner, chunkKey);
        } catch (Throwable ignored) {
        }
    }

    static void bumpEpochs(Object owner, long[] chunkKeys) {
        if (!resolve()) {
            return;
        }
        try {
            bumpBatchHandle.invokeWithArguments(owner, chunkKeys);
        } catch (Throwable ignored) {
        }
    }

    static void clearEpoch(Object owner, long chunkKey) {
        if (!resolve()) {
            return;
        }
        try {
            clearHandle.invokeWithArguments(owner, chunkKey);
        } catch (Throwable ignored) {
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return mapClass != null;
        }
        synchronized (ChunkEpochMapBridge.class) {
            if (resolved) {
                return mapClass != null;
            }
            try {
                ClassLoader system = ClassLoader.getSystemClassLoader();
                mapClass = Class.forName(CLASS_NAME, true, system);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                bumpHandle = lookup.findStatic(mapClass, "bumpEpoch",
                    MethodType.methodType(void.class, Object.class, long.class));
                bumpBatchHandle = lookup.findStatic(mapClass, "bumpEpochs",
                    MethodType.methodType(void.class, Object.class, long[].class));
                clearHandle = lookup.findStatic(mapClass, "clearEpoch",
                    MethodType.methodType(void.class, Object.class, long.class));
                resolved = true;
                return true;
            } catch (Throwable ignored) {
                resolved = true;
                mapClass = null;
                return false;
            }
        }
    }
}
