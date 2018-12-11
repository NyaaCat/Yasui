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
    public List<String> ignored_world = Arrays.asList("v1_the_end", "v2_the_end");
    @Serializable
    public Map<String, Rule> rules = new HashMap<>();
    @Serializable(name = "disableai.excluded.entity_type", alias = "ignored_entity_type")
    public List<String> ai_ignored_entity_type = Arrays.asList(EntityType.PLAYER.name(), EntityType.SNOWMAN.name(), EntityType.ARMOR_STAND.name());
    @Serializable(name = "disableai.chunk_entity", alias = "chunk_entity")
    public int ai_chunk_entity = 20;
    @Serializable
    public boolean listen_mob_spawn = true;
    @Serializable
    public List<String> ignored_spawn_reason = Arrays.asList(CreatureSpawnEvent.SpawnReason.CUSTOM.name());

    @Serializable(name = "entity_limit.excluded.entity_type")
    public List<String> entity_limit_excluded_entity_type = Arrays.asList(EntityType.PLAYER.name(), EntityType.ARMOR_STAND.name());
    @Serializable(name = "entity_limit.excluded.has_custom_name")
    public boolean entity_limit_excluded_has_custom_name = true;
    @Serializable(name = "entity_limit.excluded.has_owner")
    public boolean entity_limit_excluded_has_owner = true;
    @Serializable(name = "entity_limit.per_chunk_max")
    public int entity_limit_per_chunk_max = 50;

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
