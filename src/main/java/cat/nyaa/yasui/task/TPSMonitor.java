package cat.nyaa.yasui.task;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.config.Rule;
import cat.nyaa.yasui.other.BroadcastType;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.other.Utils;
import com.google.common.base.Strings;
import com.udojava.evalex.AbstractFunction;
import com.udojava.evalex.Expression;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TPSMonitor extends BukkitRunnable {
    private final Yasui plugin;
    private BigDecimal tps_1m = new BigDecimal(20);
    private BigDecimal tps_5m = new BigDecimal(20);
    private BigDecimal tps_15m = new BigDecimal(20);

    public static Map<String, Map<ModuleType, Operation>> worldLimits = new HashMap<>();

    public TPSMonitor(Yasui pl) {
        plugin = pl;
        this.runTaskTimer(plugin, plugin.config.task_delay_tick, plugin.config.scan_interval_tick);
        for (World world : Bukkit.getWorlds()) {
            worldLimits.put(world.getName(), new HashMap<>());
        }
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
                            plugin.getLogger().info("run " + key);
                            runRule(rule, w);
                        } else {
                            plugin.getLogger().warning(String.format("rule: %s, world %s not exist.", key, worldName));
                        }
                    }
                }
            }
        }
        plugin.redstoneListener.worlds.clear();
        for (String name : worldLimits.keySet()) {
            Map<ModuleType, Operation> operationMap = worldLimits.get(name);
            if (operationMap.containsKey(ModuleType.redstone_suppressor)) {
                plugin.redstoneListener.worlds.add(name);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Chunk center = p.getLocation().getChunk();
            int viewDistance = 16 > Bukkit.getViewDistance() ? Bukkit.getViewDistance() : 16;
            RegionTask.getOrCreateTask(p.getWorld().getChunkAt(center.getX() - viewDistance, center.getX() - viewDistance));
            RegionTask.getOrCreateTask(p.getWorld().getChunkAt(center.getX() + viewDistance, center.getX() - viewDistance));
            RegionTask.getOrCreateTask(p.getWorld().getChunkAt(center.getX() + viewDistance, center.getX() + viewDistance));
            RegionTask.getOrCreateTask(p.getWorld().getChunkAt(center.getX() - viewDistance, center.getX() + viewDistance));
        }
    }

    public void runRule(Rule rule, World world) {
        int oldTickSpeed = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED).intValue();
        int newTickSpeed = -1;
        BigDecimal engage = eval(rule.engage_condition, world);
        if (engage != null && engage.intValue() > 0) {
            for (String name : rule.operations) {
                Operation o = plugin.config.operations.get(name);
                if (o != null) {
                    for (ModuleType module : o.modules) {
                        worldLimits.get(world.getName()).put(module, o);
                        if (module == ModuleType.random_tick_speed) {
                            newTickSpeed = Math.max(Math.max(o.random_tick_speed_min, oldTickSpeed - 1), o.random_tick_speed_min);
                        } else if (!Strings.isNullOrEmpty(o.command_executor_engage)) {
                            runCommands(world, o.command_executor_engage);
                        }
                    }
                } else {
                    plugin.getLogger().warning("operation not exist: " + name);
                }
            }
        }
        BigDecimal release = eval(rule.release_condition, world);
        if (release != null && release.intValue() > 0) {
            for (String name : rule.operations) {
                Operation o = plugin.config.operations.get(name);
                if (o != null) {
                    for (ModuleType module : o.modules) {
                        worldLimits.get(world.getName()).remove(module);
                        if (module == ModuleType.random_tick_speed) {
                            newTickSpeed = Math.min(Math.min(o.random_tick_speed_max, oldTickSpeed + 1), o.random_tick_speed_max);
                        } else if (!Strings.isNullOrEmpty(o.command_executor_engage)) {
                            runCommands(world, o.command_executor_release);
                        }
                    }
                } else {
                    plugin.getLogger().warning("operation not exist: " + name);
                }
            }
        }
        if (oldTickSpeed != newTickSpeed) {
            Utils.setRandomTickSpeed(world, newTickSpeed);
        }
        String msg = null;
        BroadcastType type = null;
        if (engage != null && engage.intValue() > 0) {
            msg = rule.engage_message;
            type = rule.engage_broadcast == null ? plugin.config.broadcast : rule.engage_broadcast;
        } else if (release != null && release.intValue() > 0) {
            msg = rule.release_message;
            type = rule.release_broadcast == null ? plugin.config.broadcast : rule.release_broadcast;
        }
        if (!Strings.isNullOrEmpty(msg) && type != BroadcastType.NONE) {
            msg = msg.replaceAll("\\{tps_1m}", String.format("%.2f", tps_1m.doubleValue()))
                    .replaceAll("\\{tps_5m}", String.format("%.2f", tps_5m.doubleValue()))
                    .replaceAll("\\{tps_15m}", String.format("%.2f", tps_15m.doubleValue()))
                    .replaceAll("\\{world_random_tick_speed}", String.valueOf(newTickSpeed >= 0 ? newTickSpeed : oldTickSpeed))
                    .replaceAll("\\{world_name}", world.getName());
            Utils.broadcast(type, msg, world);
        }
    }

    public BigDecimal eval(String condition, World world) {
        if (condition != null && condition.length() > 0) {
            Expression exp = new Expression(condition)
                    .with("tps_1m", tps_1m)
                    .with("tps_5m", tps_5m)
                    .with("tps_15m", tps_15m)
                    .with("online_players", new BigDecimal(Bukkit.getOnlinePlayers().size()))
                    .with("world_random_tick_speed", new BigDecimal(world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED)))
                    .with("world_loaded_chunks", new BigDecimal(world.getLoadedChunks().length))
                    .with("world_players", new BigDecimal(world.getPlayers().size()))
                    .with("world_living_entities", new BigDecimal(world.getLivingEntities().size()));
            if (Yasui.hasNU) {
                exp.addFunction(new AbstractFunction("getTPSFromNU", 1) {
                    @Override
                    public BigDecimal eval(List<BigDecimal> parameters) {
                        return Utils.getTPSFromNU(parameters.get(0).intValue());
                    }
                });
            }
            return exp.eval();
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
}
