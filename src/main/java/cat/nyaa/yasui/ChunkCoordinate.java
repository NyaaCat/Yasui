package cat.nyaa.yasui;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

class ChunkCoordinate {

    private final int x;

    private final int z;

    private final String world;

    private ChunkCoordinate(World world, int x, int z) {
        this.x = x;
        this.z = z;
        this.world = world.getName();
    }

    public static ChunkCoordinate of(World world, int x, int z) {
        return new ChunkCoordinate(world, x, z);
    }

    public static ChunkCoordinate of(Block b) {
        return new ChunkCoordinate(b.getWorld(), b.getX() >> 4, b.getZ() >> 4);
    }

    public static ChunkCoordinate of(Entity b) {
        return new ChunkCoordinate(b.getWorld(), b.getLocation().getBlockX() >> 4, b.getLocation().getBlockZ() >> 4);
    }

    public static ChunkCoordinate of(Chunk chunk) {
        return new ChunkCoordinate(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public ChunkCoordinate add(ChunkCoordinate another) {
        return new ChunkCoordinate(Bukkit.getWorld(world), x + another.x, z + another.z);
    }

    @Override
    public int hashCode() {
        return world.hashCode() + (x << 16 + x >> 16) ^ z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ChunkCoordinate) {
            ChunkCoordinate pair = (ChunkCoordinate) o;
            return (pair.x == x) && (pair.z == z) && world.equals(pair.world);
        }
        return false;
    }

    @Override
    public String toString() {
        return world + " Chunk " + x + ", " + z;
    }

    public BaseComponent getComponent() {
        TextComponent component = new TextComponent(I18n.format("user.chunk.coord", x, z));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[]{new TextComponent(I18n.format("user.chunk.hover", x << 4, (x << 4) + 16, z << 4, (z << 4) + 16))}));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, I18n.format("user.chunk.tp", (x << 4) + 8, (z << 4) + 8)));
        return component;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public String getWorld() {
        return world;
    }
}
