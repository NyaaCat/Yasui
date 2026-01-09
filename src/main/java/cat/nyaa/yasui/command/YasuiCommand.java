package cat.nyaa.yasui.command;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.optimizer.EntitySpreadTicker;
import cat.nyaa.yasui.optimizer.HopperOptimizer;
import cat.nyaa.yasui.optimizer.PathfindingCacheTracker;
import cat.nyaa.yasui.optimizer.PoiSearchCacheTracker;
import cat.nyaa.yasui.optimizer.VillagerPOICache;
import cat.nyaa.yasui.nms.HopperNmsHook;
import cat.nyaa.yasui.nms.PathfindingNmsHook;
import cat.nyaa.yasui.nms.PoiSearchNmsHook;
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
            HopperOptimizer.RollingStats rollingStats = plugin.getHopperOptimizer().getRollingStats();
            int activeHoppers = plugin.getHopperOptimizer().getActiveHopperCount();
            sender.sendMessage("§aHopper Optimizer: §fEnabled");
            sender.sendMessage("  §7Active Hoppers: §f" + activeHoppers);
            sender.sendMessage("  §7Full Cache Hits/Misses (1h): §f" + rollingStats.fullCacheHits() + "§7/§f" + rollingStats.fullCacheMisses());
            sender.sendMessage("  §7Full Cache Stores/Invalidations (1h): §f" + rollingStats.fullCacheStores() + "§7/§f" + rollingStats.fullCacheInvalidations());
            sender.sendMessage("  §7NMS Full-Check Hook: §f" + (HopperNmsHook.isHookActive() ? "Active" : "Inactive"));
        } else {
            sender.sendMessage("§cHopper Optimizer: §fDisabled");
        }

        // Villager POI cache
        if (plugin.getVillagerCache() != null) {
            int cacheSize = plugin.getVillagerCache().getCacheSize();
            VillagerPOICache.RollingRestoreStats rollingStats = plugin.getVillagerCache().getRollingRestoreStats();
            sender.sendMessage("§aVillager POI Cache: §fEnabled");
            sender.sendMessage("  §7Cached POIs: §f" + cacheSize);
            sender.sendMessage("  §7Job Site Restores (1h): §f" + rollingStats.applied() + "§7/§f" + rollingStats.attempts());
            sender.sendMessage("  §7Restore Candidates (1h): §f" + rollingStats.candidates());
            if (plugin.getPoiSearchCacheTracker() != null) {
                PoiSearchCacheTracker.RollingStats searchStats = plugin.getPoiSearchCacheTracker().getRollingStats();
                sender.sendMessage("  §7POI Search Cache: §f" + plugin.getPoiSearchCacheTracker().getCacheSize());
                sender.sendMessage("  §7POI Search Hits/Misses (1h): §f" + searchStats.hits() + "§7/§f" + searchStats.misses());
                sender.sendMessage("  §7AcquirePoi Hook: §f" + (PoiSearchNmsHook.isHookActive() ? "Active" : "Inactive"));
            } else {
                sender.sendMessage("  §7POI Search Cache: §fDisabled");
            }
        } else {
            sender.sendMessage("§cVillager POI Cache: §fDisabled");
        }

        // Entity distance cache
        if (plugin.getEntitySpread() != null) {
            EntitySpreadTicker.Stats stats = plugin.getEntitySpread().getStats();
            sender.sendMessage("§aEntity Distance Cache: §fEnabled");
            sender.sendMessage("  §7Near Entities: §f" + stats.nearEntities() + " §7(full vanilla)");
            sender.sendMessage("  §7Distant Entities: §f" + stats.distantEntities() + " §7(cached distance)");
            sender.sendMessage("  §7Tracked Entities: §f" + stats.trackedEntities());
        } else {
            sender.sendMessage("§cEntity Distance Cache: §fDisabled");
        }

        if (plugin.getPathfindingCacheTracker() != null) {
            PathfindingCacheTracker.RollingStats stats = plugin.getPathfindingCacheTracker().getRollingStats();
            sender.sendMessage("§aPathfinding Cache: §fEnabled");
            sender.sendMessage("  §7Cache Hits/Misses (1h): §f" + stats.hits() + "§7/§f" + stats.misses());
            sender.sendMessage("  §7Cache Stores (1h): §f" + stats.stores());
            sender.sendMessage("  §7NMS Path Cache Hook: §f" + (PathfindingNmsHook.isHookActive() ? "Active" : "Inactive"));
        } else {
            sender.sendMessage("§cPathfinding Cache: §fDisabled");
        }
    }

    private void handleReload(CommandSender sender) {
        sender.sendMessage("§6Reloading Yasui configuration...");

        // Reload config and restart optimizers
        plugin.reloadConfiguration();

        sender.sendMessage("§aConfiguration reloaded and optimizers restarted!");
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage("§6=== Yasui - Server Optimization Plugin ===");
        sender.sendMessage("§fVersion: §a" + plugin.getPluginMeta().getVersion());
        sender.sendMessage("§fTarget: §aPaper 1.21.8 + Java 21");
        sender.sendMessage("");
        sender.sendMessage("§fOptimizations:");
        sender.sendMessage("  §7- Hopper full-check caching (NMS hook)");
        sender.sendMessage("  §7- Villager job-site restore + AcquirePoi search caching");
        sender.sendMessage("  §7- Distance cache for quick near/distant checks");
        sender.sendMessage("  §7- Pathfinding result cache (short TTL)");
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
