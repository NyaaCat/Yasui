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
    public static Map<ChunkCoordinate, ChunkTask> taskMap = new HashMap<>();
    public static int delay = 1;
    public ChunkCoordinate id;
    public int pistonEvents = 0;
    public int redstoneEvents = 0;
    public int LivingEntityCount = 0;
    public int maxPistonEvents = 0;
    public int maxRedstoneEvents = 0;
    public int disabledRadius = 0;
    public ChunkCoordinate sourceId = null;
    public boolean allowRedstone = true;
    public boolean noAI = false;

    public ChunkTask(ChunkCoordinate id) {
        this.id = id;
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

    @Override
    public void run() {
        World world = Bukkit.getWorld(id.getWorld());
        Yasui plugin = Yasui.INSTANCE;
        if (world != null) {
            Map<ModuleType, Operation> map = TPSMonitor.worldLimits.get(world.getName());
            if (map != null) {
                Operation redstone = map.get(ModuleType.redstone_suppressor);
                if (redstone != null && allowRedstone && (pistonEvents >= redstone.redstone_suppressor_piston_per_chunk || redstoneEvents >= redstone.redstone_suppressor_per_chunk)) {
                    plugin.redstoneListener.disableRedstone(world.getChunkAt(id.getX(), id.getZ()), redstone.redstone_suppressor_supress_chunk_region, redstoneEvents, pistonEvents);
                } else if (redstone == null ||
                        (maxPistonEvents < redstone.redstone_suppressor_piston_per_chunk && maxRedstoneEvents < redstone.redstone_suppressor_per_chunk)) {
                    allowRedstone = true;
                    sourceId = null;
                    maxRedstoneEvents = 0;
                    maxPistonEvents = 0;
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
}