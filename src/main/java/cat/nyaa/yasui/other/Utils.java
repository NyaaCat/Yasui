package cat.nyaa.yasui.other;

import cat.nyaa.nyaacore.Message;
import cat.nyaa.nyaacore.utils.ReflectionUtils;
import cat.nyaa.nyaautils.NyaaUtils;
import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.config.BroadcastConfig;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.task.ChunkTask;
import cat.nyaa.yasui.task.WorldTask;
import com.google.common.base.Strings;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
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

    public static int getLivingEntityCount(Chunk chunk, Operation mobcap) {
        int entityCount = 0;
        ChunkTask task = mobcap == null ? null : ChunkTask.getOrCreateTask(chunk);
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) {
                entityCount++;
                if (task != null) {
                    task.mobcapEntityTypeCount.put(entity.getType(), task.mobcapEntityTypeCount.getOrDefault(entity.getType(), 0) + 1);
                }
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
        ChunkTask task = ChunkTask.getOrCreateTask(chunk);
        Operation ai_suppressor = task.region.get(ModuleType.entity_ai_suppressor);
        Operation entity_culler = task.region.get(ModuleType.entity_culler);
        Operation mobcap = task.region.get(ModuleType.mobcap);
        if (task.forceUpdateEntity || ai_suppressor != null || entity_culler != null || mobcap != null || task.noAI) {
            task.mobcapEntityTypeCount.clear();
            int count = getLivingEntityCount(chunk, mobcap);
            int per_chunk_max = entity_culler != null ? entity_culler.entity_culler_per_chunk_limit : -1;
            int removed = 0;
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof LivingEntity) {
                    if (per_chunk_max >= 0 && count - removed > per_chunk_max && canRemove(entity, entity_culler)) {
                        entity.remove();
                        removed++;
                    } else {
                        setAI((LivingEntity) entity, ai_suppressor == null, ai_suppressor);
                    }
                }
            }
            task.forceUpdateEntity = false;
            task.noAI = ai_suppressor != null;
            task.LivingEntityCount = count - removed;
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

    public static void broadcast(BroadcastConfig config, String msg, World world) {
        String permission = config.type == BroadcastType.ADMIN_CHAT ? "yasui.admin" : config.permission;
        Message.MessageType type = Message.MessageType.CHAT;
        if (config.type == BroadcastType.ACTIONBAR) {
            type = Message.MessageType.ACTION_BAR;
        } else if (config.type == BroadcastType.SUBTITLE) {
            type = Message.MessageType.SUBTITLE;
        } else if (config.type == BroadcastType.TITLE) {
            type = Message.MessageType.TITLE;
        }
        Message message = new Message(ChatColor.translateAlternateColorCodes('&', msg));
        if (config.log_console) {
            Yasui.INSTANCE.getLogger().info(message.inner.toLegacyText());
        }
        if (config.type != BroadcastType.NONE) {
            for (Player p : world == null ? Bukkit.getOnlinePlayers() : world.getPlayers()) {
                if ((Strings.isNullOrEmpty(permission) || p.hasPermission(permission))) {
                    message.send(p, type);
                }
            }
        }
    }

    public static void setAI(LivingEntity entity, boolean ai, Operation operation) {
        if (ai || operation == null || operation.entity_ai_suppressor_exclude_type.contains(entity.getType()) ||
                (operation.entity_ai_suppressor_exclude_tagged && entity.getCustomName() != null) ||
                (operation.entity_ai_suppressor_exclude_tamed && entity instanceof Tameable && ((Tameable) entity).getOwner() != null)) {
            nerfAI(entity, false);
            entity.setAI(true);
        } else if (operation.entity_ai_suppresse_method != NoAIType.NO_TARGETING) {
            if (operation.entity_ai_suppresse_method == NoAIType.NERFING) {
                nerfAI(entity, true);
            } else if (operation.entity_ai_suppresse_method == NoAIType.NO_AI) {
                entity.setAI(false);
            }
        }
    }

    private static void nerfAI(LivingEntity entity, boolean flag) {
        if (entity instanceof Mob) {
            ((Mob) entity).setAware(!flag);
        }
    }

    public static <E extends Enum<E>> EnumSet<E> toEnumSet(Class<E> c, List<String> list) {
        EnumSet<E> set = EnumSet.noneOf(c);
        if (list != null) {
            for (String name : list) {
                if (name.equals("PIG_ZOMBIE")){
                    name = "ZOMBIFIED_PIGLIN";
                }
                E e = Enum.valueOf(c, name);
                set.add(e);
            }
        }
        return set;
    }

    public static Map<EntityType, Integer> loadEntityTypeLimit(ConfigurationSection config) {
        Map<EntityType, Integer> map = new HashMap<>();
        for (String name : config.getKeys(false)) {
            map.put(EntityType.valueOf(name), config.getInt(name));
        }
        return map;
    }

    public static void updatePlayerViewDistance(Player player) {
        World world = player.getWorld();
        WorldTask worldTask = WorldTask.getOrCreateTask(world);
        Operation vd = Yasui.INSTANCE.config.getDefaultRegion(world).get(ModuleType.adjust_view_distance);
        if ((vd != null || Yasui.INSTANCE.config.enabledModules.contains(ModuleType.adjust_view_distance)) && player.getViewDistance() != worldTask.worldViewDistance) {
            player.setViewDistance(worldTask.worldViewDistance);
        }
    }
}