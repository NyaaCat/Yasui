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
    private boolean poiCompetitorCacheEnabled;
    private int poiCompetitorCacheTtlTicks;
    private int poiCompetitorCacheTtlJitterTicks;
    private int poiCompetitorCacheMaxEntries;
    private boolean poiCompetitorCacheEmptyResults;
    private List<POIRule> poiRules;

    // Entity distance cache settings
    private boolean entitySpreadEnabled;
    private int spreadScanInterval;
    private double nearDistance;

    // Pathfinding cache settings
    private boolean pathfindingCacheEnabled;
    private int pathfindingCacheTtlTicks;
    private int pathfindingCacheTtlJitterTicks;

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
        poiCompetitorCacheEnabled = true;
        poiCompetitorCacheTtlTicks = 2;
        poiCompetitorCacheTtlJitterTicks = 1;
        poiCompetitorCacheMaxEntries = 10000;
        poiCompetitorCacheEmptyResults = false;
        poiRules = new ArrayList<>();

        entitySpreadEnabled = true;
        spreadScanInterval = 100;
        nearDistance = 32.0;

        pathfindingCacheEnabled = true;
        pathfindingCacheTtlTicks = 3;
        pathfindingCacheTtlJitterTicks = 1;

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
            }
            ConfigurationSection competitorSection = poiSection.getConfigurationSection("competitor-scan-cache");
            if (competitorSection != null) {
                poiCompetitorCacheEnabled = competitorSection.getBoolean("enabled", true);
                poiCompetitorCacheTtlTicks = Math.max(0, competitorSection.getInt("ttl-ticks", 2));
                poiCompetitorCacheTtlJitterTicks = Math.max(0, competitorSection.getInt("ttl-jitter-ticks", 1));
                poiCompetitorCacheMaxEntries = Math.max(0, competitorSection.getInt("max-entries", 10000));
                poiCompetitorCacheEmptyResults = competitorSection.getBoolean("cache-empty-results", false);
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
}
