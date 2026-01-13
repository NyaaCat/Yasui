package cat.nyaa.yasui.optimizer;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.YasuiConfig;
import cat.nyaa.yasui.nms.ChunkEpochNmsHook;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Collection;
import java.util.List;

/**
 * Tracks chunk epoch bumps for block/POI-affecting changes.
 */
public class ChunkEpochTracker implements Listener {
    private final Yasui plugin;
    private final YasuiConfig config;

    public ChunkEpochTracker(Yasui plugin, YasuiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void shutdown() {
        // No scheduled tasks to cancel.
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        bumpBlock(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        bumpBlock(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent event) {
        bumpBlock(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockFade(BlockFadeEvent event) {
        bumpBlock(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockForm(BlockFormEvent event) {
        bumpBlock(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockGrow(BlockGrowEvent event) {
        bumpBlock(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockSpread(BlockSpreadEvent event) {
        bumpBlock(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLeavesDecay(LeavesDecayEvent event) {
        bumpBlock(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockFromTo(BlockFromToEvent event) {
        bumpBlock(event.getBlock());
        bumpBlock(event.getToBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        bumpBlock(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        bumpBlockStates(event.getBlocks());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onStructureGrow(StructureGrowEvent event) {
        bumpBlockStates(event.getBlocks());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        bumpBlocks(event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        bumpBlocks(event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        bumpBlocks(event.getBlocks());
        bumpBlock(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        bumpBlocks(event.getBlocks());
        bumpBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!config.isChunkEpochEnabled()) {
            return;
        }
        World world = event.getWorld();
        bumpChunk(world, event.getChunk().getX(), event.getChunk().getZ());
    }

    private void bumpBlock(Block block) {
        if (!config.isChunkEpochEnabled() || block == null) {
            return;
        }
        bumpChunk(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    private void bumpBlocks(Collection<Block> blocks) {
        if (!config.isChunkEpochEnabled() || blocks == null || blocks.isEmpty()) {
            return;
        }
        World world = null;
        LongSet keys = new LongOpenHashSet();
        for (Block block : blocks) {
            if (block == null) {
                continue;
            }
            if (world == null) {
                world = block.getWorld();
            }
            keys.add(packChunkKey(block.getX() >> 4, block.getZ() >> 4));
        }
        bumpChunks(world, keys);
    }

    private void bumpBlockStates(List<BlockState> blocks) {
        if (!config.isChunkEpochEnabled() || blocks == null || blocks.isEmpty()) {
            return;
        }
        World world = null;
        LongSet keys = new LongOpenHashSet();
        for (BlockState state : blocks) {
            if (state == null) {
                continue;
            }
            if (world == null) {
                world = state.getWorld();
            }
            keys.add(packChunkKey(state.getX() >> 4, state.getZ() >> 4));
        }
        bumpChunks(world, keys);
    }

    private void bumpChunk(World world, int chunkX, int chunkZ) {
        if (!config.isChunkEpochEnabled() || world == null) {
            return;
        }
        long key = packChunkKey(chunkX, chunkZ);
        ServerLevel level = ((CraftWorld) world).getHandle();
        PoiManager poiManager = level.getPoiManager();
        ChunkEpochNmsHook.bumpEpoch(level, key);
        ChunkEpochNmsHook.bumpEpoch(poiManager, key);
    }

    private void bumpChunks(World world, LongSet keys) {
        if (!config.isChunkEpochEnabled() || world == null || keys == null || keys.isEmpty()) {
            return;
        }
        long[] values = new long[keys.size()];
        int i = 0;
        LongIterator iterator = keys.iterator();
        while (iterator.hasNext()) {
            values[i++] = iterator.nextLong();
        }
        ServerLevel level = ((CraftWorld) world).getHandle();
        PoiManager poiManager = level.getPoiManager();
        ChunkEpochNmsHook.bumpEpochs(level, values);
        ChunkEpochNmsHook.bumpEpochs(poiManager, values);
    }

    private static long packChunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }
}
