package cat.nyaa.yasui;


import cat.nyaa.utils.ReflectionUtil;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class TPSMonitor extends BukkitRunnable {
    private final Main plugin;

    public TPSMonitor(Main pl) {
        plugin = pl;
        this.runTaskTimer(plugin, 20, plugin.config.check_interval_tick);
    }

    public static double[] getTPS() {
        try {
            Object nmsServer = ReflectionUtil.getNMSClass("MinecraftServer").getMethod("getServer").invoke(null);
            Field field = nmsServer.getClass().getField("recentTps");
            return (double[]) field.get(nmsServer);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void run() {
        if (!plugin.config.enable) {
            return;
        }
        double[] tps = getTPS();
        double tps_1m = tps[0];
        double tps_5m = tps[1];
        double tps_15m = tps[2];
        if (!plugin.disableAIWorlds.isEmpty()) {
            if (tps_1m <= plugin.config.tps_disable_ai) {
                plugin.disableAI();
            } else if (tps_15m >= plugin.config.tps_enable_ai) {
                plugin.enableAI();
            }
        } else {
            if (tps_5m <= plugin.config.tps_disable_ai) {
                plugin.disableAI();
            }
        }
    }
}
