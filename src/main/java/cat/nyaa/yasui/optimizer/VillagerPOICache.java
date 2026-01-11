package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Villager POI (Point of Interest) Cache Optimizer
 *
 * Caches job site locations observed in villager brain memory.
 * When memory is cleared, restores cached job site if it is still valid.
 */
public class VillagerPOICache implements Listener {
    private static final long POI_CACHE_MAX_AGE_MS = 5 * 60 * 1000L;
    private static final long MEMORY_RESTORE_COOLDOWN_MS = 5 * 1000L;
    private static final double MAX_RESTORE_DISTANCE_SQUARED = 64 * 64;
    private static final long ROLLING_BUCKET_MS = 60 * 1000L;

    private final Yasui plugin;
    private final YasuiConfig config;
    private final Map<UUID, POICache> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMemoryRestore = new ConcurrentHashMap<>();
    private final Map<UUID, StaticState> staticStates = new ConcurrentHashMap<>();
    private final long[] rollingRestoreAttempts = new long[60];
    private final long[] rollingRestoreApplied = new long[60];
    private final long[] rollingRestoreCandidates = new long[60];
    private int rollingRestoreIndex = 0;
    private long rollingRestoreBucketStart = alignToMinute(System.currentTimeMillis());
    private long lastCleanupTime = System.currentTimeMillis();
    private final NamespacedKey pdcPoiWorldKey;
    private final NamespacedKey pdcPoiPosKey;
    private final NamespacedKey pdcPoiTypeKey;

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

    private BukkitTask scanTask;

    public VillagerPOICache(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.pdcPoiWorldKey = new NamespacedKey(plugin, "poi_world");
        this.pdcPoiPosKey = new NamespacedKey(plugin, "poi_pos");
        this.pdcPoiTypeKey = new NamespacedKey(plugin, "poi_type");
    }

    /**
     * POI cache record
     */
    public record POICache(Location location, int blockHashCode, long timestamp) {}

