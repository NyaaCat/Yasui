package cat.nyaa.yasui;


import cat.nyaa.nyaacore.Message;
import com.udojava.evalex.AbstractFunction;
import com.udojava.evalex.Expression;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TPSMonitor extends BukkitRunnable {
    private final Yasui plugin;
    private BigDecimal tps_1m = new BigDecimal(20);
    private BigDecimal tps_5m = new BigDecimal(20);
    private BigDecimal tps_15m = new BigDecimal(20);

    public TPSMonitor(Yasui pl) {
        plugin = pl;
        this.runTaskTimer(plugin, plugin.config.task_delay_tick, plugin.config.check_interval_tick);
    }

    @Override
    public void run() {
        if (!plugin.config.enable) {
            return;
        }
        updateTPS();
        //HashSet<String> old_disableAIWorlds = new HashSet<>(plugin.disableAIWorlds);
        //HashSet<String> old_entityLimitWorlds = new HashSet<>(plugin.entityLimitWorlds);
        for (String key : plugin.config.rules.keySet()) {
            Rule rule = plugin.config.rules.get(key);
            if (rule != null && rule.enable && rule.condition != null && rule.condition.length() > 0) {
                BigDecimal result = null;
                if (rule.worlds != null && !rule.worlds.isEmpty()) {
                    List<World> worldList = new ArrayList<>();
                    for (String worldName : rule.worlds) {
                        World w = Bukkit.getWorld(worldName);
                        if (w != null) {
                            result = eval(rule.condition, w);
                            if (result != null && result.intValue() > 0) {
                                runRule(rule, w);
                                worldList.add(w);
                            }
                        } else {
                            plugin.getLogger().warning(String.format("rule: %s, world %s not exist.", key, worldName));
                        }
                    }
                    runCommands(rule, worldList);
                }
            }
        }
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.config.ignored_world.contains(world.getName())) {
                String name = world.getName();
                //if ((!old_entityLimitWorlds.contains(name) && plugin.entityLimitWorlds.contains(name)) ||
                //        (old_disableAIWorlds.contains(name) != plugin.disableAIWorlds.contains(name))) {
                Utils.checkWorld(world);
                //}
            }
        }
    }

    public void runRule(Rule rule, World world) {
        BigDecimal enableAI = eval(rule.enable_ai, world);
        if (enableAI != null && enableAI.intValue() > 0) {
            plugin.disableAIWorlds.remove(world.getName());
        }
        BigDecimal disableAI = eval(rule.disable_ai, world);
        if (disableAI != null && disableAI.intValue() > 0) {
            plugin.disableAIWorlds.add(world.getName());
        }
        BigDecimal tickSpeed = eval(rule.world_random_tick_speed, world);
        if (tickSpeed != null && tickSpeed.intValue() >= 0) {
            Utils.setRandomTickSpeed(world, tickSpeed.intValue());
        }
        BigDecimal limitEnable = eval(rule.entity_limit_enable, world);
        if (limitEnable != null && limitEnable.intValue() > 0) {
            plugin.entityLimitWorlds.add(world.getName());
        }
        BigDecimal limitDisable = eval(rule.entity_limit_disable, world);
        if (limitDisable != null && limitDisable.intValue() > 0) {
            plugin.entityLimitWorlds.remove(world.getName());
        }
        BigDecimal redstone_max_change = eval(rule.redstone_limit_max_change, world);
        if (redstone_max_change != null) {
            if (redstone_max_change.intValue() >= 0) {
                plugin.redstoneListener.worlds.add(world.getName());
                plugin.redstoneListener.redstoneLimitMaxChange = redstone_max_change.intValue();
                plugin.redstoneListener.redstoneLimitDisableSeconds = eval(rule.redstone_limit_disable_seconds, world).intValue();
                plugin.redstoneListener.redstoneLimitDisableRadius = eval(rule.redstone_limit_disable_radius, world).intValue();
            } else {
                plugin.redstoneListener.worlds.remove(world.getName());
            }
        }
        if (rule.messageType != null && rule.message != null) {
            String msg = rule.message.replaceAll("\\{tps_1m}", String.format("%.2f", tps_1m.doubleValue()))
                    .replaceAll("\\{tps_5m}", String.format("%.2f", tps_5m.doubleValue()))
                    .replaceAll("\\{tps_15m}", String.format("%.2f", tps_15m.doubleValue()))
                    .replaceAll("\\{tps_1m}", String.format("%.2f", tps_1m.doubleValue()));
            if (tickSpeed != null) {
                msg = msg.replaceAll("\\{world_random_tick_speed}", String.valueOf(tickSpeed.intValue()));
            }
            if (redstone_max_change != null) {
                msg = msg.replaceAll("\\{redstone_max_change}", String.valueOf(redstone_max_change.intValue()))
                        .replaceAll("\\{redstone_disable_radius}", String.valueOf(plugin.redstoneListener.redstoneLimitDisableRadius))
                        .replaceAll("\\{redstone_disable_seconds}", String.valueOf(plugin.redstoneListener.redstoneLimitDisableSeconds));
            }
            new Message(ChatColor.translateAlternateColorCodes('&', msg)).broadcast(rule.messageType, p -> (p.getWorld().equals(world)));
        }
    }

    public BigDecimal eval(String condition, World world) {
        if (condition != null && condition.length() > 0) {
            Expression exp = new Expression(condition)
                    .with("tps_1m", tps_1m)
                    .with("tps_5m", tps_5m)
                    .with("tps_15m", tps_15m)
                    .with("online_players", new BigDecimal(Bukkit.getOnlinePlayers().size()))
                    .with("random_tick_speed", new BigDecimal(world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED)));
            if (Yasui.hasNU) {
                exp.addFunction(new AbstractFunction("getTPSFromNU", 1) {
                    @Override
                    public BigDecimal eval(List<BigDecimal> parameters) {
                        return Utils.getTPSFromNU(parameters.get(0).intValue());
                    }
                });
            }
            exp.with("world_random_tick_speed", new BigDecimal(world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED)))
                    .with("loaded_chunks", new BigDecimal(world.getLoadedChunks().length))
                    .with("world_players", new BigDecimal(world.getPlayers().size()))
                    .with("world_living_entities", new BigDecimal(world.getLivingEntities().size()))
                    .with("redstone_max_change", new BigDecimal(plugin.redstoneListener.redstoneLimitMaxChange))
                    .with("redstone_disable_radius", new BigDecimal(plugin.redstoneListener.redstoneLimitDisableRadius))
                    .with("redstone_disable_seconds", new BigDecimal(plugin.redstoneListener.redstoneLimitDisableSeconds));
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

    public void runCommands(Rule rule, List<World> worlds) {
        if (rule.commands != null && !rule.commands.isEmpty() && !worlds.isEmpty()) {
            for (String cmd : rule.commands) {
                for (World world : worlds) {
                    String s = cmd.replaceAll("\\{world_name}", world.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s);
                    if (!cmd.contains("{world_name}")) {
                        break;
                    }
                }
            }
        }
    }
}
