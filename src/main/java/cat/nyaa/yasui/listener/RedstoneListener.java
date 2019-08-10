package cat.nyaa.yasui.listener;

import cat.nyaa.yasui.I18n;
import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.other.ChunkCoordinate;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.other.Utils;
import cat.nyaa.yasui.task.ChunkTask;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class RedstoneListener implements Listener {
    private final Yasui plugin;

    public RedstoneListener(Yasui pl) {
        plugin = pl;
        pl.getServer().getPluginManager().registerEvents(this, pl);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClickButton(PlayerInteractEvent event) {
        if (event.hasBlock() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block b = event.getClickedBlock();
            if (b != null && (b.getType() == Material.LEVER || Tag.BUTTONS.isTagged(b.getType()))) {
                ChunkCoordinate id = ChunkCoordinate.of(b);
                ChunkTask task = ChunkTask.getOrCreateTask(id);
                if (!task.allowRedstone) {
                    ChunkCoordinate source = task.sourceId;
                    event.getPlayer().sendMessage(I18n.format("user.redstone.msg", source.getWorld(), source.getX(), source.getZ(), task.disabledRadius, task.maxRedstoneEvents, task.maxPistonEvents));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void redstoneChange(BlockRedstoneEvent event) {
        ChunkTask task = ChunkTask.getOrCreateTask(event.getBlock().getChunk());
        if (event.getOldCurrent() < event.getNewCurrent() && task.region.get(ModuleType.redstone_suppressor) != null) {
            redstoneChange(event.getBlock(), false);
            if (!task.allowRedstone) {
                event.setNewCurrent(event.getOldCurrent());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        redstoneChange(event.getBlock(), true);
        if (!ChunkTask.getOrCreateTask(event.getBlock().getChunk()).allowRedstone) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        redstoneChange(event.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickupItem(InventoryPickupItemEvent event) {
        Inventory inv = event.getInventory();
        if (inv != null && inv.getLocation() != null) {
            if (!ChunkTask.getOrCreateTask(inv.getLocation().getChunk()).allowRedstone) {
                Utils.disableHopper(inv.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMoveItem(InventoryMoveItemEvent event) {
        Location from = event.getSource().getLocation();
        Location to = event.getDestination().getLocation();
        if (from != null && to != null) {
            if (!ChunkTask.getOrCreateTask(from.getChunk()).allowRedstone) {
                Utils.disableHopper(from);
            }
            if (!ChunkTask.getOrCreateTask(to.getChunk()).allowRedstone) {
                Utils.disableHopper(to);
            }
        }
    }

    private void redstoneChange(Block block, boolean piston) {
        ChunkCoordinate id = ChunkCoordinate.of(block);
        ChunkTask task = ChunkTask.getOrCreateTask(id);
        if (task.region.get(ModuleType.redstone_suppressor) != null) {
            if (!piston) {
                task.redstoneEvents++;
            } else {
                task.pistonEvents++;
            }
        }
    }

    public void disableRedstone(Chunk chunk, int radius, int redstone, int piston) {
        ChunkCoordinate source = ChunkCoordinate.of(chunk);
        List<ChunkCoordinate> list = Utils.getChunks(chunk, radius);
        for (ChunkCoordinate id : list) {
            ChunkTask task = ChunkTask.getOrCreateTask(id);
            task.allowRedstone = false;
            task.disabledRadius = radius;
            task.sourceId = source;
            task.maxRedstoneEvents = redstone;
            task.maxPistonEvents = piston;
        }
        Utils.broadcast(plugin.config.broadcast, I18n.format("user.redstone.msg", chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), radius, redstone, piston), null);
    }
}
