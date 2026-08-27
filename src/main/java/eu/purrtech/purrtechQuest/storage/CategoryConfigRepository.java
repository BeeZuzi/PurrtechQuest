package eu.purrtech.purrtechQuest.storage;

import eu.purrtech.purrtechQuest.model.QuestCategoryConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single flat {@code categories.yml} holding every category's {@link QuestCategoryConfig}, one top-level
 * section per category id. Unlike quests there's no "one file per entry" split — category configs are tiny
 * and few, so a single file (loaded once, rewritten whole on every save) is simpler without a real downside.
 * A category id with no section here just hasn't been configured yet — {@link #get} falls back to
 * {@link QuestCategoryConfig#defaults}, so nothing needs to exist on disk for the feature to work out of
 * the box.
 */
public final class CategoryConfigRepository {

    private final Path file;
    private final Map<String, QuestCategoryConfig> configs = new LinkedHashMap<>();

    public CategoryConfigRepository(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            configs.put(id, new QuestCategoryConfig(id,
                    section.getString("description", ""),
                    section.getBoolean("show-active-quest", true),
                    section.getBoolean("show-progress", true),
                    section.getBoolean("show-description", true),
                    section.isSet("slot") ? section.getInt("slot") : null,
                    section.getString("icon", "CHEST")));
        }
    }

    public QuestCategoryConfig get(String categoryId) {
        return configs.getOrDefault(categoryId, QuestCategoryConfig.defaults(categoryId));
    }

    /**
     * Saves {@code config}. If {@code config.slot()} is set and some *other* already-configured category
     * currently sits on that same slot, the two swap — that other category takes whatever slot
     * {@code config.id()} previously had (possibly none) — rather than the new save silently colliding with
     * it. Two categories landing on the same slot only ever happens here as a momentary in-between state,
     * never as something both end up keeping.
     */
    public void save(QuestCategoryConfig config) {
        Integer newSlot = config.slot();
        if (newSlot != null) {
            Integer previousSlot = get(config.id()).slot();
            for (QuestCategoryConfig other : new ArrayList<>(configs.values())) {
                if (!other.id().equals(config.id()) && newSlot.equals(other.slot())) {
                    configs.put(other.id(), withSlot(other, previousSlot));
                    break;
                }
            }
        }
        configs.put(config.id(), config);
        persist();
    }

    private static QuestCategoryConfig withSlot(QuestCategoryConfig config, Integer slot) {
        return new QuestCategoryConfig(config.id(), config.description(), config.showActiveQuest(),
                config.showProgress(), config.showDescription(), slot, config.icon());
    }

    private void persist() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (QuestCategoryConfig config : configs.values()) {
            yaml.set(config.id() + ".description", config.description());
            yaml.set(config.id() + ".show-active-quest", config.showActiveQuest());
            yaml.set(config.id() + ".show-progress", config.showProgress());
            yaml.set(config.id() + ".show-description", config.showDescription());
            yaml.set(config.id() + ".icon", config.icon());
            if (config.slot() != null) {
                yaml.set(config.id() + ".slot", config.slot());
            }
        }
        try {
            Files.createDirectories(file.getParent());
            yaml.save(file.toFile());
        } catch (IOException e) {
            throw new QuestStorageException("Could not save category config to " + file, e);
        }
    }
}
