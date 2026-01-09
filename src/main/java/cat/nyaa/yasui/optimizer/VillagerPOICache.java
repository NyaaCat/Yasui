package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

/**
 * Villager POI (Point of Interest) Cache Optimizer
 *
 * Optimizes villager AI by caching POI lookups and safely restoring job site memory.
 * Profiler shows villager AI consuming 20.25ms (38% of tick time):
 * - AcquirePoi: 2.02ms (POI searches)
 * - PoiCompetitorScan: 2.35ms (competitor checking)
 * - Brain.startEachNonRunningBehavior: 10.69ms (behavior tree evaluation)
 *
 * Strategy:
 * 1. Cache job site locations observed in villager brain memory
 * 2. If memory is cleared, reapply cached job site when safe
 * 3. Gate optimization via config rules (type/name/distance)
 */
public class VillagerPOICache implements Listener {
    private final Yasui plugin;
    private final YasuiConfig config;
    private final Map<UUID, POICache> cache = new ConcurrentHashMap<>();
    private final Map<String, CachedPOISearchResult> poiSearchCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMemoryRestore = new ConcurrentHashMap<>();
    private final LongAdder restoreAttempts = new LongAdder();
    private final LongAdder restoreApplied = new LongAdder();
    private final long[] rollingRestoreAttempts = new long[60];
    private final long[] rollingRestoreApplied = new long[60];
    private int rollingRestoreIndex = 0;
    private long rollingRestoreBucketStart = alignToMinute(System.currentTimeMillis());
    private final Map<UUID, Integer> brainHooks = new ConcurrentHashMap<>();
    private int currentTick = 0;

    private final Set<Material> poiBlocks = Set.of(
        Material.COMPOSTER,
        Material.BARREL,
        Material.BLAST_FURNACE,
        Material.BREWING_STAND,
        Material.CARTOGRAPHY_TABLE,
        Material.CAULDRON,
        Material.WATER_CAULDRON,
        Material.LAVA_CAULDRON,
        Material.POWDER_SNOW_CAULDRON,
        Material.FLETCHING_TABLE,
        Material.GRINDSTONE,
        Material.LECTERN,
        Material.LOOM,
        Material.SMITHING_TABLE,
        Material.SMOKER,
        Material.STONECUTTER
    );

    // POI search cache TTL
    private static final int POI_SEARCH_CACHE_TTL = 100; // 5 seconds
    private static final long POI_CACHE_MAX_AGE_MS = 5 * 60 * 1000L;
    private static final long MEMORY_RESTORE_COOLDOWN_MS = 5 * 1000L;
    private static final double MAX_RESTORE_DISTANCE_SQUARED = 64 * 64;
    private static final int JOB_SITE_HOOK_PRIORITY = 5;
    private static final long ROLLING_BUCKET_MS = 60 * 1000L;

    private BukkitTask scanTask;
    private BukkitTask tickTask;

