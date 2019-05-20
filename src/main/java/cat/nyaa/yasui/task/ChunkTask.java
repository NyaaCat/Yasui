package cat.nyaa.yasui.task;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.other.ChunkCoordinate;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.other.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class ChunkTask extends BukkitRunnable {
    public ChunkCoordinate id;
    public static Map<ChunkCoordinate, ChunkTask> taskMap = new HashMap<>();
    public int pistonEvents = 0;
    public int redstoneEvents = 0;
    public int LivingEntityCount = 0;
    public static int delay = 1;

    public ChunkTask(ChunkCoordinate id) {
        this.id = id;
    }


    @Override
    public void run() {
        World world = Bukkit.getWorld(id.getWorld());
        Yasui plugin = Yasui.INSTANCE;
        if (world != null) {
            Map<ModuleType, Operation> map = TPSMonitor.worldLimits.get(world.getName());
            if (map != null) {
                Operation redstone = map.get(ModuleType.redstone_suppressor);
                if (redstone != null
                        && !plugin.redstoneListener.disabledChunks.containsKey(id)
                        && (pistonEvents >= redstone.redstone_suppressor_piston_per_chunk || redstoneEvents >= redstone.redstone_suppressor_per_chunk)) {
                    plugin.redstoneListener.disableRedstone(world.getChunkAt(id.getX(), id.getZ()), redstone.redstone_suppressor_supress_chunk_region, redstoneEvents, pistonEvents);
                }
            }
            if (world.isChunkLoaded(id.getX(), id.getZ())) {
                Chunk chunk = world.getChunkAt(id.getX(), id.getZ());
                Utils.checkLivingEntity(chunk);
            }
        }
        redstoneEvents = 0;
        pistonEvents = 0;
    }

    public static ChunkTask getOrCreateTask(Chunk chunk) {
        return getOrCreateTask(ChunkCoordinate.of(chunk));
    }

    public static ChunkTask getOrCreateTask(ChunkCoordinate id) {
        ChunkTask task = taskMap.get(id);
        if (task == null) {
            task = new ChunkTask(id);
            task.runTaskTimer(Yasui.INSTANCE, delay++, Yasui.INSTANCE.config.scan_interval_tick);
            if (delay >= Yasui.INSTANCE.config.scan_interval_tick) {
                delay = 1;
            }
            taskMap.put(id, task);
        }
        return task;
    }
}