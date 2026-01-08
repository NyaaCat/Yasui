package cat.nyaa.yasui;

import cat.nyaa.yasui.command.YasuiCommand;
import cat.nyaa.yasui.optimizer.HopperOptimizer;
import cat.nyaa.yasui.optimizer.VillagerPOICache;
import cat.nyaa.yasui.optimizer.EntitySpreadTicker;
import cat.nyaa.yasui.optimizer.ChunkTickCache;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Yasui - Paper 1.21.8 Server Optimization Plugin
 *
 * Reduces server tick time through targeted optimizations:
 * - Hopper caching with async pre-computation
 * - Villager POI caching to reduce repeated lookups
 * - Distance-based entity spread ticking
 * - Chunk random tick position caching
 */
public class Yasui extends JavaPlugin {
    private YasuiConfig config;
    private HopperOptimizer hopperOptimizer;
    private VillagerPOICache villagerCache;
    private EntitySpreadTicker entitySpread;
    private ChunkTickCache chunkCache;

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
            entitySpread.start();
            getLogger().info("Entity spread ticker enabled");
        }

        if (config.isChunkCacheEnabled()) {
            chunkCache = new ChunkTickCache(this, config);
            getServer().getPluginManager().registerEvents(chunkCache, this);
            chunkCache.initialize();
            getLogger().info("Chunk tick cache enabled");
        }

        // Register command
        getCommand("yasui").setExecutor(new YasuiCommand(this));

        getLogger().info("Yasui optimization plugin loaded successfully!");
    }

    @Override
    public void onDisable() {
        // Shutdown optimizers gracefully
        if (hopperOptimizer != null) {
            hopperOptimizer.shutdown();
        }

        if (entitySpread != null) {
            entitySpread.shutdown();
        }

        if (villagerCache != null) {
            villagerCache.clearAll();
        }

        if (chunkCache != null) {
            chunkCache.clearAll();
        }

        getLogger().info("Yasui optimization plugin disabled");
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

    public ChunkTickCache getChunkCache() {
        return chunkCache;
    }
}
