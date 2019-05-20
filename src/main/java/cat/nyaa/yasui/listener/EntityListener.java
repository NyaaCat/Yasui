package cat.nyaa.yasui.listener;


import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.other.NoAIType;
import cat.nyaa.yasui.other.Utils;
import cat.nyaa.yasui.task.ChunkTask;
import cat.nyaa.yasui.task.TPSMonitor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.Map;

public class EntityListener implements Listener {
    private Yasui plugin;

    public EntityListener(Yasui pl) {
        pl.getServer().getPluginManager().registerEvents(this, pl);
        this.plugin = pl;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        Map<ModuleType, Operation> map = TPSMonitor.worldLimits.get(event.getEntity().getWorld().getName());
        if (map != null) {
            Operation entity_culler = map.get(ModuleType.entity_culler);
            Operation entity_ai_suppressor = map.get(ModuleType.entity_ai_suppressor);
            if (entity_culler != null || entity_ai_suppressor != null) {
                int count = ChunkTask.getOrCreateTask(event.getLocation().getChunk()).LivingEntityCount;//Utils.getLivingEntityCount(event.getLocation().getChunk());
                if (entity_culler != null && entity_culler.entity_culler_per_chunk_limit <= count && !entity_culler.entity_culler_excluded_type.contains(event.getEntityType())) {
                    event.setCancelled(true);
                    return;
                }
                Utils.setAI(event.getEntity(), entity_ai_suppressor == null, entity_ai_suppressor);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityTargetLivingEntity(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && event.getEntity() instanceof LivingEntity) {
            Map<ModuleType, Operation> l = TPSMonitor.worldLimits.get(event.getEntity().getWorld().getName());
            Operation o = l.get(ModuleType.entity_ai_suppressor);
            if (o != null && o.entity_ai_suppresse_method == NoAIType.NO_TARGETING) {
                event.setCancelled(true);
            }
        }
    }
}
