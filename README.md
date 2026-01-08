# Yasui - Paper Server Optimization Plugin

**Target:** Paper 1.21.8 + Java 21
**Goal:** Reduce server tick time by ~13-15ms while preserving vanilla behavior

## Overview

Yasui is a performance optimization plugin that reduces server lag by caching expensive operations identified through Paper's profiler. Based on analysis showing 87.29ms/tick (~11.5 TPS), this plugin targets a reduction to 72-74ms/tick (13.5-14 TPS) through intelligent caching and event-driven optimizations.

## Features

### 1. Hopper Optimization (~3-4ms savings)

**Problem:** Hopper transfer operations repeatedly check container fullness using expensive `isFullContainer()` calls, consuming 4.99ms/tick.

**Solution:**
- **Event-driven caching**: Monitors hopper activity via `InventoryMoveItemEvent` and `InventoryPickupItemEvent`
- **Periodic pre-computation**: Async scans every 5 ticks to cache container fullness states
- **Early cancellation**: Cancels transfer events at LOWEST priority before vanilla logic executes
- **Fast exchange support**: Updates cache 1 tick after transfer for rapid item exchange machines
- **TTL-based expiration**: Cache entries valid for 1 second (configurable)

**Technical Details:**
- Uses NMS `HopperBlockEntity.getContainerAt()` for fast container checks
- Validates `WorldlyContainer.getSlotsForFace()` for proper slot filtering
- Batch processing limited to 200 hoppers/tick to prevent lag spikes
- Thread-safe `ConcurrentHashMap` for cache storage

**Events Handled:**
- `InventoryMoveItemEvent` - Validate transfers & track successful moves
- `InventoryPickupItemEvent` - Prevent pickups to full hoppers
- `BlockPlaceEvent` / `BlockBreakEvent` - Track hopper lifecycle
- `ChunkLoadEvent` / `ChunkUnloadEvent` - Clean up cache on chunk changes

### 2. Villager POI Cache (~5ms savings)

**Problem:** Villager AI consumes 20.25ms/tick searching for Points of Interest (job sites):
- AcquirePoi: 2.02ms (POI searches)
- PoiCompetitorScan: 2.35ms (competitor checking)
- Brain behavior evaluation: 10.69ms

**Solution:**
- **Memory restoration**: Caches job site locations from villager brain memory (JOB_SITE / POTENTIAL_JOB_SITE)
- **Safe brain injection**: Adds custom `CachedJobSiteBehavior` to villager CORE activity to restore cached POI
- **POI block validation**: Tracks 16 job site block types (composter, barrel, furnaces, tables, etc.)
- **Distance gating**: Only optimizes villagers within configurable distance of players
- **Smart invalidation**: Clears cache when POI blocks are placed/broken

**Cache Details:**
- Cache entry: (location, blockHashCode, timestamp)
- TTL: 5 minutes per POI entry
- Memory restore cooldown: 5 seconds per villager
- Maximum restore distance: 64 blocks (8x8 chunk area)

**Restoration Conditions:**
Only restores cached POI when:
- Villager has empty job site memory (safe to restore)
- Villager is adult with profession (not NONE or NITWIT)
- Cached POI is within 64 blocks and in same world
- POI block still exists and is valid
- Cooldown period has expired

**Statistics Tracked:**
- Cache size: Number of villagers being optimized
- Restore attempts: Total restoration attempts
- Restore applied: Successful restorations

### 3. Entity Distance Cache (gate for optimizations)

**Purpose:** Categorizes entities by distance to nearest player to enable intelligent optimization gating.

**Features:**
- **Distance categorization**: Classifies entities as NEAR (<32 blocks) or DISTANT (>32 blocks)
- **No tick freezing**: Unlike older approaches, all entities still tick normally in vanilla
- **Periodic scanning**: Updates every 100 ticks (5 seconds) by default
- **Squared distance caching**: Stores distance² to avoid expensive sqrt() calculations

**Usage:**
Provides distance data for other optimizations to make intelligent decisions about when to apply expensive operations. Does not directly modify entity behavior.

**Statistics:**
- Near entities: Count within threshold (full vanilla behavior)
- Distant entities: Count beyond threshold (marked for potential optimization)
- Tracked entities: Total entities being monitored

## Why It Works

### Profiler Analysis

The Paper profiler identified these bottlenecks:
- **Hoppers:** 4.99ms/tick - Repeated fullness checks on every transfer attempt
- **Villagers:** 20.25ms/tick - POI searches, competitor scans, behavior tree evaluation
- **Entity ticking:** 43.21ms/tick - All active entities tick every tick

### Optimization Strategy

