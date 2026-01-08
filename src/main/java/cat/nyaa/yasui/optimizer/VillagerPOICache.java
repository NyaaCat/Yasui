package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Villager POI (Point of Interest) Cache Optimizer
 *
 * Caches POI lookup results for villagers to reduce repeated expensive searches.
 * The profiler shows AcquirePoi behavior consuming 1.08ms per tick, which adds up
 * significantly with large villager populations.
 *
 * Strategy:
 * - Cache POI location and block hash code per villager
 * - Invalidate on POI block changes (break/place)
 * - Validate cached POI still exists before using
 * - Configurable per entity type and naming status
 *
 * Note: Full NMS hooking would require Paper internals. This implementation
 * provides the caching infrastructure that can be integrated with NMS hooks.
 */
public class VillagerPOICache implements Listener {
    private final Yasui plugin;
    private final YasuiConfig config;
    private final Map<UUID, POICache> cache = new ConcurrentHashMap<>();
    private final Set<Material> poiBlocks = Set.of(
        Material.COMPOSTER,
        Material.BARREL,
        Material.BLAST_FURNACE,
        Material.BREWING_STAND,
        Material.CARTOGRAPHY_TABLE,
        Material.CAULDRON,
        Material.FLETCHING_TABLE,
        Material.GRINDSTONE,
        Material.LECTERN,
        Material.LOOM,
        Material.SMITHING_TABLE,
        Material.SMOKER,
        Material.STONECUTTER
    );

    public VillagerPOICache(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * POI cache record
     */
    public record POICache(Location location, int blockHashCode, long timestamp) {}

    /**
     * Initialize the cache
     */
    public void initialize() {
        plugin.getLogger().info("Villager POI cache initialized");
    }

    /**
     * Clear all cached POI data
     */
    public void clearAll() {
        cache.clear();
    }

    /**
     * Check if should optimize this entity
     */
    public boolean shouldOptimize(Entity entity) {
        if (entity.getType() != EntityType.VILLAGER) {
            return false;
        }

        boolean hasName = entity.customName() != null;
        return config.shouldOptimizePOI(EntityType.VILLAGER, hasName);
    }

    /**
     * Get cached POI for a villager
     */
    public POICache getCachedPOI(UUID villagerUUID) {
        return cache.get(villagerUUID);
    }

    /**
     * Cache a POI location for a villager
     */
    public void cachePOI(UUID villagerUUID, Location poiLocation) {
        Block block = poiLocation.getBlock();
        int hashCode = block.getType().hashCode();
        cache.put(villagerUUID, new POICache(poiLocation, hashCode, System.currentTimeMillis()));
    }

    /**
     * Validate a cached POI still exists and is unchanged
     */
    public boolean validateCachedPOI(POICache cached) {
        if (cached == null) {
            return false;
        }

        Block block = cached.location().getBlock();
        return block.getType().hashCode() == cached.blockHashCode() && isPOIBlock(block.getType());
    }

    /**
     * Check if a block type is a POI block
     */
    public boolean isPOIBlock(Material material) {
        return poiBlocks.contains(material);
    }

    /**
     * Invalidate POI cache when a POI block is broken
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (isPOIBlock(type)) {
            invalidatePOIAt(event.getBlock().getLocation());
        }
    }

    /**
     * Invalidate POI cache when a POI block is placed
     * (nearby villagers may want to acquire it)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();
        if (isPOIBlock(type)) {
            invalidatePOIAt(event.getBlock().getLocation());
        }
    }

    /**
     * Invalidate all cached POIs at a specific location
     */
    private void invalidatePOIAt(Location location) {
        // Remove all cache entries pointing to this location
        cache.entrySet().removeIf(entry -> {
            POICache poiCache = entry.getValue();
            return poiCache.location().equals(location);
        });
    }

    /**
     * Remove a specific villager from cache
     */
    public void removePOI(UUID villagerUUID) {
        cache.remove(villagerUUID);
    }

    /**
     * Get cache size for monitoring
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Clean up expired cache entries
     * Can be called periodically to prevent memory buildup
     */
    public void cleanupExpired(long maxAge) {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry ->
            (now - entry.getValue().timestamp()) > maxAge
        );
    }
}
