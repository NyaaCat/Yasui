package cat.nyaa.yasui;


import cat.nyaa.nyaacore.CommandReceiver;
import cat.nyaa.nyaacore.LanguageRepository;
import cat.nyaa.nyaacore.Message;
import cat.nyaa.nyaacore.Pair;
import cat.nyaa.nyaacore.utils.NmsUtils;
import com.google.common.collect.EnumMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.*;

public class CommandHandler extends CommandReceiver {
    private final Yasui plugin;

    public CommandHandler(Yasui plugin, LanguageRepository i18n) {
        super(plugin, i18n);
        this.plugin = plugin;
    }

    public String getHelpPrefix() {
        return "";
    }

    @SubCommand(value = "status", permission = "yasui.admin")
    public void commandStatus(CommandSender sender, Arguments args) {
        msg(sender, "user.status.line_0");
        for (World world : plugin.getServer().getWorlds()) {
            msg(sender, "user.status.line_1", world.getName(), world.getLivingEntities().size(),
                    plugin.disableAIWorlds.contains(world.getName()) ? "YES" : "NO", world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED),
                    plugin.entityLimitWorlds.contains(world.getName()) ? "YES" : "NO");
        }
    }

    @SubCommand(value = "debug", permission = "yasui.admin")
    public void commandDebug(CommandSender sender, Arguments args) {
        if (args.length() >= 4) {
            World world = Bukkit.getWorld(args.nextString());
            if (args.nextBoolean()) {
                plugin.disableAIWorlds.add(world.getName());
            } else {
                plugin.disableAIWorlds.remove(world.getName());
            }
            if (args.nextBoolean()) {
                plugin.entityLimitWorlds.add(world.getName());
            } else {
                plugin.entityLimitWorlds.remove(world.getName());
            }
            Utils.checkWorld(world);
        }
    }

    @SubCommand(value = "reload", permission = "yasui.admin")
    public void commandReload(CommandSender sender, Arguments args) {
        plugin.reload();
    }

    @SubCommand(value = "chunkevents", permission = "yasui.profiler")
    public void commandChunkEvents(CommandSender sender, Arguments args) {
        World world = getWorld(sender, args);
        if (plugin.profilerStatsMonitor == null) {
            msg(sender, "user.profiler.not_enabled");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Deque<Pair<Long, Map<ChunkCoordinate, ProfilerStatsMonitor.ChunkStat>>> redstoneStats = plugin.profilerStatsMonitor.getRedstoneStats(world);
            Iterator<Pair<Long, Map<ChunkCoordinate, ProfilerStatsMonitor.ChunkStat>>> descendingIterator = redstoneStats.descendingIterator();
            List<Pair<Long, Map<ChunkCoordinate, ProfilerStatsMonitor.ChunkStat>>> snapshot = new ArrayList<>(600);
            while (descendingIterator.hasNext()) {
                snapshot.add(descendingIterator.next());
                if (snapshot.size() == 600) break;
            }
            Map<ChunkCoordinate, ProfilerStatsMonitor.ChunkStat> stat = snapshot.stream().map(Pair::getValue).reduce(new HashMap<>(), (total, tick) -> {
                tick.forEach((chunk, value) -> {
                    if (value.getPhysics() != 0 || value.getRedstone() != 0) {
                        total.computeIfAbsent(chunk, (ignored) -> new ProfilerStatsMonitor.ChunkStat()).add(value);
                    }
                });
                return total;
            });
            stat.entrySet().stream().sorted(Comparator.comparing(e -> -e.getValue().getRedstone())).limit(plugin.config.profiler_event_chunk_count).forEach(
                    e -> new Message("")
                        .append(e.getKey().getComponent())
                        .append(I18n.format("user.chunk.total", e.getValue().getPhysics() + e.getValue().getRedstone()))
                        .append(I18n.format("user.chunk.events", e.getValue().getRedstone(), e.getValue().getPhysics()))
                        .send(sender)
            );
        });
    }

    @SubCommand(value = "chunkentities", permission = "yasui.profiler")
    public void commandChunkEntities(CommandSender sender, Arguments args) {
        World world = getWorld(sender, args);
        List<Entity> entities = world.getEntities();
        Multimap<ChunkCoordinate, EntityType> entitiesMap = entities.stream().collect(Multimaps.toMultimap(ChunkCoordinate::of, Entity::getType, () -> Multimaps.newMultimap(new HashMap<>(), () -> EnumMultiset.create(EntityType.class))));

        List<Block> blocks = NmsUtils.getTileEntities(world);
        Multimap<ChunkCoordinate, Material> tileEntitiesMap = blocks.stream().collect(Multimaps.toMultimap(ChunkCoordinate::of, Block::getType, () -> Multimaps.newMultimap(new HashMap<>(), () -> EnumMultiset.create(Material.class))));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            msg(sender, "user.profiler.header_entity", plugin.config.profiler_entity_chunk_count);
            entitiesMap.asMap().entrySet().stream().sorted(Comparator.comparing(e -> -e.getValue().size())).limit(plugin.config.profiler_entity_chunk_count).forEach(e -> {
                new Message("").append(e.getKey().getComponent()).append(I18n.format("user.chunk.total", e.getValue().size())).send(sender);
                EnumMultiset<EntityType> values = EnumMultiset.create(EntityType.class);
                values.addAll(e.getValue());
                values.entrySet().stream().sorted(Comparator.comparingInt(v -> -v.getCount())).forEach(
                        v -> msg(sender, "user.profiler.entity", v.getElement().name(), v.getCount())
                );
            });
            msg(sender, "user.profiler.header_tileentity", plugin.config.profiler_entity_chunk_count);
            tileEntitiesMap.asMap().entrySet().stream().sorted(Comparator.comparing(e -> -e.getValue().size())).limit(plugin.config.profiler_entity_chunk_count).forEach(e -> {
                new Message("").append(e.getKey().getComponent()).append(I18n.format("user.chunk.total", e.getValue().size())).send(sender);
                EnumMultiset<Material> values = EnumMultiset.create(Material.class);
                values.addAll(e.getValue());
                values.entrySet().stream().sorted(Comparator.comparingInt(v -> -v.getCount())).forEach(
                        v -> msg(sender, "user.profiler.tileentity", v.getElement().name(), v.getCount())
                );
            });
        });
    }

    private World getWorld(CommandSender sender, Arguments args) {
        World world;
        if (args.top() == null) {
            world = asPlayer(sender).getWorld();
        } else {
            String worldName = args.nextString();
            world = Bukkit.getWorld(worldName);
            if (world == null) {
                throw new BadCommandException("user.error.bad_world", worldName);
            }
        }
        return world;
    }
}
