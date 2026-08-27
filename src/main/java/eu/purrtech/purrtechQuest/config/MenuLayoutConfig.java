package eu.purrtech.purrtechQuest.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads {@code menus.yml} — the size and button placement of every GUI screen, for admins/config-technicians
 * who want to rearrange a menu without touching Java. Extracted from the jar on first run
 * ({@code plugins/PurrtechQuest/menus.yml}), then editable like any other config file; missing menus/slots
 * get filled in from the bundled defaults on next load, the same self-healing behavior
 * {@link MessagesConfig} already has for {@code lang/*.yml}.
 * <p>
 * Every {@code Gui} screen still has a hardcoded fallback for its own size and every one of its buttons —
 * this file only ever <em>overrides</em> those defaults, and any override that doesn't make sense (a size
 * that isn't a whole number of rows, a slot outside that size) is rejected in favor of the built-in default
 * rather than producing a broken or throwing GUI. Two buttons landing on the same custom slot isn't
 * rejected the same way (there's no single "correct" resolution), but is logged as a warning at startup so
 * a technician notices instead of wondering why a button vanished.
 * <p>
 * {@link #reload()} re-reads the file from disk in place, so a technician's edit — a moved button, a
 * resized menu, {@link #fillEmptySlots()} toggled off — takes effect on the next {@code /questadmin reload}
 * rather than needing a full server restart. Every {@code Gui} already holds a reference to the single
 * shared instance of this class (see {@code PurrtechQuest}'s field), so reloading in place is enough; no
 * screen needs to be told about it separately.
 */
public final class MenuLayoutConfig {

    private static final String RESOURCE_PATH = "menus.yml";

    private final JavaPlugin plugin;
    private final Map<String, Menu> menus = new HashMap<>();
    private boolean fillEmptySlots = true;

    private record Menu(int size, Map<String, Integer> slots) {
    }

    private MenuLayoutConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static MenuLayoutConfig load(JavaPlugin plugin) {
        MenuLayoutConfig config = new MenuLayoutConfig(plugin);
        config.reload();
        return config;
    }

    /** Re-reads {@code menus.yml} from disk, discarding whatever was previously loaded. See the class javadoc. */
    public void reload() {
        menus.clear();
        File file = new File(plugin.getDataFolder(), RESOURCE_PATH);
        boolean isNewFile = !file.exists();
        if (isNewFile) {
            try {
                file.getParentFile().mkdirs();
                try (InputStream in = plugin.getResource(RESOURCE_PATH)) {
                    if (in != null) {
                        Files.copy(in, file.toPath());
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not extract default " + RESOURCE_PATH + ": " + e.getMessage());
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!isNewFile) {
            mergeMissingKeys(file, config);
        }

        this.fillEmptySlots = config.getBoolean("fill-empty-slots", true);

        for (String menuId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(menuId);
            if (section == null) {
                continue;
            }
            int size = section.getInt("size", -1);
            Map<String, Integer> slots = new HashMap<>();
            ConfigurationSection slotsSection = section.getConfigurationSection("slots");
            if (slotsSection != null) {
                for (String buttonId : slotsSection.getKeys(false)) {
                    slots.put(buttonId, slotsSection.getInt(buttonId));
                }
            }
            menus.put(menuId, new Menu(size, slots));
            warnAboutCollisions(menuId, slots);
        }
    }

    /** Same "fill in what's missing, never touch what's already there" behavior as {@code MessagesConfig}. */
    private void mergeMissingKeys(File file, YamlConfiguration config) {
        try (InputStream in = plugin.getResource(RESOURCE_PATH)) {
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
                plugin.getLogger().info("Added new menu layout defaults to " + RESOURCE_PATH);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not merge new menu layout defaults into " + RESOURCE_PATH + ": " + e.getMessage());
        }
    }

    private void warnAboutCollisions(String menuId, Map<String, Integer> slots) {
        Map<Integer, String> seen = new HashMap<>();
        for (Map.Entry<String, Integer> entry : slots.entrySet()) {
            String previous = seen.putIfAbsent(entry.getValue(), entry.getKey());
            if (previous != null) {
                plugin.getLogger().warning("menus.yml: '" + menuId + "' has both '" + previous + "' and '"
                        + entry.getKey() + "' on slot " + entry.getValue() + " — one will silently overwrite the other.");
            }
        }
    }

    /** The inventory size (in slots — a multiple of 9, up to 54) for {@code menuId}, or {@code fallback} if unset/invalid. */
    public int resolveSize(String menuId, int fallback) {
        Menu menu = menus.get(menuId);
        if (menu == null || menu.size() <= 0 || menu.size() % 9 != 0 || menu.size() > 54) {
            return fallback;
        }
        return menu.size();
    }

    /**
     * The slot for {@code buttonId} within {@code menuId}, or {@code fallback} if unset or outside
     * {@code resolvedSize} (the value this same menu's {@link #resolveSize} call already returned).
     */
    public int resolveSlot(String menuId, String buttonId, int fallback, int resolvedSize) {
        Menu menu = menus.get(menuId);
        if (menu == null) {
            return fallback;
        }
        Integer slot = menu.slots().get(buttonId);
        if (slot == null || slot < 0 || slot >= resolvedSize) {
            return fallback;
        }
        return slot;
    }

    /**
     * Whether a screen's otherwise-empty slots should get the gray-glass-pane filler item, top-level
     * {@code fill-empty-slots} in {@code menus.yml} (default {@code true}, matching every screen's behavior
     * before this existed). Only covers actual leftover empty space — a slot a screen deliberately leaves
     * blank because a specific button doesn't apply right now (e.g. an objective type with no editable
     * amount) is unaffected either way.
     */
    public boolean fillEmptySlots() {
        return fillEmptySlots;
    }
}
