package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity Spread Ticker - Distance-Based Optimization
 *
 * Reduces entity ticking load by spreading ticks of distant entities across multiple game ticks.
 * Entities near players (<32 blocks default) tick normally with full vanilla behavior.
 * Entities far from players (>32 blocks) tick less frequently using spread ticking.
 *
 * Key Features:
 * - Distance-based categorization (NEAR vs DISTANT)
 * - Fair distribution using UUID hash to bucket assignment
 * - No starvation - all entities tick eventually
 * - Configurable per entity type and naming status
 * - Preserves full vanilla behavior, just reduces frequency for distant entities
 *
 * Example: 200 distant villagers with interval=4 → 50 per tick instead of 200 per tick
 *
 * Note: This implementation provides the infrastructure. Full integration requires
 * NMS hooks into ServerLevel.tickNonPassenger() as described in the plan.
 */
public class EntitySpreadTicker {
    private final Yasui plugin;
    private final YasuiConfig config;
    private final Map<UUID, TickBucket> tickBuckets = new ConcurrentHashMap<>();
    private final Map<UUID, DistanceCategory> distanceCache = new ConcurrentHashMap<>();
    private int currentTick = 0;
    private BukkitTask tickCounterTask;
    private BukkitTask distanceScanTask;

    public EntitySpreadTicker(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Tick bucket for fair distribution
     */
    public record TickBucket(int bucket) {}

    /**
     * Distance category for entities
     */
    public enum DistanceCategory {
        NEAR,      // < configured distance (default 32 blocks) - full vanilla ticking
        DISTANT    // > configured distance - spread ticking
    }

    /**
     * Start the spread ticker system
     */
    public void start() {
        // Increment tick counter every tick
        tickCounterTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            currentTick++;
        }, 1, 1);

        // Update distance categories periodically (async)
        int scanInterval = config.getSpreadScanInterval();
        distanceScanTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            updateDistanceCategories();
        }, scanInterval, scanInterval);

        plugin.getLogger().info("Entity spread ticker started (scan interval: " + scanInterval + " ticks, near distance: " + config.getNearDistance() + " blocks)");
    }

    /**
     * Shutdown the spread ticker
     */
    public void shutdown() {
        if (tickCounterTask != null) {
            tickCounterTask.cancel();
        }
        if (distanceScanTask != null) {
            distanceScanTask.cancel();
        }
        tickBuckets.clear();
        distanceCache.clear();
    }

    /**
     * Update distance categories for all entities
     */
    private void updateDistanceCategories() {
        double nearDistance = config.getNearDistance();

        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                // Skip players
                if (entity instanceof Player) {
                    continue;
                }

                // Calculate nearest player distance
                double nearestDistance = getNearestPlayerDistance(entity);

                // Categorize based on distance
                DistanceCategory category = nearestDistance < nearDistance
                    ? DistanceCategory.NEAR
                    : DistanceCategory.DISTANT;

                distanceCache.put(entity.getUniqueId(), category);
            }
        }
    }

    /**
     * Get nearest player distance to an entity
     */
    private double getNearestPlayerDistance(Entity entity) {
        double minDistance = Double.MAX_VALUE;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(entity.getWorld())) {
                double distance = player.getLocation().distance(entity.getLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                }
            }
        }

        return minDistance;
    }

    /**
     * Check if an entity should tick this game tick
     *
     * @param entity Entity to check
     * @return true if should tick, false if should skip
     */
    public boolean shouldTickThisTick(Entity entity) {
        UUID uuid = entity.getUniqueId();

        // Check distance category
        DistanceCategory category = distanceCache.getOrDefault(uuid, DistanceCategory.NEAR);

        // Near entities: always tick
        if (category == DistanceCategory.NEAR) {
            return true;
        }

        // Distant entities: check spread interval
        TickBucket bucket = tickBuckets.computeIfAbsent(uuid,
            id -> new TickBucket(Math.abs(id.hashCode())));

        int interval = getSpreadInterval(entity);

        // Check if this entity should tick this game tick
        return (currentTick % interval) == (bucket.bucket() % interval);
    }

    /**
     * Get spread interval for an entity based on config
     */
    private int getSpreadInterval(Entity entity) {
        EntityType type = entity.getType();
        boolean hasName = entity.customName() != null;
        return config.getSpreadInterval(type, hasName);
    }

    /**
     * Get distance category for an entity
     */
    public DistanceCategory getDistanceCategory(UUID entityUUID) {
        return distanceCache.getOrDefault(entityUUID, DistanceCategory.NEAR);
    }

    /**
     * Get current tick count
     */
    public int getCurrentTick() {
        return currentTick;
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

        return new Stats(nearCount, distantCount, tickBuckets.size());
    }

    public record Stats(long nearEntities, long distantEntities, int bucketsUsed) {}
}
