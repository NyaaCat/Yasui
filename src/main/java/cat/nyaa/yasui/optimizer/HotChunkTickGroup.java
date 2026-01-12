package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Persists tick group assignments for mobs in hot chunks.
 */
public class HotChunkTickGroup implements Listener {
    private final Yasui plugin;
    private final YasuiConfig config;
    private final NamespacedKey groupKey;
    private final NamespacedKey groupVersionKey;
    private BukkitTask rescanTask;
    private Iterator<Chunk> rescanIterator;

    public HotChunkTickGroup(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.groupKey = new NamespacedKey(plugin, "tick_group");
        this.groupVersionKey = new NamespacedKey(plugin, "tick_group_version");
    }

    public void start() {
        if (!isEnabled()) {
            return;
        }
        scheduleRescan();
    }

    public void shutdown() {
        if (rescanTask != null) {
            rescanTask.cancel();
            rescanTask = null;
        }
        rescanIterator = null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!isEnabled()) {
            return;
        }
        World world = event.getWorld();
        if (world == null) {
            return;
        }
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        if (!isChunkHot(world, chunkX, chunkZ, event.getEntities())) {
            return;
        }
        assignGroups(event.getEntities());
    }

    private void scheduleRescan() {
        if (rescanTask != null) {
            rescanTask.cancel();
            rescanTask = null;
        }
        List<Chunk> chunks = new ArrayList<>();
        for (World world : plugin.getServer().getWorlds()) {
            chunks.addAll(Arrays.asList(world.getLoadedChunks()));
        }
        if (chunks.isEmpty()) {
            rescanIterator = null;
            return;
        }
        rescanIterator = chunks.iterator();
        int batch = Math.max(1, config.getHotChunkTickGroupRescanChunks());
        rescanTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> runRescanBatch(batch), 1L, 1L);
    }

    private void runRescanBatch(int batch) {
        if (rescanIterator == null) {
            return;
        }
        int processed = 0;
        while (processed < batch && rescanIterator.hasNext()) {
            Chunk chunk = rescanIterator.next();
            if (chunk == null) {
                processed++;
                continue;
            }
            World world = chunk.getWorld();
            if (isChunkHot(world, chunk.getX(), chunk.getZ(), null)) {
                assignGroups(Arrays.asList(chunk.getEntities()));
            }
            processed++;
        }
        if (!rescanIterator.hasNext()) {
            if (rescanTask != null) {
                rescanTask.cancel();
                rescanTask = null;
            }
            rescanIterator = null;
        }
    }

    private void assignGroups(Iterable<? extends Entity> entities) {
        int groupCount = getGroupCount();
        int groupVersion = getGroupVersion();
        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            assignGroup(mob, groupCount, groupVersion);
        }
    }

    private void assignGroup(Mob mob, int groupCount, int groupVersion) {
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        Integer storedVersion = pdc.get(groupVersionKey, PersistentDataType.INTEGER);
        if (storedVersion != null && storedVersion == groupVersion) {
            Integer storedGroup = pdc.get(groupKey, PersistentDataType.INTEGER);
            if (storedGroup != null) {
                return;
            }
        }
        int group = computeGroup(mob.getUniqueId(), groupCount);
        pdc.set(groupKey, PersistentDataType.INTEGER, group);
        pdc.set(groupVersionKey, PersistentDataType.INTEGER, groupVersion);
    }

    private boolean isChunkHot(World world, int chunkX, int chunkZ, Iterable<? extends Entity> entities) {
        if (world == null) {
            return false;
        }
        HotChunkTracker tracker = plugin.getHotChunkTracker();
        if (tracker != null && tracker.isChunkHot(world, chunkX, chunkZ)) {
            return true;
        }
        if (entities == null) {
            return false;
        }
        int mobCount = 0;
        for (Entity entity : entities) {
            if (entity instanceof Mob) {
                mobCount++;
            }
        }
        return mobCount >= config.getHotChunkMobThreshold();
    }

    private boolean isEnabled() {
        return config.isHotChunksEnabled() && config.getHotChunkTickGroups() > 0;
    }

    private int getGroupCount() {
        return Math.max(1, config.getHotChunkTickGroups() + 1);
    }

    private int getGroupVersion() {
        return getGroupCount();
    }

    private static int computeGroup(UUID uuid, int groupCount) {
        int hash = uuid != null ? uuid.hashCode() : 0;
        return Math.floorMod(hash, groupCount);
    }
}
