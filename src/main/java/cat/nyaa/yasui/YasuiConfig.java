package cat.nyaa.yasui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration handler for Yasui plugin
 */
public class YasuiConfig {
    private final Yasui plugin;

    // Hopper settings
    private boolean hopperEnabled;
    private boolean hopperFullCacheEnabled;
    private int hopperFullCacheTtlTicks;
    private boolean hopperFullCacheNegativeEnabled;
    private int hopperFullCacheNegativeTtlTicks;
    private boolean hopperFullCacheInvalidateOnEvent;

    // Villager POI settings
    private boolean villagerPOIEnabled;
    private boolean restoreJobSite;
    private boolean acquirePoiCacheEnabled;
    private int acquirePoiCacheTtlTicks;
    private int acquirePoiCacheTtlJitterTicks;
    private int acquirePoiCacheMaxEntries;
    private boolean acquirePoiCacheEmptyResults;
    private boolean acquirePoiCachePredicateAware;
    private int acquirePoiCacheSourceBucketSize;
    private boolean acquirePoiCacheFallbackOnInsufficient;
    private boolean acquirePoiCacheRenewOnHit;
    private boolean poiCompetitorCacheEnabled;
    private int poiCompetitorCacheTtlTicks;
    private int poiCompetitorCacheTtlJitterTicks;
    private int poiCompetitorCacheMaxEntries;
    private boolean poiCompetitorCacheEmptyResults;
    private boolean poiCompetitorCacheRenewOnHit;
    private boolean poiLookupCacheEnabled;
    private int poiLookupCacheTtlTicks;
    private int poiLookupCacheTtlJitterTicks;
    private int poiLookupCacheMaxEntries;
    private boolean poiLookupCacheEmptyResults;
    private boolean poiLookupCachePredicateAware;
    private int poiLookupCacheSourceBucketSize;
    private boolean poiLookupCacheRenewOnHit;
    private boolean poiTypeCacheEnabled;
    private int poiTypeCacheTtlTicks;
    private int poiTypeCacheTtlJitterTicks;
    private int poiTypeCacheMaxEntries;
    private boolean poiTypeCacheEmptyResults;
    private boolean poiTypeCachePredicateAware;
    private boolean poiTypeCacheRenewOnHit;
    private List<POIRule> poiRules;

    // Entity distance cache settings
    private boolean entitySpreadEnabled;
    private int spreadScanInterval;
    private double nearDistance;

    // Pathfinding cache settings
    private boolean pathfindingCacheEnabled;
    private int pathfindingCacheTtlTicks;
    private int pathfindingCacheTtlJitterTicks;
    private int pathfindingCacheMaxEntriesPerNav;
    private int pathfindingCacheMobMoveThreshold;
    private int pathfindingCacheTargetMoveThreshold;
    private int pathfindingCacheNegativeTtlTicks;
    private boolean chunkEpochEnabled;

    // Block state cache settings
    private boolean blockStateCacheEnabled;
    private int blockStateCacheTtlTicks;
    private int blockStateCacheMaxEntries;

    // Natural spawner settings
    private boolean naturalSpawnerEnabled;

    // Hot chunk settings
    private boolean hotChunksEnabled;
    private int hotChunkScanInterval;
    private int hotChunkMobThreshold;
    private int hotChunkAreaRadius;
    private boolean hotChunkUseSpreadSnapshots;
    private long hotChunkSnapshotMaxAgeMs;
    private double hotChunkHeatDecay;
    private double hotChunkMinHeat;
    private boolean hotChunkBlockStateCacheEnabled;
    private int hotChunkBlockStateCacheTtlTicks;
    private boolean hotChunkVillagerPdcEnabled;
    private boolean hotChunkVillagerStaticEnabled;
    private int hotChunkVillagerStaticStableTicks;
    private double hotChunkVillagerStaticMoveThreshold;
    private int hotChunkVillagerStaticScanIntervalTicks;
    private boolean hotChunkPathfindingBoostEnabled;
    private int hotChunkPathfindingTtlTicks;
    private int hotChunkPathfindingTtlJitterTicks;
    private int hotChunkPathfindingMobMoveThreshold;
    private int hotChunkPathfindingTargetMoveThreshold;
    private int hotChunkPathfindingNegativeTtlTicks;
    private boolean hotChunkAcquirePoiBoostEnabled;
    private int hotChunkAcquirePoiTtlTicks;
    private int hotChunkAcquirePoiTtlJitterTicks;
    private boolean hotChunkAcquirePoiRenewOnHit;
    private boolean hotChunkPoiCompetitorBoostEnabled;
    private int hotChunkPoiCompetitorTtlTicks;
    private int hotChunkPoiCompetitorTtlJitterTicks;
    private boolean hotChunkPoiCompetitorRenewOnHit;
    private boolean hotChunkPoiLookupBoostEnabled;
    private int hotChunkPoiLookupTtlTicks;
    private int hotChunkPoiLookupTtlJitterTicks;
    private boolean hotChunkPoiLookupRenewOnHit;
    private boolean hotChunkPoiTypeBoostEnabled;
    private int hotChunkPoiTypeTtlTicks;
    private int hotChunkPoiTypeTtlJitterTicks;
    private boolean hotChunkPoiTypeRenewOnHit;

