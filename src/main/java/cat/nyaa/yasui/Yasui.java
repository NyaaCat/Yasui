package cat.nyaa.yasui;

import cat.nyaa.nyaacore.utils.NmsUtils;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public final class Yasui extends JavaPlugin {

    public static Yasui INSTANCE;
    public static boolean hasNU;
    public Configuration config;
    public I18n i18n;
    public CommandHandler commandHandler;
    public Set<String> disableAIWorlds = new HashSet<>();
    public TPSMonitor tpsMonitor;
    public EntityListener entityListener;

    @Override
    public void onEnable() {
        INSTANCE = this;
        hasNU = getServer().getPluginManager().isPluginEnabled("NyaaUtils");
        config = new Configuration(this);
        config.load();
        i18n = new I18n(this, this.config.language);
        i18n.load();
        commandHandler = new CommandHandler(this, this.i18n);
        getCommand("yasui").setExecutor(commandHandler);
        getCommand("yasui").setTabCompleter(commandHandler);
        tpsMonitor = new TPSMonitor(this);
        entityListener = new EntityListener(this);
    }

    @Override
    public void onDisable() {
        disable(true);
    }

    public void disable(boolean saveConfig) {
        getServer().getScheduler().cancelTasks(this);
        getCommand("yasui").setExecutor(null);
        getCommand("yasui").setTabCompleter(null);
        HandlerList.unregisterAll(this);
        if (saveConfig) {
            config.save();
        }
    }

    public void disableAI(World w, boolean ignoreWorldEntityCount) {
        for (World world : getServer().getWorlds()) {
            if (config.ignored_world.contains(world.getName())) {
                continue;
            }
            if (w != null && !w.getName().equalsIgnoreCase(world.getName())) {
                continue;
            }
            if (ignoreWorldEntityCount || world.getLivingEntities().size() >= this.config.world_entity) {
                if (!disableAIWorlds.contains(world.getName())) {
                    disableAIWorlds.add(world.getName());
                    getLogger().info("disable entity ai in " + world.getName());
                }
                for (Chunk chunk : world.getLoadedChunks()) {
                    int entityCount = Utils.getLivingEntityCount(chunk);
                    if (entityCount >= this.config.chunk_entity) {
                        for (Entity entity : chunk.getEntities()) {
                            if (entity instanceof LivingEntity && !config.ignored_entity_type.contains(entity.getType().name())) {
                                NmsUtils.setFromMobSpawner(entity, true);
                            }
                        }
                    }
                }
            }
        }
    }

    public void enableAI(World w) {
        for (World world : getServer().getWorlds()) {
            if (!disableAIWorlds.contains(world.getName())) {
                continue;
            } else {
                disableAIWorlds.remove(world.getName());
            }
            if (w != null && !w.getName().equalsIgnoreCase(world.getName())) {
                continue;
            }
            getLogger().info("enable entity ai in " + world.getName());
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof LivingEntity) {
                        NmsUtils.setFromMobSpawner((LivingEntity) entity, false);
                    }
                }
            }
        }
    }

    public void reload() {
        disable(false);
        onEnable();
    }
}
