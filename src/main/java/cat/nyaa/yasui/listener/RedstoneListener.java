package cat.nyaa.yasui.listener;

import cat.nyaa.yasui.I18n;
import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.other.ChunkCoordinate;
import cat.nyaa.yasui.other.Utils;
import cat.nyaa.yasui.task.ChunkTask;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
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
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

import java.util.*;

public class RedstoneListener implements Listener {
    private final Yasui plugin;
    public Map<ChunkCoordinate, Integer> disabledChunks = new HashMap<>();
    public Map<ChunkCoordinate, Integer> redstoneEvents = new HashMap<>();
    public Map<ChunkCoordinate, Integer> pistonEvents = new HashMap<>();
    public Set<String> worlds = new HashSet<>();

    public RedstoneListener(Yasui pl) {
        plugin = pl;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClickButton(PlayerInteractEvent event) {
        if (event.hasBlock() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block b = event.getClickedBlock();
            if (b != null && disabledChunks.containsKey(ChunkCoordinate.of(b)) && (b.getType() == Material.LEVER || Tag.BUTTONS.isTagged(b.getType()))) {
                //event.getPlayer().sendMessage(I18n.format("user.redstone.disabled_here", disabledChunks.get(ChunkCoordinate.of(b)), redstoneLimitMaxChange));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void redstoneChange(BlockRedstoneEvent event) {
        if (event.getOldCurrent() < event.getNewCurrent() && worlds.contains(event.getBlock().getWorld().getName())) {
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickupItem(InventoryPickupItemEvent event) {
        Inventory inv = event.getInventory();
        if (inv != null && inv.getLocation() != null) {
            if (disabledChunks.containsKey(ChunkCoordinate.of(inv.getLocation().getChunk()))) {
                Utils.disableHopper(inv.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMoveItem(InventoryMoveItemEvent event) {
        Location from = event.getSource().getLocation();
        Location to = event.getDestination().getLocation();
        if (from != null && to != null) {
            if (disabledChunks.containsKey(ChunkCoordinate.of(from.getChunk()))) {
                Utils.disableHopper(from);
            }
            if (disabledChunks.containsKey(ChunkCoordinate.of(to.getChunk()))) {
                Utils.disableHopper(to);
            }
        }
    }

    private void onPistonMove(Block block, int blocks) {
        if (worlds.contains(block.getWorld().getName())) {
            ChunkCoordinate id = ChunkCoordinate.of(block);
            ChunkTask task = ChunkTask.getOrCreateTask(id);
            task.pistonEvents += blocks;
        }
    }

    public void disableRedstone(Chunk chunk, int radius, int redstone, int piston) {
        List<ChunkCoordinate> list = Utils.getChunks(chunk, radius);
        for (ChunkCoordinate id : list) {
            disabledChunks.put(id, redstone);
        }
        Collection<Entity> players = chunk.getWorld().getNearbyEntities(chunk.getBlock(8, 128, 8).getLocation(), (radius + 4) * 16, 128, (radius + 4) * 16, entity -> entity instanceof Player);
        for (Entity p : players) {
            if (!p.isDead()) {
                p.sendMessage(I18n.format("user.redstone.msg", chunk.getX(), chunk.getZ(), radius, redstone));
            }
        }
    }
}
