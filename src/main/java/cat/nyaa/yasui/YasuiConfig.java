package cat.nyaa.yasui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration handler for Yasui plugin
 */
public class YasuiConfig {
    private final Yasui plugin;

    // Hopper settings
    private boolean hopperEnabled;
    private int hopperAsyncInterval;
    private long hopperCacheTTL;

    // Villager POI settings
    private boolean villagerPOIEnabled;
    private boolean cachePOILookups;
    private List<POIRule> poiRules;

    // Entity spread settings
    private boolean entitySpreadEnabled;
    private int spreadScanInterval;
    private double nearDistance;
    private List<SpreadRule> spreadRules;

    // Chunk cache settings
    private boolean chunkCacheEnabled;
    private boolean cacheRandomTickPositions;

    public YasuiConfig(Yasui plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        // Hopper optimization
        ConfigurationSection hopperSection = plugin.getConfig().getConfigurationSection("optimizations.hopper");
        if (hopperSection != null) {
            hopperEnabled = hopperSection.getBoolean("enabled", true);
            hopperAsyncInterval = hopperSection.getInt("async-scan-interval", 5);
            hopperCacheTTL = hopperSection.getLong("cache-ttl", 1000);
        }

        // Villager POI optimization
        ConfigurationSection poiSection = plugin.getConfig().getConfigurationSection("optimizations.villager-poi");
        if (poiSection != null) {
            villagerPOIEnabled = poiSection.getBoolean("enabled", true);
            cachePOILookups = poiSection.getBoolean("cache-poi-lookups", true);

            poiRules = new ArrayList<>();
            List<?> rulesRaw = poiSection.getList("rules");
            if (rulesRaw != null) {
                for (Object ruleObj : rulesRaw) {
                    if (ruleObj instanceof ConfigurationSection ruleSection) {
                        try {
                            EntityType type = EntityType.valueOf(ruleSection.getString("type", "VILLAGER"));
                            boolean named = ruleSection.getBoolean("named", false);
                            String distance = ruleSection.getString("distance", ">0");
                            boolean optimize = ruleSection.getBoolean("optimize", true);
                            poiRules.add(new POIRule(type, named, distance, optimize));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid entity type in POI rule: " + ruleSection.getString("type"));
                        }
                    }
                }
            }
        }

        // Entity spread optimization
        ConfigurationSection spreadSection = plugin.getConfig().getConfigurationSection("optimizations.entity-spread");
        if (spreadSection != null) {
            entitySpreadEnabled = spreadSection.getBoolean("enabled", true);
            spreadScanInterval = spreadSection.getInt("scan-interval", 100);
            nearDistance = spreadSection.getDouble("near-distance", 32.0);

            spreadRules = new ArrayList<>();
            List<?> rulesRaw = spreadSection.getList("rules");
            if (rulesRaw != null) {
                for (Object ruleObj : rulesRaw) {
                    if (ruleObj instanceof ConfigurationSection ruleSection) {
                        try {
                            EntityType type = EntityType.valueOf(ruleSection.getString("type", "VILLAGER"));
                            boolean named = ruleSection.getBoolean("named", false);
                            int interval = ruleSection.getInt("spread-interval", 1);
                            spreadRules.add(new SpreadRule(type, named, interval));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid entity type in spread rule: " + ruleSection.getString("type"));
                        }
                    }
                }
            }
        }

        // Chunk cache optimization
        ConfigurationSection chunkSection = plugin.getConfig().getConfigurationSection("optimizations.chunk-cache");
        if (chunkSection != null) {
            chunkCacheEnabled = chunkSection.getBoolean("enabled", true);
            cacheRandomTickPositions = chunkSection.getBoolean("cache-random-tick-positions", true);
        }
    }

    // Hopper getters
    public boolean isHopperEnabled() {
        return hopperEnabled;
    }

    public int getHopperAsyncInterval() {
        return hopperAsyncInterval;
    }

    public long getHopperCacheTTL() {
        return hopperCacheTTL;
    }

    // Villager POI getters
    public boolean isVillagerPOIEnabled() {
        return villagerPOIEnabled;
    }

    public boolean isCachePOILookups() {
        return cachePOILookups;
    }

    public boolean shouldOptimizePOI(EntityType type, boolean hasName) {
        for (POIRule rule : poiRules) {
            if (rule.type() == type && rule.named() == hasName) {
                return rule.optimize();
            }
        }
        return false;
    }

    // Entity spread getters
    public boolean isEntitySpreadEnabled() {
        return entitySpreadEnabled;
    }

    public int getSpreadScanInterval() {
        return spreadScanInterval;
    }

    public double getNearDistance() {
        return nearDistance;
    }

    public int getSpreadInterval(EntityType type, boolean hasName) {
        for (SpreadRule rule : spreadRules) {
            if (rule.type() == type && rule.named() == hasName) {
                return rule.interval();
            }
        }
        return 1; // Default: tick every tick (no spread)
    }

    // Chunk cache getters
    public boolean isChunkCacheEnabled() {
        return chunkCacheEnabled;
    }

    public boolean isCacheRandomTickPositions() {
        return cacheRandomTickPositions;
    }

    // Configuration records
    public record POIRule(EntityType type, boolean named, String distance, boolean optimize) {}

    public record SpreadRule(EntityType type, boolean named, int interval) {}
}
