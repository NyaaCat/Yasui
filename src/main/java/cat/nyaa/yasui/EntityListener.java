package cat.nyaa.yasui;


import cat.nyaa.nyaacore.utils.NmsUtils;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public class EntityListener implements Listener {
    private Yasui plugin;

    public EntityListener(Yasui pl) {
        pl.getServer().getPluginManager().registerEvents(this, pl);
        this.plugin = pl;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (chunk == null || !plugin.disableAIWorlds.contains(chunk.getWorld().getName()) || Utils.getLivingEntityCount(chunk) < plugin.config.chunk_entity) {
            return;
        }
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity && !plugin.config.ignored_entity_type.contains(entity.getType().name())) {
                NmsUtils.setFromMobSpawner(entity, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (plugin.config.listen_mob_spawn && !plugin.config.ignored_spawn_reason.contains(event.getSpawnReason().name()) & plugin.disableAIWorlds.contains(event.getLocation().getWorld().getName())) {
            if (Utils.getLivingEntityCount(event.getLocation().getChunk()) >= plugin.config.chunk_entity && plugin.config.ignored_entity_type.contains(event.getEntityType().name())) {
                NmsUtils.setFromMobSpawner(event.getEntity(), true);
            }
        }
    }
}
