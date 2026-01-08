package cat.nyaa.yasui.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * NMS Utility class for accessing Paper/NMS internals via MethodHandles
 *
 * This class provides fast, cached access to NMS methods without ByteBuddy overhead.
 * Uses MethodHandles which are faster than reflection and don't cause tick time issues.
 */
public class NMSUtil {
    private static final Map<String, MethodHandle> methodHandleCache = new ConcurrentHashMap<>();
    private static final Map<String, MethodHandle> fieldHandleCache = new ConcurrentHashMap<>();

    /**
     * Get a cached MethodHandle for a method
     *
     * @param clazz Target class
     * @param methodName Method name
     * @param paramTypes Parameter types
     * @return MethodHandle for the method
     */
    public static MethodHandle getMethodHandle(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        String key = clazz.getName() + "#" + methodName + "#" + getParamKey(paramTypes);
        return methodHandleCache.computeIfAbsent(key, k -> {
            try {
                Method method = clazz.getDeclaredMethod(methodName, paramTypes);
                method.setAccessible(true);
                return MethodHandles.lookup().unreflect(method);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("Failed to get MethodHandle for " + clazz.getName() + "." + methodName, e);
            }
        });
    }

    /**
     * Get a cached MethodHandle for a field getter
     *
     * @param clazz Target class
     * @param fieldName Field name
     * @return MethodHandle for getting the field
     */
    public static MethodHandle getFieldGetter(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + "#" + fieldName + "#getter";
        return fieldHandleCache.computeIfAbsent(key, k -> {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return MethodHandles.lookup().unreflectGetter(field);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Failed to get field getter for " + clazz.getName() + "." + fieldName, e);
            }
        });
    }

    /**
     * Get a cached MethodHandle for a field setter
     *
     * @param clazz Target class
     * @param fieldName Field name
     * @return MethodHandle for setting the field
     */
    public static MethodHandle getFieldSetter(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + "#" + fieldName + "#setter";
        return fieldHandleCache.computeIfAbsent(key, k -> {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return MethodHandles.lookup().unreflectSetter(field);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Failed to get field setter for " + clazz.getName() + "." + fieldName, e);
            }
        });
    }

    /**
     * Invoke a method handle safely
     *
     * @param handle MethodHandle to invoke
     * @param args Arguments
     * @return Result of invocation
     */
    public static Object invokeHandle(MethodHandle handle, Object... args) {
        try {
            return handle.invokeWithArguments(args);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke MethodHandle", t);
        }
    }

    /**
     * Generate a parameter key for caching
     */
    private static String getParamKey(Class<?>[] paramTypes) {
        if (paramTypes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Class<?> type : paramTypes) {
            sb.append(type.getName()).append(",");
        }
        return sb.toString();
    }

    /**
     * Clear all cached handles (useful for reload)
     */
    public static void clearCache() {
        methodHandleCache.clear();
        fieldHandleCache.clear();
    }

    /**
     * Functional interface for method hooks
     */
    @FunctionalInterface
    public interface MethodHook {
        Object invoke(MethodHandle original, Object... args) throws Throwable;
    }
}
