package cat.nyaa.yasui;

import cat.nyaa.yasui.other.ChunkCoordinate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.Map;

public class ProfilerListener implements Listener {
    final private Yasui plugin;

    public ProfilerListener(Yasui pl) {
        plugin = pl;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPhysicsEvent(BlockPhysicsEvent event) {
        ProfilerStatsMonitor.ChunkStat stat = getStat(event);
        if (stat == null) return;
        stat.incPhysics();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockRedstoneEvent(BlockRedstoneEvent event) {
        ProfilerStatsMonitor.ChunkStat stat = getStat(event);
        if (stat == null) return;
        stat.incRedstone();
    }

    private ProfilerStatsMonitor.ChunkStat getStat(BlockEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        if (chunk == null) return null;
        Map<ChunkCoordinate, ProfilerStatsMonitor.ChunkStat> currentRedstoneStats = plugin.profilerStatsMonitor.currentRedstoneStats(chunk.getWorld());
        return currentRedstoneStats.computeIfAbsent(ChunkCoordinate.of(chunk.getWorld(), chunk.getX(), chunk.getZ()), (k) -> new ProfilerStatsMonitor.ChunkStat());
    }
}
