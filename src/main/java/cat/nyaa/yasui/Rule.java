package cat.nyaa.yasui;

import cat.nyaa.nyaacore.Message;
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
    @Serializable
    public Message.MessageType messageType;
    @Serializable
    public String message;
    @Serializable(name = "entity_limit.enable")
    public String entity_limit_enable;
    @Serializable(name = "entity_limit.disable")
    public String entity_limit_disable;
    @Serializable
    public List<String> commands;
}
