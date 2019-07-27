package cat.nyaa.yasui;

import cat.nyaa.nyaacore.LanguageRepository;
import org.bukkit.plugin.java.JavaPlugin;

public class I18n extends LanguageRepository {
    public static I18n instance = null;
    private final Yasui plugin;
    private String lang = null;

    public I18n(Yasui plugin, String lang) {
        instance = this;
        this.plugin = plugin;
        this.lang = lang;
        load();
    }

    public static String format(String key, Object... args) {
        return instance.getFormatted(key, args);
    }

    @Override
    protected JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    protected String getLanguage() {
        return lang;
    }
}
