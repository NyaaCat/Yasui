package cat.nyaa.yasui;

import cat.nyaa.nyaacore.LanguageRepository;
import org.bukkit.plugin.java.JavaPlugin;
import org.librazy.nyaautils_lang_checker.LangKey;

public class I18n extends LanguageRepository {
    public static I18n instance = null;
    private final Main plugin;
    private String lang = null;

    public I18n(Main plugin, String lang) {
        instance = this;
        this.plugin = plugin;
        this.lang = lang;
        load();
    }

    public static String format(@LangKey String key, Object... args) {
        return instance.get(key, args);
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