    public YasuiConfig(Yasui plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        hopperEnabled = true;
        hopperFullCacheEnabled = true;
        hopperFullCacheTtlTicks = 2;
        hopperFullCacheNegativeEnabled = false;
        hopperFullCacheNegativeTtlTicks = 1;
        hopperFullCacheInvalidateOnEvent = true;

        villagerPOIEnabled = true;
        restoreJobSite = true;
        acquirePoiCacheEnabled = true;
        acquirePoiCacheTtlTicks = 100;
        acquirePoiCacheTtlJitterTicks = 10;
        acquirePoiCacheMaxEntries = 20000;
        acquirePoiCacheEmptyResults = false;
        acquirePoiCachePredicateAware = false;
        acquirePoiCacheSourceBucketSize = 2;
        acquirePoiCacheFallbackOnInsufficient = false;
        acquirePoiCacheRenewOnHit = false;
        poiCompetitorCacheEnabled = true;
        poiCompetitorCacheTtlTicks = 2;
        poiCompetitorCacheTtlJitterTicks = 1;
        poiCompetitorCacheMaxEntries = 10000;
        poiCompetitorCacheEmptyResults = false;
        poiCompetitorCacheRenewOnHit = false;
        poiLookupCacheEnabled = true;
        poiLookupCacheTtlTicks = 100;
        poiLookupCacheTtlJitterTicks = 10;
        poiLookupCacheMaxEntries = 20000;
        poiLookupCacheEmptyResults = false;
        poiLookupCachePredicateAware = true;
        poiLookupCacheSourceBucketSize = 2;
        poiLookupCacheRenewOnHit = false;
        poiTypeCacheEnabled = true;
        poiTypeCacheTtlTicks = 100;
        poiTypeCacheTtlJitterTicks = 10;
        poiTypeCacheMaxEntries = 20000;
        poiTypeCacheEmptyResults = true;
        poiTypeCachePredicateAware = true;
        poiTypeCacheRenewOnHit = false;
        poiRules = new ArrayList<>();

        entitySpreadEnabled = true;
        spreadScanInterval = 100;
        nearDistance = 32.0;

        pathfindingCacheEnabled = true;
        pathfindingCacheTtlTicks = 3;
        pathfindingCacheTtlJitterTicks = 1;
        pathfindingCacheMaxEntriesPerNav = 4;
        pathfindingCacheMobMoveThreshold = 0;
        pathfindingCacheTargetMoveThreshold = 1;
        pathfindingCacheNegativeTtlTicks = 0;
        chunkEpochEnabled = true;

        blockStateCacheEnabled = true;
        blockStateCacheTtlTicks = 20;
        blockStateCacheMaxEntries = 20000;

        naturalSpawnerEnabled = true;

        hotChunksEnabled = true;
        hotChunkScanInterval = 40;
        hotChunkMobThreshold = 16;
        hotChunkAreaRadius = 1;
        hotChunkUseSpreadSnapshots = true;
        hotChunkSnapshotMaxAgeMs = 10000L;
        hotChunkHeatDecay = 0.85;
        hotChunkMinHeat = 0.15;
        hotChunkBlockStateCacheEnabled = true;
        hotChunkBlockStateCacheTtlTicks = 100;
        hotChunkVillagerPdcEnabled = true;
        hotChunkVillagerStaticEnabled = true;
        hotChunkVillagerStaticStableTicks = 100;
        hotChunkVillagerStaticMoveThreshold = 0.1;
        hotChunkVillagerStaticScanIntervalTicks = 200;
        hotChunkPathfindingBoostEnabled = true;
        hotChunkPathfindingTtlTicks = 8;
        hotChunkPathfindingTtlJitterTicks = 3;
        hotChunkPathfindingMobMoveThreshold = 1;
        hotChunkPathfindingTargetMoveThreshold = 2;
        hotChunkPathfindingNegativeTtlTicks = 0;
        hotChunkAcquirePoiBoostEnabled = true;
        hotChunkAcquirePoiTtlTicks = 200;
        hotChunkAcquirePoiTtlJitterTicks = 20;
        hotChunkAcquirePoiRenewOnHit = true;
        hotChunkPoiCompetitorBoostEnabled = true;
        hotChunkPoiCompetitorTtlTicks = 20;
        hotChunkPoiCompetitorTtlJitterTicks = 10;
        hotChunkPoiCompetitorRenewOnHit = true;
        hotChunkPoiLookupBoostEnabled = true;
        hotChunkPoiLookupTtlTicks = 200;
        hotChunkPoiLookupTtlJitterTicks = 20;
        hotChunkPoiLookupRenewOnHit = true;
        hotChunkPoiTypeBoostEnabled = true;
        hotChunkPoiTypeTtlTicks = 200;
        hotChunkPoiTypeTtlJitterTicks = 20;
        hotChunkPoiTypeRenewOnHit = true;

        ConfigurationSection hopperSection = plugin.getConfig().getConfigurationSection("optimizations.hopper");
        if (hopperSection != null) {
            hopperEnabled = hopperSection.getBoolean("enabled", true);
            hopperFullCacheEnabled = hopperSection.getBoolean("full-cache-enabled", true);
            hopperFullCacheTtlTicks = Math.max(0, hopperSection.getInt("full-cache-ttl-ticks", 2));
            hopperFullCacheNegativeEnabled = hopperSection.getBoolean("full-cache-negative-enabled", false);
            hopperFullCacheNegativeTtlTicks = Math.max(0, hopperSection.getInt("full-cache-negative-ttl-ticks", 1));
            hopperFullCacheInvalidateOnEvent = hopperSection.getBoolean("full-cache-invalidate-on-event", true);
        }

        ConfigurationSection poiSection = plugin.getConfig().getConfigurationSection("optimizations.villager-poi");
        if (poiSection != null) {
            villagerPOIEnabled = poiSection.getBoolean("enabled", true);
            restoreJobSite = poiSection.getBoolean("restore-job-site", true);
            ConfigurationSection acquireSection = poiSection.getConfigurationSection("acquire-poi-cache");
            if (acquireSection != null) {
                acquirePoiCacheEnabled = acquireSection.getBoolean("enabled", true);
                acquirePoiCacheTtlTicks = Math.max(0, acquireSection.getInt("ttl-ticks", 100));
                acquirePoiCacheTtlJitterTicks = Math.max(0, acquireSection.getInt("ttl-jitter-ticks", 10));
                acquirePoiCacheMaxEntries = Math.max(0, acquireSection.getInt("max-entries", 20000));
                acquirePoiCacheEmptyResults = acquireSection.getBoolean("cache-empty-results", false);
                acquirePoiCachePredicateAware = acquireSection.getBoolean("predicate-aware", false);
                acquirePoiCacheSourceBucketSize = Math.max(1, acquireSection.getInt("source-bucket-size", 2));
                acquirePoiCacheFallbackOnInsufficient = acquireSection.getBoolean("fallback-on-insufficient", false);
                acquirePoiCacheRenewOnHit = acquireSection.getBoolean("renew-on-hit", false);
            }
            ConfigurationSection competitorSection = poiSection.getConfigurationSection("competitor-scan-cache");
            if (competitorSection != null) {
                poiCompetitorCacheEnabled = competitorSection.getBoolean("enabled", true);
                poiCompetitorCacheTtlTicks = Math.max(0, competitorSection.getInt("ttl-ticks", 2));
                poiCompetitorCacheTtlJitterTicks = Math.max(0, competitorSection.getInt("ttl-jitter-ticks", 1));
                poiCompetitorCacheMaxEntries = Math.max(0, competitorSection.getInt("max-entries", 10000));
                poiCompetitorCacheEmptyResults = competitorSection.getBoolean("cache-empty-results", false);
                poiCompetitorCacheRenewOnHit = competitorSection.getBoolean("renew-on-hit", false);
            }
            ConfigurationSection lookupSection = poiSection.getConfigurationSection("poi-lookup-cache");
            if (lookupSection != null) {
                poiLookupCacheEnabled = lookupSection.getBoolean("enabled", true);
                poiLookupCacheTtlTicks = Math.max(0, lookupSection.getInt("ttl-ticks", 100));
                poiLookupCacheTtlJitterTicks = Math.max(0, lookupSection.getInt("ttl-jitter-ticks", 10));
                poiLookupCacheMaxEntries = Math.max(0, lookupSection.getInt("max-entries", 20000));
                poiLookupCacheEmptyResults = lookupSection.getBoolean("cache-empty-results", false);
                poiLookupCachePredicateAware = lookupSection.getBoolean("predicate-aware", true);
                poiLookupCacheSourceBucketSize = Math.max(1, lookupSection.getInt("source-bucket-size", 2));
                poiLookupCacheRenewOnHit = lookupSection.getBoolean("renew-on-hit", false);
            }
            ConfigurationSection typeSection = poiSection.getConfigurationSection("poi-type-cache");
            if (typeSection != null) {
                poiTypeCacheEnabled = typeSection.getBoolean("enabled", true);
                poiTypeCacheTtlTicks = Math.max(0, typeSection.getInt("ttl-ticks", 100));
                poiTypeCacheTtlJitterTicks = Math.max(0, typeSection.getInt("ttl-jitter-ticks", 10));
                poiTypeCacheMaxEntries = Math.max(0, typeSection.getInt("max-entries", 20000));
                poiTypeCacheEmptyResults = typeSection.getBoolean("cache-empty-results", true);
                poiTypeCachePredicateAware = typeSection.getBoolean("predicate-aware", true);
                poiTypeCacheRenewOnHit = typeSection.getBoolean("renew-on-hit", false);
            }

            poiRules = new ArrayList<>();
            List<Map<?, ?>> rulesRaw = poiSection.getMapList("rules");
            if (rulesRaw != null) {
                for (Map<?, ?> ruleMap : rulesRaw) {
                    try {
                        EntityType type = EntityType.valueOf(readString(ruleMap, "type", "VILLAGER"));
                        boolean named = readBoolean(ruleMap, "named", false);
                        DistanceRule distanceRule = DistanceRule.parse(ruleMap.get("distance"));
                        boolean optimize = readBoolean(ruleMap, "optimize", true);
                        poiRules.add(new POIRule(type, named, distanceRule, optimize));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid entity type in POI rule: " + ruleMap.get("type"));
                    }
                }
            }
        }

        ConfigurationSection spreadSection = plugin.getConfig().getConfigurationSection("optimizations.entity-spread");
        if (spreadSection != null) {
            entitySpreadEnabled = spreadSection.getBoolean("enabled", true);
            spreadScanInterval = spreadSection.getInt("scan-interval", 100);
            nearDistance = spreadSection.getDouble("near-distance", 32.0);
        }

        ConfigurationSection pathSection = plugin.getConfig().getConfigurationSection("optimizations.pathfinding-cache");
        if (pathSection != null) {
            pathfindingCacheEnabled = pathSection.getBoolean("enabled", true);
            pathfindingCacheTtlTicks = Math.max(0, pathSection.getInt("ttl-ticks", 3));
            pathfindingCacheTtlJitterTicks = Math.max(0, pathSection.getInt("ttl-jitter-ticks", 1));
            pathfindingCacheMaxEntriesPerNav = Math.max(1, pathSection.getInt("max-entries-per-nav", 4));
            pathfindingCacheMobMoveThreshold = Math.max(0, pathSection.getInt("mob-move-threshold", 0));
            pathfindingCacheTargetMoveThreshold = Math.max(0, pathSection.getInt("target-move-threshold", 1));
            pathfindingCacheNegativeTtlTicks = Math.max(0, pathSection.getInt("negative-ttl-ticks", 0));
        }

        ConfigurationSection spawnerSection = plugin.getConfig().getConfigurationSection("optimizations.natural-spawner");
        if (spawnerSection != null) {
            naturalSpawnerEnabled = spawnerSection.getBoolean("enabled", true);
            blockStateCacheTtlTicks = Math.max(0, spawnerSection.getInt("blockstate-cache-ttl-ticks", blockStateCacheTtlTicks));
            blockStateCacheMaxEntries = Math.max(0, spawnerSection.getInt("blockstate-cache-max-entries", blockStateCacheMaxEntries));
            blockStateCacheEnabled = naturalSpawnerEnabled;
        }

        ConfigurationSection blockStateSection = plugin.getConfig().getConfigurationSection("optimizations.blockstate-cache");
        if (blockStateSection != null) {
            blockStateCacheEnabled = blockStateSection.getBoolean("enabled", blockStateCacheEnabled);
            blockStateCacheTtlTicks = Math.max(0, blockStateSection.getInt("ttl-ticks", blockStateCacheTtlTicks));
            blockStateCacheMaxEntries = Math.max(0, blockStateSection.getInt("max-entries", blockStateCacheMaxEntries));
        }

        ConfigurationSection epochSection = plugin.getConfig().getConfigurationSection("optimizations.chunk-epoch");
        if (epochSection != null) {
            chunkEpochEnabled = epochSection.getBoolean("enabled", true);
        }

        ConfigurationSection hotSection = plugin.getConfig().getConfigurationSection("optimizations.hot-chunks");
        if (hotSection != null) {
            hotChunksEnabled = hotSection.getBoolean("enabled", true);
            hotChunkScanInterval = Math.max(1, hotSection.getInt("scan-interval", 40));
            hotChunkMobThreshold = Math.max(1, hotSection.getInt("mob-threshold", 16));
            hotChunkAreaRadius = Math.max(0, hotSection.getInt("area-radius", 1));
            hotChunkUseSpreadSnapshots = hotSection.getBoolean("use-entity-spread-snapshots", true);
            hotChunkSnapshotMaxAgeMs = Math.max(0L, hotSection.getLong("snapshot-max-age-ms", 10000L));
            hotChunkHeatDecay = clampDouble(hotSection.getDouble("heat-decay", 0.85), 0.0, 1.0);
            hotChunkMinHeat = clampDouble(hotSection.getDouble("min-heat", 0.15), 0.0, 1.0);

            ConfigurationSection hotBlockStateSection = hotSection.getConfigurationSection("blockstate-cache");
            if (hotBlockStateSection != null) {
                hotChunkBlockStateCacheEnabled = hotBlockStateSection.getBoolean("enabled", true);
                hotChunkBlockStateCacheTtlTicks = Math.max(0, hotBlockStateSection.getInt("ttl-ticks", 100));
            }

            ConfigurationSection pdcSection = hotSection.getConfigurationSection("villager-pdc");
            if (pdcSection != null) {
                hotChunkVillagerPdcEnabled = pdcSection.getBoolean("enabled", true);
            }
            ConfigurationSection villagerStaticSection = hotSection.getConfigurationSection("villager-static");
            if (villagerStaticSection != null) {
                hotChunkVillagerStaticEnabled = villagerStaticSection.getBoolean("enabled", true);
                hotChunkVillagerStaticStableTicks = Math.max(0, villagerStaticSection.getInt("stable-ticks", 100));
                hotChunkVillagerStaticMoveThreshold = Math.max(0.0, villagerStaticSection.getDouble("move-threshold", 0.1));
                hotChunkVillagerStaticScanIntervalTicks = Math.max(1, villagerStaticSection.getInt("scan-interval-ticks", 200));
            }

            ConfigurationSection pathBoostSection = hotSection.getConfigurationSection("pathfinding-cache");
            if (pathBoostSection != null) {
                hotChunkPathfindingBoostEnabled = pathBoostSection.getBoolean("enabled", true);
                hotChunkPathfindingTtlTicks = Math.max(0, pathBoostSection.getInt("ttl-ticks", 8));
                hotChunkPathfindingTtlJitterTicks = Math.max(0, pathBoostSection.getInt("ttl-jitter-ticks", 3));
                hotChunkPathfindingMobMoveThreshold = Math.max(0, pathBoostSection.getInt("mob-move-threshold", 1));
                hotChunkPathfindingTargetMoveThreshold = Math.max(0, pathBoostSection.getInt("target-move-threshold", 2));
                hotChunkPathfindingNegativeTtlTicks = Math.max(0, pathBoostSection.getInt("negative-ttl-ticks", 0));
            }

            ConfigurationSection acquireBoostSection = hotSection.getConfigurationSection("acquire-poi-cache");
            if (acquireBoostSection != null) {
                hotChunkAcquirePoiBoostEnabled = acquireBoostSection.getBoolean("enabled", true);
                hotChunkAcquirePoiTtlTicks = Math.max(0, acquireBoostSection.getInt("ttl-ticks", 200));
                hotChunkAcquirePoiTtlJitterTicks = Math.max(0, acquireBoostSection.getInt("ttl-jitter-ticks", 20));
                hotChunkAcquirePoiRenewOnHit = acquireBoostSection.getBoolean("renew-on-hit", true);
            }

            ConfigurationSection competitorBoostSection = hotSection.getConfigurationSection("competitor-scan-cache");
            if (competitorBoostSection != null) {
                hotChunkPoiCompetitorBoostEnabled = competitorBoostSection.getBoolean("enabled", true);
                hotChunkPoiCompetitorTtlTicks = Math.max(0, competitorBoostSection.getInt("ttl-ticks", 20));
                hotChunkPoiCompetitorTtlJitterTicks = Math.max(0, competitorBoostSection.getInt("ttl-jitter-ticks", 10));
                hotChunkPoiCompetitorRenewOnHit = competitorBoostSection.getBoolean("renew-on-hit", true);
            }

            ConfigurationSection lookupBoostSection = hotSection.getConfigurationSection("poi-lookup-cache");
            if (lookupBoostSection != null) {
                hotChunkPoiLookupBoostEnabled = lookupBoostSection.getBoolean("enabled", true);
                hotChunkPoiLookupTtlTicks = Math.max(0, lookupBoostSection.getInt("ttl-ticks", 200));
                hotChunkPoiLookupTtlJitterTicks = Math.max(0, lookupBoostSection.getInt("ttl-jitter-ticks", 20));
                hotChunkPoiLookupRenewOnHit = lookupBoostSection.getBoolean("renew-on-hit", true);
            }

            ConfigurationSection typeBoostSection = hotSection.getConfigurationSection("poi-type-cache");
            if (typeBoostSection != null) {
                hotChunkPoiTypeBoostEnabled = typeBoostSection.getBoolean("enabled", true);
                hotChunkPoiTypeTtlTicks = Math.max(0, typeBoostSection.getInt("ttl-ticks", 200));
                hotChunkPoiTypeTtlJitterTicks = Math.max(0, typeBoostSection.getInt("ttl-jitter-ticks", 20));
                hotChunkPoiTypeRenewOnHit = typeBoostSection.getBoolean("renew-on-hit", true);
            }
        }

    }

