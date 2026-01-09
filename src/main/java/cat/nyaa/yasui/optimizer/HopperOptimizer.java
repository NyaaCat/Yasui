package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import cat.nyaa.yasui.nms.HopperNmsHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hopper Optimizer:
 * - NMS full-check cache to short-circuit isFullContainer
 * - Tracks active hoppers for stats
 */
public class HopperOptimizer implements Listener {
    private static final long ROLLING_BUCKET_MS = 60 * 1000L;

    private final Yasui plugin;
    private final YasuiConfig config;
    private final Set<Location> activeHoppers = ConcurrentHashMap.newKeySet();
    private final long[] rollingFullCacheHits = new long[60];
    private final long[] rollingFullCacheMisses = new long[60];
    private final long[] rollingFullCacheStores = new long[60];
    private final long[] rollingFullCacheInvalidations = new long[60];
    private int rollingIndex = 0;
    private long rollingBucketStart = alignToMinute(System.currentTimeMillis());
    private BukkitTask statsTask;

    public HopperOptimizer(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public record RollingStats(long fullCacheHits, long fullCacheMisses,
                               long fullCacheStores, long fullCacheInvalidations) {}

    public void start() {
        HopperNmsHook.configure(config.isHopperFullCacheEnabled(), config.getHopperFullCacheTtlTicks());
        statsTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushFullCacheStats, 20L, 20L);
        seedActiveHoppers();
        plugin.getLogger().info("Hopper optimizer started");
    }

    public void shutdown() {
        if (statsTask != null) {
            statsTask.cancel();
        }
        activeHoppers.clear();
        clearRollingBuckets();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        trackHopperInventory(event.getSource());
        trackHopperInventory(event.getDestination());

        if (config.isHopperFullCacheEnabled() && config.isHopperFullCacheInvalidateOnEvent()) {
            invalidateFullCache(event.getSource());
            invalidateFullCache(event.getDestination());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperPickupSuccess(InventoryPickupItemEvent event) {
        trackHopperInventory(event.getInventory());

        if (config.isHopperFullCacheEnabled() && config.isHopperFullCacheInvalidateOnEvent()) {
            invalidateFullCache(event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() == Material.HOPPER) {
            activeHoppers.add(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.HOPPER) {
            activeHoppers.remove(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        trackChunkHoppers(event.getChunk(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        trackChunkHoppers(event.getChunk(), false);
    }

    public int getActiveHopperCount() {
        return activeHoppers.size();
    }

    public RollingStats getRollingStats() {
        flushFullCacheStats();
        updateRollingBuckets(System.currentTimeMillis());
        return new RollingStats(
            sumRolling(rollingFullCacheHits),
            sumRolling(rollingFullCacheMisses),
            sumRolling(rollingFullCacheStores),
            sumRolling(rollingFullCacheInvalidations)
        );
    }

    private void trackChunkHoppers(Chunk chunk, boolean add) {
        ChunkAccess handle = ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);
        for (var entry : handle.blockEntities.values()) {
            if (!(entry instanceof HopperBlockEntity hopper)) {
                continue;
            }
            BlockPos pos = hopper.getBlockPos();
            Location loc = new Location(chunk.getWorld(), pos.getX(), pos.getY(), pos.getZ());
            if (add) {
                activeHoppers.add(loc);
            } else {
                activeHoppers.remove(loc);
            }
        }
    }

    private void trackHopperInventory(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        Location loc = inventory.getLocation();
        if (loc != null) {
            Block block = loc.getBlock();
            if (block.getType() == Material.HOPPER) {
                activeHoppers.add(loc);
            }
        }
    }

    private void invalidateFullCache(Inventory inventory) {
        if (!(inventory instanceof CraftInventory craft)) {
            return;
        }
        HopperNmsHook.invalidate(craft.getInventory());
    }

    private void seedActiveHoppers() {
        activeHoppers.clear();
        plugin.getServer().getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) {
                trackChunkHoppers(chunk, true);
            }
        });
    }

    private void flushFullCacheStats() {
        if (!config.isHopperFullCacheEnabled()) {
            return;
        }
        updateRollingBuckets(System.currentTimeMillis());
        long[] stats = HopperNmsHook.drainStats();
        rollingFullCacheHits[rollingIndex] += stats[0];
        rollingFullCacheMisses[rollingIndex] += stats[1];
        rollingFullCacheStores[rollingIndex] += stats[2];
        rollingFullCacheInvalidations[rollingIndex] += stats[3];
    }

    private void updateRollingBuckets(long now) {
        long elapsed = now - rollingBucketStart;
        if (elapsed < ROLLING_BUCKET_MS) {
            return;
        }
        int steps = (int) Math.min(rollingFullCacheHits.length, elapsed / ROLLING_BUCKET_MS);
        for (int i = 0; i < steps; i++) {
            rollingIndex = (rollingIndex + 1) % rollingFullCacheHits.length;
            rollingFullCacheHits[rollingIndex] = 0;
            rollingFullCacheMisses[rollingIndex] = 0;
            rollingFullCacheStores[rollingIndex] = 0;
            rollingFullCacheInvalidations[rollingIndex] = 0;
        }
        rollingBucketStart += (long) steps * ROLLING_BUCKET_MS;
    }

    private void clearRollingBuckets() {
        for (int i = 0; i < rollingFullCacheHits.length; i++) {
            rollingFullCacheHits[i] = 0;
            rollingFullCacheMisses[i] = 0;
            rollingFullCacheStores[i] = 0;
            rollingFullCacheInvalidations[i] = 0;
        }
        rollingIndex = 0;
        rollingBucketStart = alignToMinute(System.currentTimeMillis());
    }

    private long sumRolling(long[] buckets) {
        long total = 0;
        for (long bucket : buckets) {
            total += bucket;
        }
        return total;
    }

    private static long alignToMinute(long now) {
        return (now / ROLLING_BUCKET_MS) * ROLLING_BUCKET_MS;
    }
}
