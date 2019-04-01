package cat.nyaa.yasui;

import cat.nyaa.nyaacore.utils.NmsUtils;
import cat.nyaa.nyaacore.utils.ReflectionUtils;
import cat.nyaa.nyaautils.NyaaUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.minecart.HopperMinecart;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
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

    public static void checkEntity(Chunk chunk) {
        int count = getLivingEntityCount(chunk);
        int max = Yasui.INSTANCE.entityLimitWorlds.contains(chunk.getWorld().getName()) ? Yasui.INSTANCE.config.entity_limit_per_chunk_max : -1;
        boolean disableAI = Yasui.INSTANCE.disableAIWorlds.contains(chunk.getWorld().getName());
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            if (max >= 0 && count - removed > max && canRemove(entity)) {
                entity.remove();
                removed++;
                continue;
            }
            if (count >= Yasui.INSTANCE.config.ai_chunk_entity && entity instanceof LivingEntity && !Yasui.INSTANCE.config.ai_ignored_entity_type.contains(entity.getType().name())) {
                NmsUtils.setFromMobSpawner(entity, disableAI);
            } else {
                NmsUtils.setFromMobSpawner(entity, false);
            }
        }
    }

    public static boolean canRemove(Entity entity) {
        if (entity instanceof LivingEntity) {
            if (//entity.isInvulnerable() ||
                    (Yasui.INSTANCE.config.entity_limit_excluded_has_custom_name && entity.getCustomName() != null) ||
                            (Yasui.INSTANCE.config.entity_limit_excluded_has_owner && entity instanceof Tameable && ((Tameable) entity).getOwner() != null) ||
                            (Yasui.INSTANCE.config.entity_limit_excluded_entity_type.contains(entity.getType().name()))
            ) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static void checkWorld(World w) {
        if (Yasui.INSTANCE.config.entity_limit_global_enable) {
            Yasui.INSTANCE.entityLimitWorlds.add(w.getName());
        }
        for (Chunk chunk : w.getLoadedChunks()) {
            checkEntity(chunk);
        }
    }

    public static List<ChunkCoordinate> getChunks(Chunk chunk, int radius) {
        List<ChunkCoordinate> list = new ArrayList<>();
        World world = chunk.getWorld();
        if (radius > 0) {
            int maxX = chunk.getX() + radius;
            int maxZ = chunk.getZ() + radius;
            for (int minX = chunk.getX() - radius; minX <= maxX; minX++) {
                for (int minZ = chunk.getZ() - radius; minZ <= maxZ; minZ++) {
                    list.add(ChunkCoordinate.of(world.getChunkAt(minX, minZ)));
                }
            }
        } else {
            list.add(ChunkCoordinate.of(chunk));
        }
        return list;
    }

    public static void disableHopper(Location loc) {
        if (loc.getBlock().getType() == Material.HOPPER) {
            Block b = loc.getBlock();
            Hopper hopperData = (Hopper) b.getBlockData();
            hopperData.setEnabled(false);
            b.setBlockData(hopperData, false);
        } else {
            Collection<Entity> entity = loc.getWorld().getNearbyEntities(loc, 0.1, 0.1, 0.1, e -> e instanceof HopperMinecart);
            for (Entity e : entity) {
                if (e instanceof HopperMinecart) {
                    ((HopperMinecart) e).setEnabled(false);
                }
            }
        }
    }
}