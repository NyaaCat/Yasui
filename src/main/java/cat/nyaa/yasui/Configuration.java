package cat.nyaa.yasui;

import cat.nyaa.nyaacore.configuration.ISerializable;
import cat.nyaa.nyaacore.configuration.PluginConfigure;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.config.Rule;
import cat.nyaa.yasui.other.BroadcastType;
import cat.nyaa.yasui.other.ModuleType;
import cat.nyaa.yasui.other.Utils;
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
    @Serializable
    public BroadcastType broadcast = BroadcastType.CHAT;
    public Map<String, Rule> rules = new HashMap<>();
    public Map<String, Operation> operations = new HashMap<>();
    public EnumSet<ModuleType> enabledModules;
    @Serializable(name = "modules")
    private List<String> _modules;

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
        if (_modules == null) {
            _modules = Arrays.stream(ModuleType.values()).map(Enum::name).collect(Collectors.toList());
            saveExample();
        }
        enabledModules = Utils.toEnumSet(ModuleType.class, _modules);
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
}
