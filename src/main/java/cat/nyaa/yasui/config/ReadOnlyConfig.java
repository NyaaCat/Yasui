package cat.nyaa.yasui.config;

import cat.nyaa.nyaacore.configuration.FileConfigure;
import cat.nyaa.yasui.Yasui;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class ReadOnlyConfig extends FileConfigure {
    private final Yasui plugin;
    public final String filename;
    private final File dir;

    public ReadOnlyConfig(Yasui plugin, File dir, String filename) {
        this.plugin = plugin;
        this.dir = dir;
        this.filename = filename;
    }

    @Override
    protected String getFileName() {
        return filename;
    }

    @Override
    protected JavaPlugin getPlugin() {
        return plugin;
    }

    public void save() {
    }

    public void load() {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(new File(dir, getFileName()));
        } catch (IOException | InvalidConfigurationException ex) {
            throw new RuntimeException(ex);
        }
        deserialize(cfg);
    }
}
