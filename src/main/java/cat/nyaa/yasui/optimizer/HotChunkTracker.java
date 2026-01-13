package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import cat.nyaa.yasui.nms.HotChunkMapBridge;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks "hot" chunks based on mob density and publishes heat values to NMS hooks.
 */
public class HotChunkTracker {
    private static final int DEFAULT_TOP_CHUNKS = 3;
    private static final Comparator<HotChunkInfo> TOP_CHUNK_ORDER = Comparator
        .comparingDouble(HotChunkInfo::heat).reversed()
        .thenComparingInt(HotChunkInfo::areaMobCount).reversed()
        .thenComparingInt(HotChunkInfo::mobCount).reversed()
        .thenComparing(HotChunkInfo::worldName)
        .thenComparingInt(HotChunkInfo::chunkX)
        .thenComparingInt(HotChunkInfo::chunkZ);

    private final Yasui plugin;
    private final YasuiConfig config;
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);
    private final Map<UUID, Long2FloatMap> heatByWorld = new HashMap<>();
    private final Map<UUID, Long2IntMap> lastCountsByWorld = new HashMap<>();
    private final Map<UUID, Long2IntMap> lastAreaCountsByWorld = new HashMap<>();
    private BukkitTask scanTask;
    private long lastScanTimeMs = 0L;

    public HotChunkTracker(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        int interval = Math.max(1, config.getHotChunkScanInterval());
        scanTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::scan, 20L, interval);
        scan();
        plugin.getLogger().info("Hot chunk tracker started (scan interval: " + interval + " ticks)");
    }

    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
        }
        heatByWorld.clear();
        lastCountsByWorld.clear();
        lastAreaCountsByWorld.clear();
        lastScanTimeMs = 0L;
        clearHookMaps();
    }

    public boolean isChunkHot(World world, int chunkX, int chunkZ) {
        return getHeat(world, chunkX, chunkZ) >= (float) config.getHotChunkMinHeat();
    }

    public float getHeat(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return 0f;
        }
        Long2FloatMap heatMap = heatByWorld.get(world.getUID());
        if (heatMap == null) {
            return 0f;
        }
        return heatMap.get(packChunkKey(chunkX, chunkZ));
    }

    public Stats getStats() {
        int trackedChunks = 0;
        float maxHeat = 0f;
        List<HotChunkInfo> topChunks = new ArrayList<>();
        int limit = Math.max(1, DEFAULT_TOP_CHUNKS);

        for (World world : Bukkit.getWorlds()) {
            UUID worldId = world.getUID();
            Long2FloatMap heatMap = heatByWorld.get(worldId);
            if (heatMap == null || heatMap.isEmpty()) {
                continue;
            }
            trackedChunks += heatMap.size();
            Long2IntMap counts = lastCountsByWorld.getOrDefault(worldId, Long2IntMaps.EMPTY_MAP);
            Long2IntMap areaCounts = lastAreaCountsByWorld.getOrDefault(worldId, counts);
            for (Long2FloatMap.Entry entry : heatMap.long2FloatEntrySet()) {
                long key = entry.getLongKey();
                float heat = entry.getFloatValue();
                if (heat > maxHeat) {
                    maxHeat = heat;
                }
                int mobCount = counts.get(key);
                int areaMobCount = areaCounts.get(key);
                addTopChunk(topChunks, limit, new HotChunkInfo(
                    world.getName(),
                    unpackChunkX(key),
                    unpackChunkZ(key),
                    heat,
                    mobCount,
                    areaMobCount
                ));
            }
        }

        topChunks.sort(TOP_CHUNK_ORDER);
        return new Stats(
            trackedChunks,
            trackedChunks,
            maxHeat,
            config.getHotChunkScanInterval(),
            config.getHotChunkMobThreshold(),
            config.getHotChunkAreaRadius(),
            (float) config.getHotChunkMinHeat(),
            topChunks,
            lastScanTimeMs
        );
    }

    private void addTopChunk(List<HotChunkInfo> list, int limit, HotChunkInfo info) {
        if (list.size() < limit) {
            list.add(info);
            return;
        }
        int worstIndex = 0;
        for (int i = 1; i < list.size(); i++) {
            if (TOP_CHUNK_ORDER.compare(list.get(i), list.get(worstIndex)) > 0) {
                worstIndex = i;
            }
        }
        if (TOP_CHUNK_ORDER.compare(info, list.get(worstIndex)) < 0) {
            list.set(worstIndex, info);
        }
    }

    private void scan() {
        if (!scanRunning.compareAndSet(false, true)) {
            return;
        }

        EntitySpreadTicker spread = plugin.getEntitySpread();
        if (config.isHotChunkUseSpreadSnapshots() && spread != null) {
            List<EntitySpreadTicker.MobChunkSnapshot> snapshots = spread.getLastMobChunkSnapshots();
            long maxAgeMs = config.getHotChunkSnapshotMaxAgeMs();
            if (snapshots != null && (maxAgeMs <= 0L
                || System.currentTimeMillis() - spread.getLastSnapshotTimeMs() <= maxAgeMs)) {
                submitMobSnapshots(snapshots);
                return;
            }
        }

        List<EntityChunkSnapshot> snapshots = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Mob.class)) {
                if (entity.isDead()) {
                    continue;
                }
                Location location = entity.getLocation();
                snapshots.add(new EntityChunkSnapshot(
                    world.getUID(),
                    location.getBlockX() >> 4,
                    location.getBlockZ() >> 4
                ));
            }
        }
        submitSnapshots(snapshots);
    }

    private void submitMobSnapshots(List<EntitySpreadTicker.MobChunkSnapshot> snapshots) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, Long2IntMap> counts = new HashMap<>();
            for (EntitySpreadTicker.MobChunkSnapshot snapshot : snapshots) {
                Long2IntMap worldCounts = counts.computeIfAbsent(snapshot.worldId(), id -> new Long2IntOpenHashMap());
                long key = packChunkKey(snapshot.chunkX(), snapshot.chunkZ());
                worldCounts.put(key, worldCounts.get(key) + 1);
            }
            submitCounts(counts);
        });
    }

    private void submitSnapshots(List<EntityChunkSnapshot> snapshots) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, Long2IntMap> counts = new HashMap<>();
            for (EntityChunkSnapshot snapshot : snapshots) {
                Long2IntMap worldCounts = counts.computeIfAbsent(snapshot.worldId(), id -> new Long2IntOpenHashMap());
                long key = packChunkKey(snapshot.chunkX(), snapshot.chunkZ());
                worldCounts.put(key, worldCounts.get(key) + 1);
            }
            submitCounts(counts);
        });
    }

    private void submitCounts(Map<UUID, Long2IntMap> countsByWorld) {
        int radius = Math.max(0, config.getHotChunkAreaRadius());
        Map<UUID, Long2IntMap> areaCountsByWorld = radius > 0
            ? computeAreaCountsByWorld(countsByWorld, radius)
            : countsByWorld;
        Runnable apply = () -> {
            try {
                applyCounts(countsByWorld, areaCountsByWorld);
            } finally {
                scanRunning.set(false);
            }
        };
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, apply);
        } else {
            apply.run();
        }
    }

    private Map<UUID, Long2IntMap> computeAreaCountsByWorld(Map<UUID, Long2IntMap> countsByWorld, int radius) {
        Map<UUID, Long2IntMap> areaCounts = new HashMap<>();
        for (Map.Entry<UUID, Long2IntMap> entry : countsByWorld.entrySet()) {
            areaCounts.put(entry.getKey(), computeAreaCounts(entry.getValue(), radius));
        }
        return areaCounts;
    }

    private Long2IntMap computeAreaCounts(Long2IntMap counts, int radius) {
        if (counts.isEmpty() || radius <= 0) {
            return counts;
        }
        Long2IntOpenHashMap areaCounts = new Long2IntOpenHashMap(counts.size());
        LongIterator iterator = counts.keySet().iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            int sum = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long neighbor = packChunkKey(chunkX + dx, chunkZ + dz);
                    sum += counts.get(neighbor);
                }
            }
            areaCounts.put(key, sum);
        }
        return areaCounts;
    }

    private void applyCounts(Map<UUID, Long2IntMap> countsByWorld,
                             Map<UUID, Long2IntMap> areaCountsByWorld) {
        float decay = clamp((float) config.getHotChunkHeatDecay(), 0f, 1f);
        float minHeat = clamp((float) config.getHotChunkMinHeat(), 0f, 1f);
        int threshold = Math.max(1, config.getHotChunkMobThreshold());

        Set<UUID> activeWorlds = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            UUID worldId = world.getUID();
            activeWorlds.add(worldId);

            Long2FloatMap heatMap = heatByWorld.computeIfAbsent(worldId, id -> new Long2FloatOpenHashMap());
            ObjectIterator<Long2FloatMap.Entry> iterator = heatMap.long2FloatEntrySet().fastIterator();
            while (iterator.hasNext()) {
                Long2FloatMap.Entry entry = iterator.next();
                float heat = entry.getFloatValue() * decay;
                if (heat < minHeat) {
                    iterator.remove();
                } else {
                    entry.setValue(heat);
                }
            }

            Long2IntMap counts = countsByWorld.getOrDefault(worldId, Long2IntMaps.EMPTY_MAP);
            Long2IntMap areaCounts = areaCountsByWorld.getOrDefault(worldId, counts);
            lastCountsByWorld.put(worldId, counts);
            lastAreaCountsByWorld.put(worldId, areaCounts);
            for (Long2IntMap.Entry entry : areaCounts.long2IntEntrySet()) {
                long key = entry.getLongKey();
                float pressure = Math.min(1f, entry.getIntValue() / (float) threshold);
                float updated = Math.max(pressure, heatMap.get(key));
                if (updated < minHeat) {
                    heatMap.remove(key);
                } else {
                    heatMap.put(key, updated);
                }
            }

            updateHookMaps(world, heatMap);
        }

        heatByWorld.keySet().removeIf(id -> !activeWorlds.contains(id));
        lastCountsByWorld.keySet().removeIf(id -> !activeWorlds.contains(id));
        lastAreaCountsByWorld.keySet().removeIf(id -> !activeWorlds.contains(id));
        lastScanTimeMs = System.currentTimeMillis();
    }

    private void updateHookMaps(World world, Long2FloatMap heatMap) {
        if (heatMap.isEmpty()) {
            clearHookMap(world);
            return;
        }

        long[] keys = heatMap.keySet().toLongArray();
        Arrays.sort(keys);
        float[] heat = new float[keys.length];
        for (int i = 0; i < keys.length; i++) {
            heat[i] = heatMap.get(keys[i]);
        }

        ServerLevel level = ((CraftWorld) world).getHandle();
        PoiManager poiManager = level.getPoiManager();
        HotChunkMapBridge.update(level, keys, heat);
        HotChunkMapBridge.update(poiManager, keys, heat);
    }

    private void clearHookMaps() {
        for (World world : Bukkit.getWorlds()) {
            clearHookMap(world);
        }
    }

    private void clearHookMap(World world) {
        if (world == null) {
            return;
        }
        ServerLevel level = ((CraftWorld) world).getHandle();
        PoiManager poiManager = level.getPoiManager();
        HotChunkMapBridge.update(level, new long[0], new float[0]);
        HotChunkMapBridge.update(poiManager, new long[0], new float[0]);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long packChunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static int unpackChunkX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackChunkZ(long key) {
        return (int) key;
    }

    private record EntityChunkSnapshot(UUID worldId, int chunkX, int chunkZ) {}

    public record HotChunkInfo(String worldName, int chunkX, int chunkZ, float heat, int mobCount, int areaMobCount) {
        public String format(int areaRadius) {
            int blockX = chunkX << 4;
            int blockZ = chunkZ << 4;
            if (areaRadius <= 0) {
                return String.format(Locale.ROOT, "%s c(%d,%d) b(%d,%d) heat=%.2f mobs=%d",
                    worldName, chunkX, chunkZ, blockX, blockZ, heat, mobCount);
            }
            return String.format(Locale.ROOT, "%s c(%d,%d) b(%d,%d) heat=%.2f mobs=%d area=%d",
                worldName, chunkX, chunkZ, blockX, blockZ, heat, mobCount, areaMobCount);
        }
    }

    public record Stats(int hotChunks, int trackedChunks, float maxHeat,
                        int scanIntervalTicks, int mobThreshold, int areaRadius, float minHeat,
                        List<HotChunkInfo> topChunks, long lastScanTimeMs) {}
}