    // Hopper getters
    public boolean isHopperEnabled() {
        return hopperEnabled;
    }

    public boolean isHopperFullCacheEnabled() {
        return hopperFullCacheEnabled;
    }

    public int getHopperFullCacheTtlTicks() {
        return hopperFullCacheTtlTicks;
    }

    public boolean isHopperFullCacheNegativeEnabled() {
        return hopperFullCacheNegativeEnabled;
    }

    public int getHopperFullCacheNegativeTtlTicks() {
        return hopperFullCacheNegativeTtlTicks;
    }

    public boolean isHopperFullCacheInvalidateOnEvent() {
        return hopperFullCacheInvalidateOnEvent;
    }

    // Villager POI getters
    public boolean isVillagerPOIEnabled() {
        return villagerPOIEnabled;
    }

    public boolean isRestoreJobSiteEnabled() {
        return restoreJobSite;
    }

    public boolean isAcquirePoiCacheEnabled() {
        return acquirePoiCacheEnabled;
    }

    public int getAcquirePoiCacheTtlTicks() {
        return acquirePoiCacheTtlTicks;
    }

    public int getAcquirePoiCacheTtlJitterTicks() {
        return acquirePoiCacheTtlJitterTicks;
    }

    public int getAcquirePoiCacheMaxEntries() {
        return acquirePoiCacheMaxEntries;
    }