    /**
     * Initialize the cache and start scanning
     */
    public void initialize() {
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                scanAndOptimizeVillagers();
            } catch (Exception e) {
                plugin.getLogger().warning("Error during villager optimization: " + e.getMessage());
            }
        }, 20L, 20L); // Scan every 20 ticks (1 second)

        plugin.getLogger().info("Villager POI optimizer initialized");
    }

    /**
     * Shutdown the optimizer
     */
    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
        }
        clearAll();
    }

    /**
     * Scan and optimize all villagers
     */
    private void scanAndOptimizeVillagers() {
        updateRollingBuckets(System.currentTimeMillis());
        cleanupExpiredIfNeeded();

        boolean useSpread = plugin.getEntitySpread() != null;

        for (World world : Bukkit.getWorlds()) {
            List<Player> players = world.getPlayers();
            List<Location> playerLocations = useSpread ? Collections.emptyList() : new ArrayList<>(players.size());
            if (!useSpread) {
                for (Player player : players) {
                    playerLocations.add(player.getLocation());
                }
            }

            for (Entity entity : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                double nearestDistance = getNearestPlayerDistance(entity, playerLocations);
                if (!shouldOptimize(entity, nearestDistance)) {
                    continue;
                }

                org.bukkit.entity.Villager bukkitVillager = (org.bukkit.entity.Villager) entity;
                optimizeVillager(bukkitVillager, nearestDistance, false);
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!config.isHotChunksEnabled() || !config.isHotChunkVillagerPdcEnabled()) {
            return;
        }
        boolean hotChunk = false;
        HotChunkTracker tracker = plugin.getHotChunkTracker();
        if (tracker != null) {
            hotChunk = tracker.isChunkHot(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        }
        if (!hotChunk) {
            int mobCount = 0;
            for (Entity entity : event.getEntities()) {
                if (entity instanceof Mob) {
                    mobCount++;
                }
            }
            if (mobCount < config.getHotChunkMobThreshold()) {
                return;
            }
        }

        List<Location> playerLocations = new ArrayList<>();
        for (Player player : event.getWorld().getPlayers()) {
            playerLocations.add(player.getLocation());
        }

        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof org.bukkit.entity.Villager villager)) {
                continue;
            }
            double nearestDistance = getNearestPlayerDistance(villager, playerLocations);
            if (!shouldOptimize(villager, nearestDistance)) {
                continue;
            }
            optimizeVillager(villager, nearestDistance, true);
        }
    }

    private void cleanupExpiredIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < 5000L) {
            return;
        }
        cleanupExpired(POI_CACHE_MAX_AGE_MS);
        lastCleanupTime = now;
    }

    private void optimizeVillager(org.bukkit.entity.Villager bukkitVillager, double nearestDistance, boolean hotHint) {
        UUID uuid = bukkitVillager.getUniqueId();
        World world = bukkitVillager.getWorld();
        if (world == null) {
            return;
        }
        if (shouldSkipStatic(bukkitVillager, hotHint)) {
            return;
        }
        try {
            CraftVillager craftVillager = (CraftVillager) bukkitVillager;
            Villager nmsVillager = craftVillager.getHandle();
            ServerLevel serverLevel = ((CraftWorld) world).getHandle();

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
                GlobalPos globalPos = existingMemory.get();
                BlockPos pos = globalPos.pos();
                Location loc = new Location(
                    world,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
                );

                Block block = loc.getBlock();
                if (isPOIBlock(block.getType())) {
                    cachePOI(uuid, loc, bukkitVillager, hotHint);
                } else {
                    removePOI(uuid);
                    clearPoiPdc(bukkitVillager);
                }
            } else if (config.isRestoreJobSiteEnabled()) {
                loadPoiFromPdc(bukkitVillager, hotHint);
                tryRestoreJobSite(bukkitVillager, nmsVillager, serverLevel, nearestDistance);
            }
        } catch (Exception e) {
            removePOI(uuid);
        }
    }

    /**
     * Clear all cached POI data
     */
    public void clearAll() {
        cache.clear();
        lastMemoryRestore.clear();
        staticStates.clear();
        clearRollingRestoreBuckets();
    }

    /**
     * Check if should optimize this entity
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
        cachePOI(villagerUUID, poiLocation, null, false);
    }

    private void cachePOI(UUID villagerUUID, Location poiLocation, org.bukkit.entity.Villager villager, boolean hotHint) {
        Block block = poiLocation.getBlock();
        int hashCode = getPoiBlockKey(block.getType()).hashCode();
        cache.put(villagerUUID, new POICache(poiLocation, hashCode, System.currentTimeMillis()));
        if (villager != null && shouldUsePdc(villager, hotHint)) {
            persistPoiPdc(villager, poiLocation);
        }
    }

    private boolean shouldSkipStatic(org.bukkit.entity.Villager villager, boolean hotHint) {
        if (hotHint) {
            return false;
        }
        if (!config.isHotChunksEnabled() || !config.isHotChunkVillagerStaticEnabled() || !config.isHotChunkVillagerPdcEnabled()) {
            staticStates.remove(villager.getUniqueId());
            return false;
        }
        UUID uuid = villager.getUniqueId();
        Location location = villager.getLocation();
        World world = location.getWorld();
        HotChunkTracker tracker = plugin.getHotChunkTracker();
        if (world == null || tracker == null || !tracker.isChunkHot(world, location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            staticStates.remove(uuid);
            return false;
        }
        if (!cache.containsKey(uuid) && !hasPoiPdc(villager)) {
            return false;
        }

        long now = System.currentTimeMillis();
        StaticState state = staticStates.computeIfAbsent(uuid, id -> new StaticState());
        double dx = location.getX() - state.lastX;
        double dy = location.getY() - state.lastY;
        double dz = location.getZ() - state.lastZ;
        double moveThreshold = config.getHotChunkVillagerStaticMoveThreshold();
        if (!state.initialized) {
            state.lastMoveMs = now;
            state.initialized = true;
        } else if ((dx * dx + dy * dy + dz * dz) > (moveThreshold * moveThreshold)) {
            state.lastMoveMs = now;
        }
        state.lastX = location.getX();
        state.lastY = location.getY();
        state.lastZ = location.getZ();

        long stableMs = config.getHotChunkVillagerStaticStableTicks() * 50L;
        long scanIntervalMs = config.getHotChunkVillagerStaticScanIntervalTicks() * 50L;
        boolean isStatic = stableMs <= 0L || (now - state.lastMoveMs) >= stableMs;
        if (isStatic && scanIntervalMs > 0L && (now - state.lastScanMs) < scanIntervalMs) {
            return true;
        }
        state.lastScanMs = now;
        return false;
    }

    private void loadPoiFromPdc(org.bukkit.entity.Villager villager, boolean hotHint) {
        if (!shouldUsePdc(villager, hotHint)) {
            return;
        }
        UUID uuid = villager.getUniqueId();
        if (cache.containsKey(uuid)) {
            return;
        }
        POICache cached = readPoiFromPdc(villager);
        if (cached != null) {
            cache.put(uuid, cached);
        }
    }

    private POICache readPoiFromPdc(org.bukkit.entity.Villager villager) {
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        String worldId = pdc.get(pdcPoiWorldKey, PersistentDataType.STRING);
        Long packedPos = pdc.get(pdcPoiPosKey, PersistentDataType.LONG);
        String typeName = pdc.get(pdcPoiTypeKey, PersistentDataType.STRING);
        if (worldId == null || packedPos == null || typeName == null) {
            return null;
        }

        World world = villager.getWorld();
        if (world == null || !worldId.equals(world.getUID().toString())) {
            clearPoiPdc(villager);
            return null;
        }

        BlockPos pos = BlockPos.of(packedPos);
        Location loc = new Location(world, pos.getX(), pos.getY(), pos.getZ());
        Block block = loc.getBlock();
        Material actualType = block.getType();
        Material expectedType = Material.matchMaterial(typeName);
        if (expectedType == null || !isPOIBlock(actualType)) {
            clearPoiPdc(villager);
            return null;
        }
        if (getPoiBlockKey(actualType) != expectedType) {
            clearPoiPdc(villager);
            return null;
        }

        int hashCode = getPoiBlockKey(actualType).hashCode();
        return new POICache(loc, hashCode, System.currentTimeMillis());
    }

    private void persistPoiPdc(org.bukkit.entity.Villager villager, Location poiLocation) {
        World world = poiLocation.getWorld();
        if (world == null) {
            return;
        }
        Block block = poiLocation.getBlock();
        Material type = getPoiBlockKey(block.getType());
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        pdc.set(pdcPoiWorldKey, PersistentDataType.STRING, world.getUID().toString());
        pdc.set(pdcPoiPosKey, PersistentDataType.LONG, new BlockPos(
            poiLocation.getBlockX(),
            poiLocation.getBlockY(),
            poiLocation.getBlockZ()
        ).asLong());
        pdc.set(pdcPoiTypeKey, PersistentDataType.STRING, type.name());
    }

    private void clearPoiPdc(org.bukkit.entity.Villager villager) {
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        pdc.remove(pdcPoiWorldKey);
        pdc.remove(pdcPoiPosKey);
        pdc.remove(pdcPoiTypeKey);
    }

    private boolean hasPoiPdc(org.bukkit.entity.Villager villager) {
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        return pdc.has(pdcPoiWorldKey, PersistentDataType.STRING)
            && pdc.has(pdcPoiPosKey, PersistentDataType.LONG)
            && pdc.has(pdcPoiTypeKey, PersistentDataType.STRING);
    }

    private boolean shouldUsePdc(org.bukkit.entity.Villager villager, boolean hotHint) {
        if (!config.isHotChunksEnabled() || !config.isHotChunkVillagerPdcEnabled()) {
            return false;
        }
        if (hotHint) {
            return true;
        }
        HotChunkTracker tracker = plugin.getHotChunkTracker();
        if (tracker == null) {
            return false;
        }
        Location location = villager.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        return tracker.isChunkHot(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
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
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (isPOIBlock(type)) {
            invalidatePOIAt(event.getBlock().getLocation());
        }
    }

    /**
     * Invalidate POI cache when a POI block is placed
     */
    @EventHandler(priority = EventPriority.MONITOR)
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
        cache.entrySet().removeIf(entry -> {
            POICache poiCache = entry.getValue();
            if (poiCache.location().equals(location)) {
                lastMemoryRestore.remove(entry.getKey());
                staticStates.remove(entry.getKey());
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity instanceof org.bukkit.entity.Villager villager) {
                    clearPoiPdc(villager);
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Remove a specific villager from cache
     */
    public void removePOI(UUID villagerUUID) {
        cache.remove(villagerUUID);
        lastMemoryRestore.remove(villagerUUID);
        staticStates.remove(villagerUUID);
    }

    /**
     * Get cache size for monitoring
     */
    public int getCacheSize() {
        return cache.size();
    }

    public RollingRestoreStats getRollingRestoreStats() {
        updateRollingBuckets(System.currentTimeMillis());
        long attempts = 0;
        long applied = 0;
        long candidates = 0;
        for (int i = 0; i < rollingRestoreAttempts.length; i++) {
            attempts += rollingRestoreAttempts[i];
            applied += rollingRestoreApplied[i];
            candidates += rollingRestoreCandidates[i];
        }
        return new RollingRestoreStats(attempts, applied, candidates);
    }

    /**
     * Clean up expired cache entries
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
            staticStates.remove(entry.getKey());
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

    private Predicate<Holder<PoiType>> getAcquirableJobSitePredicate(Villager villager) {
        return villager.getVillagerData().profession().value().heldJobSite();
    }

    private POICache getRestoreCandidate(org.bukkit.entity.Villager bukkitVillager, Villager nmsVillager, double nearestDistance) {
        if (!config.isRestoreJobSiteEnabled()) {
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
            removePOI(uuid);
            return null;
        }

        long now = System.currentTimeMillis();
        if ((now - cached.timestamp()) > POI_CACHE_MAX_AGE_MS) {
            removePOI(uuid);
            return null;
        }

        long lastRestore = lastMemoryRestore.getOrDefault(uuid, 0L);
        if (now - lastRestore < MEMORY_RESTORE_COOLDOWN_MS) {
            return null;
        }

        Location poiLocation = cached.location();
        if (poiLocation.getWorld() != bukkitVillager.getWorld()) {
            return null;
        }
        if (poiLocation.distanceSquared(bukkitVillager.getLocation()) > MAX_RESTORE_DISTANCE_SQUARED) {
            return null;
        }

        recordRestoreCandidate();
        return cached;
    }

    private void recordRestoreAttempt() {
        updateRollingBuckets(System.currentTimeMillis());
        rollingRestoreAttempts[rollingRestoreIndex]++;
    }

    private void recordRestoreApplied() {
        updateRollingBuckets(System.currentTimeMillis());
        rollingRestoreApplied[rollingRestoreIndex]++;
    }

    private void recordRestoreCandidate() {
        updateRollingBuckets(System.currentTimeMillis());
        rollingRestoreCandidates[rollingRestoreIndex]++;
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
            rollingRestoreCandidates[rollingRestoreIndex] = 0;
        }
        rollingRestoreBucketStart += (long) steps * ROLLING_BUCKET_MS;
    }

    private void clearRollingRestoreBuckets() {
        for (int i = 0; i < rollingRestoreAttempts.length; i++) {
            rollingRestoreAttempts[i] = 0;
            rollingRestoreApplied[i] = 0;
            rollingRestoreCandidates[i] = 0;
        }
        rollingRestoreIndex = 0;
        rollingRestoreBucketStart = alignToMinute(System.currentTimeMillis());
    }

    private static long alignToMinute(long now) {
        return (now / ROLLING_BUCKET_MS) * ROLLING_BUCKET_MS;
    }

    private double getNearestPlayerDistance(Entity entity, List<Location> playerLocations) {
        EntitySpreadTicker spread = plugin.getEntitySpread();
        if (spread != null) {
            double distanceSquared = spread.getNearestPlayerDistanceSquared(entity.getUniqueId());
            if (Double.isFinite(distanceSquared)) {
                return Math.sqrt(distanceSquared);
            }
        }

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

    public record RollingRestoreStats(long attempts, long applied, long candidates) {}

    private static final class StaticState {
        private boolean initialized = false;
        private double lastX;
        private double lastY;
        private double lastZ;
        private long lastMoveMs;
        private long lastScanMs;
    }
}
