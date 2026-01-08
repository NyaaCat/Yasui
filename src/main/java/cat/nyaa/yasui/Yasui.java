package cat.nyaa.yasui;

import cat.nyaa.yasui.command.YasuiCommand;
import cat.nyaa.yasui.optimizer.HopperOptimizer;
import cat.nyaa.yasui.optimizer.VillagerPOICache;
import cat.nyaa.yasui.optimizer.EntitySpreadTicker;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Yasui - Paper 1.21.8 Server Optimization Plugin
 *
 * Reduces server tick time through targeted optimizations:
 * - Hopper caching with periodic pre-computation
 * - Villager POI caching to reduce repeated lookups
 * - Entity distance cache for gating expensive optimizations
 */
public class Yasui extends JavaPlugin {
    private YasuiConfig config;
    private HopperOptimizer hopperOptimizer;
    private VillagerPOICache villagerCache;
    private EntitySpreadTicker entitySpread;

    @Override
    public void onEnable() {
        // Save default config if not exists
        saveDefaultConfig();

        // Load configuration
        config = new YasuiConfig(this);

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
            getLogger().info("Entity distance cache enabled (no tick freezing)");
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
}
