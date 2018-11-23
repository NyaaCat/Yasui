package cat.nyaa.yasui;

import cat.nyaa.nyaacore.configuration.ISerializable;

import java.util.List;

public class Rule implements ISerializable {
    @Serializable
    public boolean enable;
    @Serializable
    public String condition = "";
    @Serializable
    public List<String> worlds;
    @Serializable
    public String enable_ai;
    @Serializable
    public String disable_ai;
    @Serializable
    public String world_random_tick_speed;
}
