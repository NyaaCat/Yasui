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
- PoiAccess lookup cache for findAny/findClosest (NMS hook, short TTL)
- PoiManager getType/exists cache (NMS hook, short TTL)
- Validates POI block existence before restoration
- Optional hot-chunk PDC persistence and static-villager backoff for trade farms

Note: The AcquirePoi and PoiCompetitorScan hooks use JVM attach. If attach is disabled, the caches will be inactive.

### Entity Distance Cache

Categorizes entities by distance to nearest player for quick near/distant checks.

- NEAR (<32 blocks) or DISTANT (>32 blocks) classification
- Periodic distance recalculation
- No tick freezing - all entities continue normal behavior

### Hot Chunk Detection & Adaptive Caches

Detects hot areas by mob density and scales cache behavior without changing vanilla AI logic.

- Hot chunks are 16x16 by default, with optional `area-radius` aggregation (e.g., radius 1 = 3x3 chunks) to catch larger farms
- Heat is a [0,1] value that decays each scan and spikes based on mob density
- In hot chunks, pathfinding + POI caches (AcquirePoi/Competitor/PoiAccess/PoiType) scale linearly by heat
- Villagers in hot chunks can persist POI cache via PDC and skip scans if static
- Optional tick groups spread Mob AI ticks across N+1 groups (hot chunks only)
- Reuses Entity Distance Cache snapshots to avoid extra main-thread scans (optional)

Tick groups skip Mob `serverAiStep` (AI/navigation/control updates) but still allow normal movement/physics ticks.

### Pathfinding Cache

Caches recent pathfinding results to avoid repeated `createPath()` work within a short TTL.

- NMS hook caches path results per navigation instance
- Keeps AI behavior unchanged (only reuses identical results)
- Invalidates paths when mob/target move past thresholds or when chunk epochs change

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
    full-cache-negative-enabled: false
    full-cache-negative-ttl-ticks: 1
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
      predicate-aware: false
      source-bucket-size: 2
      fallback-on-insufficient: false
      renew-on-hit: false
    competitor-scan-cache:
      enabled: true
      ttl-ticks: 2
      ttl-jitter-ticks: 1
      max-entries: 10000
      cache-empty-results: false
      renew-on-hit: false
    poi-lookup-cache:
      enabled: true
      ttl-ticks: 100
      ttl-jitter-ticks: 10
      max-entries: 20000
      cache-empty-results: false
      predicate-aware: true
      source-bucket-size: 2
      renew-on-hit: false
    poi-type-cache:
      enabled: true
      ttl-ticks: 100
      ttl-jitter-ticks: 10
      max-entries: 20000
      cache-empty-results: true
      predicate-aware: true
      renew-on-hit: false

  chunk-epoch:
    enabled: true

  entity-spread:
    enabled: true
    scan-interval: 100          # ticks
    near-distance: 32           # blocks

  pathfinding-cache:
    enabled: true
    ttl-ticks: 3
    ttl-jitter-ticks: 1
    max-entries-per-nav: 4
    mob-move-threshold: 0
    target-move-threshold: 1
    negative-ttl-ticks: 0

  hot-chunks:
    enabled: true
    scan-interval: 40
    mob-threshold: 16
    area-radius: 1
    use-entity-spread-snapshots: true
    snapshot-max-age-ms: 10000
    heat-decay: 0.85
    min-heat: 0.15
    tick-groups: 0
    tick-group-rescan-chunks: 16
    villager-pdc:
      enabled: true
    villager-static:
      enabled: true
      stable-ticks: 100
      move-threshold: 0.1
      scan-interval-ticks: 200
    pathfinding-cache:
      enabled: true
      ttl-ticks: 8
      ttl-jitter-ticks: 3
      mob-move-threshold: 1
      target-move-threshold: 2
      negative-ttl-ticks: 0
    acquire-poi-cache:
      enabled: true
      ttl-ticks: 200
      ttl-jitter-ticks: 20
      renew-on-hit: true
    competitor-scan-cache:
      enabled: true
      ttl-ticks: 20
      ttl-jitter-ticks: 10
      renew-on-hit: true
    poi-lookup-cache:
      enabled: true
      ttl-ticks: 200
      ttl-jitter-ticks: 20
      renew-on-hit: true
    poi-type-cache:
      enabled: true
      ttl-ticks: 200
      ttl-jitter-ticks: 20
      renew-on-hit: true

```

### Hot Chunk Heat Model

```
pressure = min(1.0, mobCount / mobThreshold)
heat = heat * heatDecay
heat = max(heat, pressure)
```

`mobCount` is per-chunk when `area-radius = 0`, otherwise it is the summed count across the (2r+1)x(2r+1) area.
Heat is clamped to [0,1] and cools down by `heat-decay` every scan.

Linear scaling for hot-chunk caches:

```
effective = base + round((hot - base) * heat)
```

This linearly interpolates between the base value and the hot value as heat rises.

### Tuning Notes

- Pathfinding caches are safest with short TTLs; paths are tied to the current terrain + target position.
- For static farms, conservative `mob-move-threshold` values are 1-2 blocks (3-4 only if movement is constrained and terrain is static).
- Large thresholds or long TTLs can keep stale paths around; chunk-epoch invalidation helps but cannot catch every dynamic change.

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
  POI Lookup Cache: 98
  POI Lookup Hits/Misses (1h): 821/44
  PoiAccess Hook: Active
  POI Type Cache: 410
  POI Type Hits/Misses (1h): 913/50
  POI Exists Hits/Misses (1h): 402/33
  PoiManager Hook: Active

Entity Distance Cache: Enabled
  Near Entities: 45 (full vanilla)
  Distant Entities: 1203 (cached distance)
  Tracked Entities: 1248

Pathfinding Cache: Enabled
  Cache Hits/Misses (1h): 418/93
  Cache Stores (1h): 347
  NMS Path Cache Hook: Active

Hot Chunk Tracker: Enabled
  Hot Chunks: 6 (tracked: 18) / 423 Total Chunk Loaded
  Max Heat: 0.92 (min heat: 0.15)
  Mob Threshold: 16 (scan 40t, radius 1)
  Top Hot Chunks:
    - world c(12,34) b(192,544) heat=0.92 mobs=6 area=44
  Hot Boosts: Pathfinding On | AcquirePoi On | Competitor On | PoiLookup On | PoiType On
  Tick Groups: 2 (tick once every 2t, hot-only)
  Villager PDC: Enabled
  Villager Static: Enabled (stable 100t, scan 200t)
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
- PoiAccess/PoiManager hooks rely on the same attach mechanism
- Caches are TTL-based and use weak maps where appropriate
- Chunk epoch tracker invalidates caches on block/POI changes
- Hot chunk counts/area aggregation run async; world snapshots happen on main thread
- Hot chunk cache boosts scale linearly by heat; area-radius aggregates adjacent chunks
- Tick group PDC assignment is batched across ticks to minimize main-thread load

## License

See LICENSE file.
