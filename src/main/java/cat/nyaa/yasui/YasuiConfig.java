package cat.nyaa.yasui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration handler for Yasui plugin
 */
public class YasuiConfig {
    private final Yasui plugin;

    // Hopper settings
    private boolean hopperEnabled;
    private int hopperAsyncInterval;
    private long hopperCacheTTL;
    private int hopperMaxScanPerTick;

    // Villager POI settings
    private boolean villagerPOIEnabled;
    private boolean cachePOILookups;
    private List<POIRule> poiRules;

    // Entity distance cache settings
    private boolean entitySpreadEnabled;
    private int spreadScanInterval;
    private double nearDistance;
    private int defaultSpreadInterval;
    private List<SpreadRule> spreadRules;

    public YasuiConfig(Yasui plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        // Defaults (used when sections are missing)
        hopperEnabled = true;
        hopperAsyncInterval = 5;
        hopperCacheTTL = 1000;
        hopperMaxScanPerTick = 200;

        villagerPOIEnabled = true;
        cachePOILookups = true;
        poiRules = new ArrayList<>();

        entitySpreadEnabled = true;
        spreadScanInterval = 100;
        nearDistance = 32.0;
        defaultSpreadInterval = 2;
        spreadRules = new ArrayList<>();

        // Hopper optimization
        ConfigurationSection hopperSection = plugin.getConfig().getConfigurationSection("optimizations.hopper");
        if (hopperSection != null) {
            hopperEnabled = hopperSection.getBoolean("enabled", true);
            hopperAsyncInterval = hopperSection.getInt("async-scan-interval", 5);
            hopperCacheTTL = hopperSection.getLong("cache-ttl", 1000);
            hopperMaxScanPerTick = Math.max(1, hopperSection.getInt("max-scan-per-tick", 200));
        }

        // Villager POI optimization
        ConfigurationSection poiSection = plugin.getConfig().getConfigurationSection("optimizations.villager-poi");
        if (poiSection != null) {
            villagerPOIEnabled = poiSection.getBoolean("enabled", true);
            cachePOILookups = poiSection.getBoolean("cache-poi-lookups", true);

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

        // Entity distance cache
        ConfigurationSection spreadSection = plugin.getConfig().getConfigurationSection("optimizations.entity-spread");
        if (spreadSection != null) {
            entitySpreadEnabled = spreadSection.getBoolean("enabled", true);
            spreadScanInterval = spreadSection.getInt("scan-interval", 100);
            nearDistance = spreadSection.getDouble("near-distance", 32.0);
            defaultSpreadInterval = spreadSection.getInt("default-interval", 2);

            spreadRules = new ArrayList<>();
            List<Map<?, ?>> rulesRaw = spreadSection.getMapList("rules");
            if (rulesRaw != null) {
                for (Map<?, ?> ruleMap : rulesRaw) {
                    try {
                        EntityType type = EntityType.valueOf(readString(ruleMap, "type", "VILLAGER"));
                        boolean named = readBoolean(ruleMap, "named", false);
                        int interval = Math.max(1, readInt(ruleMap, "spread-interval", 1));
                        spreadRules.add(new SpreadRule(type, named, interval));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid entity type in spread rule: " + ruleMap.get("type"));
                    }
                }
            }
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

    public int getHopperMaxScanPerTick() {
        return hopperMaxScanPerTick;
    }

    // Villager POI getters
    public boolean isVillagerPOIEnabled() {
        return villagerPOIEnabled;
    }

    public boolean isCachePOILookups() {
        return cachePOILookups;
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

    public int getSpreadInterval(EntityType type, boolean hasName) {
        if (spreadRules != null) {
            for (SpreadRule rule : spreadRules) {
                if (rule.type() == type && rule.named() == hasName) {
                    return Math.max(1, rule.interval());
                }
            }
        }
        return Math.max(1, defaultSpreadInterval); // Default: use configured default interval
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

    public record SpreadRule(EntityType type, boolean named, int interval) {}

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
                return new DistanceRule(op, Double.parseDouble(number.trim()));
            } catch (NumberFormatException e) {
                return new DistanceRule(DistanceOp.GE, 0.0);
            }
        }
    }

    private static boolean readBoolean(Map<?, ?> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return defaultValue;
    }

    private static int readInt(Map<?, ?> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String readString(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }
}
