package cat.nyaa.yasui;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class RedstoneListener extends BukkitRunnable implements Listener {
    private final Yasui plugin;
    public Integer redstoneLimitMaxChange = -1;
    public Integer redstoneLimitDisableRadius = 0;
    public Map<ChunkCoordinate, LoadingCache<Integer, Integer>> history;
    public Map<ChunkCoordinate, Integer> disabledChunks;
    public Map<ChunkCoordinate, RedstoneMonitor> redstoneMonitorTasks = new HashMap<>();
    public Set<String> worlds = new HashSet<>();
    private int time = 0;

    public RedstoneListener(Yasui pl) {
        plugin = pl;
        history = new HashMap<>();
        disabledChunks = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClickButton(PlayerInteractEvent event) {
        if (event.hasBlock() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block b = event.getClickedBlock();
            if (b != null && disabledChunks.containsKey(ChunkCoordinate.of(b)) && (b.getType() == Material.LEVER || Tag.BUTTONS.isTagged(b.getType()))) {
                event.getPlayer().sendMessage(I18n.format("user.redstone.disabled_here", disabledChunks.get(ChunkCoordinate.of(b)), redstoneLimitMaxChange));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void redstoneChange(BlockRedstoneEvent event) {
        if (event.getOldCurrent() < event.getNewCurrent()) {
            onPistonMove(event.getBlock(), 1);
            if (disabledChunks.containsKey(ChunkCoordinate.of(event.getBlock()))) {
                event.setNewCurrent(event.getOldCurrent());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        onPistonMove(event.getBlock(), event.getBlocks().size());
        if (disabledChunks.containsKey(ChunkCoordinate.of(event.getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        onPistonMove(event.getBlock(), event.getBlocks().size());
    }

    private void onPistonMove(Block block, int blocks) {
        if (worlds.contains(block.getWorld().getName())) {
            ChunkCoordinate id = ChunkCoordinate.of(block);
            if (!redstoneMonitorTasks.containsKey(id)) {
                addTask(id);
            }
            if (!history.containsKey(id)) {
                history.put(id, this.newCache());
            }
            LoadingCache<Integer, Integer> cache = history.get(id);
            cache.put(time, cache.getUnchecked(time) + blocks);
        }
    }

    private void addTask(ChunkCoordinate id) {
        if (!redstoneMonitorTasks.containsKey(id)) {
            RedstoneMonitor task = new RedstoneMonitor(id);
            task.runTaskTimer(plugin, 20, plugin.config.redstone_limit_check_interval_tick);
            redstoneMonitorTasks.put(id, task);
        }
    }

    private LoadingCache<Integer, Integer> newCache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(plugin.config.redstone_limit_time_range + 5, TimeUnit.SECONDS)
                .build(
                        new CacheLoader<Integer, Integer>() {
                            public Integer load(Integer key) {
                                return 0;
                            }
                        });
    }

    @Override
    public void run() {
        time = (int) (System.currentTimeMillis() / 1000);
    }

    public void disableRedstone(Chunk chunk, int radius, int redstoneActivity) {
        List<ChunkCoordinate> list = Utils.getChunks(chunk, radius);
        for (ChunkCoordinate id : list) {
            disabledChunks.put(id, redstoneActivity);
            addTask(id);
        }
        if (plugin.config.redstone_limit_log) {
            plugin.getLogger().info(I18n.format("log.redstone", chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), radius));
        }
        Collection<Entity> players = chunk.getWorld().getNearbyEntities(chunk.getBlock(8, 128, 8).getLocation(), (radius + 4) * 16, 128, (radius + 4) * 16, entity -> entity instanceof Player);
        for (Entity p : players) {
            if (!p.isDead()) {
                p.sendMessage(I18n.format("user.redstone.msg", chunk.getX(), chunk.getZ(), radius, redstoneActivity));
            }
        }
    }

    public int getHistory(ChunkCoordinate id, int seconds) {
        int total = 0;
        LoadingCache<Integer, Integer> ch = history.get(id);
        if (ch != null) {
            for (int i = 0; i <= seconds; i++) {
                total += ch.getUnchecked(time - i);
            }
        }
        return total;
    }

    class RedstoneMonitor extends BukkitRunnable {
        public ChunkCoordinate id;

        public RedstoneMonitor(ChunkCoordinate id) {
            this.id = id;
        }

        @Override
        public void run() {
            if (!disabledChunks.containsKey(id)) {
                if (redstoneLimitMaxChange >= 0) {
                    int total = getHistory(id, plugin.config.redstone_limit_time_range);
                    if (total > redstoneLimitMaxChange) {
                        World w = Bukkit.getWorld(id.getWorld());
                        if (w != null) {
                            disableRedstone(w.getChunkAt(id.getX(), id.getZ()), redstoneLimitDisableRadius, total);
                            return;
                        }
                    }
                }
                if (redstoneLimitMaxChange < 0 || (history.get(id).getUnchecked(time) == 0 && getHistory(id, plugin.config.redstone_limit_time_range) == 0) || !worlds.contains(id.getWorld())) {
                    cancel();
                    history.remove(id);
                    redstoneMonitorTasks.remove(id);
                }
            } else {
                if (disabledChunks.get(id) <= redstoneLimitMaxChange) {
                    disabledChunks.remove(id);
                }
            }
        }
    }
}
