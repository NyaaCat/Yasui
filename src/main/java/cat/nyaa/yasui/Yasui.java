package cat.nyaa.yasui;

import cat.nyaa.yasui.command.YasuiCommand;
import cat.nyaa.yasui.optimizer.HopperOptimizer;
import cat.nyaa.yasui.optimizer.HotChunkTracker;
import cat.nyaa.yasui.optimizer.HotChunkTickGroup;
import cat.nyaa.yasui.optimizer.VillagerPOICache;
import cat.nyaa.yasui.optimizer.EntitySpreadTicker;
import cat.nyaa.yasui.optimizer.PathfindingCacheTracker;
import cat.nyaa.yasui.optimizer.PoiCompetitorCacheTracker;
import cat.nyaa.yasui.optimizer.PoiSearchCacheTracker;
import cat.nyaa.yasui.optimizer.PoiLookupCacheTracker;
import cat.nyaa.yasui.optimizer.PoiTypeCacheTracker;
import cat.nyaa.yasui.optimizer.ChunkEpochTracker;
import cat.nyaa.yasui.nms.HopperNmsHook;
import cat.nyaa.yasui.nms.PathfindingNmsHook;
import cat.nyaa.yasui.nms.PoiCompetitorNmsHook;
import cat.nyaa.yasui.nms.PoiSearchNmsHook;
import cat.nyaa.yasui.nms.PoiLookupNmsHook;
import cat.nyaa.yasui.nms.PoiTypeNmsHook;
import cat.nyaa.yasui.nms.TickGroupNmsHook;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Yasui - Paper 1.21.8 Server Optimization Plugin
 *
 * Reduces server tick time through targeted optimizations:
 * - Hopper full-check caching
 * - Villager POI caching to reduce repeated lookups
 * - Entity distance cache for quick near/distant checks
 * - Pathfinding result cache (short TTL)
 * - AcquirePoi search caching (short TTL)
 * - PoiCompetitorScan POI type caching (short TTL)
 */
public class Yasui extends JavaPlugin {
    private YasuiConfig config;
    private HopperOptimizer hopperOptimizer;
    private VillagerPOICache villagerCache;
    private EntitySpreadTicker entitySpread;
    private PathfindingCacheTracker pathfindingCacheTracker;
    private PoiSearchCacheTracker poiSearchCacheTracker;
    private PoiCompetitorCacheTracker poiCompetitorCacheTracker;
    private PoiLookupCacheTracker poiLookupCacheTracker;
    private PoiTypeCacheTracker poiTypeCacheTracker;
    private HotChunkTracker hotChunkTracker;
    private HotChunkTickGroup hotChunkTickGroup;
    private ChunkEpochTracker chunkEpochTracker;