    public boolean isAcquirePoiCacheEmptyResults() {
        return acquirePoiCacheEmptyResults;
    }

    public boolean isAcquirePoiCachePredicateAware() {
        return acquirePoiCachePredicateAware;
    }

    public int getAcquirePoiCacheSourceBucketSize() {
        return acquirePoiCacheSourceBucketSize;
    }

    public boolean isAcquirePoiCacheFallbackOnInsufficient() {
        return acquirePoiCacheFallbackOnInsufficient;
    }

    public boolean isAcquirePoiCacheRenewOnHit() {
        return acquirePoiCacheRenewOnHit;
    }

    public boolean isPoiCompetitorCacheEnabled() {
        return poiCompetitorCacheEnabled;
    }

    public int getPoiCompetitorCacheTtlTicks() {
        return poiCompetitorCacheTtlTicks;
    }

    public int getPoiCompetitorCacheTtlJitterTicks() {
        return poiCompetitorCacheTtlJitterTicks;
    }

    public int getPoiCompetitorCacheMaxEntries() {
        return poiCompetitorCacheMaxEntries;
    }

    public boolean isPoiCompetitorCacheEmptyResults() {
        return poiCompetitorCacheEmptyResults;
    }

    public boolean isPoiCompetitorCacheRenewOnHit() {
        return poiCompetitorCacheRenewOnHit;
    }

