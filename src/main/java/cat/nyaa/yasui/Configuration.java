package cat.nyaa.yasui;

import cat.nyaa.nyaacore.configuration.ISerializable;
import cat.nyaa.nyaacore.configuration.PluginConfigure;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Configuration extends PluginConfigure {
    private final Yasui plugin;

    @Serializable
    public String language = "en_US";
    @Serializable
    public boolean enable = true;
    @Serializable
    public int check_interval_tick = 20 * 30;
    @Serializable
    public int world_entity = 2000;
    @Serializable
    public int chunk_entity = 20;
    @Serializable
    public List<String> ignored_world = Arrays.asList("v1_the_end", "v2_the_end");
    @Serializable
    public Map<String, Rule> rules = new HashMap<>();
    @Serializable
    public List<String> ignored_entity_type = Arrays.asList(EntityType.SNOWMAN.name(),EntityType.ARMOR_STAND.name());
    @Serializable
    public boolean listen_mob_spawn = false;
    @Serializable
    public List<String> ignored_spawn_reason = Arrays.asList(CreatureSpawnEvent.SpawnReason.CUSTOM.name());

    public Configuration(Yasui plugin) {
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
