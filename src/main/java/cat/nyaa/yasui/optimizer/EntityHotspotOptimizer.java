package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Hotspot-aware entity optimizations:
 * - Off-screen collision suppression
 * - Collision time-slicing
 * - Pathfinding budget
 * - Goal throttling
 */
public class EntityHotspotOptimizer implements Listener {
    private static final double IDLE_MOVE_THRESHOLD_SQUARED = 0.0004;

    private final Yasui plugin;
    private final YasuiConfig config;

    private BukkitTask scanTask;
    private BukkitTask tickTask;

    private final Map<ChunkKey, HotspotChunk> timeSliceChunks = new HashMap<>();
    private final Set<UUID> suppressionEntities = new HashSet<>();
    private final Set<UUID> timeSliceEntities = new HashSet<>();
    private final Set<UUID> pathBudgetEntities = new HashSet<>();
    private final Set<UUID> goalThrottleEntities = new HashSet<>();

    private final Map<UUID, Boolean> originalCollidable = new ConcurrentHashMap<>();
    private final Map<UUID, List<GoalSwap>> goalSwaps = new ConcurrentHashMap<>();
    private final Map<UUID, IdleState> idleStates = new ConcurrentHashMap<>();

    private final LongAdder suppressedCount = new LongAdder();
    private final LongAdder timeSliceCount = new LongAdder();
    private final LongAdder pathBudgetCount = new LongAdder();
    private final LongAdder goalThrottleCount = new LongAdder();

    public EntityHotspotOptimizer(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        int scanInterval = Math.max(5, config.getEntityHotspotScanInterval());
        scanTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::scanHotspots,
            20L,
            scanInterval
        );

        tickTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::tickTimeSlicing,
            1L,
            1L
        );

        scanHotspots();
        plugin.getLogger().info("Entity hotspot optimizer started (scan interval: " + scanInterval + " ticks)");
    }

    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
        }
        if (tickTask != null) {
            tickTask.cancel();
        }
        restoreAllCollidable();
        restoreAllPathfinding();
        restoreAllGoals();
        suppressionEntities.clear();
        timeSliceEntities.clear();
        timeSliceChunks.clear();
        pathBudgetEntities.clear();
        goalThrottleEntities.clear();
        originalCollidable.clear();
        goalSwaps.clear();
        idleStates.clear();
    }

    public Stats getStats() {
        return new Stats(
            suppressedCount.sum(),
            timeSliceCount.sum(),
            pathBudgetCount.sum(),
            goalThrottleCount.sum(),
            countWrappedGoals()
        );
    }

    private void scanHotspots() {
        if (!config.isEntityHotspotEnabled()) {
            return;
        }

        Map<ChunkKey, List<LivingEntity>> chunkEntities = new HashMap<>();
        Map<UUID, LivingEntity> allLiving = new HashMap<>();
        Map<UUID, ChunkKey> entityChunks = new HashMap<>();
        Map<World, List<Location>> playerLocations = new HashMap<>();

        for (World world : Bukkit.getWorlds()) {
            List<Location> players = new ArrayList<>();
            for (Player player : world.getPlayers()) {
                players.add(player.getLocation());
            }
            playerLocations.put(world, players);

            List<LivingEntity> living = world.getLivingEntities();
            if (living.isEmpty()) {
                continue;
            }
            for (LivingEntity entity : living) {
                if (entity instanceof Player) {
                    continue;
                }
                if (!entity.isValid()) {
                    continue;
                }
                allLiving.put(entity.getUniqueId(), entity);
                ChunkKey key = new ChunkKey(world.getUID(), entity.getLocation().getBlockX() >> 4, entity.getLocation().getBlockZ() >> 4);
                entityChunks.put(entity.getUniqueId(), key);
                chunkEntities.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
            }
        }

        Set<ChunkKey> hotspots = new HashSet<>();
        for (Map.Entry<ChunkKey, List<LivingEntity>> entry : chunkEntities.entrySet()) {
            if (entry.getValue().size() >= config.getEntityHotspotThreshold()) {
                hotspots.add(entry.getKey());
            }
        }

        Set<UUID> newSuppressionEntities = new HashSet<>();
        Set<UUID> newTimeSliceEntities = new HashSet<>();
        Set<UUID> newPathBudgetEntities = new HashSet<>();
        Set<UUID> newGoalThrottleEntities = new HashSet<>();
        Map<ChunkKey, HotspotChunk> newTimeSliceChunks = new HashMap<>();

        Map<UUID, Double> distanceCache = buildDistanceCache(allLiving.values(), playerLocations);

        for (LivingEntity entity : allLiving.values()) {
            boolean isDistant = !config.isEntityHotspotRequireDistant() || isDistant(entity, distanceCache);
            boolean isHotspot = hotspots.contains(entityChunks.get(entity.getUniqueId()));
            if (!isDistant) {
                continue;
            }

            if (config.isCollisionSuppressionEnabled()
                && (!config.isCollisionRequireHotspot() || isHotspot)
                && isCollisionEligible(entity)
                && canManageCollidable(entity)) {
                newSuppressionEntities.add(entity.getUniqueId());
            }

            if (config.isCollisionTimeSlicingEnabled()
                && (!config.isCollisionRequireHotspot() || isHotspot)
                && isCollisionEligible(entity)
                && canManageCollidable(entity)
                && !newSuppressionEntities.contains(entity.getUniqueId())) {
                newTimeSliceEntities.add(entity.getUniqueId());
            }

            if (config.isPathfindingBudgetEnabled()
                && (!config.isPathfindingRequireHotspot() || isHotspot)
                && isAiEligible(entity)) {
                newPathBudgetEntities.add(entity.getUniqueId());
            }

            if (config.isGoalThrottleEnabled()
                && (!config.isGoalThrottleRequireHotspot() || isHotspot)
                && isAiEligible(entity)) {
                newGoalThrottleEntities.add(entity.getUniqueId());
                wrapGoalsIfNeeded(entity);
            }
        }

        cleanupGoalSwaps(newGoalThrottleEntities, allLiving);

        for (ChunkKey key : hotspots) {
            List<LivingEntity> entities = chunkEntities.getOrDefault(key, Collections.emptyList());
            if (entities.isEmpty()) {
                continue;
            }
            List<LivingEntity> timeSlice = new ArrayList<>();
            for (LivingEntity entity : entities) {
                if (newTimeSliceEntities.contains(entity.getUniqueId())) {
                    timeSlice.add(entity);
                }
            }
            if (!timeSlice.isEmpty()) {
                timeSlice.sort(Comparator.comparingInt(Entity::getEntityId));
                newTimeSliceChunks.put(key, new HotspotChunk(timeSlice));
            }
        }

        restoreCollidableForRemoved(suppressionEntities, timeSliceEntities, newSuppressionEntities, newTimeSliceEntities);
        applySuppression(newSuppressionEntities);
        applyPathfindingBudget(newPathBudgetEntities);

        suppressionEntities.clear();
        suppressionEntities.addAll(newSuppressionEntities);
        timeSliceEntities.clear();
        timeSliceEntities.addAll(newTimeSliceEntities);
        pathBudgetEntities.clear();
        pathBudgetEntities.addAll(newPathBudgetEntities);
        goalThrottleEntities.clear();
        goalThrottleEntities.addAll(newGoalThrottleEntities);
        pruneIdleStates(goalThrottleEntities);
        timeSliceChunks.clear();
        timeSliceChunks.putAll(newTimeSliceChunks);

        suppressedCount.reset();
        timeSliceCount.reset();
        pathBudgetCount.reset();
        goalThrottleCount.reset();
        suppressedCount.add(suppressionEntities.size());
        timeSliceCount.add(timeSliceEntities.size());
        pathBudgetCount.add(pathBudgetEntities.size());
        goalThrottleCount.add(goalThrottleEntities.size());
    }

    private Map<UUID, Double> buildDistanceCache(Iterable<LivingEntity> entities, Map<World, List<Location>> playerLocations) {
        Map<UUID, Double> distances = new HashMap<>();
        EntitySpreadTicker spread = plugin.getEntitySpread();
        if (spread != null) {
            for (LivingEntity entity : entities) {
                distances.put(entity.getUniqueId(), spread.getNearestPlayerDistanceSquared(entity.getUniqueId()));
            }
            return distances;
        }
        for (LivingEntity entity : entities) {
            List<Location> players = playerLocations.getOrDefault(entity.getWorld(), Collections.emptyList());
            if (players.isEmpty()) {
                distances.put(entity.getUniqueId(), Double.POSITIVE_INFINITY);
                continue;
            }
            double min = Double.MAX_VALUE;
            Location entityLocation = entity.getLocation();
            for (Location playerLocation : players) {
                double dist = playerLocation.distanceSquared(entityLocation);
                if (dist < min) {
                    min = dist;
                }
            }
            distances.put(entity.getUniqueId(), min);
        }

        return distances;
    }

    private boolean isDistant(LivingEntity entity, Map<UUID, Double> distanceCache) {
        double distanceSquared = distanceCache.getOrDefault(entity.getUniqueId(), Double.POSITIVE_INFINITY);
        if (!Double.isFinite(distanceSquared)) {
            return entity.getWorld().getPlayers().isEmpty();
        }
        return distanceSquared >= config.getEntityHotspotNearDistanceSquared();
    }

    private void applySuppression(Set<UUID> newSuppressionEntities) {
        for (UUID uuid : newSuppressionEntities) {
            LivingEntity entity = getLivingEntity(uuid);
            if (entity == null) {
                continue;
            }
            setCollidable(entity, false);
        }
    }

    private void restoreCollidableForRemoved(Set<UUID> oldSuppression, Set<UUID> oldTimeSlice,
                                             Set<UUID> newSuppression, Set<UUID> newTimeSlice) {
        Set<UUID> oldManaged = new HashSet<>(oldSuppression);
        oldManaged.addAll(oldTimeSlice);
        Set<UUID> newManaged = new HashSet<>(newSuppression);
        newManaged.addAll(newTimeSlice);

        for (UUID uuid : oldManaged) {
            if (!newManaged.contains(uuid)) {
                LivingEntity entity = getLivingEntity(uuid);
                Boolean original = originalCollidable.remove(uuid);
                if (entity != null && original != null) {
                    setCollidable(entity, original);
                }
            }
        }
    }

    private void tickTimeSlicing() {
        if (!config.isEntityHotspotEnabled() || !config.isCollisionTimeSlicingEnabled()) {
            return;
        }
        int period = Math.max(1, config.getCollisionEntitiesPerCollidable());
        int tick = MinecraftServer.currentTick;

        for (HotspotChunk chunk : timeSliceChunks.values()) {
            List<LivingEntity> entities = chunk.entities();
            int count = entities.size();
            if (count == 0) {
                continue;
            }
            int budget = Math.max(1, count / period);
            int start = Math.floorMod(tick, count);

            if (!chunk.initialized() || chunk.lastBudget() != budget) {
                for (LivingEntity entity : entities) {
                    if (entity.isValid()) {
                        setCollidable(entity, false);
                    }
                }
            } else {
                for (int i = 0; i < chunk.lastBudget(); i++) {
                    int index = (chunk.lastStart() + i) % count;
                    LivingEntity entity = entities.get(index);
                    if (entity.isValid()) {
                        setCollidable(entity, false);
                    }
                }
            }

            for (int i = 0; i < budget; i++) {
                int index = (start + i) % count;
                LivingEntity entity = entities.get(index);
                if (entity.isValid()) {
                    setCollidable(entity, true);
                }
            }

            chunk.updateState(start, budget);
        }
    }

    private void applyPathfindingBudget(Set<UUID> newPathBudgetEntities) {
        float multiplier = config.getPathfindingMultiplier();
        for (UUID uuid : newPathBudgetEntities) {
            LivingEntity entity = getLivingEntity(uuid);
            if (entity == null) {
                continue;
            }
            Mob mob = getNmsMob(entity);
            if (mob == null) {
                continue;
            }
            PathNavigation navigation = mob.getNavigation();
            navigation.setMaxVisitedNodesMultiplier(multiplier);
        }

        for (UUID uuid : new HashSet<>(pathBudgetEntities)) {
            if (!newPathBudgetEntities.contains(uuid)) {
                LivingEntity entity = getLivingEntity(uuid);
                if (entity == null) {
                    continue;
                }
                Mob mob = getNmsMob(entity);
                if (mob == null) {
                    continue;
                }
                mob.getNavigation().resetMaxVisitedNodesMultiplier();
            }
        }
    }

    private void restoreAllCollidable() {
        for (Map.Entry<UUID, Boolean> entry : originalCollidable.entrySet()) {
            LivingEntity entity = getLivingEntity(entry.getKey());
            if (entity != null) {
                setCollidable(entity, entry.getValue());
            }
        }
        originalCollidable.clear();
    }

    private void restoreAllPathfinding() {
        for (UUID uuid : pathBudgetEntities) {
            LivingEntity entity = getLivingEntity(uuid);
            if (entity == null) {
                continue;
            }
            Mob mob = getNmsMob(entity);
            if (mob != null) {
                mob.getNavigation().resetMaxVisitedNodesMultiplier();
            }
        }
        pathBudgetEntities.clear();
    }

    private void restoreAllGoals() {
        for (Map.Entry<UUID, List<GoalSwap>> entry : goalSwaps.entrySet()) {
            LivingEntity entity = getLivingEntity(entry.getKey());
            if (entity == null) {
                continue;
            }
            for (GoalSwap swap : entry.getValue()) {
                swap.selector().removeGoal(swap.wrapper());
                swap.selector().addGoal(swap.priority(), swap.original());
            }
        }
        goalSwaps.clear();
    }

    private void cleanupGoalSwaps(Set<UUID> targets, Map<UUID, LivingEntity> allLiving) {
        if (!config.isGoalThrottleEnabled()) {
            restoreAllGoals();
            return;
        }
        goalSwaps.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            LivingEntity entity = allLiving.get(uuid);
            if (entity != null && entity.isValid() && targets.contains(uuid)) {
                return false;
            }
            for (GoalSwap swap : entry.getValue()) {
                swap.selector().removeGoal(swap.wrapper());
                swap.selector().addGoal(swap.priority(), swap.original());
            }
            return true;
        });
    }

    private void pruneIdleStates(Set<UUID> targets) {
        idleStates.keySet().removeIf(uuid -> !targets.contains(uuid));
    }

    private void wrapGoalsIfNeeded(LivingEntity entity) {
        if (!(entity instanceof org.bukkit.entity.Mob)) {
            return;
        }
        UUID uuid = entity.getUniqueId();
        if (goalSwaps.containsKey(uuid)) {
            return;
        }
        Mob mob = getNmsMob(entity);
        if (mob == null) {
            return;
        }

        List<GoalSwap> swaps = new ArrayList<>();
        swaps.addAll(wrapSelectorGoals(mob.goalSelector, mob));
        swaps.addAll(wrapSelectorGoals(mob.targetSelector, mob));

        if (!swaps.isEmpty()) {
            goalSwaps.put(uuid, swaps);
        }
    }

    private List<GoalSwap> wrapSelectorGoals(GoalSelector selector, Mob mob) {
        List<GoalSwap> swaps = new ArrayList<>();
        List<WrappedGoal> availableGoals = new ArrayList<>(selector.getAvailableGoals());

        for (WrappedGoal wrapped : availableGoals) {
            Goal goal = wrapped.getGoal();
            if (goal instanceof ThrottledGoal) {
                continue;
            }
            YasuiConfig.GoalThrottleRule rule = config.getGoalThrottleRule(goal.getClass().getName());
            if (rule == null) {
                continue;
            }
            ThrottledGoal throttled = new ThrottledGoal(mob, goal, rule, this);
            swaps.add(new GoalSwap(selector, wrapped.getPriority(), goal, throttled));
        }

        if (!swaps.isEmpty()) {
            for (GoalSwap swap : swaps) {
                selector.removeGoal(swap.original());
            }
            for (GoalSwap swap : swaps) {
                selector.addGoal(swap.priority(), swap.wrapper());
            }
        }

        return swaps;
    }

    private boolean isCollisionEligible(LivingEntity entity) {
        if (!isEntityAllowed(entity)) {
            return false;
        }
        if (!config.isEntityHotspotOnlyPassive()) {
            return true;
        }
        return isPassive(entity);
    }

    private boolean isAiEligible(LivingEntity entity) {
        return isEntityAllowed(entity) && entity instanceof org.bukkit.entity.Mob;
    }

    private boolean isEntityAllowed(LivingEntity entity) {
        if (entity instanceof Player) {
            return false;
        }
        if (!entity.isValid() || entity.isDead()) {
            return false;
        }
        if (config.isEntityHotspotExcludeNamed() && entity.customName() != null) {
            return false;
        }
        if (config.isEntityHotspotExcludeLeashed() && entity.isLeashed()) {
            return false;
        }
        if (config.isEntityHotspotExcludeTamed() && entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        if (config.isEntityHotspotExcludeBaby() && entity instanceof Ageable ageable && !ageable.isAdult()) {
            return false;
        }

        EntityType type = entity.getType();
        Set<EntityType> include = config.getEntityHotspotIncludeTypes();
        if (!include.isEmpty() && !include.contains(type)) {
            return false;
        }
        return !config.getEntityHotspotExcludeTypes().contains(type);
    }

    private boolean isPassive(LivingEntity entity) {
        return entity instanceof Animals
            || entity instanceof WaterMob
            || entity instanceof Ambient
            || entity.getType() == EntityType.VILLAGER
            || entity.getType() == EntityType.WANDERING_TRADER;
    }

    private boolean canManageCollidable(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        if (originalCollidable.containsKey(uuid)) {
            return true;
        }
        boolean current = entity.isCollidable();
        if (!current) {
            return false;
        }
        originalCollidable.put(uuid, current);
        return true;
    }

    private void setCollidable(LivingEntity entity, boolean value) {
        if (entity.isCollidable() != value) {
            entity.setCollidable(value);
        }
    }

    private LivingEntity getLivingEntity(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof LivingEntity living && living.isValid()) {
            return living;
        }
        return null;
    }

    private Mob getNmsMob(LivingEntity entity) {
        CraftLivingEntity craft = (CraftLivingEntity) entity;
        if (craft.getHandle() instanceof Mob mob) {
            return mob;
        }
        return null;
    }

    public boolean shouldThrottleGoal(Mob mob) {
        return config.isGoalThrottleEnabled()
            && goalThrottleEntities.contains(mob.getUUID());
    }

    private int getGoalThrottleInterval(Mob mob, int baseInterval, boolean forCanUse, int tick) {
        int interval = Math.max(1, baseInterval);
        if (!config.isGoalThrottleIdleBackoffEnabled()) {
            return interval;
        }
        int multiplier = getIdleBackoffMultiplier(mob, tick);
        int maxInterval = forCanUse
            ? config.getGoalThrottleIdleBackoffMaxCanUseInterval()
            : config.getGoalThrottleIdleBackoffMaxTickInterval();
        int adjusted = interval * Math.max(1, multiplier);
        return Math.min(adjusted, maxInterval);
    }

    private int getThrottleTick(Mob mob, Goal goal, int interval, int tick) {
        if (!config.isGoalThrottleStaggerEnabled() || interval <= 1) {
            return tick;
        }
        int hash = mob.getUUID().hashCode() ^ goal.getClass().getName().hashCode();
        int offset = Math.floorMod(hash, interval);
        return tick + offset;
    }

    private int getIdleBackoffMultiplier(Mob mob, int tick) {
        IdleState state = idleStates.computeIfAbsent(
            mob.getUUID(),
            uuid -> new IdleState(mob.getX(), mob.getY(), mob.getZ(), tick)
        );

        double x = mob.getX();
        double y = mob.getY();
        double z = mob.getZ();
        double dx = x - state.lastX;
        double dy = y - state.lastY;
        double dz = z - state.lastZ;
        boolean moved = (dx * dx + dy * dy + dz * dz) > IDLE_MOVE_THRESHOLD_SQUARED;
        if (moved) {
            state.lastX = x;
            state.lastY = y;
            state.lastZ = z;
        }

        boolean active = moved || mob.getTarget() != null;
        if (!active) {
            PathNavigation navigation = mob.getNavigation();
            if (navigation != null && navigation.isInProgress() && !navigation.isStuck()) {
                active = true;
            }
        }

        if (active) {
            state.lastActiveTick = tick;
            return 1;
        }

        int idleTicks = tick - state.lastActiveTick;
        int threshold = config.getGoalThrottleIdleBackoffThreshold();
        if (idleTicks < threshold) {
            return 1;
        }
        int step = Math.max(1, config.getGoalThrottleIdleBackoffStep());
        return 1 + Math.max(0, (idleTicks - threshold) / step);
    }

    public record Stats(long suppressedEntities, long timeSlicedEntities, long pathBudgetedEntities, long goalThrottledEntities, long wrappedGoals) {}

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {}

    private static final class HotspotChunk {
        private final List<LivingEntity> entities;
        private int lastStart = -1;
        private int lastBudget = -1;
        private boolean initialized = false;

        private HotspotChunk(List<LivingEntity> entities) {
            this.entities = entities;
        }

        private List<LivingEntity> entities() {
            return entities;
        }

        private int lastStart() {
            return lastStart;
        }

        private int lastBudget() {
            return lastBudget;
        }

        private boolean initialized() {
            return initialized;
        }

        private void updateState(int start, int budget) {
            this.lastStart = start;
            this.lastBudget = budget;
            this.initialized = true;
        }
    }

    private record GoalSwap(GoalSelector selector, int priority, Goal original, Goal wrapper) {}

    private static final class ThrottledGoal extends Goal {
        private final Mob mob;
        private final Goal delegate;
        private final YasuiConfig.GoalThrottleRule rule;
        private final EntityHotspotOptimizer optimizer;
        private int lastCanUseTick = Integer.MIN_VALUE;
        private boolean lastCanUseResult = false;
        private int lastContinueTick = Integer.MIN_VALUE;
        private boolean lastContinueResult = false;
        private int lastTick = Integer.MIN_VALUE;

        private ThrottledGoal(Mob mob, Goal delegate, YasuiConfig.GoalThrottleRule rule, EntityHotspotOptimizer optimizer) {
            this.mob = mob;
            this.delegate = delegate;
            this.rule = rule;
            this.optimizer = optimizer;
        }

        @Override
        public boolean canUse() {
            if (!optimizer.shouldThrottleGoal(mob)) {
                return delegate.canUse();
            }
            int tick = MinecraftServer.currentTick;
            int interval = optimizer.getGoalThrottleInterval(mob, rule.canUseInterval(), true, tick);
            int effectiveTick = optimizer.getThrottleTick(mob, delegate, interval, tick);
            if (effectiveTick - lastCanUseTick < interval) {
                return lastCanUseResult;
            }
            lastCanUseTick = effectiveTick;
            lastCanUseResult = delegate.canUse();
            return lastCanUseResult;
        }

        @Override
        public boolean canContinueToUse() {
            if (!optimizer.shouldThrottleGoal(mob)) {
                return delegate.canContinueToUse();
            }
            int tick = MinecraftServer.currentTick;
            int interval = optimizer.getGoalThrottleInterval(mob, rule.canUseInterval(), true, tick);
            int effectiveTick = optimizer.getThrottleTick(mob, delegate, interval, tick);
            if (effectiveTick - lastContinueTick < interval) {
                return lastContinueResult;
            }
            lastContinueTick = effectiveTick;
            lastContinueResult = delegate.canContinueToUse();
            return lastContinueResult;
        }

        @Override
        public boolean isInterruptable() {
            return delegate.isInterruptable();
        }

        @Override
        public void start() {
            delegate.start();
        }

        @Override
        public void stop() {
            delegate.stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return delegate.requiresUpdateEveryTick();
        }

        @Override
        public void tick() {
            if (!optimizer.shouldThrottleGoal(mob)) {
                delegate.tick();
                return;
            }
            int tick = MinecraftServer.currentTick;
            int interval = optimizer.getGoalThrottleInterval(mob, rule.tickInterval(), false, tick);
            int effectiveTick = optimizer.getThrottleTick(mob, delegate, interval, tick);
            if (effectiveTick - lastTick < interval) {
                return;
            }
            lastTick = effectiveTick;
            delegate.tick();
        }

        @Override
        public ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<Goal.Flag> getFlags() {
            return delegate.getFlags();
        }

        @Override
        public String toString() {
            return "Throttled(" + delegate + ")";
        }
    }

    private static final class IdleState {
        private double lastX;
        private double lastY;
        private double lastZ;
        private int lastActiveTick;

        private IdleState(double x, double y, double z, int tick) {
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastActiveTick = tick;
        }
    }

    private long countWrappedGoals() {
        long total = 0;
        for (List<GoalSwap> swaps : goalSwaps.values()) {
            total += swaps.size();
        }
        return total;
    }
}