    public boolean isPoiLookupCacheEnabled() {
        return poiLookupCacheEnabled;
    }

    public int getPoiLookupCacheTtlTicks() {
        return poiLookupCacheTtlTicks;
    }

    public int getPoiLookupCacheTtlJitterTicks() {
        return poiLookupCacheTtlJitterTicks;
    }

    public int getPoiLookupCacheMaxEntries() {
        return poiLookupCacheMaxEntries;
    }

    public boolean isPoiLookupCacheEmptyResults() {
        return poiLookupCacheEmptyResults;
    }

    public boolean isPoiLookupCachePredicateAware() {
        return poiLookupCachePredicateAware;
    }

    public int getPoiLookupCacheSourceBucketSize() {
        return poiLookupCacheSourceBucketSize;
    }

    public boolean isPoiLookupCacheRenewOnHit() {
        return poiLookupCacheRenewOnHit;
    }

    public boolean isPoiTypeCacheEnabled() {
        return poiTypeCacheEnabled;
    }

    public int getPoiTypeCacheTtlTicks() {
        return poiTypeCacheTtlTicks;
    }

    public int getPoiTypeCacheTtlJitterTicks() {
        return poiTypeCacheTtlJitterTicks;
    }

    public int getPoiTypeCacheMaxEntries() {
        return poiTypeCacheMaxEntries;
    }

