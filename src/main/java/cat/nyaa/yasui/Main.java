package cat.nyaa.yasui;

import cat.nyaa.nyaacore.utils.ReflectionUtils;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public final class Main extends JavaPlugin {

    public Configuration config;
    public I18n i18n;
    public CommandHandler commandHandler;
    public ArrayList<String> disableAIWorlds = new ArrayList<>();
    public TPSMonitor tpsMonitor;

    @Override
    public void onEnable() {
        config = new Configuration(this);
        config.load();
        i18n = new I18n(this, this.config.language);
        i18n.load();
        commandHandler = new CommandHandler(this, this.i18n);
        getCommand("yasui").setExecutor(commandHandler);
        getCommand("yasui").setTabCompleter(commandHandler);
        tpsMonitor = new TPSMonitor(this);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getCommand("yasui").setExecutor(null);
        getCommand("yasui").setTabCompleter(null);
        HandlerList.unregisterAll(this);
        config.save();
    }

    public void disableAI() {
        for (World world : getServer().getWorlds()) {
            if (config.ignored_world.contains(world.getName())) {
                continue;
            }
            if (world.getLivingEntities().size() >= this.config.world_entity) {
                if (!disableAIWorlds.contains(world.getName())) {
                    disableAIWorlds.add(world.getName());
                    getLogger().info("disable entity ai in " + world.getName());
                }
                for (Chunk chunk : world.getLoadedChunks()) {
                    int entityCount = 0;
                    for (Entity entity : chunk.getEntities()) {
                        if (entity instanceof LivingEntity) {
                            entityCount++;
                        }
                    }
                    if (entityCount >= this.config.chunk_entity) {
                        for (Entity entity : chunk.getEntities()) {
                            if (entity instanceof LivingEntity) {
                                setAI((LivingEntity) entity, false);
                            }
                        }
                    }
                }
            }
        }
    }

    public void enableAI() {
        for (World world : getServer().getWorlds()) {
            if (!disableAIWorlds.contains(world.getName())) {
                continue;
            } else {
                disableAIWorlds.remove(world.getName());
            }
            getLogger().info("enable entity ai in " + world.getName());
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof LivingEntity) {
                        setAI((LivingEntity) entity, true);
                    }
                }
            }
        }
    }

    public void setAI(LivingEntity entity, boolean ai) {
        try {
            if (entity.isValid() && !(entity instanceof ArmorStand)) {
                Class craftEntityClazz = ReflectionUtils.getOBCClass("entity.CraftEntity");
                Method getNMSEntityMethod = craftEntityClazz.getMethod("getHandle");
                Object e = getNMSEntityMethod.invoke(entity);
                Class nmsEntityClazz = ReflectionUtils.getNMSClass("Entity");
                Field field = nmsEntityClazz.getField("fromMobSpawner");
                field.setBoolean(e, !ai);
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void reload() {
        onDisable();
        onEnable();
    }
}
