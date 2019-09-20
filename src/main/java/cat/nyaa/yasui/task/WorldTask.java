package cat.nyaa.yasui.task;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.other.ModuleType;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class WorldTask extends BukkitRunnable {
    public static Map<String, WorldTask> taskMap = new HashMap<>();
    public static int delay = 0;
    public int worldViewDistance;
    public String id;
    public int livingEntityCount = 0;
    public int loadedChunkCount = 0;
    public int tileEntityCount;
    public Map<EntityType, Integer> mobcapEntityTypeCount = new HashMap<>();

    public WorldTask(World world) {
        this.id = world.getName();
        worldViewDistance = world.getViewDistance();
        update(world);
        for (Chunk chunk : world.getLoadedChunks()) {
            ChunkTask.getOrCreateTask(chunk);
        }
    }

    public static WorldTask getOrCreateTask(World world) {
        return getOrCreateTask(world.getName());
    }

    public static WorldTask getOrCreateTask(String id) {
        WorldTask task = taskMap.get(id);
        if (task == null) {
            World world = Bukkit.getWorld(id);
            task = new WorldTask(world);
            if (delay <= 0) {
                delay = Yasui.INSTANCE.config.scan_interval_tick;
            }
            delay -= 8;
            task.runTaskTimer(Yasui.INSTANCE, delay, Yasui.INSTANCE.config.scan_interval_tick);
            taskMap.put(id, task);
        }
        return task;
    }

    @Override
    public void run() {
        World world = Bukkit.getWorld(id);
        if (world != null) {
            update(world);
        } else {
            taskMap.remove(id);
            cancel();
        }
    }

    public void update(World world) {
        if (world != null) {
            livingEntityCount = 0;
            loadedChunkCount = 0;
            tileEntityCount = 0;
            mobcapEntityTypeCount.clear();
            if (Yasui.isPaper) {
                loadedChunkCount = world.getChunkCount();
                tileEntityCount = world.getTileEntityCount();
            } else {
                Chunk[] chunks = world.getLoadedChunks();
                loadedChunkCount = chunks.length;
                for (Chunk chunk : chunks) {
                    tileEntityCount += chunk.getTileEntities().length;
                }
            }
            if (Yasui.INSTANCE.config.getDefaultRegion(world).get(ModuleType.mobcap) != null) {
                for (LivingEntity e : world.getLivingEntities()) {
                    livingEntityCount++;
                    mobcapEntityTypeCount.put(e.getType(), mobcapEntityTypeCount.getOrDefault(e.getType(), 0) + 1);
                }
            } else {
                livingEntityCount = world.getLivingEntities().size();
            }
        }
    }
}
