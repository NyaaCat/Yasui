package cat.nyaa.yasui;

import cat.nyaa.nyaacore.configuration.ISerializable;
import cat.nyaa.nyaacore.configuration.PluginConfigure;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public class Configuration extends PluginConfigure {
    private final Main plugin;

    @Serializable
    public String language = "en_US";
    @Serializable
    public boolean enable = true;
    @Serializable
    public int check_interval_tick = 20 * 60;
    @Serializable
    public double tps_disable_ai = 18.0D;
    @Serializable
    public double tps_enable_ai = 19.9D;
    @Serializable
    public int world_entity = 2000;
    @Serializable
    public int chunk_entity = 20;
    @Serializable
    public List<String> ignored_world = Arrays.asList("v1_the_end", "v2_the_end");

    public Configuration(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    protected JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    public void deserialize(ConfigurationSection config) {
        ISerializable.deserialize(config, this);
    }

    @Override
    public void serialize(ConfigurationSection config) {
        ISerializable.serialize(config, this);
    }
}
