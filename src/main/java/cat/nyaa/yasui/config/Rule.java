package cat.nyaa.yasui.config;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.other.BroadcastType;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Rule extends ReadOnlyConfig {
    @Serializable
    public boolean enabled;
    //@Serializable
    public String name;
    @Serializable
    public List<String> operations;
    @Serializable
    public List<String> worlds;

    @Serializable(name = "engage.condition")
    public String engage_condition;
    @Serializable(name = "engage.message")
    public String engage_message;
    @Serializable(name = "engage.broadcast")
    public BroadcastType engage_broadcast;

    @Serializable(name = "release.condition")
    public String release_condition;
    @Serializable(name = "release.message")
    public String release_message;
    @Serializable(name = "release.broadcast")
    public BroadcastType release_broadcast;
    public Map<String, String> lastMessages = new HashMap<>();

    public Rule(Yasui plugin, File dir, String filename) {
        super(plugin, dir, filename);
        load();
        name = filename.substring(0, filename.length() - 4);
    }

    public Rule() {
        super(Yasui.INSTANCE, Yasui.INSTANCE.getDataFolder(), "");
        //load();
    }
}
