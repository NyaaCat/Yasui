package cat.nyaa.yasui.task;

import cat.nyaa.yasui.I18n;
import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.config.Rule;
import cat.nyaa.yasui.other.BroadcastType;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.other.Utils;
import cat.nyaa.yasui.region.Region;
import com.google.common.base.Strings;
import com.udojava.evalex.AbstractFunction;
import com.udojava.evalex.Expression;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.util.List;

public class TPSMonitor extends BukkitRunnable {
    private final Yasui plugin;
    private BigDecimal tps_1m = new BigDecimal(20);
    private BigDecimal tps_5m = new BigDecimal(20);
    private BigDecimal tps_15m = new BigDecimal(20);

    public TPSMonitor(Yasui pl) {
        plugin = pl;
        this.runTaskTimer(plugin, plugin.config.scan_interval_tick, plugin.config.scan_interval_tick);
    }

    @Override
    public void run() {
        if (!plugin.config.enable) {
            return;
        }
        updateTPS();
        for (String key : plugin.config.rules.keySet()) {
            Rule rule = plugin.config.rules.get(key);
            if (rule != null && rule.enabled && !Strings.isNullOrEmpty(rule.engage_condition) && !Strings.isNullOrEmpty(rule.release_condition)) {
                if (rule.worlds != null && !rule.worlds.isEmpty()) {
                    for (String worldName : rule.worlds) {
                        World w = Bukkit.getWorld(worldName);
                        if (w != null) {
                            runRule(rule, w, plugin.config.getDefaultRegion(w));
                        } else {
                            plugin.getLogger().warning(String.format("rule: %s, world %s not exist.", key, worldName));
                        }
                    }
                }
            }
        }
        for (String name : plugin.config.regionConfig.regions.keySet()) {
            Region region = plugin.config.regionConfig.regions.get(name);
            if (!region.defaultRegion && region.enabled) {
                World w = Bukkit.getWorld(region.world);
                if (w != null) {
                    for (String ruleName : region.enforce) {
                        Rule rule = plugin.config.rules.get(ruleName);
                        if (rule != null) {
                            runRule(rule, w, region);
                        }
                    }
                }
            }
        }
    }

