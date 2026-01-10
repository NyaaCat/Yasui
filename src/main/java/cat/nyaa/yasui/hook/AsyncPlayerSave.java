package cat.nyaa.yasui.hook;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async player save hook (bootstrap-safe: JDK only, NMS via reflection).
 */
public final class AsyncPlayerSave {
    private static volatile boolean playerSaveEnabled = false;
    private static volatile boolean asyncStatsEnabled = false;
    private static volatile boolean asyncAdvancementsEnabled = false;
    private static volatile boolean waitOnShutdown = true;
    private static volatile long shutdownTimeoutMillis = TimeUnit.SECONDS.toMillis(30);
    private static volatile int workerThreads = 2;
    private static volatile ExecutorService executor;
    private static final Set<CompletableFuture<Void>> pending = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Long> latestSeq = new ConcurrentHashMap<>();
    private static final AtomicLong seqCounter = new AtomicLong();

    private static volatile MethodHandle nbtWriteHandle;
    private static volatile MethodHandle safeReplaceHandle;
    private static volatile MethodHandle playerUuidHandle;

    private static volatile MethodHandle statsSaveHandle;
    private static volatile MethodHandle statsToJsonHandle;
    private static volatile Field statsFileField;
    private static volatile MethodHandle fileUtilsWriteHandle;

    private static volatile Field advCodecField;
    private static volatile MethodHandle advAsDataHandle;
    private static volatile Field advPathField;
    private static volatile Field advGsonField;
    private static volatile Object jsonOpsInstance;
    private static volatile MethodHandle codecEncodeStartHandle;
    private static volatile MethodHandle dataResultGetOrThrowHandle;
    private static volatile MethodHandle gsonToJsonHandle;
    private static volatile MethodHandle fileUtilCreateDirsHandle;

    private AsyncPlayerSave() {}

    public static void configure(boolean playerSaveEnabled,
                                 int workerThreads,
                                 boolean asyncStatsEnabled,
                                 boolean asyncAdvancementsEnabled,
                                 boolean waitOnShutdown,
                                 int shutdownTimeoutSeconds) {
        AsyncPlayerSave.playerSaveEnabled = playerSaveEnabled;
        AsyncPlayerSave.asyncStatsEnabled = asyncStatsEnabled;
        AsyncPlayerSave.asyncAdvancementsEnabled = asyncAdvancementsEnabled;
        AsyncPlayerSave.waitOnShutdown = waitOnShutdown;
        AsyncPlayerSave.shutdownTimeoutMillis = TimeUnit.SECONDS.toMillis(Math.max(0, shutdownTimeoutSeconds));
        AsyncPlayerSave.workerThreads = Math.max(1, workerThreads);

        boolean asyncActive = playerSaveEnabled || asyncStatsEnabled || asyncAdvancementsEnabled;
        if (!asyncActive) {
            shutdown(false);
            return;
        }
        ensureExecutor();
    }

    public static void shutdown(boolean wait) {
        ExecutorService exec = executor;
        if (exec == null) {
            return;
        }
        if (wait && waitOnShutdown) {
            waitForPending(shutdownTimeoutMillis);
        }
        exec.shutdown();
        executor = null;
        pending.clear();
    }

    public static void writeCompressed(Object tag, Object path, Object player) {
        if (!playerSaveEnabled) {
            writeCompressedSync(tag, path);
            return;
        }
        if (!(path instanceof Path tempPath)) {
            return;
        }
        String uuid = getPlayerUuid(player);
        if (uuid == null) {
            writeCompressedSync(tag, tempPath);
            return;
        }
        long seq = seqCounter.incrementAndGet();
        latestSeq.put(uuid, seq);
        submit(() -> {
            if (!isLatest(uuid, seq)) {
                deleteTemp(tempPath);
                return;
            }
            if (!writeCompressedSync(tag, tempPath)) {
                deleteTemp(tempPath);
                return;
            }
            if (!isLatest(uuid, seq)) {
                deleteTemp(tempPath);
                return;
            }
            Path parent = tempPath.getParent();
            if (parent == null) {
                return;
            }
            Path current = parent.resolve(uuid + ".dat");
            Path backup = parent.resolve(uuid + ".dat_old");
            safeReplaceSync(current, tempPath, backup);
        });
    }

    public static void safeReplaceFile(Object current, Object latest, Object oldBackup) {
        if (playerSaveEnabled) {
            return;
        }
        safeReplaceSync(current, latest, oldBackup);
    }

    public static void saveStats(Object statsCounter) {
        if (statsCounter == null) {
            return;
        }
        if (!asyncStatsEnabled) {
            invokeStatsSave(statsCounter);
            return;
        }
        String json = invokeStatsToJson(statsCounter);
        File file = getStatsFile(statsCounter);
        if (json == null || file == null) {
            invokeStatsSave(statsCounter);
            return;
        }
        submit(() -> writeStringToFile(file, json));
    }

