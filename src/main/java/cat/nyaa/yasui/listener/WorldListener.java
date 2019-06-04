package cat.nyaa.yasui.listener;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.other.Utils;
import cat.nyaa.yasui.task.TPSMonitor;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.HashMap;

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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        TPSMonitor.worldLimits.put(event.getWorld().getName(), new HashMap<>());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        TPSMonitor.worldLimits.remove(event.getWorld().getName());
    }
}