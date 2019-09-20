package cat.nyaa.yasui.config;

import cat.nyaa.nyaacore.configuration.ISerializable;
import cat.nyaa.yasui.other.BroadcastType;

public class BroadcastConfig implements ISerializable {

    @Serializable
    public BroadcastType type = BroadcastType.CHAT;
    @Serializable
    public boolean log_console = true;
    @Serializable
    public String permission = "yasui.command.status";
}
