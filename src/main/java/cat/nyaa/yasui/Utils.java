package cat.nyaa.yasui;

import cat.nyaa.nyaacore.utils.NmsUtils;
import cat.nyaa.nyaacore.utils.ReflectionUtils;
import cat.nyaa.nyaautils.NyaaUtils;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class Utils {

    public static void setRandomTickSpeed(World world, int speed) {
        Integer s = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
        if (s != speed) {
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, speed);
        }
    }

    public static double[] getTPS() {
        try {
            Object nmsServer = ReflectionUtils.getNMSClass("MinecraftServer").getMethod("getServer").invoke(null);
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

    public static int getLivingEntityCount(Chunk chunk) {
        int entityCount = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof ArmorStand)) {
                entityCount++;
            }
        }
        return entityCount;
    }

    public static BigDecimal getTPSFromNU(int seconds) {
        List<Byte> history = NyaaUtils.instance.tpsPingTask.tpsHistory();
        List<Byte> last = history.stream().skip(Math.max(0, history.size() - seconds)).collect(Collectors.toList());
        int totalTPS = 0;
        for (Byte tps : last) {
            totalTPS += tps;
        }
        return new BigDecimal(totalTPS / last.size());
    }
}
