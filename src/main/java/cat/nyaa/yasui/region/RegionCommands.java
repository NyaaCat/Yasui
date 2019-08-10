package cat.nyaa.yasui.region;

import cat.nyaa.nyaacore.LanguageRepository;
import cat.nyaa.nyaacore.cmdreceiver.Arguments;
import cat.nyaa.nyaacore.cmdreceiver.BadCommandException;
import cat.nyaa.nyaacore.cmdreceiver.CommandReceiver;
import cat.nyaa.nyaacore.cmdreceiver.SubCommand;
import cat.nyaa.yasui.CommandHandler;
import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.config.Rule;
import cat.nyaa.yasui.other.ChunkCoordinate;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.task.ChunkTask;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegionCommands extends CommandReceiver {
    private Yasui plugin;

    public RegionCommands(Object plugin, LanguageRepository i18n) {
        super((Yasui) plugin, i18n);
        this.plugin = (Yasui) plugin;
    }

    @Override
    public String getHelpPrefix() {
        return "region";
    }

    @SubCommand(value = "create", permission = "yasui.admin")
    public void commandCreate(CommandSender sender, Arguments args) {
        Player player = asPlayer(sender);
        World world = player.getWorld();
        String name = args.nextString();
        if (plugin.config.regionConfig.regions.get(name) != null) {
            throw new BadCommandException("user.region.exist", name);
        }
        Plugin we = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
        Chunk chunk1 = null;
        Chunk chunk2 = null;
        if (args.remains() == 5) {
            String block = args.nextString();
            if (block.equalsIgnoreCase("block")) {
                chunk1 = world.getBlockAt(args.nextInt(), 64, args.nextInt()).getChunk();
                chunk2 = world.getBlockAt(args.nextInt(), 64, args.nextInt()).getChunk();
            } else if (block.equalsIgnoreCase("chunk")) {
                chunk1 = world.getChunkAt(args.nextInt(), args.nextInt());
                chunk2 = world.getChunkAt(args.nextInt(), args.nextInt());
            }
        } else if (we != null) {
            com.sk89q.worldedit.regions.Region selection = null;
            try {
                selection = ((WorldEditPlugin) we).getSession(player).getSelection(BukkitAdapter.adapt(player.getWorld()));
            } catch (Exception e) {
                //e.printStackTrace();
            }
            if (selection != null) {
                chunk1 = BukkitAdapter.adapt(player.getWorld(), selection.getMinimumPoint()).getChunk();
                chunk2 = BukkitAdapter.adapt(player.getWorld(), selection.getMaximumPoint()).getChunk();
            }
        }
        if (chunk1 == null) {
            throw new BadCommandException("manual.region.create.usage");
        }
        Region r = new Region(chunk1, chunk2);
        r.name = name;
        plugin.config.regionConfig.regions.put(name, r);
        plugin.config.save();
        update(r);
        printRegionInfo(sender, r);
    }

    @SubCommand(value = "enable", permission = "yasui.admin", tabCompleter = "tabCompleteRegionName")
    public void commandEnable(CommandSender sender, Arguments args) {
        Region region = getRegion(args.nextString());
        region.enabled = true;
        update(region);
        printRegionInfo(sender, region);
    }

    @SubCommand(value = "disable", permission = "yasui.admin", tabCompleter = "tabCompleteRegionName")
    public void commandDisable(CommandSender sender, Arguments args) {
        Region region = getRegion(args.nextString());
        region.enabled = false;
        update(region);
        printRegionInfo(sender, region);
    }

    @SubCommand(value = "rule", permission = "yasui.admin", tabCompleter = "tabCompleteRule")
    public void commandRule(CommandSender sender, Arguments args) {
        Region region = getRegion(args.nextString());
        String type = args.nextString();
        String ruleName = args.nextString();
        Rule rule = plugin.config.rules.get(ruleName);
        if (rule != null && type.equalsIgnoreCase("bypass") && !region.bypass.contains(ruleName)) {
            region.bypass.add(ruleName);
            region.enforce.remove(ruleName);
        } else if (rule != null && type.equalsIgnoreCase("enforce") && !region.enforce.contains(ruleName)) {
            region.bypass.remove(ruleName);
            region.enforce.add(ruleName);
        } else if (type.equalsIgnoreCase("remove")) {
            region.bypass.remove(ruleName);
            region.enforce.remove(ruleName);
        } else {
            throw new BadCommandException();
        }
        printRegionInfo(sender, region);
    }

    @SubCommand(value = "info", permission = "yasui.admin", tabCompleter = "tabCompleteRegionName")
    public void commandInfo(CommandSender sender, Arguments args) {
        printRegionInfo(sender, args.remains() > 0 ? getRegion(args.nextString()) : plugin.config.getRegion(ChunkCoordinate.of(asPlayer(sender))));
    }

    @SubCommand(value = "list", permission = "yasui.admin")
    public void commandList(CommandSender sender, Arguments args) {
        for (Region v : plugin.config.regionConfig.regions.values()) {
            if (!v.defaultRegion) {
                printRegionInfo(sender, v);
            }
        }
    }

    public void update(Region region) {
        for (ModuleType type : ModuleType.values()) {
            String ruleName = region.getSource(type);
            if (ruleName != null && region.bypass.contains(ruleName)) {
                region.remove(type);
            }
        }
        for (ChunkCoordinate id : region.getChunks()) {
            ChunkTask task = ChunkTask.taskMap.get(id);
            if (task != null) {
                task.region = plugin.config.getRegion(id);
            }
        }
    }

    private Region getRegion(String name) {
        Region region = plugin.config.regionConfig.regions.get(name);
        if (region != null && !region.defaultRegion) {
            return region;
        }
        throw new BadCommandException("user.error.region_not_exist", name);
    }

    public List<String> tabCompleteRuleName(CommandSender sender, Arguments args) {
        List<String> list = new ArrayList<>();
        if (args.remains() >= 1) {
            list.addAll(CommandHandler.tabCompleteStringSet(sender, args, plugin.config.rules.keySet()));
        }
        return list;
    }

    public List<String> tabCompleteRegionName(CommandSender sender, Arguments args) {
        List<String> list = new ArrayList<>();
        if (args.remains() >= 1) {
            String name = args.nextString();
            for (Region r : plugin.config.regionConfig.regions.values()) {
                if (!r.defaultRegion && r.name.startsWith(name)) {
                    list.add(r.name);
                }
            }
        }
        return list;
    }

    public List<String> tabCompleteRule(CommandSender sender, Arguments args) {
        List<String> list = new ArrayList<>();
        if (args.length() == 3) {
            list.addAll(tabCompleteRegionName(sender, args));
        } else if (args.length() == 4) {
            args.nextString();
            String s = args.nextString();
            if ("bypass".startsWith(s)) {
                list.add("bypass");
            }
            if ("enforce".startsWith(s)) {
                list.add("enforce");
            }
            if ("remove".startsWith(s)) {
                list.add("remove");
            }
        } else if (args.length() == 5) {
            Region region = plugin.config.regionConfig.regions.get(args.nextString());
            if (region != null && !region.defaultRegion) {
                if ("remove".equalsIgnoreCase(args.nextString())) {
                    Set<String> tmp = new HashSet<>();
                    tmp.addAll(region.bypass);
                    tmp.addAll(region.enforce);
                    list.addAll(CommandHandler.tabCompleteStringSet(sender, args, tmp));
                } else {
                    list.addAll(tabCompleteRuleName(sender, args));
                }
            }
        }
        return list;
    }

    public void printRegionInfo(CommandSender sender, Region region) {
        msg(sender, "user.region.info.name", region.name, region.enabled);
        msg(sender, "user.region.info.location", region.world, region.minChunkX, region.minChunkZ, region.maxChunkX, region.maxChunkZ);
        if (!region.bypass.isEmpty()) {
            String s = "";
            for (String rule : region.bypass) {
                if (s.length() > 0) {
                    s += ", ";
                }
                s += rule;
            }
            msg(sender, "user.region.info.bypass", s);
        }
        if (!region.enforce.isEmpty()) {
            String s = "";
            for (String rule : region.enforce) {
                if (s.length() > 0) {
                    s += ", ";
                }
                s += rule;
            }
            msg(sender, "user.region.info.enforce", s);
        }
    }
}
