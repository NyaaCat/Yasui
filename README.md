# Yasui

Server optimization plugin for Paper 1.21.8. Reduces tick time by caching expensive operations while keeping AI and collision behavior vanilla.

**Requirements:** Paper 1.21.8, Java 21+

Start parameters:

```
-XX:+EnableDynamicAgentLoading -Djdk.attach.allowAttachSelf=true
```

With full JDK.

## Features

### Hopper Optimizer

Caches container fullness state to short-circuit redundant `isFullContainer()` checks during hopper transfers.

- NMS hook that short-circuits repeated full checks (tiny TTL cache)

Note: The NMS hook uses JVM attach. If attach is disabled, the hook will be inactive.

### Villager POI Cache

Reduces POI search overhead by caching job site locations and AcquirePoi searches.

- Caches job site positions from villager brain memory
- Restores cached POI when villager loses job site memory
- AcquirePoi search cache (NMS hook, short TTL)
- PoiCompetitorScan POI type cache (NMS hook, short TTL)
- Validates POI block existence before restoration

Note: The AcquirePoi and PoiCompetitorScan hooks use JVM attach. If attach is disabled, the caches will be inactive.

### Entity Distance Cache

Categorizes entities by distance to nearest player for quick near/distant checks.

- NEAR (<32 blocks) or DISTANT (>32 blocks) classification
- Periodic distance recalculation
- No tick freezing - all entities continue normal behavior

### Pathfinding Cache

Caches recent pathfinding results to avoid repeated `createPath()` work within a short TTL.

- NMS hook caches path results per navigation instance
- Keeps AI behavior unchanged (only reuses identical results)

Note: This hook also uses JVM attach. If attach is disabled, the cache will be inactive.

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
    full-cache-enabled: true
    full-cache-ttl-ticks: 2
    full-cache-invalidate-on-event: true

  villager-poi:
    enabled: true
    restore-job-site: true
    acquire-poi-cache:
      enabled: true
      ttl-ticks: 100
      ttl-jitter-ticks: 10
      max-entries: 20000
      cache-empty-results: false
    competitor-scan-cache:
      enabled: true
      ttl-ticks: 2
      ttl-jitter-ticks: 1
      max-entries: 10000
      cache-empty-results: false

  entity-spread:
    enabled: true
    scan-interval: 100          # ticks
    near-distance: 32           # blocks

  pathfinding-cache:
    enabled: true
    ttl-ticks: 3
    ttl-jitter-ticks: 1
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
  Full Cache Hits/Misses (1h): 423/12
  Full Cache Stores/Invalidations (1h): 2341/56
  NMS Full-Check Hook: Active

Villager POI Cache: Enabled
  Cached POIs: 87
  Job Site Restores (1h): 23/45
  Restore Candidates (1h): 312
  POI Search Cache: 156
  POI Search Hits/Misses (1h): 37/112
  AcquirePoi Hook: Active
  POI Competitor Cache: 72
  POI Competitor Hits/Misses (1h): 412/98
  CompetitorScan Hook: Active

Entity Distance Cache: Enabled
  Near Entities: 45 (full vanilla)
  Distant Entities: 1203 (cached distance)
  Tracked Entities: 1248

Pathfinding Cache: Enabled
  Cache Hits/Misses (1h): 418/93
  Cache Stores (1h): 347
  NMS Path Cache Hook: Active
```

## Building

```bash
./gradlew clean build
```

**Output:**
- `build/libs/Yasui-*-reobf.jar` - Production (reobfuscated)
- `build/libs/Yasui-*.jar` - Development (Mojang mappings)

## Technical Notes

- Uses NMS hooks (JVM attach) for hopper, pathfinding, and AcquirePoi caching
- Caches are TTL-based and use weak maps where appropriate
- Entity distance calculations run async; world snapshots happen on main thread

## License

See LICENSE file.
