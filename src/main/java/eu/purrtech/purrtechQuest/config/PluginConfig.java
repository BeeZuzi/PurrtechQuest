package eu.purrtech.purrtechQuest.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginConfig {

    private final StorageType storageType;
    private final String sqliteFileName;
    private final MySqlSettings mysql;
    private final String defaultLocale;
    private final String questsDirectoryName;
    private final TrackingDisplay trackingDisplay;
    private final boolean debug;
    private final boolean guideEnabled;

    private PluginConfig(StorageType storageType, String sqliteFileName, MySqlSettings mysql, String defaultLocale,
                          String questsDirectoryName, TrackingDisplay trackingDisplay, boolean debug,
                          boolean guideEnabled) {
        this.storageType = storageType;
        this.sqliteFileName = sqliteFileName;
        this.mysql = mysql;
        this.defaultLocale = defaultLocale;
        this.questsDirectoryName = questsDirectoryName;
        this.trackingDisplay = trackingDisplay;
        this.debug = debug;
        this.guideEnabled = guideEnabled;
    }

    public static PluginConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        StorageType storageType = StorageType.valueOf(
                config.getString("storage.type", "SQLITE").toUpperCase());
        String sqliteFileName = config.getString("storage.sqlite-file", "data.db");
        MySqlSettings mysql = new MySqlSettings(
                config.getString("storage.mysql.host", "localhost"),
                config.getInt("storage.mysql.port", 3306),
                config.getString("storage.mysql.database", "purrtechquest"),
                config.getString("storage.mysql.username", "purrtechquest"),
                config.getString("storage.mysql.password", ""),
                Math.max(1, config.getInt("storage.mysql.pool-size", 10)),
                config.getBoolean("storage.mysql.use-ssl", false));
        String defaultLocale = config.getString("default-locale", "en").toLowerCase();
        String questsDirectoryName = config.getString("quests-directory", "quests");
        TrackingDisplay trackingDisplay = TrackingDisplay.valueOf(
                config.getString("tracking.display", "ACTION_BAR").toUpperCase());
        boolean debug = config.getBoolean("debug", false);
        boolean guideEnabled = config.getBoolean("guide.enabled", true);

        return new PluginConfig(storageType, sqliteFileName, mysql, defaultLocale, questsDirectoryName,
                trackingDisplay, debug, guideEnabled);
    }

    public StorageType storageType() {
        return storageType;
    }

    public String sqliteFileName() {
        return sqliteFileName;
    }

    public MySqlSettings mysql() {
        return mysql;
    }

    public String defaultLocale() {
        return defaultLocale;
    }

    public String questsDirectoryName() {
        return questsDirectoryName;
    }

    public TrackingDisplay trackingDisplay() {
        return trackingDisplay;
    }

    public boolean debug() {
        return debug;
    }

    public boolean guideEnabled() {
        return guideEnabled;
    }
}
