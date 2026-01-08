package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
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
        double nearDistanceSquared = config.getNearDistance() * config.getNearDistance();
        Map<UUID, DistanceCategory> updated = new HashMap<>();
        Map<UUID, Double> updatedDistances = new HashMap<>();

        for (World world : plugin.getServer().getWorlds()) {
            List<Player> players = world.getPlayers();
            if (players.isEmpty()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Player) {
                        continue;
                    }
                    updated.put(entity.getUniqueId(), DistanceCategory.DISTANT);
                    updatedDistances.put(entity.getUniqueId(), Double.POSITIVE_INFINITY);
                }
                continue;
            }

            List<Location> playerLocations = new ArrayList<>(players.size());
            for (Player player : players) {
                playerLocations.add(player.getLocation());
            }

            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                double nearestDistanceSquared = getNearestPlayerDistanceSquared(entity, playerLocations);
                DistanceCategory category = nearestDistanceSquared < nearDistanceSquared
                    ? DistanceCategory.NEAR
                    : DistanceCategory.DISTANT;
                updated.put(entity.getUniqueId(), category);
                updatedDistances.put(entity.getUniqueId(), nearestDistanceSquared);
            }
        }

        distanceCache.clear();
        distanceCache.putAll(updated);
        distanceSquaredCache.clear();
        distanceSquaredCache.putAll(updatedDistances);
    }

    /**
     * Get nearest player distance squared to an entity
     */
    private double getNearestPlayerDistanceSquared(Entity entity, List<Location> playerLocations) {
        Location entityLocation = entity.getLocation();
        double minDistanceSquared = Double.MAX_VALUE;

        for (Location playerLocation : playerLocations) {
            double distance = playerLocation.distanceSquared(entityLocation);
            if (distance < minDistanceSquared) {
                minDistanceSquared = distance;
            }
        }

        return minDistanceSquared;
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

    public record Stats(long nearEntities, long distantEntities, int trackedEntities) {}
}
