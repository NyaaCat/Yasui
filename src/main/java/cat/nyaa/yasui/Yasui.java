package cat.nyaa.yasui;

import cat.nyaa.yasui.command.YasuiCommand;
import cat.nyaa.yasui.optimizer.HopperOptimizer;
import cat.nyaa.yasui.optimizer.VillagerPOICache;
import cat.nyaa.yasui.optimizer.EntitySpreadTicker;
import cat.nyaa.yasui.optimizer.PathfindingCacheTracker;
import cat.nyaa.yasui.optimizer.PoiCompetitorCacheTracker;
import cat.nyaa.yasui.optimizer.PoiSearchCacheTracker;
import cat.nyaa.yasui.nms.AsyncPlayerSaveNmsHook;
import cat.nyaa.yasui.nms.HopperNmsHook;
import cat.nyaa.yasui.nms.PathfindingNmsHook;
import cat.nyaa.yasui.nms.PoiCompetitorNmsHook;
import cat.nyaa.yasui.nms.PoiSearchNmsHook;
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

    @Override
    public void onEnable() {
        // Save default config if not exists
        saveDefaultConfig();

        // Load configuration
        config = new YasuiConfig(this);
        boolean acquirePoiEnabled = config.isVillagerPOIEnabled() && config.isAcquirePoiCacheEnabled();
        boolean competitorCacheEnabled = config.isVillagerPOIEnabled() && config.isPoiCompetitorCacheEnabled();

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
        boolean asyncSaveEnabled = config.isAsyncPlayerSaveEnabled()
            || config.isAsyncPlayerSaveStatsEnabled()
            || config.isAsyncPlayerSaveAdvancementsEnabled();
        if (asyncSaveEnabled) {
            boolean hookActive = AsyncPlayerSaveNmsHook.install(this);
            if (hookActive) {
                getLogger().info("Async player save hook active");
            } else {
                String error = AsyncPlayerSaveNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("Async player save hook failed: " + error);
                } else {
                    getLogger().warning("Async player save hook failed");
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
            config.getPathfindingCacheTtlJitterTicks()
        );
        PoiSearchNmsHook.configure(
            acquirePoiEnabled,
            config.getAcquirePoiCacheTtlTicks(),
            config.getAcquirePoiCacheTtlJitterTicks(),
            config.getAcquirePoiCacheMaxEntries(),
            config.isAcquirePoiCacheEmptyResults(),
            config.isAcquirePoiCachePredicateAware()
        );
        PoiCompetitorNmsHook.configure(
            competitorCacheEnabled,
            config.getPoiCompetitorCacheTtlTicks(),
            config.getPoiCompetitorCacheTtlJitterTicks(),
            config.getPoiCompetitorCacheMaxEntries(),
            config.isPoiCompetitorCacheEmptyResults()
        );
        AsyncPlayerSaveNmsHook.configure(
            config.isAsyncPlayerSaveEnabled(),
            config.getAsyncPlayerSaveWorkerThreads(),
            config.isAsyncPlayerSaveStatsEnabled(),
            config.isAsyncPlayerSaveAdvancementsEnabled(),
            config.isAsyncPlayerSaveWaitOnShutdown(),
            config.getAsyncPlayerSaveShutdownTimeoutSeconds()
        );
        PoiCompetitorNmsHook.configure(
            competitorCacheEnabled,
            config.getPoiCompetitorCacheTtlTicks(),
            config.getPoiCompetitorCacheTtlJitterTicks(),
            config.getPoiCompetitorCacheMaxEntries(),
            config.isPoiCompetitorCacheEmptyResults()
        );

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

        if (pathfindingCacheTracker != null) {
            pathfindingCacheTracker.shutdown();
        }
        if (poiSearchCacheTracker != null) {
            poiSearchCacheTracker.shutdown();
        }
        if (poiCompetitorCacheTracker != null) {
            poiCompetitorCacheTracker.shutdown();
        }
        if (config != null) {
            AsyncPlayerSaveNmsHook.shutdown(config.isAsyncPlayerSaveWaitOnShutdown());
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

        // Reload config object
        config = new YasuiConfig(this);

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
        boolean acquirePoiEnabled = config.isVillagerPOIEnabled() && config.isAcquirePoiCacheEnabled();
        boolean competitorCacheEnabled = config.isVillagerPOIEnabled() && config.isPoiCompetitorCacheEnabled();
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
        boolean asyncSaveEnabled = config.isAsyncPlayerSaveEnabled()
            || config.isAsyncPlayerSaveStatsEnabled()
            || config.isAsyncPlayerSaveAdvancementsEnabled();
        if (asyncSaveEnabled && !AsyncPlayerSaveNmsHook.isActive()) {
            boolean hookActive = AsyncPlayerSaveNmsHook.install(this);
            if (hookActive) {
                getLogger().info("Async player save hook active");
            } else {
                String error = AsyncPlayerSaveNmsHook.getErrorMessage();
                if (error != null) {
                    getLogger().warning("Async player save hook failed: " + error);
                } else {
                    getLogger().warning("Async player save hook failed");
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
            config.getPathfindingCacheTtlJitterTicks()
        );
        PoiSearchNmsHook.configure(
            acquirePoiEnabled,
            config.getAcquirePoiCacheTtlTicks(),
            config.getAcquirePoiCacheTtlJitterTicks(),
            config.getAcquirePoiCacheMaxEntries(),
            config.isAcquirePoiCacheEmptyResults(),
            config.isAcquirePoiCachePredicateAware()
        );
        AsyncPlayerSaveNmsHook.configure(
            config.isAsyncPlayerSaveEnabled(),
            config.getAsyncPlayerSaveWorkerThreads(),
            config.isAsyncPlayerSaveStatsEnabled(),
            config.isAsyncPlayerSaveAdvancementsEnabled(),
            config.isAsyncPlayerSaveWaitOnShutdown(),
            config.getAsyncPlayerSaveShutdownTimeoutSeconds()
        );

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

}
