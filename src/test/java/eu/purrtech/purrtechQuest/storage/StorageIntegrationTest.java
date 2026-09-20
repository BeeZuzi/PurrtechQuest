package eu.purrtech.purrtechQuest.storage;

import eu.purrtech.purrtechQuest.model.NpcProviderType;
import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.model.PlayerQuestData;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestGiverRef;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.model.QuestReward;
import eu.purrtech.purrtechQuest.model.QuestRewardTier;
import eu.purrtech.purrtechQuest.model.QuestStatus;
import eu.purrtech.purrtechQuest.storage.migration.SchemaMigrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check of the Phase 0 storage layer against a real SQLite file and real YAML files —
 * no mocks. This is what actually exercises HikariCP, the schema migration, and the quest-giver
 * mapping, since {@code ./gradlew runServer} currently can't run on Gradle 9 (unrelated run-paper
 * 3.0.2 incompatibility, tracked separately from this plugin's code).
 */
class StorageIntegrationTest {

    @Test
    void migratesSchemaAndRoundTripsPlayerProgress(@TempDir Path tempDir) throws Exception {
        try (DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("test.db"))) {
            SchemaMigrator.migrate(databaseManager.dataSource());

            PlayerDataRepository repository = new SqlitePlayerDataRepository(databaseManager.dataSource());
            try {
                UUID playerId = UUID.randomUUID();

                PlayerQuestData written = new PlayerQuestData(playerId);
                QuestProgress progress = new QuestProgress("mine_iron_1", 1);
                progress.status(QuestStatus.IN_PROGRESS);
                progress.incrementObjective(0, 7);
                progress.startedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
                written.put(progress);

                repository.save(written).get(5, TimeUnit.SECONDS);

                PlayerQuestData read = repository.load(playerId).get(5, TimeUnit.SECONDS);
                QuestProgress readProgress = read.progress("mine_iron_1");

                assertNotNull(readProgress);
                assertEquals(QuestStatus.IN_PROGRESS, readProgress.status());
                assertEquals(7, readProgress.objectiveProgress(0));
                assertEquals(progress.startedAt(), readProgress.startedAt());
                assertNull(readProgress.completedAt());
                assertEquals(0, readProgress.timesCompleted());
            } finally {
                repository.close();
            }
        }
    }

    @Test
    void roundTripsQuestDefinitionThroughYaml(@TempDir Path tempDir) {
        QuestDefinitionRepository repository = new YamlQuestDefinitionRepository(
                tempDir.resolve("quests"), Logger.getAnonymousLogger());

        Quest quest = new Quest(
                "mine_iron_1",
                "Sběr železa",
                "Vytěž 10 kusů železné rudy.",
                "mining",
                List.of(new QuestObjective(ObjectiveType.COLLECT_ITEM, "RAW_IRON", 10)),
                List.of(new QuestReward.Money(50.0, "Kapsa mincí"), new QuestReward.Experience(20)),
                List.of(new QuestRewardTier("purrtechquest.rank.vip", "VIP",
                        List.of(new QuestReward.Money(25.0).withName("VIP bonus")))),
                List.of(),
                false,
                0,
                false,
                true,
                new QuestGiverRef(NpcProviderType.CITIZENS, "12")
        );

        repository.save(quest);
        List<Quest> loaded = repository.loadAll();

        assertEquals(1, loaded.size());
        Quest roundTripped = loaded.get(0);
        assertEquals(quest.id(), roundTripped.id());
        assertEquals(quest.displayName(), roundTripped.displayName());
        assertEquals(quest.objectives(), roundTripped.objectives());
        assertEquals(quest.rewards(), roundTripped.rewards());
        assertEquals(quest.rewardTiers(), roundTripped.rewardTiers());
        // Names survive the round trip; a reward saved without one (pre-names quest files) stays unnamed.
        assertEquals("Kapsa mincí", roundTripped.rewards().get(0).name());
        assertEquals(null, roundTripped.rewards().get(1).name());
        assertEquals("VIP bonus", roundTripped.rewardTiers().get(0).rewards().get(0).name());
        assertEquals(quest.questGiver(), roundTripped.questGiver());
        assertTrue(roundTripped.autoTurnIn());
    }
}
