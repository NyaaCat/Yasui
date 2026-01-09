package cat.nyaa.yasui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private boolean villagerBehaviorThrottleEnabled;
    private boolean villagerBehaviorThrottleRequireDistant;
    private int villagerBehaviorThrottleInterval;
    private boolean villagerBehaviorThrottleOneShotOnly;

    // Entity distance cache settings
    private boolean entitySpreadEnabled;
    private int spreadScanInterval;
    private double nearDistance;

    // Entity hotspot optimizer settings
    private boolean entityHotspotEnabled;
    private int entityHotspotScanInterval;
    private int entityHotspotThreshold;
    private boolean entityHotspotRequireDistant;
    private boolean entityHotspotOnlyPassive;
    private boolean entityHotspotExcludeNamed;
    private boolean entityHotspotExcludeLeashed;
    private boolean entityHotspotExcludeTamed;
    private boolean entityHotspotExcludeBaby;
    private Set<EntityType> entityHotspotIncludeTypes;
    private Set<EntityType> entityHotspotExcludeTypes;

    private boolean collisionSuppressionEnabled;
    private boolean collisionTimeSlicingEnabled;
    private boolean collisionRequireHotspot;
    private int collisionEntitiesPerCollidable;

    private boolean pathfindingBudgetEnabled;
    private boolean pathfindingRequireHotspot;
    private float pathfindingMultiplier;

    private boolean goalThrottleEnabled;
    private boolean goalThrottleRequireHotspot;
    private boolean goalThrottleStaggerEnabled;
    private boolean goalThrottleIdleBackoffEnabled;
    private int goalThrottleIdleBackoffThreshold;
    private int goalThrottleIdleBackoffStep;
    private int goalThrottleIdleBackoffMaxCanUseInterval;
    private int goalThrottleIdleBackoffMaxTickInterval;
    private int goalThrottleDefaultCanUseInterval;
    private int goalThrottleDefaultTickInterval;
    private List<GoalThrottleRule> goalThrottleRules;

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
        villagerBehaviorThrottleEnabled = true;
        villagerBehaviorThrottleRequireDistant = true;
        villagerBehaviorThrottleInterval = 40;
        villagerBehaviorThrottleOneShotOnly = true;

        entitySpreadEnabled = true;
        spreadScanInterval = 100;
        nearDistance = 32.0;

        entityHotspotEnabled = true;
        entityHotspotScanInterval = 40;
        entityHotspotThreshold = 24;
        entityHotspotRequireDistant = true;
        entityHotspotOnlyPassive = true;
        entityHotspotExcludeNamed = true;
        entityHotspotExcludeLeashed = true;
        entityHotspotExcludeTamed = true;
        entityHotspotExcludeBaby = false;
        entityHotspotIncludeTypes = new HashSet<>();
        entityHotspotExcludeTypes = new HashSet<>();

        collisionSuppressionEnabled = true;
        collisionTimeSlicingEnabled = true;
        collisionRequireHotspot = true;
        collisionEntitiesPerCollidable = 12;

        pathfindingBudgetEnabled = true;
        pathfindingRequireHotspot = true;
        pathfindingMultiplier = 0.6f;

        goalThrottleEnabled = true;
        goalThrottleRequireHotspot = true;
        goalThrottleStaggerEnabled = true;
        goalThrottleIdleBackoffEnabled = true;
        goalThrottleIdleBackoffThreshold = 60;
        goalThrottleIdleBackoffStep = 40;
        goalThrottleIdleBackoffMaxCanUseInterval = 200;
        goalThrottleIdleBackoffMaxTickInterval = 20;
        goalThrottleDefaultCanUseInterval = 20;
        goalThrottleDefaultTickInterval = 2;
        goalThrottleRules = defaultGoalThrottleRules();

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

            ConfigurationSection behaviorSection = poiSection.getConfigurationSection("behavior-throttle");
            if (behaviorSection != null) {
                villagerBehaviorThrottleEnabled = behaviorSection.getBoolean("enabled", true);
                villagerBehaviorThrottleRequireDistant = behaviorSection.getBoolean("require-distant", true);
                villagerBehaviorThrottleInterval = Math.max(1, behaviorSection.getInt("interval", 40));
                villagerBehaviorThrottleOneShotOnly = behaviorSection.getBoolean("one-shot-only", true);
            }
        }

        // Entity distance cache
        ConfigurationSection spreadSection = plugin.getConfig().getConfigurationSection("optimizations.entity-spread");
        if (spreadSection != null) {
            entitySpreadEnabled = spreadSection.getBoolean("enabled", true);
            spreadScanInterval = spreadSection.getInt("scan-interval", 100);
            nearDistance = spreadSection.getDouble("near-distance", 32.0);
        }

        ConfigurationSection hotspotSection = plugin.getConfig().getConfigurationSection("optimizations.entity-optimizer");
        if (hotspotSection != null) {
            entityHotspotEnabled = hotspotSection.getBoolean("enabled", true);
            entityHotspotScanInterval = Math.max(5, hotspotSection.getInt("scan-interval", 40));
            entityHotspotThreshold = Math.max(1, hotspotSection.getInt("hotspot-threshold", 24));
            entityHotspotRequireDistant = hotspotSection.getBoolean("require-distant", true);

            ConfigurationSection filterSection = hotspotSection.getConfigurationSection("filters");
            if (filterSection != null) {
                entityHotspotOnlyPassive = filterSection.getBoolean("only-passive", true);
                entityHotspotExcludeNamed = filterSection.getBoolean("exclude-named", true);
                entityHotspotExcludeLeashed = filterSection.getBoolean("exclude-leashed", true);
                entityHotspotExcludeTamed = filterSection.getBoolean("exclude-tamed", true);
                entityHotspotExcludeBaby = filterSection.getBoolean("exclude-babies", false);
                entityHotspotIncludeTypes = readEntityTypeSet(filterSection.getStringList("include-types"));
                entityHotspotExcludeTypes = readEntityTypeSet(filterSection.getStringList("exclude-types"));
            }

            ConfigurationSection collisionSection = hotspotSection.getConfigurationSection("collision");
            if (collisionSection != null) {
                collisionSuppressionEnabled = collisionSection.getBoolean("suppression-enabled", true);
                collisionTimeSlicingEnabled = collisionSection.getBoolean("time-slicing-enabled", true);
                collisionRequireHotspot = collisionSection.getBoolean("require-hotspot", true);
                collisionEntitiesPerCollidable = Math.max(1, collisionSection.getInt("entities-per-collidable", 12));
            }

            ConfigurationSection aiSection = hotspotSection.getConfigurationSection("ai");
            if (aiSection != null) {
                pathfindingBudgetEnabled = aiSection.getBoolean("pathfinding-budget-enabled", true);
                pathfindingRequireHotspot = aiSection.getBoolean("pathfinding-require-hotspot", true);
                pathfindingMultiplier = (float) Math.max(0.1, Math.min(1.0, aiSection.getDouble("pathfinding-multiplier", 0.6)));

                goalThrottleEnabled = aiSection.getBoolean("goal-throttle-enabled", true);
                goalThrottleRequireHotspot = aiSection.getBoolean("goal-throttle-require-hotspot", true);
                goalThrottleStaggerEnabled = aiSection.getBoolean("goal-throttle-stagger-enabled", true);
                goalThrottleIdleBackoffEnabled = aiSection.getBoolean("goal-throttle-idle-backoff-enabled", true);
                goalThrottleIdleBackoffThreshold = Math.max(0, aiSection.getInt("goal-throttle-idle-backoff-threshold", 60));
                goalThrottleIdleBackoffStep = Math.max(1, aiSection.getInt("goal-throttle-idle-backoff-step", 40));
                goalThrottleIdleBackoffMaxCanUseInterval = Math.max(1, aiSection.getInt("goal-throttle-idle-backoff-max-can-use-interval", 200));
                goalThrottleIdleBackoffMaxTickInterval = Math.max(1, aiSection.getInt("goal-throttle-idle-backoff-max-tick-interval", 20));
                goalThrottleDefaultCanUseInterval = Math.max(1, aiSection.getInt("goal-throttle-default-can-use-interval", 20));
                goalThrottleDefaultTickInterval = Math.max(1, aiSection.getInt("goal-throttle-default-tick-interval", 2));
                goalThrottleRules = readGoalThrottleRules(aiSection.getMapList("goal-throttle-goals"));
                if (goalThrottleRules.isEmpty()) {
                    goalThrottleRules = defaultGoalThrottleRules();
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

    public boolean isVillagerBehaviorThrottleEnabled() {
        return villagerBehaviorThrottleEnabled;
    }

    public boolean isVillagerBehaviorThrottleRequireDistant() {
        return villagerBehaviorThrottleRequireDistant;
    }

    public int getVillagerBehaviorThrottleInterval() {
        return villagerBehaviorThrottleInterval;
    }

    public boolean isVillagerBehaviorThrottleOneShotOnly() {
        return villagerBehaviorThrottleOneShotOnly;
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

    // Entity hotspot optimizer getters
    public boolean isEntityHotspotEnabled() {
        return entityHotspotEnabled;
    }

    public int getEntityHotspotScanInterval() {
        return entityHotspotScanInterval;
    }

    public int getEntityHotspotThreshold() {
        return entityHotspotThreshold;
    }

    public boolean isEntityHotspotRequireDistant() {
        return entityHotspotRequireDistant;
    }

    public boolean isEntityHotspotOnlyPassive() {
        return entityHotspotOnlyPassive;
    }

    public boolean isEntityHotspotExcludeNamed() {
        return entityHotspotExcludeNamed;
    }

    public boolean isEntityHotspotExcludeLeashed() {
        return entityHotspotExcludeLeashed;
    }

    public boolean isEntityHotspotExcludeTamed() {
        return entityHotspotExcludeTamed;
    }

    public boolean isEntityHotspotExcludeBaby() {
        return entityHotspotExcludeBaby;
    }

    public Set<EntityType> getEntityHotspotIncludeTypes() {
        return entityHotspotIncludeTypes;
    }

    public Set<EntityType> getEntityHotspotExcludeTypes() {
        return entityHotspotExcludeTypes;
    }

    public double getEntityHotspotNearDistanceSquared() {
        return nearDistance * nearDistance;
    }

    public boolean isCollisionSuppressionEnabled() {
        return collisionSuppressionEnabled;
    }

    public boolean isCollisionTimeSlicingEnabled() {
        return collisionTimeSlicingEnabled;
    }

    public boolean isCollisionRequireHotspot() {
        return collisionRequireHotspot;
    }

    public int getCollisionEntitiesPerCollidable() {
        return collisionEntitiesPerCollidable;
    }

    public boolean isPathfindingBudgetEnabled() {
        return pathfindingBudgetEnabled;
    }

    public boolean isPathfindingRequireHotspot() {
        return pathfindingRequireHotspot;
    }

    public float getPathfindingMultiplier() {
        return pathfindingMultiplier;
    }

    public boolean isGoalThrottleEnabled() {
        return goalThrottleEnabled;
    }

    public boolean isGoalThrottleRequireHotspot() {
        return goalThrottleRequireHotspot;
    }

    public boolean isGoalThrottleStaggerEnabled() {
        return goalThrottleStaggerEnabled;
    }

    public boolean isGoalThrottleIdleBackoffEnabled() {
        return goalThrottleIdleBackoffEnabled;
    }

    public int getGoalThrottleIdleBackoffThreshold() {
        return goalThrottleIdleBackoffThreshold;
    }

    public int getGoalThrottleIdleBackoffStep() {
        return goalThrottleIdleBackoffStep;
    }

    public int getGoalThrottleIdleBackoffMaxCanUseInterval() {
        return goalThrottleIdleBackoffMaxCanUseInterval;
    }

    public int getGoalThrottleIdleBackoffMaxTickInterval() {
        return goalThrottleIdleBackoffMaxTickInterval;
    }

    public GoalThrottleRule getGoalThrottleRule(String goalClassName) {
        if (goalThrottleRules == null || goalThrottleRules.isEmpty()) {
            return null;
        }
        String simple = goalClassName.substring(goalClassName.lastIndexOf('.') + 1);
        GoalThrottleRule wildcard = null;
        for (GoalThrottleRule rule : goalThrottleRules) {
            if ("*".equals(rule.name())) {
                wildcard = rule;
                continue;
            }
            if (rule.name().equalsIgnoreCase(goalClassName) || rule.name().equalsIgnoreCase(simple)) {
                return rule;
            }
        }
        if (wildcard != null) {
            return new GoalThrottleRule(simple, wildcard.canUseInterval(), wildcard.tickInterval());
        }
        return null;
    }

    public int getGoalThrottleDefaultCanUseInterval() {
        return goalThrottleDefaultCanUseInterval;
    }

    public int getGoalThrottleDefaultTickInterval() {
        return goalThrottleDefaultTickInterval;
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

    public record GoalThrottleRule(String name, int canUseInterval, int tickInterval) {}

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

    private Set<EntityType> readEntityTypeSet(List<String> raw) {
        Set<EntityType> result = new HashSet<>();
        if (raw == null) {
            return result;
        }
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                result.add(EntityType.valueOf(entry.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid entity type in entity optimizer: " + entry);
            }
        }
        return result;
    }

    private List<GoalThrottleRule> readGoalThrottleRules(List<Map<?, ?>> rawRules) {
        List<GoalThrottleRule> rules = new ArrayList<>();
        if (rawRules == null) {
            return rules;
        }
        for (Map<?, ?> ruleMap : rawRules) {
            String name = readString(ruleMap, "name", "").trim();
            if (name.isEmpty()) {
                continue;
            }
            int canUseInterval = Math.max(1, readInt(ruleMap, "can-use-interval", goalThrottleDefaultCanUseInterval));
            int tickInterval = Math.max(1, readInt(ruleMap, "tick-interval", goalThrottleDefaultTickInterval));
            rules.add(new GoalThrottleRule(name, canUseInterval, tickInterval));
        }
        return rules;
    }

    private List<GoalThrottleRule> defaultGoalThrottleRules() {
        List<GoalThrottleRule> rules = new ArrayList<>();
        rules.add(new GoalThrottleRule("MeleeAttackGoal", 20, 3));
        rules.add(new GoalThrottleRule("NearestAttackableTargetGoal", 20, 2));
        rules.add(new GoalThrottleRule("HurtByTargetGoal", 20, 2));
        rules.add(new GoalThrottleRule("RandomStrollGoal", 20, 2));
        rules.add(new GoalThrottleRule("WaterAvoidingRandomStrollGoal", 20, 2));
        rules.add(new GoalThrottleRule("RandomSwimmingGoal", 20, 2));
        rules.add(new GoalThrottleRule("MoveThroughVillageGoal", 40, 4));
        rules.add(new GoalThrottleRule("RemoveBlockGoal", 40, 4));
        rules.add(new GoalThrottleRule("BreakDoorGoal", 40, 4));
        rules.add(new GoalThrottleRule("RandomLookAroundGoal", 20, 2));
        rules.add(new GoalThrottleRule("LookAtPlayerGoal", 20, 2));
        return rules;
    }
}