    public void runRule(Rule rule, World world, Region region) {
        if (region.bypass.contains(rule.name)) {
            return;
        }
        int oldTickSpeed = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED).intValue();
        int newTickSpeed = -1;
        WorldTask wt = WorldTask.getOrCreateTask(world);
        int oldVD = wt.worldViewDistance;
        int newVD = -1;
        BigDecimal engage = eval(rule.engage_condition, world, rule.filename);
        boolean broadcast = false;
        if ((engage != null && engage.intValue() > 0) || (region.enforce.contains(rule.name))) {
            engage = new BigDecimal(1);
            for (String name : rule.operations) {
                Operation o = getOperation(name);
                if (o != null) {
                    for (ModuleType module : o.modules) {
                        if (!region.defaultRegion && module == ModuleType.random_tick_speed) {
                            continue;
                        }
                        Operation oldVar = region.add(module, rule.name, o);
                        if (o != oldVar) {
                            broadcast = true;
                        }
                        if (module == ModuleType.random_tick_speed) {
                            newTickSpeed = Math.max(Math.max(o.random_tick_speed_min, oldTickSpeed - 1), o.random_tick_speed_min);
                        } else if (!Strings.isNullOrEmpty(o.command_executor_engage)) {
                            runCommands(world, o.command_executor_engage);
                        } else if (module == ModuleType.adjust_view_distance) {
                            newVD = Math.max(Math.max(o.adjust_view_distance_min, oldVD - 1), o.adjust_view_distance_min);
                        }
                    }
                }
            }
        }
        BigDecimal release = eval(rule.release_condition, world, rule.filename);
        if (release != null && release.intValue() > 0 && !region.enforce.contains(rule.name)) {
            for (String name : rule.operations) {
                Operation o = getOperation(name);
                if (o != null) {
                    for (ModuleType module : o.modules) {
                        boolean remove = true;
                        if (module == ModuleType.random_tick_speed) {
                            newTickSpeed = Math.min(Math.min(o.random_tick_speed_max, oldTickSpeed + 1), o.random_tick_speed_max);
                            if (o.random_tick_speed_max > newTickSpeed) {
                                remove = false;
                            }
                        } else if (!Strings.isNullOrEmpty(o.command_executor_engage)) {
                            runCommands(world, o.command_executor_release);
                        } else if (module == ModuleType.adjust_view_distance) {
                            newVD = Math.min(Math.min(o.adjust_view_distance_max, oldVD + 1), o.adjust_view_distance_max);
                            if (o.adjust_view_distance_max > newVD) {
                                remove = false;
                            }
                        }
                        if (remove) {
                            Operation oldVar = region.remove(module);
                            if (oldVar != null) {
                                broadcast = true;
                            }
                        }
                    }
                }
            }
        }
        if (oldTickSpeed != newTickSpeed && newTickSpeed >= 0) {
            Utils.setRandomTickSpeed(world, newTickSpeed);
            broadcast = true;
        }
        if (oldVD != newVD && newVD > 0) {
            wt.worldViewDistance = newVD;
            broadcast = true;
        }
        String msg = null;
        BroadcastType type = plugin.config.broadcast.type;
        if (engage != null && engage.intValue() > 0) {
            msg = rule.engage_message;
        } else if (release != null && release.intValue() > 0) {
            msg = rule.release_message;
        }
        if (!Strings.isNullOrEmpty(msg)) {
            msg = msg.replaceAll("\\{tps_1m}", String.format("%.2f", tps_1m.doubleValue()))
                    .replaceAll("\\{tps_5m}", String.format("%.2f", tps_5m.doubleValue()))
                    .replaceAll("\\{tps_15m}", String.format("%.2f", tps_15m.doubleValue()))
                    .replaceAll("\\{world_random_tick_speed}", String.valueOf(newTickSpeed >= 0 ? newTickSpeed : oldTickSpeed))
                    .replaceAll("\\{world_name}", region.name)
                    .replaceAll("\\{world_view_distance}", String.valueOf(newVD > 0 ? newVD : oldVD));
            if (!msg.equals(rule.lastMessages.get(region.name)) || broadcast) {
                Utils.broadcast(plugin.config.broadcast, msg, world);
                rule.lastMessages.put(region.name, msg);
            }
        }
    }

    public BigDecimal eval(String condition, World world, String ruleName) {
        if (condition != null && condition.length() > 0) {
            try {
                Expression exp = new Expression(condition)
                        .with("tps_1m", tps_1m)
                        .with("tps_5m", tps_5m)
                        .with("tps_15m", tps_15m)
                        .with("online_players", new BigDecimal(Bukkit.getOnlinePlayers().size()))
                        .with("world_random_tick_speed", new BigDecimal(world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED)))
                        .with("world_loaded_chunks", new BigDecimal(WorldTask.getOrCreateTask(world).loadedChunkCount))
                        .with("world_players", new BigDecimal(world.getPlayers().size()))
                        .with("world_living_entities", new BigDecimal(WorldTask.getOrCreateTask(world).livingEntityCount));
                if (Yasui.hasNU) {
                    exp.addFunction(new AbstractFunction("getTPSFromNU", 1) {
                        @Override
                        public BigDecimal eval(List<BigDecimal> parameters) {
                            return Utils.getTPSFromNU(parameters.get(0).intValue());
                        }
                    });
                }
                return exp.eval();
            } catch (Exception e) {
                plugin.getLogger().warning("=====================");
                plugin.getLogger().warning("rule: " + ruleName);
                e.printStackTrace();
                plugin.getLogger().warning("=====================");
            }
        }
        return null;
    }

    public void updateTPS() {
        double[] tps = Utils.getTPS();
        tps_1m = new BigDecimal(tps[0]);
        tps_5m = new BigDecimal(tps[1]);
        tps_15m = new BigDecimal(tps[2]);
    }

    public void runCommands(World world, String cmd) {
        String s = cmd.replaceAll("\\{world_name}", world.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s);
    }

    private Operation getOperation(String name) {
        Operation operation = plugin.config.operations.get(name);
        if (operation == null || operation.modules.isEmpty()) {
            plugin.getLogger().warning(I18n.format(operation == null ? "user.error.operation_not_exist" : "user.error.empty_operation", name));
            return null;
        }
        return operation;
    }
}
