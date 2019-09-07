package cat.nyaa.yasui.config;

import cat.nyaa.nyaacore.configuration.ISerializable;
import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.other.NoAIType;
import cat.nyaa.yasui.other.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.EnumSet;
import java.util.List;

public class Operation extends ReadOnlyConfig {

    public EnumSet<ModuleType> modules = EnumSet.noneOf(ModuleType.class);

    @Serializable(name = "entity_ai_suppressor.method")
    public NoAIType entity_ai_suppresse_method;
    @Serializable(name = "entity_ai_suppressor.exclude.type")
    private List<String> _entity_ai_suppressor_exclude_type;
    public EnumSet<EntityType> entity_ai_suppressor_exclude_type;
    @Serializable(name = "entity_ai_suppressor.exclude.tagged")
    public boolean entity_ai_suppressor_exclude_tagged;
    @Serializable(name = "entity_ai_suppressor.exclude.tamed")
    public boolean entity_ai_suppressor_exclude_tamed;

    @Serializable(name = "entity_culler.excluded.type")
    private List<String> _entity_culler_excluded_type;
    public EnumSet<EntityType> entity_culler_excluded_type;
    @Serializable(name = "entity_culler.excluded.tagged")
    public boolean entity_culler_excluded_tagged;
    @Serializable(name = "entity_culler.excluded.tamed")
    public boolean entity_culler_excluded_tamed;
    @Serializable(name = "entity_culler.per_chunk_limit")
    public int entity_culler_per_chunk_limit = -1;
    @Serializable(name = "entity_culler.per_region_limit")
    public int entity_culler_per_region_limit = -1;

    @Serializable(name = "redstone_suppressor.event_thresholds.count_per_chunk")
    public int redstone_suppressor_per_chunk = -1;
    @Serializable(name = "redstone_suppressor.event_thresholds.count_piston_per_chunk")
    public int redstone_suppressor_piston_per_chunk = -1;
    @Serializable(name = "redstone_suppressor.supress_chunk_region")
    public int redstone_suppressor_supress_chunk_region = 1;

    @Serializable(name = "random_tick_speed.min")
    public int random_tick_speed_min;
    @Serializable(name = "random_tick_speed.max")
    public int random_tick_speed_max;

    @Serializable(name = "command_executor.engage")
    public String command_executor_engage;
    @Serializable(name = "command_executor.release")
    public String command_executor_release;

    public Operation(Yasui plugin, File dir, String filename) {
        super(plugin, dir, filename);
        load();
    }

    @Override
    public void deserialize(ConfigurationSection config) {
        ISerializable.deserialize(config, this);
        entity_ai_suppressor_exclude_type = Utils.toEnumSet(EntityType.class, _entity_ai_suppressor_exclude_type);
        entity_ai_suppressor_exclude_type.add(EntityType.PLAYER);
        entity_culler_excluded_type = Utils.toEnumSet(EntityType.class, _entity_culler_excluded_type);
        for (ModuleType module : Yasui.INSTANCE.config.enabledModules) {
            if (config.isConfigurationSection(module.name())) {
                modules.add(module);
            }
        }
    }
}
