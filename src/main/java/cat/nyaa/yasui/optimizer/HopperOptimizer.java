package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hopper Optimizer using hybrid approach:
 * - Event-driven tracking of active hoppers (Bukkit API)
 * - Async pre-computation of container states (Bukkit scheduler)
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
    private BukkitTask asyncTask;

    public HopperOptimizer(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Cache record for hopper state
     */
    public record HopperCache(boolean targetFull, boolean sourceFull, long timestamp) {}

    /**
     * Start the async scanning task
     */
    public void start() {
        int interval = config.getHopperAsyncInterval();

        asyncTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Iterate over active hoppers and pre-compute their states
            for (Location loc : List.copyOf(activeHoppers)) {
                try {
                    Block block = loc.getBlock();

                    // Validate block is still a hopper
                    if (block.getType() != Material.HOPPER) {
                        activeHoppers.remove(loc);
                        cache.remove(loc);
                        continue;
                    }

                    BlockState state = block.getState();
                    if (!(state instanceof Hopper hopper)) {
                        continue;
                    }

                    // Pre-compute container states
                    boolean sourceFull = isInventoryFull(hopper.getInventory());

                    Block targetBlock = block.getRelative(BlockFace.DOWN);
                    boolean targetFull = isContainerFull(targetBlock);

                    // Cache the results
                    cache.put(loc, new HopperCache(targetFull, sourceFull, System.currentTimeMillis()));

                } catch (Exception e) {
                    // Handle world unload or chunk unload gracefully
                    activeHoppers.remove(loc);
                    cache.remove(loc);
                }
            }
        }, 0, interval);

        plugin.getLogger().info("Hopper optimizer started with async interval: " + interval + " ticks");
    }

    /**
     * Shutdown the optimizer
     */
    public void shutdown() {
        if (asyncTask != null) {
            asyncTask.cancel();
        }
        activeHoppers.clear();
        cache.clear();
    }

    /**
     * Track hopper activity via item movement
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        InventoryHolder source = event.getSource().getHolder();
        if (source instanceof Hopper hopper) {
            activeHoppers.add(hopper.getLocation());
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
     * Invalidate cache when inventory changes
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryChange(InventoryEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Hopper hopper) {
            cache.remove(hopper.getLocation());
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

    /**
     * Check if an inventory is full
     */
    private boolean isInventoryFull(Inventory inventory) {
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() < item.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a block container is full
     */
    private boolean isContainerFull(Block block) {
        BlockState state = block.getState();
        if (state instanceof InventoryHolder holder) {
            return isInventoryFull(holder.getInventory());
        }
        return false;
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
