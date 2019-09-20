package cat.nyaa.yasui.listener;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.other.Utils;
import cat.nyaa.yasui.task.WorldTask;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldListener implements Listener {
    private Yasui plugin;

    public WorldListener(Yasui pl) {
        pl.getServer().getPluginManager().registerEvents(this, pl);
        this.plugin = pl;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (chunk == null || event.isNewChunk()) {
            return;
        }
        Utils.checkLivingEntity(chunk);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        WorldTask.getOrCreateTask(event.getWorld());
    }
}