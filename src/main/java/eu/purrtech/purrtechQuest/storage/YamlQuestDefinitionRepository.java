package eu.purrtech.purrtechQuest.storage;

import eu.purrtech.purrtechQuest.model.Quest;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * One quest per {@code <id>.yml} file under a directory (typically {@code plugins/PurrtechQuest/quests/}).
 * Human-editable and git-friendly by design — the in-game quest editor (later phase) writes the same format.
 */
public final class YamlQuestDefinitionRepository implements QuestDefinitionRepository {

    private final Path questsDirectory;
    private final Logger logger;

    public YamlQuestDefinitionRepository(Path questsDirectory, Logger logger) {
        this.questsDirectory = questsDirectory;
        this.logger = logger;
        try {
            Files.createDirectories(questsDirectory);
        } catch (IOException e) {
            throw new QuestStorageException("Could not create quests directory: " + questsDirectory, e);
        }
    }

    public Path directory() {
        return questsDirectory;
    }

    @Override
    public List<Quest> loadAll() {
        List<Quest> quests = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(questsDirectory, "*.yml")) {
            for (Path file : stream) {
                try {
                    quests.add(loadFile(file));
                } catch (Exception e) {
                    // A single malformed quest file must not take the whole plugin down on startup.
                    logger.warning("Skipping invalid quest file '" + file.getFileName() + "': " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new QuestStorageException("Could not read quests directory: " + questsDirectory, e);
        }
        return quests;
    }

    private Quest loadFile(Path file) {
        String id = stripExtension(file.getFileName().toString());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        return QuestYamlMapper.fromYaml(id, config);
    }

    @Override
    public void save(Quest quest) {
        YamlConfiguration config = new YamlConfiguration();
        QuestYamlMapper.toYaml(quest, config);
        Path file = questsDirectory.resolve(quest.id() + ".yml");
        try {
            config.save(file.toFile());
        } catch (IOException e) {
            throw new QuestStorageException("Could not save quest '" + quest.id() + "'", e);
        }
    }

    @Override
    public void delete(String questId) {
        Path file = questsDirectory.resolve(questId + ".yml");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new QuestStorageException("Could not delete quest '" + questId + "'", e);
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
