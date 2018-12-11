package cat.nyaa.yasui;


import cat.nyaa.nyaacore.utils.NmsUtils;
import org.bukkit.Chunk;
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
        if (chunk == null) {
            return;
        }
        Utils.checkEntity(chunk);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (plugin.config.listen_mob_spawn && !plugin.config.ignored_spawn_reason.contains(event.getSpawnReason().name())) {
            int count = Utils.getLivingEntityCount(event.getLocation().getChunk());
            String worldName = event.getLocation().getWorld().getName();
            if (plugin.disableAIWorlds.contains(worldName) && count >= plugin.config.ai_chunk_entity && plugin.config.ai_ignored_entity_type.contains(event.getEntityType().name())) {
                NmsUtils.setFromMobSpawner(event.getEntity(), true);
            }
            int max = Yasui.INSTANCE.entityLimitWorlds.contains(worldName) ? Yasui.INSTANCE.config.entity_limit_per_chunk_max : -1;
            if (max >= 0 && count >= max && Utils.canRemove(event.getEntity())) {
                event.setCancelled(true);
            }
        }
    }
}