    public boolean isPoiTypeCacheEmptyResults() {
        return poiTypeCacheEmptyResults;
    }

    public boolean isPoiTypeCachePredicateAware() {
        return poiTypeCachePredicateAware;
    }

    public boolean isPoiTypeCacheRenewOnHit() {
        return poiTypeCacheRenewOnHit;
    }

    public boolean shouldOptimizePOI(EntityType type, boolean hasName, double nearestPlayerDistance) {
        if (poiRules == null || poiRules.isEmpty()) {
            return type == EntityType.VILLAGER;
        }
        for (POIRule rule : poiRules) {
            if (rule.matches(type, hasName, nearestPlayerDistance)) {
                return rule.optimize();
            }
        }
        return type == EntityType.VILLAGER;
    }

    // Entity distance cache getters
    public boolean isEntitySpreadEnabled() {
        return entitySpreadEnabled;
    }

    public int getSpreadScanInterval() {
        return spreadScanInterval;
    }

    public double getNearDistance() {
        return nearDistance;
    }

    // Pathfinding cache getters
    public boolean isPathfindingCacheEnabled() {
        return pathfindingCacheEnabled;
    }

    public int getPathfindingCacheTtlTicks() {
        return pathfindingCacheTtlTicks;
    }

    public int getPathfindingCacheTtlJitterTicks() {
        return pathfindingCacheTtlJitterTicks;
    }

