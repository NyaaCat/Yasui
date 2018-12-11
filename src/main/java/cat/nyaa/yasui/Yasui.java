package cat.nyaa.yasui;

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
    public Set<String> entityLimitWorlds = new HashSet<>();

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

    public void reload() {
        disable(false);
        onEnable();
    }
}
