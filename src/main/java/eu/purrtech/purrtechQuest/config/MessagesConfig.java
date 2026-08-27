package eu.purrtech.purrtechQuest.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@code lang/<locale>.yml} files (extracted from the jar on first run, then editable by admins)
 * and resolves message keys per player locale, falling back to the configured default locale. Only
 * {@link #BUNDLED_LOCALES} ship inside the jar, but that's not a hard limit on what a server can offer its
 * players — any extra {@code lang/<code>.yml} an admin drops in gets picked up automatically (see
 * {@link #loadAdditionalLocales}), it just has no bundled resource to self-heal missing/stale keys against.
 */
public final class MessagesConfig {

    private static final String[] BUNDLED_LOCALES = {"cs", "en"};

    /**
     * Keys whose bundled default changed *shape* (not just wording) after they'd already shipped — the
     * objective label feature swapped {@code %type%}/{@code %target%} for {@code %label%} in these, but the
     * keys themselves already existed, so {@link #mergeMissingKeys} never touches them (it only fills in
     * keys missing entirely). Without this, an on-disk file from before that change keeps the old template
     * forever, and since the code no longer supplies {@code %type%}/{@code %target%}, those tokens render
     * as literal unmatched text instead of being substituted. An on-disk value only gets upgraded if it
     * still matches the exact old default below — any admin customization, even a small tweak, is left
     * alone, same as everywhere else in this file.
     * <p>
     * The {@code guide-*}/{@code gui-category-title} entries under {@code "en"} cover a two-step history on
     * those specific keys: the guide item's English text was briefly forced to the same small-caps Czech
     * text {@code cs.yml} uses (and the category title to a resourcepack-specific font-glyph token), then
     * reverted back to normal localized English once that turned out to fight against players just wanting
     * their own client locale respected like every other message in this plugin. A server that had already
     * picked up the Czech-forced values via this same self-healing mechanism needs this second entry to
     * come back — its on-disk {@code en.yml} has the Czech text sitting in it, not the original English, so
     * the plain "old English -> new English" pairing below wouldn't match it.
     */
    private static final Map<String, Map<String, String>> STALE_DEFAULTS = Map.of(
            "en", Map.ofEntries(
                    Map.entry("quest.info-objective", "<gray>- %type% %target%: %progress%/%amount%</gray>"),
                    Map.entry("quest.info-objective-choice", "<yellow>- (choice) %type% %target%: %progress%/%amount%</yellow>"),
                    Map.entry("quest.info-objective-locked", "<dark_gray>- %type% %target% (unavailable, you completed a different choice)</dark_gray>"),
                    Map.entry("quest.tracking-line", "<yellow>» %quest%: %target% %progress%/%amount%</yellow>"),
                    Map.entry("quest.gui-lore-objective-line", "<gray> - %type% %target%: %progress%/%amount%</gray>"),
                    Map.entry("quest.gui-lore-objective-choice-line", "<yellow> - (choice) %type% %target%: %progress%/%amount%</yellow>"),
                    Map.entry("quest.gui-lore-objective-locked", "<dark_gray> - %type% %target% (unavailable, a different choice was completed)</dark_gray>"),
                    Map.entry("quest.guide-name", "<#F69B45><b>ᴘʀůᴠᴏᴅᴄᴇ</b></#F69B45>"),
                    Map.entry("quest.guide-intro", "<gray><i>ᴘʀᴏᴠᴇᴅᴇ ᴛě ᴘᴏꜱᴛᴜᴘɴě ꜱᴇʀᴠᴇʀᴇᴍ</i></gray>"),
                    Map.entry("quest.guide-completed", "<#F69B45><b>●</b></#F69B45> <white>ꜱᴘʟɴěɴýᴄʜ Qᴜᴇꜱᴛů: <#F8AF69>%done%</#F8AF69><dark_gray>/</dark_gray><#F69B45>%total%</#F69B45></white>"),
                    Map.entry("quest.guide-current-quest-header", "<#F69B45><b>ᴀᴋᴛᴜáʟɴí úᴋᴏʟ:</b></#F69B45>"),
                    Map.entry("quest.guide-no-active-quest", "<#F69B45><b>●</b></#F69B45>  <gray>Žádný aktivní quest</gray>"),
                    Map.entry("quest.guide-progress-header", "<#F69B45><b>●</b></#F69B45>  <white>ᴘᴏsᴛᴜᴘ úᴋᴏʟᴜ:</white>"),
                    Map.entry("quest.gui-lore-guide-hint", "<dark_gray>Klikni pro zobrazení všech questů.</dark_gray>"),
                    Map.entry("quest.gui-category-title", "<white>:offset_-8::ukoly:</white>")),
            "cs", Map.ofEntries(
                    Map.entry("quest.info-objective", "<gray>- %type% %target%: %progress%/%amount%</gray>"),
                    Map.entry("quest.info-objective-choice", "<yellow>- (volba) %type% %target%: %progress%/%amount%</yellow>"),
                    Map.entry("quest.info-objective-locked", "<dark_gray>- %type% %target% (nedostupné, splnil jsi jinou volbu)</dark_gray>"),
                    Map.entry("quest.gui-category-title", "Kategorie questů"),
                    Map.entry("quest.tracking-line", "<yellow>» %quest%: %target% %progress%/%amount%</yellow>"),
                    Map.entry("quest.gui-lore-objective-line", "<gray> - %type% %target%: %progress%/%amount%</gray>"),
                    Map.entry("quest.gui-lore-objective-choice-line", "<yellow> - (volba) %type% %target%: %progress%/%amount%</yellow>"),
                    Map.entry("quest.gui-lore-objective-locked", "<dark_gray> - %type% %target% (nedostupné, splněna jiná volba)</dark_gray>")));

    private final Map<String, YamlConfiguration> byLocale = new HashMap<>();
    private final String defaultLocale;

    private MessagesConfig(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public static MessagesConfig load(JavaPlugin plugin, String defaultLocale) {
        MessagesConfig messages = new MessagesConfig(defaultLocale);
        for (String locale : BUNDLED_LOCALES) {
            messages.loadLocale(plugin, locale);
        }
        messages.loadAdditionalLocales(plugin);
        return messages;
    }

    /**
     * Anything in {@code lang/} beyond the two bundled locales — a server offering, say, German to its
     * players just needs a {@code lang/de.yml} dropped into the plugin's data folder, no plugin update or
     * code change required. There's no bundled {@code lang/de.yml} inside the jar to compare against, so
     * unlike {@link #loadLocale}'s bundled locales, these get no self-healing merge/migration; they're
     * loaded exactly as the admin wrote them.
     */
    private void loadAdditionalLocales(JavaPlugin plugin) {
        File langDir = new File(plugin.getDataFolder(), "lang");
        File[] files = langDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String fileName = file.getName();
            String locale = fileName.substring(0, fileName.length() - ".yml".length()).toLowerCase(Locale.ROOT);
            if (byLocale.containsKey(locale)) {
                continue;
            }
            byLocale.put(locale, YamlConfiguration.loadConfiguration(file));
        }
    }

    private void loadLocale(JavaPlugin plugin, String locale) {
        String resourcePath = "lang/" + locale + ".yml";
        File file = new File(plugin.getDataFolder(), resourcePath);
        boolean isNewFile = !file.exists();
        if (isNewFile) {
            file.getParentFile().mkdirs();
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not extract default language file " + resourcePath + ": " + e.getMessage());
            }
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!isNewFile) {
            mergeMissingKeys(plugin, resourcePath, file, config);
            migrateStaleDefaults(plugin, resourcePath, locale, file, config);
        }
        byLocale.put(locale, config);
    }

    /**
     * A file already on disk (from a previous plugin version) never picks up keys that got added to the
     * bundled resource later — {@link YamlConfiguration#getString} would just fall back to returning the
     * raw key as literal text, which is exactly as confusing in-game as it sounds. This fills in only the
     * keys missing from the on-disk file, leaving every key an admin already edited untouched, then
     * persists the result so this only has to run once per new key.
     */
    private void mergeMissingKeys(JavaPlugin plugin, String resourcePath, File file, YamlConfiguration config) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return;
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : bundled.getKeys(true)) {
                if (bundled.isConfigurationSection(key) || config.isSet(key)) {
                    continue;
                }
                config.set(key, bundled.get(key));
                changed = true;
            }
            if (changed) {
                config.save(file);
                plugin.getLogger().info("Added new translation keys to " + resourcePath);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not merge new translation keys into " + resourcePath + ": " + e.getMessage());
        }
    }

    /** See {@link #STALE_DEFAULTS}. Runs after {@link #mergeMissingKeys} on every locale that has known stale keys. */
    private void migrateStaleDefaults(JavaPlugin plugin, String resourcePath, String locale, File file, YamlConfiguration config) {
        Map<String, String> staleDefaults = STALE_DEFAULTS.get(locale);
        if (staleDefaults == null) {
            return;
        }
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return;
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (Map.Entry<String, String> entry : staleDefaults.entrySet()) {
                String key = entry.getKey();
                String oldDefault = entry.getValue();
                if (bundled.isSet(key) && oldDefault.equals(config.getString(key))) {
                    config.set(key, bundled.get(key));
                    changed = true;
                }
            }
            if (changed) {
                config.save(file);
                plugin.getLogger().info("Upgraded outdated translation defaults in " + resourcePath);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not upgrade outdated translation defaults in " + resourcePath + ": " + e.getMessage());
        }
    }

    public String get(String key, String locale) {
        String normalized = locale == null ? defaultLocale : locale.toLowerCase(Locale.ROOT);
        YamlConfiguration config = byLocale.getOrDefault(normalized, byLocale.get(defaultLocale));
        if (config == null) {
            return key;
        }
        return config.getString(key, key);
    }

    /**
     * Resolves a message for the given player's client locale, substitutes placeholders, and parses the
     * result as MiniMessage. This is the one place message text turns into something you can
     * {@code player.sendMessage(...)} — callers never touch MiniMessage directly.
     */
    public Component render(String key, Player player, Map<String, String> placeholders) {
        return render(key, player.locale().getLanguage(), placeholders);
    }

    /**
     * Same as {@link #render(String, Player, Map)} but for callers with no player at all (console senders)
     * — uses the configured default locale.
     */
    public Component render(String key, Map<String, String> placeholders) {
        return render(key, (String) null, placeholders);
    }

    /**
     * Same as {@link #render(String, Player, Map)} but for callers with no player locale to key off of
     * (console senders) — uses the configured default locale.
     */
    public Component render(String key, String locale, Map<String, String> placeholders) {
        String raw = get(key, locale);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace(entry.getKey(), entry.getValue());
        }
        return MiniMessage.miniMessage().deserialize(raw);
    }
}
