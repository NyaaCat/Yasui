package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import cat.nyaa.yasui.nms.HotChunkMapBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

    private final Yasui plugin;
    private final YasuiConfig config;
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);
    private final Map<UUID, Map<Long, Float>> heatByWorld = new HashMap<>();
    private final Map<UUID, Map<Long, Integer>> lastCountsByWorld = new HashMap<>();
    private final Map<UUID, Map<Long, Integer>> lastAreaCountsByWorld = new HashMap<>();
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
        Map<Long, Float> heatMap = heatByWorld.get(world.getUID());
        if (heatMap == null) {
            return 0f;
        }
        return heatMap.getOrDefault(packChunkKey(chunkX, chunkZ), 0f);
    }

    public Stats getStats() {
        int trackedChunks = 0;
        float maxHeat = 0f;
        List<HotChunkInfo> topChunks = new ArrayList<>();
        int limit = Math.max(1, DEFAULT_TOP_CHUNKS);

        for (World world : Bukkit.getWorlds()) {
            UUID worldId = world.getUID();
            Map<Long, Float> heatMap = heatByWorld.get(worldId);
            if (heatMap == null || heatMap.isEmpty()) {
                continue;
            }
            trackedChunks += heatMap.size();
            Map<Long, Integer> counts = lastCountsByWorld.getOrDefault(worldId, Collections.emptyMap());
            Map<Long, Integer> areaCounts = lastAreaCountsByWorld.getOrDefault(worldId, counts);
            for (Map.Entry<Long, Float> entry : heatMap.entrySet()) {
                float heat = entry.getValue();
                if (heat > maxHeat) {
                    maxHeat = heat;
                }
                addTopChunk(topChunks, limit, new HotChunkInfo(
                    world.getName(),
                    unpackChunkX(entry.getKey()),
                    unpackChunkZ(entry.getKey()),
                    heat,
                    counts.getOrDefault(entry.getKey(), 0),
                    areaCounts.getOrDefault(entry.getKey(), counts.getOrDefault(entry.getKey(), 0))
                ));
            }
        }

        topChunks.sort(Comparator.comparingDouble(HotChunkInfo::heat).reversed());
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
        int minIndex = 0;
        float minHeat = list.get(0).heat();
        for (int i = 1; i < list.size(); i++) {
            float heat = list.get(i).heat();
            if (heat < minHeat) {
                minHeat = heat;
                minIndex = i;
            }
        }
        if (info.heat() > minHeat) {
            list.set(minIndex, info);
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
            Map<UUID, Map<Long, Integer>> counts = new HashMap<>();
            for (EntitySpreadTicker.MobChunkSnapshot snapshot : snapshots) {
                Map<Long, Integer> worldCounts = counts.computeIfAbsent(snapshot.worldId(), id -> new HashMap<>());
                long key = packChunkKey(snapshot.chunkX(), snapshot.chunkZ());
                worldCounts.merge(key, 1, Integer::sum);
            }
            submitCounts(counts);
        });
    }

    private void submitSnapshots(List<EntityChunkSnapshot> snapshots) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, Map<Long, Integer>> counts = new HashMap<>();
            for (EntityChunkSnapshot snapshot : snapshots) {
                Map<Long, Integer> worldCounts = counts.computeIfAbsent(snapshot.worldId(), id -> new HashMap<>());
                long key = packChunkKey(snapshot.chunkX(), snapshot.chunkZ());
                worldCounts.merge(key, 1, Integer::sum);
            }
            submitCounts(counts);
        });
    }

    private void submitCounts(Map<UUID, Map<Long, Integer>> countsByWorld) {
        int radius = Math.max(0, config.getHotChunkAreaRadius());
        Map<UUID, Map<Long, Integer>> areaCountsByWorld = radius > 0
            ? computeAreaCountsByWorld(countsByWorld, radius)
            : countsByWorld;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                applyCounts(countsByWorld, areaCountsByWorld);
            } finally {
                scanRunning.set(false);
            }
        });
    }

    private Map<UUID, Map<Long, Integer>> computeAreaCountsByWorld(Map<UUID, Map<Long, Integer>> countsByWorld, int radius) {
        Map<UUID, Map<Long, Integer>> areaCounts = new HashMap<>();
        for (Map.Entry<UUID, Map<Long, Integer>> entry : countsByWorld.entrySet()) {
            areaCounts.put(entry.getKey(), computeAreaCounts(entry.getValue(), radius));
        }
        return areaCounts;
    }

    private Map<Long, Integer> computeAreaCounts(Map<Long, Integer> counts, int radius) {
        if (counts.isEmpty() || radius <= 0) {
            return counts;
        }
        Map<Long, Integer> areaCounts = new HashMap<>(counts.size());
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            long key = entry.getKey();
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            int sum = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long neighbor = packChunkKey(chunkX + dx, chunkZ + dz);
                    Integer count = counts.get(neighbor);
                    if (count != null) {
                        sum += count;
                    }
                }
            }
            areaCounts.put(key, sum);
        }
        return areaCounts;
    }

    private void applyCounts(Map<UUID, Map<Long, Integer>> countsByWorld,
                             Map<UUID, Map<Long, Integer>> areaCountsByWorld) {
        float decay = clamp((float) config.getHotChunkHeatDecay(), 0f, 1f);
        float minHeat = clamp((float) config.getHotChunkMinHeat(), 0f, 1f);
        int threshold = Math.max(1, config.getHotChunkMobThreshold());

        Set<UUID> activeWorlds = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            UUID worldId = world.getUID();
            activeWorlds.add(worldId);

            Map<Long, Float> heatMap = heatByWorld.computeIfAbsent(worldId, id -> new HashMap<>());
            Iterator<Map.Entry<Long, Float>> iterator = heatMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, Float> entry = iterator.next();
                float heat = entry.getValue() * decay;
                if (heat < minHeat) {
                    iterator.remove();
                } else {
                    entry.setValue(heat);
                }
            }

            Map<Long, Integer> counts = countsByWorld.getOrDefault(worldId, Collections.emptyMap());
            Map<Long, Integer> areaCounts = areaCountsByWorld.getOrDefault(worldId, counts);
            lastCountsByWorld.put(worldId, counts);
            lastAreaCountsByWorld.put(worldId, areaCounts);
            for (Map.Entry<Long, Integer> entry : areaCounts.entrySet()) {
                float pressure = Math.min(1f, entry.getValue() / (float) threshold);
                float updated = Math.max(pressure, heatMap.getOrDefault(entry.getKey(), 0f));
                if (updated < minHeat) {
                    heatMap.remove(entry.getKey());
                } else {
                    heatMap.put(entry.getKey(), updated);
                }
            }

            updateHookMaps(world, heatMap);
        }

        heatByWorld.keySet().removeIf(id -> !activeWorlds.contains(id));
        lastCountsByWorld.keySet().removeIf(id -> !activeWorlds.contains(id));
        lastAreaCountsByWorld.keySet().removeIf(id -> !activeWorlds.contains(id));
        lastScanTimeMs = System.currentTimeMillis();
    }

    private void updateHookMaps(World world, Map<Long, Float> heatMap) {
        if (heatMap.isEmpty()) {
            clearHookMap(world);
            return;
        }

        List<Map.Entry<Long, Float>> entries = new ArrayList<>(heatMap.entrySet());
        entries.sort(Comparator.comparingLong(Map.Entry::getKey));
        long[] keys = new long[entries.size()];
        float[] heat = new float[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<Long, Float> entry = entries.get(i);
            keys[i] = entry.getKey();
            heat[i] = entry.getValue();
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
            if (areaRadius <= 0) {
                return String.format(Locale.ROOT, "%s (%d,%d) heat=%.2f mobs=%d",
                    worldName, chunkX, chunkZ, heat, mobCount);
            }
            return String.format(Locale.ROOT, "%s (%d,%d) heat=%.2f mobs=%d area=%d",
                worldName, chunkX, chunkZ, heat, mobCount, areaMobCount);
        }
    }

    public record Stats(int hotChunks, int trackedChunks, float maxHeat,
                        int scanIntervalTicks, int mobThreshold, int areaRadius, float minHeat,
                        List<HotChunkInfo> topChunks, long lastScanTimeMs) {}
}
