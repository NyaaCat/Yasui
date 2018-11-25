package cat.nyaa.yasui;


import cat.nyaa.nyaacore.utils.NmsUtils;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
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
        if (!plugin.disableAIWorlds.contains(chunk.getWorld().getName())) {
            return;
        }
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity && plugin.noAIMobs.contains(entity.getUniqueId())) {
                NmsUtils.setFromMobSpawner(entity, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        plugin.noAIMobs.remove(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (plugin.config.listen_mob_spawn && !plugin.config.ignored_spawn_reason.contains(event.getSpawnReason().name()) & plugin.disableAIWorlds.contains(event.getLocation().getWorld().getName())) {
            if (Utils.getLivingEntityCount(event.getLocation().getChunk()) >= plugin.config.chunk_entity && plugin.config.ignored_entity_type.contains(event.getEntityType().name())) {
                NmsUtils.setFromMobSpawner(event.getEntity(), true);
                plugin.noAIMobs.add(event.getEntity().getUniqueId());
            }
        }
    }
}
