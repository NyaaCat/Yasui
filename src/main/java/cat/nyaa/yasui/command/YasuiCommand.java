package cat.nyaa.yasui.command;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.optimizer.EntitySpreadTicker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command handler for Yasui plugin
 *
 * Commands:
 * /yasui status - Show optimization status and statistics
 * /yasui reload - Reload configuration
 * /yasui info - Show plugin information
 */
public class YasuiCommand implements CommandExecutor, TabCompleter {
    private final Yasui plugin;

    public YasuiCommand(Yasui plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yasui.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> handleStatus(sender);
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage("§6=== Yasui Optimization Status ===");

        // Hopper optimizer
        if (plugin.getHopperOptimizer() != null) {
            int activeHoppers = plugin.getHopperOptimizer().getActiveHopperCount();
            int cacheSize = plugin.getHopperOptimizer().getCacheSize();
            sender.sendMessage("§aHopper Optimizer: §fEnabled");
            sender.sendMessage("  §7Active Hoppers: §f" + activeHoppers);
            sender.sendMessage("  §7Cache Size: §f" + cacheSize);
        } else {
            sender.sendMessage("§cHopper Optimizer: §fDisabled");
        }

        // Villager POI cache
        if (plugin.getVillagerCache() != null) {
            int cacheSize = plugin.getVillagerCache().getCacheSize();
            sender.sendMessage("§aVillager POI Cache: §fEnabled");
            sender.sendMessage("  §7Cached POIs: §f" + cacheSize);
        } else {
            sender.sendMessage("§cVillager POI Cache: §fDisabled");
        }

        // Entity spread ticker
        if (plugin.getEntitySpread() != null) {
            EntitySpreadTicker.Stats stats = plugin.getEntitySpread().getStats();
            sender.sendMessage("§aEntity Spread Ticker: §fEnabled");
            sender.sendMessage("  §7Near Entities: §f" + stats.nearEntities() + " §7(full vanilla)");
            sender.sendMessage("  §7Distant Entities: §f" + stats.distantEntities() + " §7(spread ticking)");
            sender.sendMessage("  §7Buckets Used: §f" + stats.bucketsUsed());
        } else {
            sender.sendMessage("§cEntity Spread Ticker: §fDisabled");
        }

        // Chunk cache
        if (plugin.getChunkCache() != null) {
            int cacheSize = plugin.getChunkCache().getCacheSize();
            int totalPositions = plugin.getChunkCache().getTotalCachedPositions();
            sender.sendMessage("§aChunk Tick Cache: §fEnabled");
            sender.sendMessage("  §7Cached Chunks: §f" + cacheSize);
            sender.sendMessage("  §7Cached Positions: §f" + totalPositions);
        } else {
            sender.sendMessage("§cChunk Tick Cache: §fDisabled");
        }
    }

    private void handleReload(CommandSender sender) {
        sender.sendMessage("§6Reloading Yasui configuration...");

        // Reload config
        plugin.reloadConfig();

        sender.sendMessage("§aConfiguration reloaded successfully!");
        sender.sendMessage("§eNote: Some optimizers may require a server restart to fully apply changes.");
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage("§6=== Yasui - Server Optimization Plugin ===");
        sender.sendMessage("§fVersion: §a" + plugin.getPluginMeta().getVersion());
        sender.sendMessage("§fTarget: §aPaper 1.21.8 + Java 21");
        sender.sendMessage("");
        sender.sendMessage("§fOptimizations:");
        sender.sendMessage("  §7- Hopper caching with async pre-computation");
        sender.sendMessage("  §7- Villager POI lookup caching");
        sender.sendMessage("  §7- Distance-based entity spread ticking");
        sender.sendMessage("  §7- Chunk random tick position caching");
        sender.sendMessage("");
        sender.sendMessage("§fGoal: §7Reduce server tick time while preserving vanilla behavior");
        sender.sendMessage("§fCommands: §e/yasui status §7| §e/yasui reload §7| §e/yasui info");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== Yasui Commands ===");
        sender.sendMessage("§e/yasui status §7- Show optimization status and statistics");
        sender.sendMessage("§e/yasui reload §7- Reload configuration");
        sender.sendMessage("§e/yasui info §7- Show plugin information");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yasui.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("status", "reload", "info");
        }

        return new ArrayList<>();
    }
}