**Caching Strategies:**
1. **TTL-based expiration** - Reduces memory bloat while keeping useful data
2. **Event-driven invalidation** - Immediate cache clearing on relevant events
3. **Batch processing** - Spreads work across multiple ticks to prevent lag spikes

**Thread Safety:**
- `ConcurrentHashMap` for lock-free reads (most common operation)
- World access always on main thread (no race conditions)
- Minimal synchronization overhead

**NMS Optimization:**
- Direct container access avoids expensive Bukkit `BlockState` snapshots
- Direct villager brain manipulation for memory restoration
- Mojang mappings make code maintainable

### Vanilla Behavior Preservation

All optimizations are designed to preserve vanilla behavior:
- Items transfer at same speed (8-tick hopper cooldown)
- Villagers behave identically (memory restoration is safe and expected)
- Mobs act normally (no tick freezing, all entities tick)
- Redstone timing unchanged

## Installation

```bash
# Drop the reobfuscated JAR into plugins folder
cp build/libs/Yasui-mc1.21.8-7.0.x-reobf.jar /path/to/server/plugins/

# Restart server - config auto-creates at plugins/yasui/config.yml
```

No dependencies required.

## Configuration

Edit `plugins/yasui/config.yml`:

```yaml
optimizations:
  hopper:
    enabled: true                # Enable/disable hopper optimization
    async-scan-interval: 5       # Ticks between batch scans (default: 5)
    cache-ttl: 1000              # Cache validity in milliseconds (default: 1000)
    max-scan-per-tick: 200       # Max hoppers to check per tick (prevents lag spikes)

  villager-poi:
    enabled: true                # Enable/disable villager optimization
    cache-poi-lookups: true      # Cache and restore POI lookups
    rules:                       # Optional: custom optimization rules per entity type
      - type: VILLAGER
        named: true              # Only/exclude named entities
        distance: ">64"          # Distance condition (>, <, >=, <=, =)
        optimize: true           # Whether to optimize when rule matches

  entity-spread:
    enabled: true                # Enable/disable distance cache
    scan-interval: 100           # Update distance every N ticks (default: 100)
    near-distance: 32            # Block threshold for NEAR category (default: 32)
    # Legacy settings (tick freezing removed - no longer used):
    default-interval: 2          # Unused
    rules: []                    # Unused
```

### Configuration Options Explained

**Hopper Settings:**
- `enabled`: Toggle hopper optimizer on/off
- `async-scan-interval`: How often to scan all hoppers (lower = more responsive, higher = less overhead)
- `cache-ttl`: How long cached state remains valid in milliseconds
- `max-scan-per-tick`: Batch limit to prevent lag spikes from huge hopper farms

**Villager POI Settings:**
- `enabled`: Toggle villager optimizer on/off
- `cache-poi-lookups`: Enable POI caching and memory restoration
- `rules`: Advanced per-entity optimization rules (optional)
  - `type`: Entity type (e.g., VILLAGER)
  - `named`: true = only named entities, false = exclude named entities
  - `distance`: Distance condition using operators (>, <, >=, <=, =)
  - `optimize`: Whether to apply optimization when rule matches

**Entity Distance Settings:**
- `enabled`: Toggle entity distance cache on/off
- `scan-interval`: How often to recalculate distances (affects overhead)
- `near-distance`: Distance threshold in blocks for NEAR vs DISTANT categorization

## Commands

All commands require `yasui.admin` permission (OP by default).

### `/yasui status`
Displays real-time optimization status and statistics.

**Output Example:**
```
=== Yasui Optimization Status ===
Hopper Optimizer: Enabled
  Active Hoppers: 342
  Cache Size: 215

Villager POI Cache: Enabled
  Cached POIs: 87
  Job Site Restores: 152/287

Entity Distance Cache: Enabled
  Near Entities: 45 (full vanilla)
  Distant Entities: 1203 (cached distance)
  Tracked Entities: 1248
```

**Metrics Explained:**
- **Active Hoppers**: Total hoppers being tracked
- **Cache Size**: Number of cached hopper states
- **Cached POIs**: Number of villagers with cached job sites
- **Job Site Restores**: Successful/attempted memory restorations
- **Near/Distant Entities**: Entity distribution by distance category

### `/yasui reload`
Reloads configuration from disk and restarts all optimizers.

**Process:**
1. Reloads `config.yml`
2. Shuts down active optimizers
3. Reinitializes based on new config
4. Clears all caches

### `/yasui info`
Displays plugin version, target platform, and feature overview.

## Technical Details

### Architecture

**Build System:**
- Gradle 9.0 with paperweight-userdev 2.0.0-beta.19
- Produces both dev (Mojang mappings) and reobfuscated (production) JARs

