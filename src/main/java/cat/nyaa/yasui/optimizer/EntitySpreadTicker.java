package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entity Distance Cache
 *
 * Tracks near vs. distant entities without freezing ticks or AI.
 * This cache is used to gate expensive optimizations while preserving vanilla behavior.
 */
public class EntitySpreadTicker implements Listener {
    private final Yasui plugin;
    private final YasuiConfig config;
    private final Map<UUID, DistanceCategory> distanceCache = new ConcurrentHashMap<>();
    private final Map<UUID, Double> distanceSquaredCache = new ConcurrentHashMap<>();
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);
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
        distanceCache.clear();
        distanceSquaredCache.clear();
    }

    /**
     * Update distance categories for all entities
     */
    private void updateDistanceCategories() {
        if (!scanRunning.compareAndSet(false, true)) {
            return;
        }

        double nearDistanceSquared = config.getNearDistance() * config.getNearDistance();
        List<EntitySnapshot> entitySnapshots = new ArrayList<>();
        Map<UUID, List<PlayerSnapshot>> playerSnapshots = new HashMap<>();

        for (World world : plugin.getServer().getWorlds()) {
            List<PlayerSnapshot> players = new ArrayList<>();
            for (Player player : world.getPlayers()) {
                Location loc = player.getLocation();
                players.add(new PlayerSnapshot(loc.getX(), loc.getY(), loc.getZ()));
            }
            playerSnapshots.put(world.getUID(), players);

            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                Location loc = entity.getLocation();
                entitySnapshots.add(new EntitySnapshot(entity.getUniqueId(), world.getUID(), loc.getX(), loc.getY(), loc.getZ()));
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, DistanceCategory> updated = new HashMap<>(entitySnapshots.size());
            Map<UUID, Double> updatedDistances = new HashMap<>(entitySnapshots.size());

            for (EntitySnapshot snapshot : entitySnapshots) {
                List<PlayerSnapshot> players = playerSnapshots.get(snapshot.worldId());
                if (players == null || players.isEmpty()) {
                    updated.put(snapshot.uuid(), DistanceCategory.DISTANT);
                    updatedDistances.put(snapshot.uuid(), Double.POSITIVE_INFINITY);
                    continue;
                }

                double minDistanceSquared = Double.MAX_VALUE;
                for (PlayerSnapshot player : players) {
                    double dx = snapshot.x() - player.x();
                    double dy = snapshot.y() - player.y();
                    double dz = snapshot.z() - player.z();
                    double distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared < minDistanceSquared) {
                        minDistanceSquared = distanceSquared;
                    }
                }

                DistanceCategory category = minDistanceSquared < nearDistanceSquared
                    ? DistanceCategory.NEAR
                    : DistanceCategory.DISTANT;
                updated.put(snapshot.uuid(), category);
                updatedDistances.put(snapshot.uuid(), minDistanceSquared);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    distanceCache.clear();
                    distanceCache.putAll(updated);
                    distanceSquaredCache.clear();
                    distanceSquaredCache.putAll(updatedDistances);
                } finally {
                    scanRunning.set(false);
                }
            });
        });
    }

    /**
     * Get distance category for an entity
     */
    public DistanceCategory getDistanceCategory(UUID entityUUID) {
        return distanceCache.getOrDefault(entityUUID, DistanceCategory.NEAR);
    }

    public double getNearestPlayerDistanceSquared(UUID entityUUID) {
        return distanceSquaredCache.getOrDefault(entityUUID, Double.POSITIVE_INFINITY);
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

    private record EntitySnapshot(UUID uuid, UUID worldId, double x, double y, double z) {}
    private record PlayerSnapshot(double x, double y, double z) {}

    public record Stats(long nearEntities, long distantEntities, int trackedEntities) {}
}
