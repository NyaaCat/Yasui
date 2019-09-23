package cat.nyaa.yasui.task;

import cat.nyaa.yasui.Yasui;
import cat.nyaa.yasui.other.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerTask extends BukkitRunnable {
    public static Map<UUID, PlayerTask> taskMap = new HashMap<>();
    private static int delay;
    private UUID id;

    public PlayerTask(UUID uuid) {
        this.id = uuid;
    }

    public static PlayerTask getOrCreateTask(UUID id) {
        PlayerTask task = taskMap.get(id);
        if (task == null) {
            task = new PlayerTask(id);
            if (delay >= Yasui.INSTANCE.config.scan_interval_tick) {
                delay = 1;
            }
            delay += 9;
            task.runTaskTimer(Yasui.INSTANCE, delay + 40, Yasui.INSTANCE.config.scan_interval_tick);
            taskMap.put(id, task);
        }
        return task;
    }

    @Override
    public void run() {
        if (Yasui.isPaper) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isValid()) {
                Utils.updatePlayerViewDistance(player);
            }
        }
    }
}
