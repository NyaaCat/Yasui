# Yasui

Server optimization plugin for Paper 1.21.8. Reduces tick time by caching expensive operations and throttling AI behaviors for distant entities.

**Requirements:** Paper 1.21.8, Java 21+

## Features

### Hopper Optimizer

Caches container fullness state to skip redundant `isFullContainer()` checks during hopper transfers.

- Event-driven tracking via `InventoryMoveItemEvent` and `InventoryPickupItemEvent`
- Periodic batch scanning (configurable interval and batch size)
- Early cancellation at LOWEST priority when target is full
- Delayed cache update (1 tick) for rapid item exchange machines
- TTL-based cache expiration

### Villager POI Cache

Reduces POI search overhead by caching job site locations and throttling brain behaviors.

- Caches job site positions from villager brain memory
- Restores cached POI when villager loses job site memory
- Validates POI block existence before restoration
- Throttles OneShot behaviors (AcquirePoi, PoiCompetitorScan) for distant villagers
- Configurable throttle interval and distance gating

### Entity Distance Cache

Categorizes entities by distance to nearest player for use by other optimizers.

- NEAR (<32 blocks) or DISTANT (>32 blocks) classification
- Periodic distance recalculation
- No tick freezing - all entities continue normal behavior

### Entity Hotspot Optimizer

Reduces collision and AI overhead in dense entity clusters.

**Collision Management:**
- Suppresses collisions for distant entities in hotspots
- Time-slices collisions across ticks (1 collidable per N entities)

**AI Management:**
- Reduces pathfinding search budget for distant hotspot mobs
- Throttles goal `canUse` checks with configurable intervals
- Staggers goal checks to spread cost across ticks
- Backs off goal checks for idle/stuck mobs (still rechecks periodically)

**Filters:**
- Hotspot detection by per-chunk entity count
- Distance gating (require distant)
- Entity type filters (passive-only, named, leashed, tamed, baby)

## Installation

```bash
./gradlew clean build
cp build/libs/Yasui-mc1.21.8-*-reobf.jar /path/to/server/plugins/
```

Config generates at `plugins/yasui/config.yml` on first run.

## Configuration

```yaml
optimizations:
  hopper:
    enabled: true
    async-scan-interval: 5      # ticks between batch scans
    cache-ttl: 1000             # milliseconds
    max-scan-per-tick: 200      # batch limit

  villager-poi:
    enabled: true
    cache-poi-lookups: true
    behavior-throttle:
      enabled: true
      require-distant: true     # only throttle beyond near-distance
      interval: 40              # ticks between tryStart calls
      one-shot-only: true       # only throttle OneShot behaviors

  entity-spread:
    enabled: true
    scan-interval: 100          # ticks
    near-distance: 32           # blocks

  entity-optimizer:
    enabled: true
    scan-interval: 40
    hotspot-threshold: 24       # entities per chunk
    require-distant: true
    filters:
      only-passive: true
      exclude-named: true
      exclude-leashed: true
      exclude-tamed: true
      exclude-babies: false
      include-types: []
      exclude-types: []
    collision:
      suppression-enabled: true
      time-slicing-enabled: true
      require-hotspot: true
      entities-per-collidable: 12
    ai:
      pathfinding-budget-enabled: true
      pathfinding-require-hotspot: true
      pathfinding-multiplier: 0.6
      goal-throttle-enabled: true
      goal-throttle-require-hotspot: true
      goal-throttle-stagger-enabled: true
      goal-throttle-idle-backoff-enabled: true
      goal-throttle-idle-backoff-threshold: 60
      goal-throttle-idle-backoff-step: 40
      goal-throttle-idle-backoff-max-can-use-interval: 200
      goal-throttle-idle-backoff-max-tick-interval: 20
      goal-throttle-default-can-use-interval: 20
      goal-throttle-default-tick-interval: 2
      goal-throttle-goals:
        - name: MeleeAttackGoal
          can-use-interval: 20
          tick-interval: 3
        - name: NearestAttackableTargetGoal
          can-use-interval: 20
          tick-interval: 2
        - name: HurtByTargetGoal
          can-use-interval: 20
          tick-interval: 2
        - name: RandomStrollGoal
          can-use-interval: 20
          tick-interval: 2
        - name: WaterAvoidingRandomStrollGoal
          can-use-interval: 20
          tick-interval: 2
        - name: RandomSwimmingGoal
          can-use-interval: 20
          tick-interval: 2
        - name: MoveThroughVillageGoal
          can-use-interval: 40
          tick-interval: 4
        - name: RemoveBlockGoal
          can-use-interval: 40
          tick-interval: 4
        - name: BreakDoorGoal
          can-use-interval: 40
          tick-interval: 4
        - name: RandomLookAroundGoal
          can-use-interval: 20
          tick-interval: 2
        - name: LookAtPlayerGoal
          can-use-interval: 20
          tick-interval: 2
```

## Commands

All commands require `yasui.admin` permission (OP by default).

| Command | Description |
|---------|-------------|
| `/yasui status` | Show optimizer statistics |
| `/yasui reload` | Reload configuration |
| `/yasui info` | Show plugin information |

### Status Output

```
=== Yasui Optimization Status ===
Hopper Optimizer: Enabled
  Active Hoppers: 342
  Cache Size: 215
  Cache Hits/Misses (1h): 423/12
  Transfers Canceled (1h): 892
  Pickups Canceled (1h): 156
  Cache Updates (1h): 2341

Villager POI Cache: Enabled
  Cached POIs: 87
  Job Site Restores (1h): 23/45
  Restore Candidates (1h): 312
  Hooked Brains: 94
  POI Search Cache: 156
  POI Search Hits/Misses (1h): 37/112
  Behavior Throttled: 67

Entity Distance Cache: Enabled
  Near Entities: 45 (full vanilla)
  Distant Entities: 1203 (cached distance)
  Tracked Entities: 1248

Entity Hotspot Optimizer: Enabled
  Suppressed Collisions: 512
  Time-Sliced Collisions: 341
  Pathfinding Budgeted: 220
  Goals Throttled: 198
  Goals Wrapped: 156
```

## Building

```bash
./gradlew clean build
```

**Output:**
- `build/libs/Yasui-*-reobf.jar` - Production (reobfuscated)
- `build/libs/Yasui-*.jar` - Development (Mojang mappings)

## Technical Notes

- Uses NMS for direct container and brain access (Mojang mappings via paperweight)
- All caches use `ConcurrentHashMap` for thread-safe reads
- World access runs on main thread only
- Chunk load/unload events trigger cache cleanup

## License

See LICENSE file.
