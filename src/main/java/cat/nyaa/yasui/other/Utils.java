package cat.nyaa.yasui.other;

import cat.nyaa.nyaacore.Message;
import cat.nyaa.nyaacore.utils.NmsUtils;
import cat.nyaa.nyaacore.utils.ReflectionUtils;
import cat.nyaa.nyaautils.NyaaUtils;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.task.ChunkTask;
import cat.nyaa.yasui.task.TPSMonitor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.minecart.HopperMinecart;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;
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
            if (entity instanceof LivingEntity) {
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

    public static void checkLivingEntity(Chunk chunk) {
        Map<ModuleType, Operation> limit = TPSMonitor.worldLimits.get(chunk.getWorld().getName());
        if (limit != null) {
            Operation ai_suppressor = limit.get(ModuleType.entity_ai_suppressor);
            Operation entity_culler = limit.get(ModuleType.entity_culler);
            if (ai_suppressor != null || entity_culler != null || ChunkTask.getOrCreateTask(chunk).noAI) {
                int count = getLivingEntityCount(chunk);
                int per_chunk_max = entity_culler != null ? entity_culler.entity_culler_per_chunk_limit : -1;
                int removed = 0;
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof LivingEntity) {
                        if (per_chunk_max >= 0 && count - removed > per_chunk_max && canRemove(entity, entity_culler)) {
                            entity.remove();
                            removed++;
                        }
                        setAI((LivingEntity) entity, ai_suppressor == null, ai_suppressor);
                    }
                }
                ChunkTask.getOrCreateTask(chunk).noAI = ai_suppressor != null;
                ChunkTask.getOrCreateTask(chunk).LivingEntityCount = count - removed;
            }
        }
    }

    public static boolean canRemove(Entity entity, Operation operation) {
        if (entity instanceof LivingEntity) {
            if (operation != null) {
                if (//entity.isInvulnerable() ||
                        entity instanceof Player ||
                                (operation.entity_culler_excluded_tagged && entity.getCustomName() != null) ||
                                (operation.entity_culler_excluded_tamed && entity instanceof Tameable && ((Tameable) entity).getOwner() != null) ||
                                (operation.entity_culler_excluded_type.contains(entity.getType()))
                ) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static void checkWorld(World w) {
        for (Chunk chunk : w.getLoadedChunks()) {
            checkLivingEntity(chunk);
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

    public static void broadcast(BroadcastType broadcastType, String msg, World world) {
        Message.MessageType type = Message.MessageType.CHAT;
        if (broadcastType == BroadcastType.ACTIONBAR) {
            type = Message.MessageType.ACTION_BAR;
        } else if (broadcastType == BroadcastType.SUBTITLE) {
            type = Message.MessageType.SUBTITLE;
        }
        new Message(ChatColor.translateAlternateColorCodes('&', msg)).broadcast(type, p -> broadcastType == BroadcastType.ADMIN_CHAT ? p.isOp() : world == null || p.getWorld().equals(world));
    }

    public static void setAI(LivingEntity entity, boolean ai, Operation operation) {
        if (ai || operation == null) {
            NmsUtils.setFromMobSpawner(entity, false);
            entity.setAI(true);
        } else if (operation.entity_ai_suppresse_method != NoAIType.NO_TARGETING
                && !operation.entity_ai_suppressor_exclude_type.contains(entity.getType())
                && !(operation.entity_ai_suppressor_excluded_tagged && entity.getCustomName() != null)
                && !(operation.entity_ai_suppressor_excluded_tamed && entity instanceof Tameable && ((Tameable) entity).getOwner() != null)) {
            if (operation.entity_ai_suppresse_method == NoAIType.NERFING) {
                NmsUtils.setFromMobSpawner(entity, true);
            } else if (operation.entity_ai_suppresse_method == NoAIType.NO_AI) {
                entity.setAI(false);
            }
        }
    }

    public static <E extends Enum<E>> EnumSet<E> toEnumSet(Class<E> c, List<String> list) {
        EnumSet<E> set = EnumSet.noneOf(c);
        if (list != null) {
            for (String name : list) {
                set.add(Enum.valueOf(c, name));
            }
        }
        return set;
    }
}