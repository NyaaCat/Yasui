package cat.nyaa.yasui.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class HotChunkMapBridge {
    private static final String CLASS_NAME = "cat.nyaa.yasui.hook.HotChunkMap";
    private static volatile boolean resolved = false;
    private static volatile Class<?> mapClass;
    private static volatile MethodHandle updateHandle;

    private HotChunkMapBridge() {}

    public static void update(Object owner, long[] chunkKeys, float[] heat) {
        if (!resolve()) {
            return;
        }
        try {
            updateHandle.invokeWithArguments(owner, chunkKeys, heat);
        } catch (Throwable ignored) {
        }
    }

    private static boolean resolve() {
        if (resolved && mapClass != null) {
            return true;
        }
        synchronized (HotChunkMapBridge.class) {
            if (resolved && mapClass != null) {
                return true;
            }
            try {
                ClassLoader system = ClassLoader.getSystemClassLoader();
                mapClass = Class.forName(CLASS_NAME, true, system);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                updateHandle = lookup.findStatic(mapClass, "update",
                    MethodType.methodType(void.class, Object.class, long[].class, float[].class));
                resolved = true;
                return true;
            } catch (Throwable ignored) {
                mapClass = null;
                return false;
            }
        }
    }
}
