package cat.nyaa.yasui.listener;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.other.ChunkCoordinate;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.other.NoAIType;
import cat.nyaa.yasui.other.Utils;
import cat.nyaa.yasui.task.ChunkTask;
import cat.nyaa.yasui.task.WorldTask;
import com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent;
import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;
import org.bukkit.Chunk;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

public class EntityListener implements Listener {
    private Yasui plugin;

    public EntityListener(Yasui pl) {
        pl.getServer().getPluginManager().registerEvents(this, pl);
        if (Yasui.isPaper) {
            pl.getServer().getPluginManager().registerEvents(new EntityListenerPaper(pl), pl);
        }
        this.plugin = pl;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        Chunk chunk = event.getLocation().getChunk();
        ChunkTask chunkTask = ChunkTask.getOrCreateTask(chunk);
        WorldTask worldTask = WorldTask.getOrCreateTask(chunk.getWorld());
        Operation entity_culler = chunkTask.region.get(ModuleType.entity_culler);
        Operation entity_ai_suppressor = chunkTask.region.get(ModuleType.entity_ai_suppressor);
        Operation mobcap = chunkTask.region.get(ModuleType.mobcap);
        if (mobcap != null) {
            if (worldTask.livingEntityCount >= mobcap.mobcap_global_hard && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
                event.setCancelled(true);
                return;
            }
            plugin.entityListener.onPreCreatureSpawn(event.getEntityType(), chunk, event, chunkTask, mobcap);
        }
        if (!event.isCancelled() && (entity_culler != null || entity_ai_suppressor != null)) {
            if (entity_culler != null && entity_culler.entity_culler_per_chunk_limit <= chunkTask.LivingEntityCount && !entity_culler.entity_culler_excluded_type.contains(event.getEntityType())) {
                event.setCancelled(true);
                return;
            }
            Utils.setAI(event.getEntity(), entity_ai_suppressor == null, entity_ai_suppressor);
            chunkTask.LivingEntityCount++;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityTargetLivingEntity(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && event.getEntity() instanceof LivingEntity) {
            ChunkTask task = ChunkTask.getOrCreateTask(event.getEntity().getLocation().getChunk());
            Operation o = task.region.get(ModuleType.entity_ai_suppressor);
            Entity entity = event.getEntity();
            if (o != null && o.entity_ai_suppresse_method == NoAIType.NO_TARGETING
                    && !o.entity_ai_suppressor_exclude_type.contains(entity.getType())
                    && !(o.entity_ai_suppressor_exclude_tagged && entity.getCustomName() != null)
                    && !(o.entity_ai_suppressor_exclude_tamed && entity instanceof Tameable && ((Tameable) entity).getOwner() != null)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        ChunkTask task = ChunkTask.getOrCreateTask(e.getChunk());
        WorldTask worldTask = WorldTask.getOrCreateTask(e.getWorld());
        task.LivingEntityCount--;
        worldTask.livingEntityCount--;
        if (task.region.get(ModuleType.mobcap) != null) {
            task.mobcapEntityTypeCount.put(e.getType(), task.mobcapEntityTypeCount.getOrDefault(e.getType(), 1) - 1);
            worldTask.mobcapEntityTypeCount.put(e.getType(), worldTask.mobcapEntityTypeCount.getOrDefault(e.getType(), 1) - 1);
        }
    }

    public void onPreCreatureSpawn(EntityType type, Chunk chunk, Event event, ChunkTask chunkTask, Operation mobcap) {
        if (mobcap != null) {
            WorldTask wt = WorldTask.getOrCreateTask(chunk.getWorld());
            int total = wt.livingEntityCount;
            if (total >= 0 && (total >= mobcap.mobcap_global_soft || total >= mobcap.mobcap_global_hard)) {
                cancelEvent(event);
                return;
            }
            Integer chunkLimit = mobcap.mobcap_chunk_types.getOrDefault(type, mobcap.mobcap_chunk_default);
            Integer chunkCurrent = chunkTask.mobcapEntityTypeCount.getOrDefault(type, 0);
            Integer worldCurrent = wt.mobcapEntityTypeCount.getOrDefault(type, 0);
            if (chunkLimit >= 0 && chunkLimit <= chunkCurrent) {
                cancelEvent(event);
                return;
            }
            Integer globalLimit = mobcap.mobcap_global_types.getOrDefault(type, -1);
            if (globalLimit >= 0 && globalLimit <= worldCurrent) {
                cancelEvent(event);
                return;
            }
            if (event instanceof CreatureSpawnEvent) {
                chunkCurrent++;
                worldCurrent++;
                chunkTask.mobcapEntityTypeCount.put(type, chunkCurrent);
                wt.mobcapEntityTypeCount.put(type, worldCurrent);
                wt.livingEntityCount++;
            }
        }
    }

    private void cancelEvent(Event event) {
        if (event instanceof CreatureSpawnEvent) {
            ((CreatureSpawnEvent) event).setCancelled(true);
        } else if (event instanceof PreCreatureSpawnEvent) {
            ((PreCreatureSpawnEvent) event).setCancelled(true);
            ((PreCreatureSpawnEvent) event).setShouldAbortSpawn(true);
        }
    }
}

class EntityListenerPaper implements Listener {
    private Yasui plugin;

    public EntityListenerPaper(Yasui pl) {
        plugin = pl;
    }

    @EventHandler(ignoreCancelled = true)
    public void onNaturallySpawn(PlayerNaturallySpawnCreaturesEvent event) {
        Player p = event.getPlayer();
        Operation mobcap = plugin.config.getRegion(ChunkCoordinate.of(p)).get(ModuleType.mobcap);
        if (mobcap != null) {
            WorldTask task = WorldTask.getOrCreateTask(p.getWorld());
            if (task.livingEntityCount >= mobcap.mobcap_global_soft || task.livingEntityCount >= mobcap.mobcap_global_hard) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreCreatureSpawn(PreCreatureSpawnEvent event) {
        Chunk chunk = event.getSpawnLocation().getChunk();
        ChunkTask task = ChunkTask.getOrCreateTask(chunk);
        Operation entity_culler = task.region.get(ModuleType.entity_culler);
        if (entity_culler != null && entity_culler.entity_culler_per_chunk_limit <= task.LivingEntityCount && !entity_culler.entity_culler_excluded_type.contains(event.getType())) {
            event.setCancelled(true);
            return;
        }
        plugin.entityListener.onPreCreatureSpawn(event.getType(), chunk, event, task, task.region.get(ModuleType.mobcap));
    }
}