**NMS Integration:**
- Direct access to `net.minecraft.*` classes using Mojang mappings
- Key NMS classes used:
  - `HopperBlockEntity`, `Container`, `WorldlyContainer` (hopper optimization)
  - `Villager`, `Brain<Villager>`, `MemoryModuleType` (POI cache)
  - `PoiManager`, `PoiType`, `GlobalPos` (POI validation)
  - `BlockPos`, `Direction`, `ServerLevel` (world access)

**Thread Safety:**
- `ConcurrentHashMap` for all caches (lock-free reads)
- World access always on main thread (no race conditions)
- Event handlers run on main thread
- Periodic tasks scheduled via Bukkit scheduler

### Performance Characteristics

**Hopper Optimizer:**
- Batch processing: 200 hoppers/tick maximum
- Cache TTL: 1 second (1000ms)
- Scan interval: Every 5 ticks (250ms)
- Memory overhead: ~48 bytes per cached hopper

**Villager POI Cache:**
- Scan interval: Every 20 ticks (1 second)
- Cache TTL: 5 minutes (300,000ms)
- Restore cooldown: 5 seconds per villager
- Memory overhead: ~64 bytes per cached POI

**Entity Distance Cache:**
- Scan interval: Every 100 ticks (5 seconds)
- Distance calculation: Uses squared distance (no sqrt)
- Memory overhead: ~40 bytes per tracked entity

### Design Decisions

**Why Event-Driven + Periodic Scanning?**
- Events capture immediate state changes
- Periodic scans catch inactive hoppers and background updates
- Hybrid approach provides both responsiveness and completeness

**Why No Tick Freezing?**
- Earlier optimization attempts froze entity ticking for distant entities
- Results in delayed mob AI and inconsistent player experience
- Current approach: distance cache for gating, all entities tick normally

**Why Direct NMS Access?**
- Bukkit API lacks optimized container fullness checks
- NMS allows direct access to internal state without expensive snapshots
- Mojang mappings make code readable and maintainable

## Building

```bash
./gradlew clean build
```

**Output:**
- Production JAR: `build/libs/Yasui-mc1.21.8-7.0.x-reobf.jar` (use this on servers)
- Dev JAR: `build/libs/Yasui-mc1.21.8-7.0.x.jar` (Mojang mappings for development)

**Requirements:**
- Java 21+
- Gradle 9.0+

## How It Works

### Hopper Optimization Flow

```
1. Player/automation places item in hopper input
2. InventoryMoveItemEvent fires (LOWEST priority)
3. HopperOptimizer checks cache for target fullness
   - If full: Cancel event (save computation)
   - If not full: Allow vanilla logic to execute
4. Vanilla hopper transfer logic executes
5. InventoryMoveItemEvent fires (MONITOR priority)
   - Updates active hopper set
   - Invalidates cache
   - Schedules cache update 1 tick later
6. Cache update calculates new state
7. Next transfer uses fresh cache
```

### Villager POI Cache Flow

```
1. Villager brain tick occurs
2. Scan task (every 20 ticks):
   - Reads JOB_SITE and POTENTIAL_JOB_SITE from brain memory
   - If present, caches location + block hash + timestamp
3. When villager loses job site memory:
   - CachedJobSiteBehavior checks if cached POI available
   - Validates cache (not expired, POI block still exists, within range)
   - Restores cached location to POTENTIAL_JOB_SITE memory
   - Villager proceeds with known job site
   - Avoids expensive AcquirePoi behavior
```

### Entity Distance Cache Flow

```
1. Periodic task (every 100 ticks):
   - Scans all loaded worlds
   - Calculates squared distance from each entity to nearest player
   - Categorizes as NEAR (<32 blocks) or DISTANT (>32 blocks)
   - Stores in ConcurrentHashMap
2. Other optimizations query distance data to make decisions
3. All entities continue to tick normally (no behavior modification)
```

## Troubleshooting

### Job Site Restores Shows 0/0

This is normal if:
- All villagers with cached POIs still remember their job sites
- Restoration only triggers when villager brain memory is empty
- Villagers must be adult with valid profession (not NONE/NITWIT)
- Cache must not be expired (5 minute TTL)

### Hopper Cache Not Working

Check that:
- Hoppers are in loaded chunks
- `cache-ttl` hasn't expired (default 1000ms)
- Hoppers are actively attempting transfers
- `enabled: true` in config

### High Memory Usage

Adjust cache settings:
- Reduce `cache-ttl` for hoppers (default: 1000ms)
- Reduce scan intervals to clean up inactive entries
- Use distance rules to limit villager optimization scope

## Credits

- **Original authors**: cylin
- **Refactored for Paper 1.21.8**: Complete rewrite using modern NMS integration and paperweight

## License

See project license file for details.
