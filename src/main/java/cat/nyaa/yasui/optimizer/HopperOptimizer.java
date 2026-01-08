package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hopper Optimizer using hybrid approach:
 * - Event-driven tracking of active hoppers (Bukkit API)
 * - Periodic pre-computation of container states (Bukkit scheduler)
 * - Cached results for fast main-thread access
 *
 * This preserves vanilla hopper transfer speeds (8 ticks) while reducing
 * redundant container fullness checks that consume CPU time.
 */
public class HopperOptimizer implements Listener {
    private final Yasui plugin;
    private final YasuiConfig config;
    private final Set<Location> activeHoppers = ConcurrentHashMap.newKeySet();
    private final Map<Location, HopperCache> cache = new ConcurrentHashMap<>();
    private BukkitTask scanTask;
    private final List<Location> scanSnapshot = new ArrayList<>();
    private int scanIndex = 0;
    private int scanInterval = 1;
    private int maxScanPerTick = 200;

    public HopperOptimizer(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Cache record for hopper state
     */
    public record HopperCache(boolean targetFull, boolean sourceFull, long timestamp) {}

    /**
     * Start the periodic scanning task
     * Note: This serves as a fallback for hoppers that haven't had recent activity
     */
    public void start() {
        scanInterval = Math.max(1, config.getHopperAsyncInterval());
        maxScanPerTick = Math.max(1, config.getHopperMaxScanPerTick());

        scanTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runScanTick, 0, 1);

        plugin.getLogger().info("Hopper optimizer started (scan interval: " + scanInterval + " ticks, max per tick: " + maxScanPerTick + ")");
    }

    /**
     * Update cache for a single hopper
     */
    private void updateSingleHopperCache(Location loc) {
        try {
            if (loc.getWorld() == null) {
                activeHoppers.remove(loc);
                cache.remove(loc);
                return;
            }
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            if (!loc.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                activeHoppers.remove(loc);
                cache.remove(loc);
                return;
            }

            Block block = loc.getBlock();

            // Validate block is still a hopper
            if (block.getType() != Material.HOPPER) {
                activeHoppers.remove(loc);
                cache.remove(loc);
                return;
            }

            ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();
            BlockPos pos = new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (!(level.getBlockEntity(pos) instanceof HopperBlockEntity hopperEntity)) {
                return;
            }

            // Pre-compute container states via NMS to avoid costly Bukkit snapshots
            boolean sourceFull = isHopperInventoryFull(hopperEntity);

            Direction facing = level.getBlockState(pos).getValue(HopperBlock.FACING);
            BlockPos targetPos = pos.relative(facing);
            Container target = null;
            if (level.isLoaded(targetPos)) {
                target = HopperBlockEntity.getContainerAt(level, targetPos);
            }
            boolean targetFull = target != null && isContainerFull(target, facing.getOpposite());

            // Cache the results
            cache.put(loc, new HopperCache(targetFull, sourceFull, System.currentTimeMillis()));

        } catch (Exception e) {
            // Handle world unload or chunk unload gracefully
            activeHoppers.remove(loc);
            cache.remove(loc);
        }
    }