    @Override
    public void onEnable() {
        // Save default config if not exists
        saveDefaultConfig();
        ensureConfigDefaults();

        // Load configuration
        config = new YasuiConfig(this);
        boolean acquirePoiEnabled = config.isVillagerPOIEnabled() && config.isAcquirePoiCacheEnabled();
        boolean competitorCacheEnabled = config.isVillagerPOIEnabled() && config.isPoiCompetitorCacheEnabled();
        boolean poiLookupEnabled = config.isVillagerPOIEnabled() && config.isPoiLookupCacheEnabled();
        boolean poiTypeEnabled = config.isVillagerPOIEnabled() && config.isPoiTypeCacheEnabled();
        boolean tickGroupEnabled = config.isHotChunksEnabled() && config.getHotChunkTickGroups() > 0;

        if (config.isHopperFullCacheEnabled()) {
            boolean hookActive = HopperNmsHook.install(this);
            if (hookActive) {
                getLogger().info("Hopper full-check hook active");
            } else {
                String error = HopperNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("Hopper full-check hook failed: " + error);
                } else {
                    getLogger().warning("Hopper full-check hook failed");
                }
            }
        }
        if (config.isPathfindingCacheEnabled()) {
            boolean hookActive = PathfindingNmsHook.install(this);
            if (hookActive) {
                getLogger().info("Pathfinding cache hook active");
            } else {
                String error = PathfindingNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("Pathfinding cache hook failed: " + error);
                } else {
                    getLogger().warning("Pathfinding cache hook failed");
                }
            }
        }
        if (tickGroupEnabled) {
            boolean hookActive = TickGroupNmsHook.install(this);
            if (hookActive) {
                getLogger().info("Tick group hook active");
            } else {
                String error = TickGroupNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("Tick group hook failed: " + error);
                } else {
                    getLogger().warning("Tick group hook failed");
                }
            }
        }
        if (acquirePoiEnabled) {
            boolean hookActive = PoiSearchNmsHook.install(this);
            if (hookActive) {
                getLogger().info("AcquirePoi cache hook active");
            } else {
                String error = PoiSearchNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("AcquirePoi cache hook failed: " + error);
                } else {
                    getLogger().warning("AcquirePoi cache hook failed");
                }
            }
        }
        if (competitorCacheEnabled) {
            boolean hookActive = PoiCompetitorNmsHook.install(this);
            if (hookActive) {
                getLogger().info("PoiCompetitor cache hook active");
            } else {
                String error = PoiCompetitorNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("PoiCompetitor cache hook failed: " + error);
                } else {
                    getLogger().warning("PoiCompetitor cache hook failed");
                }
            }
        }
        if (poiLookupEnabled) {
            boolean hookActive = PoiLookupNmsHook.install(this);
            if (hookActive) {
                getLogger().info("PoiLookup cache hook active");
            } else {
                String error = PoiLookupNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("PoiLookup cache hook failed: " + error);
                } else {
                    getLogger().warning("PoiLookup cache hook failed");
                }
            }
        }
        if (poiTypeEnabled) {
            boolean hookActive = PoiTypeNmsHook.install(this);
            if (hookActive) {
                getLogger().info("PoiType cache hook active");
            } else {
                String error = PoiTypeNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("PoiType cache hook failed: " + error);
                } else {
                    getLogger().warning("PoiType cache hook failed");
                }
            }
        }
        HopperNmsHook.configure(
            config.isHopperFullCacheEnabled(),
            config.getHopperFullCacheTtlTicks(),
            config.isHopperFullCacheNegativeEnabled(),
            config.getHopperFullCacheNegativeTtlTicks()
        );
        PathfindingNmsHook.configure(
            config.isPathfindingCacheEnabled(),
            config.getPathfindingCacheTtlTicks(),
            config.getPathfindingCacheTtlJitterTicks(),
            config.getPathfindingCacheMaxEntriesPerNav(),
            config.getPathfindingCacheMobMoveThreshold(),
            config.getPathfindingCacheTargetMoveThreshold(),
            config.getPathfindingCacheNegativeTtlTicks()
        );
        TickGroupNmsHook.configure(tickGroupEnabled, config.getHotChunkTickGroups());
        PoiSearchNmsHook.configure(
            acquirePoiEnabled,
            config.getAcquirePoiCacheTtlTicks(),
            config.getAcquirePoiCacheTtlJitterTicks(),
            config.getAcquirePoiCacheMaxEntries(),
            config.isAcquirePoiCacheEmptyResults(),
            config.isAcquirePoiCachePredicateAware(),
            config.getAcquirePoiCacheSourceBucketSize(),
            config.isAcquirePoiCacheFallbackOnInsufficient(),
            config.isAcquirePoiCacheRenewOnHit()
        );
        PoiCompetitorNmsHook.configure(
            competitorCacheEnabled,
            config.getPoiCompetitorCacheTtlTicks(),
            config.getPoiCompetitorCacheTtlJitterTicks(),
            config.getPoiCompetitorCacheMaxEntries(),
            config.isPoiCompetitorCacheEmptyResults(),
            config.isPoiCompetitorCacheRenewOnHit()
        );
        PoiLookupNmsHook.configure(
            poiLookupEnabled,
            config.getPoiLookupCacheTtlTicks(),
            config.getPoiLookupCacheTtlJitterTicks(),
            config.getPoiLookupCacheMaxEntries(),
            config.isPoiLookupCacheEmptyResults(),
            config.isPoiLookupCachePredicateAware(),
            config.getPoiLookupCacheSourceBucketSize(),
            config.isPoiLookupCacheRenewOnHit()
        );
        PoiTypeNmsHook.configure(
            poiTypeEnabled,
            config.getPoiTypeCacheTtlTicks(),
            config.getPoiTypeCacheTtlJitterTicks(),
            config.getPoiTypeCacheMaxEntries(),
            config.isPoiTypeCacheEmptyResults(),
            config.isPoiTypeCachePredicateAware(),
            config.isPoiTypeCacheRenewOnHit()
        );
        configureHotChunkCaches();

        // Initialize optimizers
        if (config.isHopperEnabled()) {
            hopperOptimizer = new HopperOptimizer(this, config);
            getServer().getPluginManager().registerEvents(hopperOptimizer, this);
            hopperOptimizer.start();
            getLogger().info("Hopper optimizer enabled");
        }

        if (config.isVillagerPOIEnabled()) {
            villagerCache = new VillagerPOICache(this, config);
            getServer().getPluginManager().registerEvents(villagerCache, this);
            villagerCache.initialize();
            getLogger().info("Villager POI cache enabled");
        }

        if (config.isChunkEpochEnabled()) {
            chunkEpochTracker = new ChunkEpochTracker(this, config);
            getServer().getPluginManager().registerEvents(chunkEpochTracker, this);
            getLogger().info("Chunk epoch tracker enabled");
        }

        if (config.isEntitySpreadEnabled()) {
            entitySpread = new EntitySpreadTicker(this, config);
            getServer().getPluginManager().registerEvents(entitySpread, this);
            entitySpread.start();
            getLogger().info("Entity distance cache enabled");
        }

        if (config.isPathfindingCacheEnabled()) {
            pathfindingCacheTracker = new PathfindingCacheTracker(this, config);
            pathfindingCacheTracker.start();
            getLogger().info("Pathfinding cache enabled");
        }

        if (acquirePoiEnabled) {
            poiSearchCacheTracker = new PoiSearchCacheTracker(this, config);
            poiSearchCacheTracker.start();
            getLogger().info("AcquirePoi search cache enabled");
        }

        if (competitorCacheEnabled) {
            poiCompetitorCacheTracker = new PoiCompetitorCacheTracker(this, config);
            poiCompetitorCacheTracker.start();
            getLogger().info("PoiCompetitor cache enabled");
        }

        if (poiLookupEnabled) {
            poiLookupCacheTracker = new PoiLookupCacheTracker(this, config);
            poiLookupCacheTracker.start();
            getLogger().info("PoiLookup cache enabled");
        }

        if (poiTypeEnabled) {
            poiTypeCacheTracker = new PoiTypeCacheTracker(this, config);
            poiTypeCacheTracker.start();
            getLogger().info("PoiType cache enabled");
        }

        if (config.isHotChunksEnabled()) {
            hotChunkTracker = new HotChunkTracker(this, config);
            hotChunkTracker.start();
            getLogger().info("Hot chunk tracker enabled");
        }
        if (tickGroupEnabled) {
            hotChunkTickGroup = new HotChunkTickGroup(this, config);
            getServer().getPluginManager().registerEvents(hotChunkTickGroup, this);
            hotChunkTickGroup.start();
            getLogger().info("Hot chunk tick groups enabled");
        }

        // Register command
        getCommand("yasui").setExecutor(new YasuiCommand(this));

        getLogger().info("Yasui optimization plugin loaded successfully!");
    }

    @Override
    public void onDisable() {
        // Shutdown optimizers gracefully
        if (hopperOptimizer != null) {
            HandlerList.unregisterAll(hopperOptimizer);
            hopperOptimizer.shutdown();
        }

        if (entitySpread != null) {
            HandlerList.unregisterAll(entitySpread);
            entitySpread.shutdown();
        }

        if (villagerCache != null) {
            HandlerList.unregisterAll(villagerCache);
            villagerCache.shutdown();
        }

        if (chunkEpochTracker != null) {
            HandlerList.unregisterAll(chunkEpochTracker);
            chunkEpochTracker.shutdown();
        }

        if (pathfindingCacheTracker != null) {
            pathfindingCacheTracker.shutdown();
        }
        if (poiSearchCacheTracker != null) {
            poiSearchCacheTracker.shutdown();
        }
        if (poiCompetitorCacheTracker != null) {
            poiCompetitorCacheTracker.shutdown();
        }
        if (poiLookupCacheTracker != null) {
            poiLookupCacheTracker.shutdown();
        }
        if (poiTypeCacheTracker != null) {
            poiTypeCacheTracker.shutdown();
        }

        if (hotChunkTracker != null) {
            hotChunkTracker.shutdown();
        }
        if (hotChunkTickGroup != null) {
            HandlerList.unregisterAll(hotChunkTickGroup);
            hotChunkTickGroup.shutdown();
        }

        getLogger().info("Yasui optimization plugin disabled");
    }

    /**
     * Reload configuration and restart optimizers
     */
    public void reloadConfiguration() {
        getLogger().info("Reloading Yasui configuration...");

        // Reload config file
        reloadConfig();
        ensureConfigDefaults();

        // Reload config object
        config = new YasuiConfig(this);
        boolean tickGroupEnabled = config.isHotChunksEnabled() && config.getHotChunkTickGroups() > 0;

        if (config.isHopperFullCacheEnabled() && !HopperNmsHook.isHookActive()) {
            boolean hookActive = HopperNmsHook.install(this);
            if (hookActive) {
                getLogger().info("Hopper full-check hook active");
            } else {
                String error = HopperNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("Hopper full-check hook failed: " + error);
                } else {
                    getLogger().warning("Hopper full-check hook failed");
                }
            }
        }
        if (config.isPathfindingCacheEnabled() && !PathfindingNmsHook.isHookActive()) {
            boolean hookActive = PathfindingNmsHook.install(this);
            if (hookActive) {
                getLogger().info("Pathfinding cache hook active");
            } else {
                String error = PathfindingNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("Pathfinding cache hook failed: " + error);
                } else {
                    getLogger().warning("Pathfinding cache hook failed");
                }
            }
        }
        if (tickGroupEnabled && !TickGroupNmsHook.isHookActive()) {
            boolean hookActive = TickGroupNmsHook.install(this);
            if (hookActive) {
                getLogger().info("Tick group hook active");
            } else {
                String error = TickGroupNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("Tick group hook failed: " + error);
                } else {
                    getLogger().warning("Tick group hook failed");
                }
            }
        }
        boolean acquirePoiEnabled = config.isVillagerPOIEnabled() && config.isAcquirePoiCacheEnabled();
        boolean competitorCacheEnabled = config.isVillagerPOIEnabled() && config.isPoiCompetitorCacheEnabled();
        boolean poiLookupEnabled = config.isVillagerPOIEnabled() && config.isPoiLookupCacheEnabled();
        boolean poiTypeEnabled = config.isVillagerPOIEnabled() && config.isPoiTypeCacheEnabled();
        if (acquirePoiEnabled && !PoiSearchNmsHook.isHookActive()) {
            boolean hookActive = PoiSearchNmsHook.install(this);
            if (hookActive) {
                getLogger().info("AcquirePoi cache hook active");
            } else {
                String error = PoiSearchNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("AcquirePoi cache hook failed: " + error);
                } else {
                    getLogger().warning("AcquirePoi cache hook failed");
                }
            }
        }
        if (competitorCacheEnabled && !PoiCompetitorNmsHook.isHookActive()) {
            boolean hookActive = PoiCompetitorNmsHook.install(this);
            if (hookActive) {
                getLogger().info("PoiCompetitor cache hook active");
            } else {
                String error = PoiCompetitorNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("PoiCompetitor cache hook failed: " + error);
                } else {
                    getLogger().warning("PoiCompetitor cache hook failed");
                }
            }
        }
        if (poiLookupEnabled && !PoiLookupNmsHook.isHookActive()) {
            boolean hookActive = PoiLookupNmsHook.install(this);
            if (hookActive) {
                getLogger().info("PoiLookup cache hook active");
            } else {
                String error = PoiLookupNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("PoiLookup cache hook failed: " + error);
                } else {
                    getLogger().warning("PoiLookup cache hook failed");
                }
            }
        }
        if (poiTypeEnabled && !PoiTypeNmsHook.isHookActive()) {
            boolean hookActive = PoiTypeNmsHook.install(this);
            if (hookActive) {
                getLogger().info("PoiType cache hook active");
            } else {
                String error = PoiTypeNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("PoiType cache hook failed: " + error);
                } else {
                    getLogger().warning("PoiType cache hook failed");
                }
            }
        }
        HopperNmsHook.configure(
            config.isHopperFullCacheEnabled(),
            config.getHopperFullCacheTtlTicks(),
            config.isHopperFullCacheNegativeEnabled(),
            config.getHopperFullCacheNegativeTtlTicks()
        );
        PathfindingNmsHook.configure(
            config.isPathfindingCacheEnabled(),
            config.getPathfindingCacheTtlTicks(),
            config.getPathfindingCacheTtlJitterTicks(),
            config.getPathfindingCacheMaxEntriesPerNav(),
            config.getPathfindingCacheMobMoveThreshold(),
            config.getPathfindingCacheTargetMoveThreshold(),
            config.getPathfindingCacheNegativeTtlTicks()
        );
        TickGroupNmsHook.configure(tickGroupEnabled, config.getHotChunkTickGroups());
        PoiSearchNmsHook.configure(
            acquirePoiEnabled,
            config.getAcquirePoiCacheTtlTicks(),
            config.getAcquirePoiCacheTtlJitterTicks(),
            config.getAcquirePoiCacheMaxEntries(),
            config.isAcquirePoiCacheEmptyResults(),
            config.isAcquirePoiCachePredicateAware(),
            config.getAcquirePoiCacheSourceBucketSize(),
            config.isAcquirePoiCacheFallbackOnInsufficient(),
            config.isAcquirePoiCacheRenewOnHit()
        );
        PoiCompetitorNmsHook.configure(
            competitorCacheEnabled,
            config.getPoiCompetitorCacheTtlTicks(),
            config.getPoiCompetitorCacheTtlJitterTicks(),
            config.getPoiCompetitorCacheMaxEntries(),
            config.isPoiCompetitorCacheEmptyResults(),
            config.isPoiCompetitorCacheRenewOnHit()
        );
        PoiLookupNmsHook.configure(
            poiLookupEnabled,
            config.getPoiLookupCacheTtlTicks(),
            config.getPoiLookupCacheTtlJitterTicks(),
            config.getPoiLookupCacheMaxEntries(),
            config.isPoiLookupCacheEmptyResults(),
            config.isPoiLookupCachePredicateAware(),
            config.getPoiLookupCacheSourceBucketSize(),
            config.isPoiLookupCacheRenewOnHit()
        );
        PoiTypeNmsHook.configure(
            poiTypeEnabled,
            config.getPoiTypeCacheTtlTicks(),
            config.getPoiTypeCacheTtlJitterTicks(),
            config.getPoiTypeCacheMaxEntries(),
            config.isPoiTypeCacheEmptyResults(),
            config.isPoiTypeCachePredicateAware(),
            config.isPoiTypeCacheRenewOnHit()
        );
        configureHotChunkCaches();

        // Restart hopper optimizer (or disable if not enabled)
        if (hopperOptimizer != null) {
            HandlerList.unregisterAll(hopperOptimizer);
            hopperOptimizer.shutdown();
            hopperOptimizer = null;
        }
        if (config.isHopperEnabled()) {
            hopperOptimizer = new HopperOptimizer(this, config);
            getServer().getPluginManager().registerEvents(hopperOptimizer, this);
            hopperOptimizer.start();
            getLogger().info("Hopper optimizer reloaded");
        } else {
            getLogger().info("Hopper optimizer disabled");
        }

        // Restart villager POI cache (or disable if not enabled)
        if (villagerCache != null) {
            HandlerList.unregisterAll(villagerCache);
            villagerCache.shutdown();
            villagerCache = null;
        }
        if (config.isVillagerPOIEnabled()) {
            villagerCache = new VillagerPOICache(this, config);
            getServer().getPluginManager().registerEvents(villagerCache, this);
            villagerCache.initialize();
            getLogger().info("Villager POI cache reloaded");
        } else {
            getLogger().info("Villager POI cache disabled");
        }

        if (chunkEpochTracker != null) {
            HandlerList.unregisterAll(chunkEpochTracker);
            chunkEpochTracker.shutdown();
            chunkEpochTracker = null;
        }
        if (config.isChunkEpochEnabled()) {
            chunkEpochTracker = new ChunkEpochTracker(this, config);
            getServer().getPluginManager().registerEvents(chunkEpochTracker, this);
            getLogger().info("Chunk epoch tracker reloaded");
        } else {
            getLogger().info("Chunk epoch tracker disabled");
        }

        // Restart entity distance cache (or disable if not enabled)
        if (entitySpread != null) {
            HandlerList.unregisterAll(entitySpread);
            entitySpread.shutdown();
            entitySpread = null;
        }
        if (config.isEntitySpreadEnabled()) {
            entitySpread = new EntitySpreadTicker(this, config);
            getServer().getPluginManager().registerEvents(entitySpread, this);
            entitySpread.start();
            getLogger().info("Entity distance cache reloaded");
        } else {
            getLogger().info("Entity distance cache disabled");
        }

        if (pathfindingCacheTracker != null) {
            pathfindingCacheTracker.shutdown();
            pathfindingCacheTracker = null;
        }
        if (config.isPathfindingCacheEnabled()) {
            pathfindingCacheTracker = new PathfindingCacheTracker(this, config);
            pathfindingCacheTracker.start();
            getLogger().info("Pathfinding cache reloaded");
        } else {
            getLogger().info("Pathfinding cache disabled");
        }

        if (poiSearchCacheTracker != null) {
            poiSearchCacheTracker.shutdown();
            poiSearchCacheTracker = null;
        }
        if (acquirePoiEnabled) {
            poiSearchCacheTracker = new PoiSearchCacheTracker(this, config);
            poiSearchCacheTracker.start();
            getLogger().info("AcquirePoi search cache reloaded");
        } else {
            getLogger().info("AcquirePoi search cache disabled");
        }

        if (poiCompetitorCacheTracker != null) {
            poiCompetitorCacheTracker.shutdown();
            poiCompetitorCacheTracker = null;
        }
        if (competitorCacheEnabled) {
            poiCompetitorCacheTracker = new PoiCompetitorCacheTracker(this, config);
            poiCompetitorCacheTracker.start();
            getLogger().info("PoiCompetitor cache reloaded");
        } else {
            getLogger().info("PoiCompetitor cache disabled");
        }

        if (poiLookupCacheTracker != null) {
            poiLookupCacheTracker.shutdown();
            poiLookupCacheTracker = null;
        }
        if (poiLookupEnabled) {
            poiLookupCacheTracker = new PoiLookupCacheTracker(this, config);
            poiLookupCacheTracker.start();
            getLogger().info("PoiLookup cache reloaded");
        } else {
            getLogger().info("PoiLookup cache disabled");
        }

        if (poiTypeCacheTracker != null) {
            poiTypeCacheTracker.shutdown();
            poiTypeCacheTracker = null;
        }
        if (poiTypeEnabled) {
            poiTypeCacheTracker = new PoiTypeCacheTracker(this, config);
            poiTypeCacheTracker.start();
            getLogger().info("PoiType cache reloaded");
        } else {
            getLogger().info("PoiType cache disabled");
        }

        if (hotChunkTracker != null) {
            hotChunkTracker.shutdown();
            hotChunkTracker = null;
        }
        if (config.isHotChunksEnabled()) {
            hotChunkTracker = new HotChunkTracker(this, config);
            hotChunkTracker.start();
            getLogger().info("Hot chunk tracker reloaded");
        } else {
            getLogger().info("Hot chunk tracker disabled");
        }

        if (hotChunkTickGroup != null) {
            HandlerList.unregisterAll(hotChunkTickGroup);
            hotChunkTickGroup.shutdown();
            hotChunkTickGroup = null;
        }
        if (tickGroupEnabled) {
            hotChunkTickGroup = new HotChunkTickGroup(this, config);
            getServer().getPluginManager().registerEvents(hotChunkTickGroup, this);
            hotChunkTickGroup.start();
            getLogger().info("Hot chunk tick groups reloaded");
        } else {
            getLogger().info("Hot chunk tick groups disabled");
        }

        getLogger().info("Configuration reload complete!");
    }

    public YasuiConfig getYasuiConfig() {
        return config;
    }

    public HopperOptimizer getHopperOptimizer() {
        return hopperOptimizer;
    }

    public VillagerPOICache getVillagerCache() {
        return villagerCache;
    }

    public EntitySpreadTicker getEntitySpread() {
        return entitySpread;
    }

    public PathfindingCacheTracker getPathfindingCacheTracker() {
        return pathfindingCacheTracker;
    }

    public PoiSearchCacheTracker getPoiSearchCacheTracker() {
        return poiSearchCacheTracker;
    }

    public PoiCompetitorCacheTracker getPoiCompetitorCacheTracker() {
        return poiCompetitorCacheTracker;
    }

    public PoiLookupCacheTracker getPoiLookupCacheTracker() {
        return poiLookupCacheTracker;
    }

    public PoiTypeCacheTracker getPoiTypeCacheTracker() {
        return poiTypeCacheTracker;
    }

    public HotChunkTracker getHotChunkTracker() {
        return hotChunkTracker;
    }

    public HotChunkTickGroup getHotChunkTickGroup() {
        return hotChunkTickGroup;
    }

    private void ensureConfigDefaults() {
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void configureHotChunkCaches() {
        boolean hotEnabled = config.isHotChunksEnabled();
        boolean pathBoost = hotEnabled && config.isHotChunkPathfindingBoostEnabled();
        boolean acquireBoost = hotEnabled && config.isHotChunkAcquirePoiBoostEnabled();
        boolean competitorBoost = hotEnabled && config.isHotChunkPoiCompetitorBoostEnabled();
        boolean lookupBoost = hotEnabled && config.isHotChunkPoiLookupBoostEnabled();
        boolean typeBoost = hotEnabled && config.isHotChunkPoiTypeBoostEnabled();

        int pathHotTtl = Math.max(config.getPathfindingCacheTtlTicks(), config.getHotChunkPathfindingTtlTicks());
        int pathHotJitter = Math.max(config.getPathfindingCacheTtlJitterTicks(), config.getHotChunkPathfindingTtlJitterTicks());
        int pathHotMobThreshold = Math.max(config.getPathfindingCacheMobMoveThreshold(), config.getHotChunkPathfindingMobMoveThreshold());
        int pathHotTargetThreshold = Math.max(config.getPathfindingCacheTargetMoveThreshold(), config.getHotChunkPathfindingTargetMoveThreshold());
        int pathHotNegativeTtl = Math.max(config.getPathfindingCacheNegativeTtlTicks(), config.getHotChunkPathfindingNegativeTtlTicks());
        PathfindingNmsHook.configureHotChunks(
            pathBoost,
            pathHotTtl,
            pathHotJitter,
            pathHotMobThreshold,
            pathHotTargetThreshold,
            pathHotNegativeTtl
        );

        int acquireHotTtl = Math.max(config.getAcquirePoiCacheTtlTicks(), config.getHotChunkAcquirePoiTtlTicks());
        int acquireHotJitter = Math.max(config.getAcquirePoiCacheTtlJitterTicks(), config.getHotChunkAcquirePoiTtlJitterTicks());
        PoiSearchNmsHook.configureHotChunks(acquireBoost, acquireHotTtl, acquireHotJitter, config.isHotChunkAcquirePoiRenewOnHit());

        int competitorHotTtl = Math.max(config.getPoiCompetitorCacheTtlTicks(), config.getHotChunkPoiCompetitorTtlTicks());
        int competitorHotJitter = Math.max(config.getPoiCompetitorCacheTtlJitterTicks(), config.getHotChunkPoiCompetitorTtlJitterTicks());
        PoiCompetitorNmsHook.configureHotChunks(competitorBoost, competitorHotTtl, competitorHotJitter, config.isHotChunkPoiCompetitorRenewOnHit());

        int lookupHotTtl = Math.max(config.getPoiLookupCacheTtlTicks(), config.getHotChunkPoiLookupTtlTicks());
        int lookupHotJitter = Math.max(config.getPoiLookupCacheTtlJitterTicks(), config.getHotChunkPoiLookupTtlJitterTicks());
        PoiLookupNmsHook.configureHotChunks(lookupBoost, lookupHotTtl, lookupHotJitter, config.isHotChunkPoiLookupRenewOnHit());

        int typeHotTtl = Math.max(config.getPoiTypeCacheTtlTicks(), config.getHotChunkPoiTypeTtlTicks());
        int typeHotJitter = Math.max(config.getPoiTypeCacheTtlJitterTicks(), config.getHotChunkPoiTypeTtlJitterTicks());
        PoiTypeNmsHook.configureHotChunks(typeBoost, typeHotTtl, typeHotJitter, config.isHotChunkPoiTypeRenewOnHit());
    }

}