    public int getPathfindingCacheMaxEntriesPerNav() {
        return pathfindingCacheMaxEntriesPerNav;
    }

    public int getPathfindingCacheMobMoveThreshold() {
        return pathfindingCacheMobMoveThreshold;
    }

    public int getPathfindingCacheTargetMoveThreshold() {
        return pathfindingCacheTargetMoveThreshold;
    }

    public int getPathfindingCacheNegativeTtlTicks() {
        return pathfindingCacheNegativeTtlTicks;
    }

    public boolean isChunkEpochEnabled() {
        return chunkEpochEnabled;
    }

    public boolean isBlockStateCacheEnabled() {
        return blockStateCacheEnabled;
    }

    public int getBlockStateCacheTtlTicks() {
        return blockStateCacheTtlTicks;
    }

    public int getBlockStateCacheMaxEntries() {
        return blockStateCacheMaxEntries;
    }

    public boolean isNaturalSpawnerEnabled() {
        return naturalSpawnerEnabled;
    }

    public int getNaturalSpawnerBlockStateCacheTtlTicks() {
        return blockStateCacheTtlTicks;
    }

    public int getNaturalSpawnerBlockStateCacheMaxEntries() {
        return blockStateCacheMaxEntries;
    }

    public boolean isHotChunksEnabled() {
        return hotChunksEnabled;
    }

    public int getHotChunkScanInterval() {
        return hotChunkScanInterval;
    }

    public int getHotChunkMobThreshold() {
        return hotChunkMobThreshold;
    }

    public int getHotChunkAreaRadius() {
        return hotChunkAreaRadius;
    }

    public boolean isHotChunkUseSpreadSnapshots() {
        return hotChunkUseSpreadSnapshots;
    }

    public long getHotChunkSnapshotMaxAgeMs() {
        return hotChunkSnapshotMaxAgeMs;
    }

    public double getHotChunkHeatDecay() {
        return hotChunkHeatDecay;
    }

    public double getHotChunkMinHeat() {
        return hotChunkMinHeat;
    }

    public boolean isHotChunkBlockStateCacheEnabled() {
        return hotChunkBlockStateCacheEnabled;
    }

    public int getHotChunkBlockStateCacheTtlTicks() {
        return hotChunkBlockStateCacheTtlTicks;
    }

    public boolean isHotChunkVillagerPdcEnabled() {
        return hotChunkVillagerPdcEnabled;
    }

    public boolean isHotChunkVillagerStaticEnabled() {
        return hotChunkVillagerStaticEnabled;
    }

    public int getHotChunkVillagerStaticStableTicks() {
        return hotChunkVillagerStaticStableTicks;
    }

    public double getHotChunkVillagerStaticMoveThreshold() {
        return hotChunkVillagerStaticMoveThreshold;
    }

    public int getHotChunkVillagerStaticScanIntervalTicks() {
        return hotChunkVillagerStaticScanIntervalTicks;
    }

    public boolean isHotChunkPathfindingBoostEnabled() {
        return hotChunkPathfindingBoostEnabled;
    }

    public int getHotChunkPathfindingTtlTicks() {
        return hotChunkPathfindingTtlTicks;
    }

    public int getHotChunkPathfindingTtlJitterTicks() {
        return hotChunkPathfindingTtlJitterTicks;
    }

    public int getHotChunkPathfindingMobMoveThreshold() {
        return hotChunkPathfindingMobMoveThreshold;
    }

