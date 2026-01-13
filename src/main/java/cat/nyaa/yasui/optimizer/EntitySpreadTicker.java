package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * Entity Distance Cache
 *
 * Tracks near vs. distant entities without freezing ticks or AI.
 * This cache is used to gate expensive optimizations while preserving vanilla behavior.
 */
public class EntitySpreadTicker implements Listener {
    private final Yasui plugin;
    private final YasuiConfig config;
    private volatile Object2ObjectMap<UUID, DistanceCategory> distanceCache = Object2ObjectMaps.emptyMap();
    private volatile Object2DoubleMap<UUID> distanceSquaredCache = createDistanceMap(0);
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);
    private volatile List<MobChunkSnapshot> lastMobChunkSnapshots = List.of();
    private volatile long lastSnapshotTimeMs = 0L;
    private BukkitTask distanceScanTask;

    /**
     * Distance category for entities
     */
    public enum DistanceCategory {
        NEAR,      // < configured distance (default 32 blocks) - full vanilla ticking
        DISTANT    // > configured distance - considered for expensive-task caching
    }

    public EntitySpreadTicker(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Start the distance cache system
     */
    public void start() {
        int scanInterval = Math.max(1, config.getSpreadScanInterval());
        distanceScanTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::updateDistanceCategories,
            1L,
            scanInterval
        );

        updateDistanceCategories();
        plugin.getLogger().info("Entity distance cache started (scan interval: " + scanInterval + " ticks)");
    }

    /**
     * Shutdown the distance cache
     */
    public void shutdown() {
        if (distanceScanTask != null) {
            distanceScanTask.cancel();
        }
        distanceCache = Object2ObjectMaps.emptyMap();
        distanceSquaredCache = createDistanceMap(0);
    }

    /**
     * Update distance categories for all entities
     */
    private void updateDistanceCategories() {
        if (!scanRunning.compareAndSet(false, true)) {
            return;
        }

        double nearDistanceSquared = config.getNearDistance() * config.getNearDistance();
        List<WorldSnapshot> worldSnapshots = new ArrayList<>();
        List<MobChunkSnapshot> mobChunkSnapshots = new ArrayList<>();
        int entityCount = 0;

        for (World world : plugin.getServer().getWorlds()) {
            List<Player> players = world.getPlayers();
            List<PlayerSnapshot> playerSnapshots = new ArrayList<>(players.size());
            for (Player player : players) {
                Location loc = player.getLocation();
                playerSnapshots.add(new PlayerSnapshot(loc.getX(), loc.getY(), loc.getZ()));
            }

            List<Entity> entities = world.getEntities();
            List<EntitySnapshot> entitySnapshots = new ArrayList<>(entities.size());
            for (Entity entity : entities) {
                if (entity instanceof Player) {
                    continue;
                }
                Location loc = entity.getLocation();
                entitySnapshots.add(new EntitySnapshot(entity.getUniqueId(), loc.getX(), loc.getY(), loc.getZ()));
                if (entity instanceof Mob) {
                    mobChunkSnapshots.add(new MobChunkSnapshot(
                        world.getUID(),
                        loc.getBlockX() >> 4,
                        loc.getBlockZ() >> 4
                    ));
                }
            }
            worldSnapshots.add(new WorldSnapshot(playerSnapshots, entitySnapshots));
            entityCount += entitySnapshots.size();
        }

        lastMobChunkSnapshots = List.copyOf(mobChunkSnapshots);
        lastSnapshotTimeMs = System.currentTimeMillis();
        int totalEntities = entityCount;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Object2ObjectOpenHashMap<UUID, DistanceCategory> updated =
                new Object2ObjectOpenHashMap<>(Math.max(totalEntities, 16));
            Object2DoubleOpenHashMap<UUID> updatedDistances = createDistanceMap(Math.max(totalEntities, 16));
            try {
                ExecutorService workerPool = plugin.getWorkerPool();
                List<CompletableFuture<WorldResult>> futures = new ArrayList<>(worldSnapshots.size());
                for (WorldSnapshot snapshot : worldSnapshots) {
                    if (workerPool != null && !workerPool.isShutdown()) {
                        futures.add(CompletableFuture.supplyAsync(
                            () -> computeWorld(snapshot, nearDistanceSquared),
                            workerPool
                        ));
                    } else {
                        futures.add(CompletableFuture.completedFuture(
                            computeWorld(snapshot, nearDistanceSquared)
                        ));
                    }
                }
                for (CompletableFuture<WorldResult> future : futures) {
                    try {
                        WorldResult result = future.join();
                        updated.putAll(result.categories());
                        updatedDistances.putAll(result.distances());
                    } catch (CompletionException e) {
                        Throwable cause = e.getCause() == null ? e : e.getCause();
                        plugin.getLogger().log(Level.WARNING, "Entity distance worker failed", cause);
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Entity distance scan failed", t);
            }

            Runnable apply = () -> {
                distanceCache = updated;
                distanceSquaredCache = updatedDistances;
                scanRunning.set(false);
            };
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, apply);
            } else {
                apply.run();
            }
        });
    }

    /**
     * Get distance category for an entity
     */
    public DistanceCategory getDistanceCategory(UUID entityUUID) {
        return distanceCache.getOrDefault(entityUUID, DistanceCategory.NEAR);
    }

    public double getNearestPlayerDistanceSquared(UUID entityUUID) {
        return distanceSquaredCache.getDouble(entityUUID);
    }

    public List<MobChunkSnapshot> getLastMobChunkSnapshots() {
        return lastMobChunkSnapshots;
    }

    public long getLastSnapshotTimeMs() {
        return lastSnapshotTimeMs;
    }

    /**
     * Get statistics for monitoring
     */
    public Stats getStats() {
        long nearCount = distanceCache.values().stream()
            .filter(cat -> cat == DistanceCategory.NEAR)
            .count();

        long distantCount = distanceCache.values().stream()
            .filter(cat -> cat == DistanceCategory.DISTANT)
            .count();

        return new Stats(nearCount, distantCount, distanceCache.size());
    }

    private WorldResult computeWorld(WorldSnapshot snapshot, double nearDistanceSquared) {
        List<PlayerSnapshot> players = snapshot.players();
        List<EntitySnapshot> entities = snapshot.entities();
        Object2ObjectOpenHashMap<UUID, DistanceCategory> categories =
            new Object2ObjectOpenHashMap<>(Math.max(entities.size(), 16));
        Object2DoubleOpenHashMap<UUID> distances = createDistanceMap(Math.max(entities.size(), 16));

        if (players.isEmpty()) {
            for (EntitySnapshot entity : entities) {
                categories.put(entity.uuid(), DistanceCategory.DISTANT);
                distances.put(entity.uuid(), Double.POSITIVE_INFINITY);
            }
            return new WorldResult(categories, distances);
        }

        for (EntitySnapshot entity : entities) {
            double minDistanceSquared = Double.MAX_VALUE;
            for (PlayerSnapshot player : players) {
                double dx = entity.x() - player.x();
                double dy = entity.y() - player.y();
                double dz = entity.z() - player.z();
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (distanceSquared < minDistanceSquared) {
                    minDistanceSquared = distanceSquared;
                }
            }

            DistanceCategory category = minDistanceSquared < nearDistanceSquared
                ? DistanceCategory.NEAR
                : DistanceCategory.DISTANT;
            categories.put(entity.uuid(), category);
            distances.put(entity.uuid(), minDistanceSquared);
        }

        return new WorldResult(categories, distances);
    }

    private record WorldSnapshot(List<PlayerSnapshot> players, List<EntitySnapshot> entities) {}
    private record WorldResult(Object2ObjectMap<UUID, DistanceCategory> categories,
                               Object2DoubleMap<UUID> distances) {}
    private record EntitySnapshot(UUID uuid, double x, double y, double z) {}
    private record PlayerSnapshot(double x, double y, double z) {}
    public record MobChunkSnapshot(UUID worldId, int chunkX, int chunkZ) {}

    public record Stats(long nearEntities, long distantEntities, int trackedEntities) {}

    private static Object2DoubleOpenHashMap<UUID> createDistanceMap(int size) {
        Object2DoubleOpenHashMap<UUID> map = new Object2DoubleOpenHashMap<>(Math.max(0, size));
        map.defaultReturnValue(Double.POSITIVE_INFINITY);
        return map;
    }
}
