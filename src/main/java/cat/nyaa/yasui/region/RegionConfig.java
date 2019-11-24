package cat.nyaa.yasui.region;

import cat.nyaa.nyaacore.configuration.FileConfigure;
import cat.nyaa.nyaacore.configuration.ISerializable;
import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.other.ChunkCoordinate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class RegionConfig extends FileConfigure {
    private final Yasui plugin;
    public HashMap<String, Region> regions = new HashMap<>();
    public LoadingCache<ChunkCoordinate, Region> cache = CacheBuilder.newBuilder()
            .maximumSize(10240)
            .expireAfterAccess(900, TimeUnit.SECONDS)
            .build(new CacheLoader<ChunkCoordinate, Region>() {
                public Region load(ChunkCoordinate id) {
                    Region region = null;
                    for (Region v : plugin.config.regionConfig.regions.values()) {
                        if (v.enabled && v.contains(id)) {
                            if (region == null || !v.defaultRegion) {
                                region = v;
                                if (!region.defaultRegion) {
                                    break;
                                }
                            }
                        }
                    }
                    return region;
                }
            });

    public RegionConfig(Yasui pl) {
        this.plugin = pl;
    }

    @Override
    protected String getFileName() {
        return "region.yml";
    }

    @Override
    protected JavaPlugin getPlugin() {
        return this.plugin;
    }

    @Override
    public void deserialize(ConfigurationSection config) {
        regions.clear();
        ISerializable.deserialize(config, this);
        if (config.isConfigurationSection("regions")) {
            ConfigurationSection list = config.getConfigurationSection("regions");
            for (String k : list.getKeys(false)) {
                Region region = new Region();
                region.deserialize(list.getConfigurationSection(k));
                regions.put(region.name, region);
            }
        }
    }

    @Override
    public void serialize(ConfigurationSection config) {
        ISerializable.serialize(config, this);
        config.set("regions", null);
        if (!regions.isEmpty()) {
            ConfigurationSection list = config.createSection("regions");
            for (String k : regions.keySet()) {
                Region r = regions.get(k);
                if (!r.defaultRegion) {
                    r.serialize(list.createSection(k));
                }
            }
        }
    }
}
