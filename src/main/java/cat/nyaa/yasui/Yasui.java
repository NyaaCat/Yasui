package cat.nyaa.yasui;

import cat.nyaa.yasui.listener.EntityListener;
import cat.nyaa.yasui.listener.RedstoneListener;
import cat.nyaa.yasui.listener.WorldListener;
import cat.nyaa.yasui.task.*;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class Yasui extends JavaPlugin {

    public static Yasui INSTANCE;
    public static boolean hasNU;
    public static boolean isPaper;
    public Configuration config;
    public I18n i18n;
    public CommandHandler commandHandler;
    public TPSMonitor tpsMonitor;
    public EntityListener entityListener;
    public ProfilerStatsMonitor profilerStatsMonitor;
    public ProfilerListener profilerListener;
    public RedstoneListener redstoneListener;
    public WorldListener worldListener;

    @Override
    public void onEnable() {
        INSTANCE = this;
        hasNU = getServer().getPluginManager().isPluginEnabled("NyaaUtils");
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            isPaper = true;
        } catch (Exception e) {
            isPaper = false;
        }
        config = new Configuration(this);
        config.load();
        i18n = new I18n(this, this.config.language);
        i18n.load();
        commandHandler = new CommandHandler(this, this.i18n);
        getCommand("yasui").setExecutor(commandHandler);
        getCommand("yasui").setTabCompleter(commandHandler);
        tpsMonitor = new TPSMonitor(this);
        entityListener = new EntityListener(this);
        worldListener = new WorldListener(this);
        if (config.profiler_listen_event) {
            profilerStatsMonitor = new ProfilerStatsMonitor(this);
            profilerStatsMonitor.run();
            profilerListener = new ProfilerListener(this);
        }
        redstoneListener = new RedstoneListener(this);
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
        ChunkTask.taskMap.clear();
        RegionTask.taskMap.clear();
        WorldTask.taskMap.clear();
        PlayerTask.taskMap.clear();
        if (saveConfig) {
            config.save();
        }
    }

    public void reload() {
        disable(false);
        onEnable();
    }
}