    /**
     * Shutdown the optimizer
     */
    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
        }
        activeHoppers.clear();
        cache.clear();
    }

    /**
     * Track hopper activity via item movement
     * CRITICAL: Update cache immediately after successful transfer for fast exchange machines
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        // Check if source is a hopper and track it
        Location sourceLoc = event.getSource().getLocation();
        if (sourceLoc != null && sourceLoc.getBlock().getType() == Material.HOPPER) {
            // Add to active hoppers (will be no-op if already present)
            activeHoppers.add(sourceLoc);

            // Invalidate cache after successful transfer
            cache.remove(sourceLoc);

            // Schedule immediate cache update for this hopper (1 tick delay)
            // This ensures fast-exchange machines get fresh cache data every tick
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                updateSingleHopperCache(sourceLoc);
            }, 1L); // 1 tick = 50ms, faster than 8-tick hopper transfer
        }

        // Also update destination if it's a hopper
        Location destLoc = event.getDestination().getLocation();
        if (destLoc != null && destLoc.getBlock().getType() == Material.HOPPER) {
            // Add to active hoppers
            activeHoppers.add(destLoc);

            // Invalidate cache after successful transfer
            cache.remove(destLoc);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                updateSingleHopperCache(destLoc);
            }, 1L);
        }
    }

    /**
     * Early cancellation of hopper transfers using cache
     * This runs at LOWEST priority to check cache before vanilla logic executes
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHopperTransferOptimization(InventoryMoveItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        // Lightweight check: Use getLocation() instead of getHolder()
        Location sourceLoc = event.getSource().getLocation();
        Location destLoc = event.getDestination().getLocation();

        if (sourceLoc != null && sourceLoc.getBlock().getType() == Material.HOPPER) {
            HopperCache cached = getCachedState(sourceLoc);
            if (cached != null && cached.targetFull()) {
                event.setCancelled(true);
                return;
            }
        }

        if (destLoc != null && destLoc.getBlock().getType() == Material.HOPPER) {
            HopperCache destCache = getCachedState(destLoc);
            if (destCache != null && destCache.sourceFull()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Early cancellation of hopper item pickups using cache
     * Prevents hoppers from attempting to pick up items when full
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHopperPickupOptimization(InventoryPickupItemEvent event) {
        // Lightweight check: Use getLocation() instead of getHolder()
        Location loc = event.getInventory().getLocation();
        if (loc == null) {
            return;
        }

        // If hopper is full, cancel item pickup
        HopperCache cached = getCachedState(loc);
        if (cached != null && cached.sourceFull()) {
            event.setCancelled(true);
        }
    }

    /**
     * Update cache after successful item pickup
     * CRITICAL: For fast exchange machines, update cache every tick
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperPickupSuccess(InventoryPickupItemEvent event) {
        // Check if this is a hopper and track it
        Location loc = event.getInventory().getLocation();
        if (loc != null && loc.getBlock().getType() == Material.HOPPER) {
            // Add to active hoppers (will be no-op if already present)
            activeHoppers.add(loc);

            // Invalidate cache after successful pickup
            cache.remove(loc);

            // Schedule immediate cache update (1 tick delay)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                updateSingleHopperCache(loc);
            }, 1L);
        }
    }

    /**
     * Track hopper placement
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() == Material.HOPPER) {
            activeHoppers.add(event.getBlock().getLocation());
        }
    }

    /**
     * Remove hopper from tracking when broken
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.HOPPER) {
            Location loc = event.getBlock().getLocation();
            activeHoppers.remove(loc);
            cache.remove(loc);
        }
    }

    /**
     * Track all hoppers when chunk loads
     * This ensures we don't miss inactive hoppers
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (state instanceof Hopper hopper) {
                activeHoppers.add(hopper.getLocation());
            }
        }
    }

    /**
     * Clean up hoppers when a chunk unloads
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (state instanceof Hopper hopper) {
                Location loc = hopper.getLocation();
                activeHoppers.remove(loc);
                cache.remove(loc);
            }
        }
    }

    /**
     * Get cached hopper state
     *
     * @param location Hopper location
     * @return Cached state or null if not cached/expired
     */
    public HopperCache getCachedState(Location location) {
        HopperCache cached = cache.get(location);
        if (cached == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - cached.timestamp() > config.getHopperCacheTTL()) {
            cache.remove(location);
            return null;
        }

        return cached;
    }

    private boolean isHopperInventoryFull(Container container) {
        int size = container.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack item = container.getItem(i);
            if (item.isEmpty() || item.getCount() < item.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    private boolean isContainerFull(Container container, Direction direction) {
        int[] slots;
        if (container instanceof WorldlyContainer worldlyContainer) {
            slots = worldlyContainer.getSlotsForFace(direction);
        } else {
            int size = container.getContainerSize();
            slots = new int[size];
            for (int i = 0; i < size; i++) {
                slots[i] = i;
            }
        }

        for (int slot : slots) {
            ItemStack item = container.getItem(slot);
            if (item.isEmpty() || item.getCount() < item.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    private void runScanTick() {
        if (activeHoppers.isEmpty()) {
            scanSnapshot.clear();
            scanIndex = 0;
            return;
        }

        if (scanSnapshot.isEmpty() || scanIndex >= scanSnapshot.size()) {
            rebuildSnapshot();
        }

        if (scanSnapshot.isEmpty()) {
            return;
        }

        int batchSize = (scanSnapshot.size() + scanInterval - 1) / scanInterval;
        batchSize = Math.min(batchSize, maxScanPerTick);

        int processed = 0;
        while (processed < batchSize && scanIndex < scanSnapshot.size()) {
            updateSingleHopperCache(scanSnapshot.get(scanIndex));
            scanIndex++;
            processed++;
        }
    }

    private void rebuildSnapshot() {
        scanSnapshot.clear();
        scanSnapshot.addAll(activeHoppers);
        scanIndex = 0;
    }

    /**
     * Get active hopper count
     */
    public int getActiveHopperCount() {
        return activeHoppers.size();
    }

    /**
     * Get cache size
     */
    public int getCacheSize() {
        return cache.size();
    }
}
