package cat.nyaa.yasui.region;

import cat.nyaa.nyaacore.configuration.ISerializable;
import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.config.Operation;
import cat.nyaa.yasui.other.ChunkCoordinate;
import cat.nyaa.yasui.other.ModuleType;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Region implements ISerializable {
    @Serializable
    public boolean enabled = true;
    @Serializable
    public String world;
    @Serializable
    public int minChunkX;
    @Serializable
    public int minChunkZ;
    @Serializable
    public int maxChunkX;
    @Serializable
    public int maxChunkZ;
    @Serializable
    public List<String> bypass = new ArrayList<>();
    @Serializable
    public List<String> enforce = new ArrayList<>();
    @Serializable
    public String name;
    public boolean defaultRegion = false;
    private Map<ModuleType, String> sources = new HashMap<>();
    private Map<ModuleType, Operation> limits = new HashMap<>();
    private Yasui plugin;

    public Region() {
        this.plugin = Yasui.INSTANCE;
    }

    public Region(World world) {
        this.world = world.getName();
        this.name = this.world;
        this.defaultRegion = true;
        this.plugin = Yasui.INSTANCE;
    }

    public Region(Chunk chunk1, Chunk chunk2) {
        world = chunk1.getWorld().getName();
        minChunkX = Math.min(chunk1.getX(), chunk2.getX());
        minChunkZ = Math.min(chunk1.getZ(), chunk2.getZ());
        maxChunkX = Math.max(chunk1.getX(), chunk2.getX());
        maxChunkZ = Math.max(chunk1.getZ(), chunk2.getZ());
        this.plugin = Yasui.INSTANCE;
    }

    public boolean contains(ChunkCoordinate id) {
        if (!world.equalsIgnoreCase(id.getWorld())) {
            return false;
        }
        if (defaultRegion) {
            return true;
        }
        return maxChunkX >= id.getX() && maxChunkZ >= id.getZ() && minChunkX <= id.getX() && minChunkZ <= id.getZ();
    }

    public Operation add(ModuleType type, String ruleName, Operation operation) {
        sources.put(type, ruleName);
        return limits.put(type, operation);
    }

    public Operation remove(ModuleType type) {
        sources.remove(type);
        return limits.remove(type);
    }

    public String getSource(ModuleType type) {
        return sources.get(type);
    }

    public Operation get(ModuleType type) {
        Operation operation = limits.get(type);
        if (!defaultRegion && operation == null) {
            Region w = plugin.config.getDefaultRegion(Bukkit.getWorld(world));
            String source = w.getSource(type);
            if (source != null && bypass.contains(source)) {
                return null;
            }
            operation = w.get(type);
        }
        return operation;
    }

    public Map<ModuleType, Operation> getLimits() {
        if (defaultRegion) {
            return limits;
        }
        Map<ModuleType, Operation> tmp = new HashMap<>();
        for (ModuleType type : ModuleType.values()) {
            tmp.put(type, get(type));
        }
        return tmp;
    }

    public List<ChunkCoordinate> getChunks() {
        List<ChunkCoordinate> list = new ArrayList<>();
        World w = Bukkit.getWorld(world);
        if (w != null) {
            for (int minX = minChunkX; minX <= maxChunkX; minX++) {
                for (int minZ = minChunkZ; minZ <= maxChunkZ; minZ++) {
                    list.add(ChunkCoordinate.of(w, minX, minZ));
                }
            }
        }
        return list;
    }
}
