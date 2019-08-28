package cat.nyaa.yasui.task;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.other.ChunkCoordinate;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.other.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class RegionTask extends BukkitRunnable {
    public static Map<ChunkCoordinate, RegionTask> taskMap = new HashMap<>();
    public Set<ChunkCoordinate> loadedChunks = new HashSet<>();
    public ChunkCoordinate id;
    private Set<ChunkCoordinate> chunkCoordinates;

    public RegionTask(ChunkCoordinate id) {
        this.id = regionID(id);
    }

    public static ChunkCoordinate regionID(ChunkCoordinate id){
        return ChunkCoordinate.of(Bukkit.getWorld(id.getWorld()), id.getX() >> 5, id.getZ() >> 5);
    }

    public static RegionTask getOrCreateTask(Chunk chunk) {
        return getOrCreateTask(ChunkCoordinate.of(chunk));
    }

    public static RegionTask getOrCreateTask(World world, int chunkX, int chunkZ) {
        return getOrCreateTask(ChunkCoordinate.of(world, chunkX, chunkZ));
    }

    private static RegionTask getOrCreateTask(ChunkCoordinate id) {
        RegionTask task = taskMap.get(regionID(id));
        if (task == null) {
            task = new RegionTask(id);
            task.runTaskTimer(Yasui.INSTANCE, 1, Yasui.INSTANCE.config.scan_interval_tick);
            taskMap.put(task.id, task);
        }
        return task;
    }

    @Override
    public void run() {
        boolean defaultRegion = true;
        World world = Bukkit.getWorld(id.getWorld());
        if (world != null) {
            if (chunkCoordinates == null) {
                chunkCoordinates = getChunkCoordinate();
            }
            Map<ModuleType, Operation> modules = Yasui.INSTANCE.config.getDefaultRegion(world).getLimits();
            List<Entity> entities = new ArrayList<>();
            int regionEntitiesCount = 0;
            for (ChunkCoordinate c : chunkCoordinates) {
                if (world.isChunkLoaded(c.getX(), c.getZ())) {
                    ChunkTask task = ChunkTask.getOrCreateTask(c);
                    if (task.region.defaultRegion) {
                        loadedChunks.add(c);
                        regionEntitiesCount += task.LivingEntityCount;
                    } else {
                        defaultRegion = false;
                    }
                } else {
                    loadedChunks.remove(c);
                    ChunkTask task = ChunkTask.taskMap.get(c);
                    if (task != null) {
                        if (!task.isCancelled()) {
                            task.cancel();
                        }
                        ChunkTask.taskMap.remove(c);
                    }
                }
            }
            if (modules != null && modules.containsKey(ModuleType.entity_culler)) {
                Operation entity_culler = modules.get(ModuleType.entity_culler);
                int max = entity_culler.entity_culler_per_region_limit;
                if (max >= 0 && regionEntitiesCount > max) {
                    for (ChunkCoordinate c : loadedChunks) {
                        entities.addAll(Arrays.stream(world.getChunkAt(c.getX(), c.getZ()).getEntities()).filter(entity -> entity instanceof LivingEntity).collect(Collectors.toList()));
                    }
                    Collections.shuffle(entities);
                    int removed = 0;
                    regionEntitiesCount = entities.size();
                    for (Entity entity : entities) {
                        if (Utils.canRemove(entity, entity_culler)) {
                            entity.remove();
                            removed++;
                        }
                        if (max - removed >= regionEntitiesCount) {
                            break;
                        }
                    }
                }
            }
            if (loadedChunks.isEmpty() && defaultRegion) {
                cancel();
                taskMap.remove(id);
            }
        } else {
            cancel();
            taskMap.remove(id);
        }
    }

    public Set<ChunkCoordinate> getChunkCoordinate() {
        Set<ChunkCoordinate> list = new HashSet<>();
        World world = Bukkit.getWorld(id.getWorld());
        if (world != null) {
            int minX = id.getX() * 32;
            int minZ = id.getZ() * 32;
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    list.add(ChunkCoordinate.of(world, minX + x, minZ + z));
                }
            }
        }
        return list;
    }
}