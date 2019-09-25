package cat.nyaa.yasui;

import cat.nyaa.nyaacore.configuration.ISerializable;
import cat.nyaa.nyaacore.configuration.PluginConfigure;
import cat.nyaa.yasui.config.BroadcastConfig;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.config.Rule;
import cat.nyaa.yasui.other.ChunkCoordinate;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.other.Utils;
import cat.nyaa.yasui.region.Region;
import cat.nyaa.yasui.region.RegionConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Configuration extends PluginConfigure {

    private final Yasui plugin;

    @Serializable
    public String language = "en_US";
    @Serializable
    public boolean enable = true;
    @Serializable
    public int scan_interval_tick = 20 * 30;
    @Serializable(name = "profiler.listen_event")
    public boolean profiler_listen_event = true;
    @Serializable(name = "profiler.event_chunk_count")
    public int profiler_event_chunk_count = 10;
    @Serializable(name = "profiler.entity_chunk_count")
    public int profiler_entity_chunk_count = 5;
    @Serializable
    public int top_listing = 10;
    @Serializable(name = "broadcast", manualSerialization = true)
    public BroadcastConfig broadcast = new BroadcastConfig();
    @StandaloneConfig
    public RegionConfig regionConfig;
    public Map<String, Rule> rules = new HashMap<>();
    public Map<String, Operation> operations = new HashMap<>();
    public EnumSet<ModuleType> enabledModules;
    @Serializable(name = "modules")
    private List<String> _modules;

    public Configuration(Yasui plugin) {
        this.plugin = plugin;
        regionConfig = new RegionConfig(plugin);
    }

    @Override
    protected JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    public void deserialize(ConfigurationSection config) {
        ISerializable.deserialize(config, this);
        if (config.isString("broadcast")) {
            String type = config.getString("broadcast");
            config.set("broadcast", null);
            config.set("broadcast.type", type);
        }
        if(config.isConfigurationSection("broadcast")) {
            broadcast.deserialize(config.getConfigurationSection("broadcast"));
        }
        if (_modules == null) {
            _modules = Arrays.stream(ModuleType.values()).map(Enum::name).collect(Collectors.toList());
            _modules.remove(ModuleType.adjust_view_distance.name());
            saveExample();
        }
        enabledModules = Utils.toEnumSet(ModuleType.class, _modules);
        if (!Yasui.isPaper) {
            _modules.remove(ModuleType.adjust_view_distance.name());
            enabledModules.remove(ModuleType.adjust_view_distance);
        }
        File rulesDir = new File(plugin.getDataFolder(), "rules");
        if (!rulesDir.exists()) {
            rulesDir.mkdirs();
        }
        for (File file : rulesDir.listFiles(pathname -> pathname != null && pathname.isFile() && pathname.getName().endsWith(".yml"))) {
            String ruleName = file.getName().substring(0, file.getName().length() - 4);
            rules.put(ruleName, new Rule(plugin, rulesDir, file.getName()));
        }
        File operationsDir = new File(plugin.getDataFolder(), "operations");
        if (!operationsDir.exists()) {
            operationsDir.mkdirs();
        }
        for (String ruleName : rules.keySet()) {
            Rule r = rules.get(ruleName);
            if (r.operations != null) {
                for (String name : r.operations) {
                    if (operations.containsKey(name)) {
                        continue;
                    }
                    File file = new File(operationsDir, name + ".yml");
                    if (file.isFile()) {
                        operations.put(name, new Operation(plugin, operationsDir, name + ".yml"));
                    } else {
                        plugin.getLogger().warning("operation file not exist: " + file.toURI());
                    }
                }
            }
        }
    }

    @Override
    public void serialize(ConfigurationSection config) {
        ISerializable.serialize(config, this);
        config.set("broadcast", null);
        broadcast.serialize(config.createSection("broadcast"));
    }

    public void saveExample() {
        try {
            CodeSource codeSource = Yasui.class.getProtectionDomain().getCodeSource();
            URL url = codeSource.getLocation();
            ZipInputStream zip = new ZipInputStream(url.openStream());
            ZipEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
                String entryName = zipEntry.getName();
                if ((entryName.startsWith("rules/") || entryName.startsWith("operations")) && entryName.endsWith(".yml")) {
                    plugin.saveResource(entryName, false);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Region getRegion(ChunkCoordinate id) {
        Region region = null;
        for (Region v : regionConfig.regions.values()) {
            if (v.enabled && v.contains(id)) {
                if (region == null || !v.defaultRegion) {
                    region = v;
                    if (!region.defaultRegion) {
                        break;
                    }
                }
            }
        }
        if (region == null) {
            region = getDefaultRegion(Bukkit.getWorld(id.getWorld()));
        }
        return region;
    }

    public Region getDefaultRegion(World world) {
        Region region = regionConfig.regions.get(world.getName());
        if (region == null || !region.defaultRegion) {
            region = new Region(world);
            regionConfig.regions.put(world.getName(), region);
        }
        return region;
    }
}