    public static void saveAdvancements(Object playerAdvancements) {
        if (playerAdvancements == null) {
            return;
        }
        if (!asyncAdvancementsEnabled) {
            invokeAdvSave(playerAdvancements);
            return;
        }
        String json = buildAdvJson(playerAdvancements);
        Path path = getAdvPath(playerAdvancements);
        if (json == null || path == null) {
            invokeAdvSave(playerAdvancements);
            return;
        }
        submit(() -> writeStringToPath(path, json));
    }

    private static void ensureExecutor() {
        ExecutorService existing = executor;
        if (existing != null && workerThreads == ((ThreadPoolExecutor) existing).getCorePoolSize()) {
            return;
        }
        shutdown(false);
        executor = new ThreadPoolExecutor(
            workerThreads,
            workerThreads,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                private final AtomicInteger index = new AtomicInteger();

                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "yasui-player-save-" + index.incrementAndGet());
                    thread.setDaemon(false);
                    return thread;
                }
            }
        );
    }

    private static void submit(Runnable task) {
        ExecutorService exec = executor;
        if (exec == null) {
            task.run();
            return;
        }
        CompletableFuture<Void> future = CompletableFuture.runAsync(task, exec);
        pending.add(future);
        future.whenComplete((result, throwable) -> pending.remove(future));
    }

    private static void waitForPending(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMillis);
        for (CompletableFuture<Void> future : pending) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            try {
                future.get(remaining, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                break;
            }
        }
    }

    private static boolean isLatest(String uuid, long seq) {
        Long current = latestSeq.get(uuid);
        return current != null && current == seq;
    }

    private static String getPlayerUuid(Object player) {
        if (player == null) {
            return null;
        }
        try {
            MethodHandle handle = playerUuidHandle;
            if (handle == null) {
                Method method = player.getClass().getMethod("getStringUUID");
                handle = MethodHandles.publicLookup().unreflect(method);
                playerUuidHandle = handle;
            }
            Object value = handle.invoke(player);
            return value instanceof String str ? str : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean writeCompressedSync(Object tag, Object path) {
        if (!(path instanceof Path tempPath)) {
            return false;
        }
        try {
            MethodHandle handle = nbtWriteHandle;
            if (handle == null) {
                handle = resolveNbtWrite();
                nbtWriteHandle = handle;
            }
            if (handle == null) {
                return false;
            }
            handle.invoke(tag, tempPath);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void safeReplaceSync(Object current, Object latest, Object oldBackup) {
        if (!(current instanceof Path currentPath) || !(latest instanceof Path latestPath) || !(oldBackup instanceof Path backupPath)) {
            return;
        }
        try {
            MethodHandle handle = safeReplaceHandle;
            if (handle == null) {
                handle = resolveSafeReplace();
                safeReplaceHandle = handle;
            }
            if (handle == null) {
                return;
            }
            handle.invoke(currentPath, latestPath, backupPath);
        } catch (Throwable ignored) {
        }
    }

    private static MethodHandle resolveNbtWrite() throws Exception {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class<?> nbtIo = Class.forName("net.minecraft.nbt.NbtIo", true, loader);
        Class<?> compoundTag = Class.forName("net.minecraft.nbt.CompoundTag", true, loader);
        return MethodHandles.publicLookup().findStatic(nbtIo, "writeCompressed",
            MethodType.methodType(void.class, compoundTag, Path.class));
    }

    private static MethodHandle resolveSafeReplace() throws Exception {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class<?> util = Class.forName("net.minecraft.Util", true, loader);
        return MethodHandles.publicLookup().findStatic(util, "safeReplaceFile",
            MethodType.methodType(void.class, Path.class, Path.class, Path.class));
    }

    private static void deleteTemp(Path tempPath) {
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException ignored) {
        }
    }

    private static void invokeStatsSave(Object statsCounter) {
        try {
            MethodHandle handle = statsSaveHandle;
            if (handle == null) {
                Method method = statsCounter.getClass().getMethod("save");
                handle = MethodHandles.publicLookup().unreflect(method);
                statsSaveHandle = handle;
            }
            handle.invoke(statsCounter);
        } catch (Throwable ignored) {
        }
    }

    private static String invokeStatsToJson(Object statsCounter) {
        try {
            MethodHandle handle = statsToJsonHandle;
            if (handle == null) {
                Method method = statsCounter.getClass().getDeclaredMethod("toJson");
                method.setAccessible(true);
                handle = MethodHandles.lookup().unreflect(method);
                statsToJsonHandle = handle;
            }
            Object value = handle.invoke(statsCounter);
            return value instanceof String str ? str : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static File getStatsFile(Object statsCounter) {
        try {
            Field field = statsFileField;
            if (field == null) {
                field = statsCounter.getClass().getDeclaredField("file");
                field.setAccessible(true);
                statsFileField = field;
            }
            Object value = field.get(statsCounter);
            return value instanceof File file ? file : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeStringToFile(File file, String content) {
        try {
            MethodHandle handle = fileUtilsWriteHandle;
            if (handle == null) {
                ClassLoader loader = ClassLoader.getSystemClassLoader();
                Class<?> fileUtils = Class.forName("org.apache.commons.io.FileUtils", true, loader);
                Method method = fileUtils.getMethod("writeStringToFile", File.class, String.class);
                handle = MethodHandles.publicLookup().unreflect(method);
                fileUtilsWriteHandle = handle;
            }
            handle.invoke(file, content);
        } catch (Throwable ignored) {
        }
    }

    private static void invokeAdvSave(Object playerAdvancements) {
        try {
            Method method = playerAdvancements.getClass().getMethod("save");
            MethodHandle handle = MethodHandles.publicLookup().unreflect(method);
            handle.invoke(playerAdvancements);
        } catch (Throwable ignored) {
        }
    }

    private static String buildAdvJson(Object playerAdvancements) {
        try {
            Object codec = getAdvCodec(playerAdvancements);
            Object data = getAdvData(playerAdvancements);
            if (codec == null || data == null) {
                return null;
            }
            Object jsonOps = getJsonOpsInstance();
            Object result = getCodecEncodeStart(codec).invoke(codec, jsonOps, data);
            Object jsonElement = getDataResultGetOrThrow().invoke(result);
            Object gson = getAdvGson();
            Object json = getGsonToJson().invoke(gson, jsonElement);
            return json instanceof String str ? str : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getAdvCodec(Object playerAdvancements) throws Exception {
        Field field = advCodecField;
        if (field == null) {
            field = playerAdvancements.getClass().getDeclaredField("codec");
            field.setAccessible(true);
            advCodecField = field;
        }
        return field.get(playerAdvancements);
    }

    private static Object getAdvData(Object playerAdvancements) throws Exception {
        MethodHandle handle = advAsDataHandle;
        if (handle == null) {
            Method method = playerAdvancements.getClass().getDeclaredMethod("asData");
            method.setAccessible(true);
            handle = MethodHandles.lookup().unreflect(method);
            advAsDataHandle = handle;
        }
        try {
            return handle.invoke(playerAdvancements);
        } catch (Throwable t) {
            throw new Exception("Failed to invoke asData()", t);
        }
    }

    private static Object getJsonOpsInstance() throws Exception {
        Object instance = jsonOpsInstance;
        if (instance != null) {
            return instance;
        }
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class<?> jsonOps = Class.forName("com.mojang.serialization.JsonOps", true, loader);
        Field field = jsonOps.getField("INSTANCE");
        instance = field.get(null);
        jsonOpsInstance = instance;
        return instance;
    }

    private static MethodHandle getCodecEncodeStart(Object codec) throws Exception {
        MethodHandle handle = codecEncodeStartHandle;
        if (handle != null) {
            return handle;
        }
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Method method = codec.getClass().getMethod(
            "encodeStart",
            Class.forName("com.mojang.serialization.DynamicOps", true, loader),
            Object.class
        );
        handle = MethodHandles.publicLookup().unreflect(method);
        codecEncodeStartHandle = handle;
        return handle;
    }

    private static MethodHandle getDataResultGetOrThrow() throws Exception {
        MethodHandle handle = dataResultGetOrThrowHandle;
        if (handle != null) {
            return handle;
        }
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class<?> dataResult = Class.forName("com.mojang.serialization.DataResult", true, loader);
        Method method = dataResult.getMethod("getOrThrow");
        handle = MethodHandles.publicLookup().unreflect(method);
        dataResultGetOrThrowHandle = handle;
        return handle;
    }

    private static Object getAdvGson() throws Exception {
        Field field = advGsonField;
        if (field == null) {
            field = Class.forName("net.minecraft.server.PlayerAdvancements", true, ClassLoader.getSystemClassLoader())
                .getDeclaredField("GSON");
            field.setAccessible(true);
            advGsonField = field;
        }
        return field.get(null);
    }

    private static MethodHandle getGsonToJson() throws Exception {
        MethodHandle handle = gsonToJsonHandle;
        if (handle != null) {
            return handle;
        }
        Class<?> gsonClass = Class.forName("com.google.gson.Gson", true, ClassLoader.getSystemClassLoader());
        Method method = gsonClass.getMethod("toJson", Class.forName("com.google.gson.JsonElement"));
        handle = MethodHandles.publicLookup().unreflect(method);
        gsonToJsonHandle = handle;
        return handle;
    }

    private static Path getAdvPath(Object playerAdvancements) {
        try {
            Field field = advPathField;
            if (field == null) {
                field = playerAdvancements.getClass().getDeclaredField("playerSavePath");
                field.setAccessible(true);
                advPathField = field;
            }
            Object value = field.get(playerAdvancements);
            return value instanceof Path path ? path : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeStringToPath(Path path, String content) {
        try {
            ensureAdvDirs(path);
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void ensureAdvDirs(Path path) {
        try {
            MethodHandle handle = fileUtilCreateDirsHandle;
            if (handle == null) {
                ClassLoader loader = ClassLoader.getSystemClassLoader();
                Class<?> fileUtil = Class.forName("net.minecraft.FileUtil", true, loader);
                Method method = fileUtil.getMethod("createDirectoriesSafe", Path.class);
                handle = MethodHandles.publicLookup().unreflect(method);
                fileUtilCreateDirsHandle = handle;
            }
            Path parent = path.getParent();
            if (parent != null) {
                handle.invoke(parent);
            }
        } catch (Throwable ignored) {
        }
    }
}