    public int getHotChunkPathfindingTargetMoveThreshold() {
        return hotChunkPathfindingTargetMoveThreshold;
    }

    public int getHotChunkPathfindingNegativeTtlTicks() {
        return hotChunkPathfindingNegativeTtlTicks;
    }

    public boolean isHotChunkAcquirePoiBoostEnabled() {
        return hotChunkAcquirePoiBoostEnabled;
    }

    public int getHotChunkAcquirePoiTtlTicks() {
        return hotChunkAcquirePoiTtlTicks;
    }

    public int getHotChunkAcquirePoiTtlJitterTicks() {
        return hotChunkAcquirePoiTtlJitterTicks;
    }

    public boolean isHotChunkAcquirePoiRenewOnHit() {
        return hotChunkAcquirePoiRenewOnHit;
    }

    public boolean isHotChunkPoiCompetitorBoostEnabled() {
        return hotChunkPoiCompetitorBoostEnabled;
    }

    public int getHotChunkPoiCompetitorTtlTicks() {
        return hotChunkPoiCompetitorTtlTicks;
    }

    public int getHotChunkPoiCompetitorTtlJitterTicks() {
        return hotChunkPoiCompetitorTtlJitterTicks;
    }

    public boolean isHotChunkPoiCompetitorRenewOnHit() {
        return hotChunkPoiCompetitorRenewOnHit;
    }

    public boolean isHotChunkPoiLookupBoostEnabled() {
        return hotChunkPoiLookupBoostEnabled;
    }

    public int getHotChunkPoiLookupTtlTicks() {
        return hotChunkPoiLookupTtlTicks;
    }

    public int getHotChunkPoiLookupTtlJitterTicks() {
        return hotChunkPoiLookupTtlJitterTicks;
    }

    public boolean isHotChunkPoiLookupRenewOnHit() {
        return hotChunkPoiLookupRenewOnHit;
    }

    public boolean isHotChunkPoiTypeBoostEnabled() {
        return hotChunkPoiTypeBoostEnabled;
    }

    public int getHotChunkPoiTypeTtlTicks() {
        return hotChunkPoiTypeTtlTicks;
    }

    public int getHotChunkPoiTypeTtlJitterTicks() {
        return hotChunkPoiTypeTtlJitterTicks;
    }

    public boolean isHotChunkPoiTypeRenewOnHit() {
        return hotChunkPoiTypeRenewOnHit;
    }


    // Configuration records
    public record POIRule(EntityType type, boolean named, DistanceRule distanceRule, boolean optimize) {
        public boolean matches(EntityType type, boolean hasName, double distance) {
            if (this.type != type) {
                return false;
            }
            if (this.named != hasName) {
                return false;
            }
            return distanceRule == null || distanceRule.matches(distance);
        }
    }

    public enum DistanceOp {
        GT, GE, LT, LE, EQ
    }

    public record DistanceRule(DistanceOp op, double value) {
        public boolean matches(double distance) {
            return switch (op) {
                case GT -> distance > value;
                case GE -> distance >= value;
                case LT -> distance < value;
                case LE -> distance <= value;
                case EQ -> distance == value;
            };
        }

        public static DistanceRule parse(Object raw) {
            if (raw == null) {
                return new DistanceRule(DistanceOp.GE, 0.0);
            }
            if (raw instanceof Number number) {
                return new DistanceRule(DistanceOp.GE, number.doubleValue());
            }
            String text = raw.toString().trim();
            if (text.isEmpty()) {
                return new DistanceRule(DistanceOp.GE, 0.0);
            }

            DistanceOp op = DistanceOp.EQ;
            String number = text;
            if (text.startsWith(">=")) {
                op = DistanceOp.GE;
                number = text.substring(2);
            } else if (text.startsWith("<=")) {
                op = DistanceOp.LE;
                number = text.substring(2);
            } else if (text.startsWith(">")) {
                op = DistanceOp.GT;
                number = text.substring(1);
            } else if (text.startsWith("<")) {
                op = DistanceOp.LT;
                number = text.substring(1);
            } else if (text.startsWith("=")) {
                op = DistanceOp.EQ;
                number = text.substring(1);
            }

            try {
                double value = Double.parseDouble(number);
                return new DistanceRule(op, value);
            } catch (NumberFormatException e) {
                return new DistanceRule(DistanceOp.GE, 0.0);
            }
        }
    }

    private static String readString(Map<?, ?> map, String key, String def) {
        Object value = map.get(key);
        if (value == null) {
            return def;
        }
        return value.toString().trim().toUpperCase(Locale.ROOT);
    }

    private static boolean readBoolean(Map<?, ?> map, String key, boolean def) {
        Object value = map.get(key);
        if (value == null) {
            return def;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
