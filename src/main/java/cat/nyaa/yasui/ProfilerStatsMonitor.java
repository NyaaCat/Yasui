package cat.nyaa.yasui;

import cat.nyaa.nyaacore.Pair;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ProfilerStatsMonitor extends BukkitRunnable {
    private final Yasui plugin;
    private final long startTickMillis;
    private final long startTickNano;
    private long currentTickMillis;
    private Map<World, Deque<Pair<Long, Map<ChunkCoordinate, ChunkStat>>>> stats = new HashMap<>(600);

    public ProfilerStatsMonitor(Yasui pl) {
        plugin = pl;
        plugin.profilerStatsMonitor = this;
        startTickMillis = System.currentTimeMillis();
        startTickNano = System.nanoTime();
        runTaskTimer(plugin, 0, 0);
    }

    @Override
    public void run() {
        currentTickMillis = startTickMillis + (System.nanoTime() - startTickNano) / 1000000;
        List<World> worlds = Bukkit.getWorlds();
        for (World world : worlds) {
            Deque<Pair<Long, Map<ChunkCoordinate, ChunkStat>>> worldStats = stats.computeIfAbsent(world, ignored -> new ConcurrentLinkedDeque<>());
            if (worldStats.size() == 600) {
                worldStats.poll();
            }
            Pair<Long, Map<ChunkCoordinate, ChunkStat>> rsCounterNode = Pair.of(currentTickMillis, new LinkedHashMap<>());
            worldStats.add(rsCounterNode);
        }
        for (World world : stats.keySet()) {
            if (!worlds.contains(world)) {
                stats.remove(world);
            }
        }
    }

    public Map<ChunkCoordinate, ChunkStat> currentRedstoneStats(World world) {
        return getRedstoneStats(world).getLast().getValue();
    }

    public Deque<Pair<Long, Map<ChunkCoordinate, ChunkStat>>> getRedstoneStats(World world) {
        return stats.get(world);
    }

    public long getCurrentTickMillis() {
        return currentTickMillis;
    }

    static class ChunkStat {
        private int redstone = 0;
        private int physics = 0;

        public int incRedstone() {
            return ++redstone;
        }

        public int incPhysics() {
            return ++physics;
        }

        public void add(ChunkStat value) {
            redstone += value.redstone;
            physics += value.physics;
        }

        public int getPhysics() {
            return physics;
        }

        public int getRedstone() {
            return redstone;
        }
    }
}
