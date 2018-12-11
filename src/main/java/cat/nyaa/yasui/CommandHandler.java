package cat.nyaa.yasui;


import cat.nyaa.nyaacore.CommandReceiver;
import cat.nyaa.nyaacore.LanguageRepository;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

public class CommandHandler extends CommandReceiver {
    private final Yasui plugin;

    public CommandHandler(Yasui plugin, LanguageRepository i18n) {
        super(plugin, i18n);
        this.plugin = plugin;
    }

    public String getHelpPrefix() {
        return "";
    }

    @SubCommand(value = "status", permission = "yasui.admin")
    public void commandStatus(CommandSender sender, Arguments args) {
        msg(sender, "user.status.line_0");
        for (World world : plugin.getServer().getWorlds()) {
            msg(sender, "user.status.line_1", world.getName(), world.getLivingEntities().size(),
                    plugin.disableAIWorlds.contains(world.getName()) ? "YES" : "NO", world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED),
                    plugin.entityLimitWorlds.contains(world.getName()) ? "YES" : "NO");
        }
    }

    @SubCommand(value = "debug", permission = "yasui.admin")
    public void commandDebug(CommandSender sender, Arguments args) {
        if (args.length() >= 4) {
            World world = Bukkit.getWorld(args.nextString());
            if (args.nextBoolean()) {
                plugin.disableAIWorlds.add(world.getName());
            } else {
                plugin.disableAIWorlds.remove(world.getName());
            }
            if (args.nextBoolean()) {
                plugin.entityLimitWorlds.add(world.getName());
            } else {
                plugin.entityLimitWorlds.remove(world.getName());
            }
            Utils.checkWorld(world);
        }
    }

    @SubCommand(value = "reload", permission = "yasui.admin")
    public void commandReload(CommandSender sender, Arguments args) {
        plugin.reload();
    }
}
