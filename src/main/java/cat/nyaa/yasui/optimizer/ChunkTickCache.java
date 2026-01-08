package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPhysicsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Chunk Tick Cache Optimizer
 *
 * Caches positions of random-tick eligible blocks per chunk section to reduce
 * repeated position calculations during chunk random ticking.
 *
 * The profiler shows chunk random ticks consuming 2.22ms per tick. By caching
 * the positions of blocks that can receive random ticks, we avoid recalculating
 * these positions every tick.
 *
 * Strategy:
 * - Use WeakHashMap keyed by chunk for automatic garbage collection
 * - Cache list of random-tick eligible block positions per chunk
 * - Invalidate cache on block changes (break/place/physics)
 * - WeakHashMap ensures chunks unloaded by server are cleaned up automatically
 *
 * Note: Full integration requires NMS hooks into ServerLevel.tickChunk().
 * This provides the caching infrastructure.
 */
public class ChunkTickCache implements Listener {
    private final Yasui plugin;
    private final YasuiConfig config;
    private final Map<ChunkKey, List<CachedPosition>> randomTickCache = new WeakHashMap<>();

    public ChunkTickCache(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Chunk key for cache lookup
     */
    private record ChunkKey(String world, int x, int z) {
        static ChunkKey from(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }

        static ChunkKey from(Block block) {
            Chunk chunk = block.getChunk();
            return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
    }

    /**
     * Cached position record
     */
    public record CachedPosition(int x, int y, int z, Material material) {}

    /**
     * Initialize the cache
     */
    public void initialize() {
        plugin.getLogger().info("Chunk tick cache initialized");
    }

    /**
     * Clear all cached data
     */
    public void clearAll() {
        randomTickCache.clear();
    }

    /**
     * Get or build cached random tick positions for a chunk
     */
    public List<CachedPosition> getCachedPositions(Chunk chunk) {
        ChunkKey key = ChunkKey.from(chunk);
        return randomTickCache.computeIfAbsent(key, k -> buildRandomTickPositions(chunk));
    }

    /**
     * Build list of random-tick eligible positions in a chunk
     */
    private List<CachedPosition> buildRandomTickPositions(Chunk chunk) {
        List<CachedPosition> positions = new ArrayList<>();

        // Scan chunk for random-tickable blocks
        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    Material material = block.getType();

                    if (isRandomTickable(material)) {
                        positions.add(new CachedPosition(
                            block.getX(),
                            block.getY(),
                            block.getZ(),
                            material
                        ));
                    }
                }
            }
        }

        return positions;
    }

    /**
     * Check if a block is eligible for random ticks
     * Uses Bukkit's block data API to query the vanilla random tick flag
     */
    private boolean isRandomTickable(Material material) {
        // Skip air and obviously non-tickable materials for performance
        if (material.isAir() || !material.isBlock()) {
            return false;
        }

        // Use the block's default data to check if it receives random ticks
        // This automatically handles all vanilla blocks correctly without hardcoding
        return material.createBlockData().isRandomlyTicked();
    }

    /**
     * Invalidate chunk cache when a block is placed
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ChunkKey key = ChunkKey.from(event.getBlock());
        randomTickCache.remove(key);
    }

    /**
     * Invalidate chunk cache when a block is broken
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ChunkKey key = ChunkKey.from(event.getBlock());
        randomTickCache.remove(key);
    }

    /**
     * Invalidate chunk cache on block physics changes
     * (crop growth, water/lava flow, etc.)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        // Only invalidate for materials that can change random tick state
        Material material = event.getBlock().getType();
        if (isRandomTickable(material) || material == Material.AIR) {
            ChunkKey key = ChunkKey.from(event.getBlock());
            randomTickCache.remove(key);
        }
    }

    /**
     * Get cache size for monitoring
     */
    public int getCacheSize() {
        return randomTickCache.size();
    }

    /**
     * Get total cached positions across all chunks
     */
    public int getTotalCachedPositions() {
        return randomTickCache.values().stream()
            .mapToInt(List::size)
            .sum();
    }
}