    public VillagerPOICache(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * POI cache record
     */
    public record POICache(Location location, int blockHashCode, long timestamp) {}

    /**
     * Cached POI search result
     */
    public record CachedPOISearchResult(List<BlockPos> positions, long timestamp) {}

    /**
     * Initialize the cache and start NMS scanning
     */
    public void initialize() {
        // Start periodic scan to inject cached POI into villager brains
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                scanAndOptimizeVillagers();
            } catch (Exception e) {
                plugin.getLogger().warning("Error during villager optimization: " + e.getMessage());
            }
        }, 20L, 20L); // Scan every 20 ticks (1 second)

        // Start tick counter for cache cleanup
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            currentTick++;
            cleanupExpiredSearchCache();
            updateRollingBuckets(System.currentTimeMillis());
            if (currentTick % 100 == 0) {
                cleanupExpired(POI_CACHE_MAX_AGE_MS);
            }
        }, 1L, 1L); // Every tick

        plugin.getLogger().info("Villager POI optimizer initialized with search caching");
    }

    /**
     * Shutdown the optimizer
     */
    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
        }
        if (tickTask != null) {
            tickTask.cancel();
        }
        clearAll();
    }

    /**
     * Scan and optimize all villagers
     */
    private void scanAndOptimizeVillagers() {
        for (World world : Bukkit.getWorlds()) {
            List<Player> players = world.getPlayers();
            List<Location> playerLocations = new ArrayList<>(players.size());
            for (Player player : players) {
                playerLocations.add(player.getLocation());
            }

            for (Entity entity : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                double nearestDistance = getNearestPlayerDistance(entity, playerLocations);
                if (!shouldOptimize(entity, nearestDistance)) {
                    continue;
                }

                org.bukkit.entity.Villager bukkitVillager = (org.bukkit.entity.Villager) entity;
                UUID uuid = bukkitVillager.getUniqueId();

                try {
                    // Get NMS villager and level
                    CraftVillager craftVillager = (CraftVillager) bukkitVillager;
                    Villager nmsVillager = craftVillager.getHandle();
                    ServerLevel serverLevel = ((CraftWorld) world).getHandle();
                    ensureBrainHooked(bukkitVillager, nmsVillager);

                    // Read and cache existing job site memory (read-only)
                    Optional<GlobalPos> existingJobSite = nmsVillager.getBrain()
                        .getMemory(MemoryModuleType.JOB_SITE);
                    Optional<GlobalPos> existingPotential = Optional.empty();
                    if (existingJobSite.isEmpty()) {
                        existingPotential = nmsVillager.getBrain()
                            .getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
                    }
                    Optional<GlobalPos> existingMemory = existingJobSite.isPresent()
                        ? existingJobSite
                        : existingPotential;

                    if (existingMemory.isPresent()) {
                        // Cache the existing job site (read-only observation)
                        GlobalPos globalPos = existingMemory.get();
                        BlockPos pos = globalPos.pos();
                        Location loc = new Location(
                            world,
                            pos.getX(),
                            pos.getY(),
                            pos.getZ()
                        );

                        // Validate it's still a POI block
                        Block block = loc.getBlock();
                        if (isPOIBlock(block.getType())) {
                            cachePOI(uuid, loc);
                        } else {
                            removePOI(uuid);
                        }
                    }
                    if (existingMemory.isEmpty() && config.isCachePOILookups()) {
                        tryRestoreJobSite(bukkitVillager, nmsVillager, serverLevel, nearestDistance);
                    }
                } catch (Exception e) {
                    // NMS access failed - remove from cache
                    removePOI(uuid);
                }
            }
        }
    }

    /**
     * Cache POI search result for reuse
     */
    public void cacheSearchResult(String searchKey, List<BlockPos> positions) {
        poiSearchCache.put(searchKey, new CachedPOISearchResult(
            new ArrayList<>(positions),
            System.currentTimeMillis()
        ));
    }

    /**
     * Get cached POI search result
     */
    public List<BlockPos> getCachedSearchResult(String searchKey) {
        CachedPOISearchResult result = poiSearchCache.get(searchKey);
        if (result != null) {
            long age = System.currentTimeMillis() - result.timestamp();
            if (age < POI_SEARCH_CACHE_TTL * 50) { // Convert ticks to ms
                return result.positions();
            } else {
                poiSearchCache.remove(searchKey);
            }
        }
        return null;
    }

    /**
     * Clean up expired POI search cache entries
     */
    private void cleanupExpiredSearchCache() {
        if (currentTick % 100 == 0) { // Every 5 seconds
            long now = System.currentTimeMillis();
            poiSearchCache.entrySet().removeIf(entry ->
                (now - entry.getValue().timestamp()) > (POI_SEARCH_CACHE_TTL * 50)
            );
        }
    }

    /**
     * Clear all cached POI data
     */
    public void clearAll() {
        cache.clear();
        poiSearchCache.clear();
        lastMemoryRestore.clear();
        restoreAttempts.reset();
        restoreApplied.reset();
        clearRollingRestoreBuckets();
        brainHooks.clear();
    }

    /**
     * Check if should optimize this entity
     * Simplified: optimize all villagers regardless of naming or distance
     */
    public boolean shouldOptimize(Entity entity, double nearestPlayerDistance) {
        if (entity.getType() != EntityType.VILLAGER) {
            return false;
        }
        boolean hasName = entity.customName() != null;
        return config.shouldOptimizePOI(entity.getType(), hasName, nearestPlayerDistance);
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
        int hashCode = getPoiBlockKey(block.getType()).hashCode();
        cache.put(villagerUUID, new POICache(poiLocation, hashCode, System.currentTimeMillis()));
    }

    /**
     * Validate a cached POI still exists and is unchanged
     */
    public boolean validateCachedPOI(POICache cached) {
        if (cached == null) {
            return false;
        }

        World world = cached.location().getWorld();
        if (world == null) {
            return false;
        }
        int chunkX = cached.location().getBlockX() >> 4;
        int chunkZ = cached.location().getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return true;
        }

        Block block = cached.location().getBlock();
        Material type = block.getType();
        if (!isPOIBlock(type)) {
            return false;
        }
        return getPoiBlockKey(type).hashCode() == cached.blockHashCode();
    }

    /**
     * Check if a block type is a POI block
     */
    public boolean isPOIBlock(Material material) {
        return poiBlocks.contains(material);
    }

    private Material getPoiBlockKey(Material material) {
        return switch (material) {
            case WATER_CAULDRON, LAVA_CAULDRON, POWDER_SNOW_CAULDRON -> Material.CAULDRON;
            default -> material;
        };
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
            if (poiCache.location().equals(location)) {
                lastMemoryRestore.remove(entry.getKey());
                return true;
            }
            return false;
        });

        // Invalidate search cache entries near this location
        String locationKey = location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
        poiSearchCache.keySet().removeIf(key -> key.contains(locationKey));
    }

    /**
     * Remove a specific villager from cache
     */
    public void removePOI(UUID villagerUUID) {
        cache.remove(villagerUUID);
        lastMemoryRestore.remove(villagerUUID);
    }

    /**
     * Get cache size for monitoring
     */
    public int getCacheSize() {
        return cache.size();
    }

    public RestoreStats getRestoreStats() {
        return new RestoreStats(restoreAttempts.sum(), restoreApplied.sum());
    }

    public RollingRestoreStats getRollingRestoreStats() {
        updateRollingBuckets(System.currentTimeMillis());
        long attempts = 0;
        long applied = 0;
        for (int i = 0; i < rollingRestoreAttempts.length; i++) {
            attempts += rollingRestoreAttempts[i];
            applied += rollingRestoreApplied[i];
        }
        return new RollingRestoreStats(attempts, applied);
    }

    /**
     * Clean up expired cache entries
     * Can be called periodically to prevent memory buildup
     */
    public void cleanupExpired(long maxAge) {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry ->
            removeIfExpired(entry, now, maxAge)
        );
    }

    private boolean removeIfExpired(Map.Entry<UUID, POICache> entry, long now, long maxAge) {
        if ((now - entry.getValue().timestamp()) > maxAge) {
            lastMemoryRestore.remove(entry.getKey());
            return true;
        }
        return false;
    }

    private boolean tryRestoreJobSite(org.bukkit.entity.Villager bukkitVillager, Villager nmsVillager, ServerLevel level, double nearestDistance) {
        POICache cached = getRestoreCandidate(bukkitVillager, nmsVillager, nearestDistance);
        if (cached == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        Location poiLocation = cached.location();
        BlockPos pos = new BlockPos(poiLocation.getBlockX(), poiLocation.getBlockY(), poiLocation.getBlockZ());
        if (!level.isLoaded(pos)) {
            return false;
        }

        PoiManager poiManager = level.getPoiManager();
        Predicate<Holder<PoiType>> acquirable = getAcquirableJobSitePredicate(nmsVillager);
        Optional<Holder<PoiType>> poiType = poiManager.getType(pos);
        if (poiType.isEmpty() || !acquirable.test(poiType.get())) {
            return false;
        }

        recordRestoreAttempt();
        if (poiManager.take(acquirable, (holder, blockPos) -> blockPos.equals(pos), pos, 1).isEmpty()) {
            return false;
        }

        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
        nmsVillager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, globalPos);
        recordRestoreApplied();
        lastMemoryRestore.put(bukkitVillager.getUniqueId(), now);
        return true;
    }

    private POICache getRestoreCandidate(org.bukkit.entity.Villager bukkitVillager, Villager nmsVillager, double nearestDistance) {
        if (!config.isCachePOILookups()) {
            return null;
        }
        if (!bukkitVillager.isAdult()) {
            return null;
        }
        if (bukkitVillager.getProfession() == org.bukkit.entity.Villager.Profession.NONE
            || bukkitVillager.getProfession() == org.bukkit.entity.Villager.Profession.NITWIT) {
            return null;
        }

        boolean hasName = bukkitVillager.customName() != null;
        if (!config.shouldOptimizePOI(EntityType.VILLAGER, hasName, nearestDistance)) {
            return null;
        }

        UUID uuid = bukkitVillager.getUniqueId();
        POICache cached = cache.get(uuid);
        if (cached == null || !validateCachedPOI(cached)) {
            return null;
        }

        long now = System.currentTimeMillis();
        if ((now - cached.timestamp()) > POI_CACHE_MAX_AGE_MS) {
            removePOI(uuid);
            return null;
        }

        if ((now - lastMemoryRestore.getOrDefault(uuid, 0L)) < MEMORY_RESTORE_COOLDOWN_MS) {
            return null;
        }

        Location poiLocation = cached.location();
        if (poiLocation.getWorld() == null || !poiLocation.getWorld().equals(bukkitVillager.getWorld())) {
            return null;
        }

        if (poiLocation.distanceSquared(bukkitVillager.getLocation()) > MAX_RESTORE_DISTANCE_SQUARED) {
            return null;
        }

        return cached;
    }

    private Predicate<Holder<PoiType>> getAcquirableJobSitePredicate(Villager villager) {
        return villager.getVillagerData().profession().value().acquirableJobSite();
    }

    private void recordRestoreAttempt() {
        updateRollingBuckets(System.currentTimeMillis());
        restoreAttempts.increment();
        rollingRestoreAttempts[rollingRestoreIndex]++;
    }

    private void recordRestoreApplied() {
        updateRollingBuckets(System.currentTimeMillis());
        restoreApplied.increment();
        rollingRestoreApplied[rollingRestoreIndex]++;
    }

    private void updateRollingBuckets(long now) {
        long elapsed = now - rollingRestoreBucketStart;
        if (elapsed < ROLLING_BUCKET_MS) {
            return;
        }
        int steps = (int) Math.min(rollingRestoreAttempts.length, elapsed / ROLLING_BUCKET_MS);
        for (int i = 0; i < steps; i++) {
            rollingRestoreIndex = (rollingRestoreIndex + 1) % rollingRestoreAttempts.length;
            rollingRestoreAttempts[rollingRestoreIndex] = 0;
            rollingRestoreApplied[rollingRestoreIndex] = 0;
        }
        rollingRestoreBucketStart += (long) steps * ROLLING_BUCKET_MS;
    }

    private void clearRollingRestoreBuckets() {
        for (int i = 0; i < rollingRestoreAttempts.length; i++) {
            rollingRestoreAttempts[i] = 0;
            rollingRestoreApplied[i] = 0;
        }
        rollingRestoreIndex = 0;
        rollingRestoreBucketStart = alignToMinute(System.currentTimeMillis());
    }

    private static long alignToMinute(long now) {
        return (now / ROLLING_BUCKET_MS) * ROLLING_BUCKET_MS;
    }

    private void ensureBrainHooked(org.bukkit.entity.Villager bukkitVillager, Villager nmsVillager) {
        UUID uuid = bukkitVillager.getUniqueId();
        Brain<Villager> brain = nmsVillager.getBrain();
        int brainId = System.identityHashCode(brain);
        Integer current = brainHooks.get(uuid);
        if (current != null && current == brainId) {
            return;
        }

        brainHooks.put(uuid, brainId);
        brain.addActivity(
            Activity.CORE,
            ImmutableList.of(Pair.of(JOB_SITE_HOOK_PRIORITY, new CachedJobSiteBehavior()))
        );
    }

    private final class CachedJobSiteBehavior extends Behavior<Villager> {
        private CachedJobSiteBehavior() {
            super(Map.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.POTENTIAL_JOB_SITE, MemoryStatus.VALUE_ABSENT
            ), 1, 1);
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
            org.bukkit.entity.Villager bukkitVillager = (org.bukkit.entity.Villager) owner.getBukkitEntity();
            double nearestDistance = getNearestPlayerDistanceForHook(owner.getUUID());
            return getRestoreCandidate(bukkitVillager, owner, nearestDistance) != null;
        }

        @Override
        protected void start(ServerLevel level, Villager entity, long gameTime) {
            org.bukkit.entity.Villager bukkitVillager = (org.bukkit.entity.Villager) entity.getBukkitEntity();
            double nearestDistance = getNearestPlayerDistanceForHook(entity.getUUID());
            tryRestoreJobSite(bukkitVillager, entity, level, nearestDistance);
        }
    }

    private double getNearestPlayerDistanceForHook(UUID uuid) {
        if (plugin.getEntitySpread() == null) {
            return Double.MAX_VALUE;
        }
        double distanceSquared = plugin.getEntitySpread().getNearestPlayerDistanceSquared(uuid);
        if (!Double.isFinite(distanceSquared)) {
            return Double.MAX_VALUE;
        }
        return Math.sqrt(distanceSquared);
    }

    private double getNearestPlayerDistance(Entity entity, List<Location> playerLocations) {
        if (playerLocations.isEmpty()) {
            return Double.MAX_VALUE;
        }

        Location entityLocation = entity.getLocation();
        double minDistanceSquared = Double.MAX_VALUE;

        for (Location playerLocation : playerLocations) {
            double distanceSquared = playerLocation.distanceSquared(entityLocation);
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
            }
        }

        return Math.sqrt(minDistanceSquared);
    }

    public record RestoreStats(long attempts, long applied) {}
    public record RollingRestoreStats(long attempts, long applied) {}
}
