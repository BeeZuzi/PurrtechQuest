package eu.purrtech.purrtechQuest.storage;

import eu.purrtech.purrtechQuest.model.QuestCategoryConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryConfigRepositoryTest {

    @Test
    void unconfiguredCategoryFallsBackToDefaults(@TempDir Path tempDir) {
        CategoryConfigRepository repository = new CategoryConfigRepository(tempDir.resolve("categories.yml"));
        QuestCategoryConfig config = repository.get("mining");
        assertEquals(QuestCategoryConfig.defaults("mining"), config);
        assertTrue(config.showActiveQuest());
        assertTrue(config.showProgress());
        assertTrue(config.showDescription());
        assertEquals("", config.description());
    }

    @Test
    void savedConfigSurvivesAFreshRepositoryInstance(@TempDir Path tempDir) {
        Path file = tempDir.resolve("categories.yml");
        CategoryConfigRepository first = new CategoryConfigRepository(file);
        first.save(new QuestCategoryConfig("mining", "Questy o těžbě.", true, false, true));

        CategoryConfigRepository reloaded = new CategoryConfigRepository(file);
        QuestCategoryConfig config = reloaded.get("mining");
        assertEquals("Questy o těžbě.", config.description());
        assertTrue(config.showActiveQuest());
        assertTrue(!config.showProgress());
        assertTrue(config.showDescription());
    }

    @Test
    void savingOneCategoryDoesNotAffectAnother(@TempDir Path tempDir) {
        Path file = tempDir.resolve("categories.yml");
        CategoryConfigRepository repository = new CategoryConfigRepository(file);
        repository.save(new QuestCategoryConfig("mining", "Mining stuff", true, true, true));
        repository.save(new QuestCategoryConfig("combat", "Combat stuff", false, false, false));

        assertEquals("Mining stuff", repository.get("mining").description());
        assertEquals("Combat stuff", repository.get("combat").description());
        assertEquals(QuestCategoryConfig.defaults("unrelated"), repository.get("unrelated"));
    }
}
