package cat.nyaa.yasui;


import cat.nyaa.nyaacore.CommandReceiver;
import cat.nyaa.nyaacore.LanguageRepository;
import cat.nyaa.nyaacore.Message;
import cat.nyaa.nyaacore.Pair;
import cat.nyaa.nyaacore.utils.NmsUtils;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.config.Rule;
import cat.nyaa.yasui.other.ChunkCoordinate;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.other.TimingsUtils;
import cat.nyaa.yasui.region.RegionCommands;
import com.google.common.collect.EnumMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.*;

public class CommandHandler extends CommandReceiver {
    private final Yasui plugin;
    @SubCommand("region")
    public RegionCommands regionCommands;

    public CommandHandler(Yasui plugin, LanguageRepository i18n) {
        super(plugin, i18n);
        this.plugin = plugin;
    }
/*
    public static List<String> tabCompleteStringSet(CommandSender sender, Arguments args, Set<String> stringSet) {
        List<String> list = new ArrayList<>();
        if (args.remains() >= 1) {
            String name = args.nextString();
            for (String k : stringSet) {
                if (k.startsWith(name)) {
                    list.add(k);
                }
            }
        }
        return list;
    }
*/
    public String getHelpPrefix() {
        return "";
    }

    @SubCommand(value = "status", permission = "yasui.command.status")
    public void commandStatus(CommandSender sender, Arguments args) {
        for (World world : Bukkit.getWorlds()) {
            printStatus(sender, world);
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
        //Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        List<Entity> entities = world.getEntities();
        Multimap<ChunkCoordinate, EntityType> entitiesMap = entities.stream().filter(entity -> !(entity instanceof LivingEntity)).collect(Multimaps.toMultimap(ChunkCoordinate::of, Entity::getType, () -> Multimaps.newMultimap(new HashMap<>(), () -> EnumMultiset.create(EntityType.class))));
        Multimap<ChunkCoordinate, EntityType> livingEntitiesMap = entities.stream().filter(entity -> entity instanceof LivingEntity).collect(Multimaps.toMultimap(ChunkCoordinate::of, Entity::getType, () -> Multimaps.newMultimap(new HashMap<>(), () -> EnumMultiset.create(EntityType.class))));
        List<Block> blocks = NmsUtils.getTileEntities(world);
        Multimap<ChunkCoordinate, Material> tileEntitiesMap = blocks.stream().collect(Multimaps.toMultimap(ChunkCoordinate::of, Block::getType, () -> Multimaps.newMultimap(new HashMap<>(), () -> EnumMultiset.create(Material.class))));
        msg(sender, "user.profiler.header_entity", plugin.config.profiler_entity_chunk_count);
        entitiesMap.asMap().entrySet().stream().sorted(Comparator.comparing(e -> -e.getValue().size())).limit(plugin.config.profiler_entity_chunk_count).forEach(e -> {
            new Message("").append(e.getKey().getComponent()).append(I18n.format("user.chunk.total", e.getValue().size())).send(sender);
            EnumMultiset<EntityType> values = EnumMultiset.create(EntityType.class);
            values.addAll(e.getValue());
            List<Multiset.Entry<EntityType>> toSort = new ArrayList<>(values.entrySet());
            toSort.sort(Comparator.comparingInt(v -> -v.getCount()));
            for (Multiset.Entry<EntityType> v : toSort) {
                msg(sender, "user.profiler.entity", v.getElement().name(), v.getCount());
            }
        });
        msg(sender, "user.profiler.header_livingentity", plugin.config.profiler_entity_chunk_count);
        livingEntitiesMap.asMap().entrySet().stream().sorted(Comparator.comparing(e -> -e.getValue().size())).limit(plugin.config.profiler_entity_chunk_count).forEach(e -> {
            new Message("").append(e.getKey().getComponent()).append(I18n.format("user.chunk.total", e.getValue().size())).send(sender);
            EnumMultiset<EntityType> values = EnumMultiset.create(EntityType.class);
            values.addAll(e.getValue());
            List<Multiset.Entry<EntityType>> toSort = new ArrayList<>(values.entrySet());
            toSort.sort(Comparator.comparingInt(v -> -v.getCount()));
            for (Multiset.Entry<EntityType> v : toSort) {
                msg(sender, "user.profiler.livingentity", v.getElement().name(), v.getCount());
            }
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
        //});
    }

    @SubCommand(value = "operation", permission = "yasui.command.operation")
    public void commandOperation(CommandSender sender, Arguments args) {
        String s = args.nextString();
        String name = args.nextString();
        Set<World> worlds = new HashSet<>();
        while (true) {
            if ("all".equalsIgnoreCase(args.top())) {
                args.nextString();
                worlds.addAll(Bukkit.getWorlds());
                break;
            } else if (!worlds.isEmpty() && args.top() == null) {
                break;
            } else {
                worlds.add(getWorld(sender, args));
            }
        }
        Operation o = plugin.config.operations.get(name);
        if (o == null) {
            throw new BadCommandException("user.error.operation_not_exist", name);
        }
        if (o.modules.isEmpty()) {
            msg(sender, "user.error.empty_operation", name);
            return;
        }
        Rule rule = new Rule();
        rule.operations = new ArrayList<>(Collections.singleton(name));
        if (s.equalsIgnoreCase("engage")) {
            rule.engage_condition = "1";
            rule.release_condition = "0";
        } else if (s.equalsIgnoreCase("release")) {
            rule.engage_condition = "0";
            rule.release_condition = "1";
        } else {
            msg(sender, "manual.operation.usage");
            return;
        }
        for (World w : worlds) {
            plugin.tpsMonitor.runRule(rule, w, plugin.config.getDefaultRegion(w));
            msg(sender, "user.operation." + s.toLowerCase(), w.getName(), name);
        }
    }

    @SubCommand(value = "counter", permission = "yasui.command.counter")
    public void commandCounter(CommandSender sender, Arguments args) {
        String s = args.nextString();
        World world = Bukkit.getWorld(s);
        if (world != null) {
            TimingsUtils.printWorldTimings(world, sender);
        } else if (s.equalsIgnoreCase("entity")) {
            TimingsUtils.printEntityTimings(sender);
        } else if (s.equalsIgnoreCase("tileentity")) {
            TimingsUtils.printTileEntityTimings(sender);
        }
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

    private void printStatus(CommandSender sender, World world) {
        msg(sender, "user.status.world_name", world.getName());
        int livingEntities = 0;
        int tileEntities = 0;
        for (Chunk chunk : world.getLoadedChunks()) {
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof LivingEntity) {
                    livingEntities++;
                }
            }
            tileEntities += chunk.getTileEntities().length;
        }
        msg(sender, "user.status.world_info", world.getLoadedChunks().length, livingEntities, tileEntities);
        Map<ModuleType, Operation> limits = plugin.config.regionConfig.regions.get(world.getName()).getLimits();
        if (limits != null && !limits.isEmpty()) {
            for (ModuleType type : limits.keySet()) {
                Operation operation = limits.get(type);
                String langKey = "user.status." + type.name();
                if (type == ModuleType.entity_ai_suppressor) {
                    msg(sender, langKey, operation.entity_ai_suppresse_method.name());
                } else if (type == ModuleType.entity_culler) {
                    msg(sender, langKey, operation.entity_culler_per_chunk_limit, operation.entity_culler_per_region_limit);
                } else if (type == ModuleType.redstone_suppressor) {
                    msg(sender, langKey, operation.redstone_suppressor_per_chunk, operation.redstone_suppressor_piston_per_chunk);
                } else if (type == ModuleType.random_tick_speed) {
                    msg(sender, langKey, world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED));
                }
            }
        }
    }
/*
    public List<String> tabCompleteOperation(CommandSender sender, Arguments args) {
        List<String> list = new ArrayList<>();
        if (args.length() == 2) {
            String s = args.nextString();
            if ("engage".startsWith(s)) {
                list.add("engage");
            }
            if ("release".startsWith(s)) {
                list.add("release");
            }
        } else if (args.length() == 3) {
            args.next();
            list.addAll(tabCompleteStringSet(sender, args, plugin.config.operations.keySet()));
        } else if (args.length() >= 4) {
            args.next();
            args.next();
            while (args.remains() > 1) {
                args.next();
            }
            if ("all".startsWith(args.top())) {
                list.add("all");
            }
            list.addAll(tabCompleteWorld(sender, args));
        }
        return list;
    }

    public List<String> tabCompleteCounter(CommandSender sender, Arguments args) {
        List<String> list = new ArrayList<>();
        if (args.length() == 2) {
            String s = args.top();
            if ("tileentity".startsWith(s)) {
                list.add("tileentity");
            }
            if ("entity".startsWith(s)) {
                list.add("entity");
            }
            list.addAll(tabCompleteWorld(sender, args));
        }
        return list;
    }

    public List<String> tabCompleteWorld(CommandSender sender, Arguments args) {
        List<String> list = new ArrayList<>();
        if (args.remains() == 1) {
            String worldName = args.nextString();
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().startsWith(worldName)) {
                    list.add(world.getName());
                }
            }
        }
        return list;
    }*/
}
