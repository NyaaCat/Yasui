package cat.nyaa.yasui;


import cat.nyaa.nyaacore.CommandReceiver;
import cat.nyaa.nyaacore.LanguageRepository;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

public class CommandHandler extends CommandReceiver<Main> {
    private final Main plugin;

    public CommandHandler(Main plugin, LanguageRepository i18n) {
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
                    plugin.disableAIWorlds.contains(world.getName()) ? "YES" : "NO");
        }
    }

    @SubCommand(value = "debug", permission = "yasui.admin")
    public void commandDebug(CommandSender sender, Arguments args) {
        if (args.length() == 2) {
            String s = args.next();
            if (s.equalsIgnoreCase("disableAI")) {
                plugin.disableAI();
            } else if (s.equalsIgnoreCase("enableAI")) {
                plugin.enableAI();
            }
        }
    }

    @SubCommand(value = "reload", permission = "yasui.admin")
    public void commandReload(CommandSender sender, Arguments args) {
        plugin.reload();
    }
}
