package eu.purrtech.purrtechQuest.storage;

import eu.purrtech.purrtechQuest.model.QuestCategoryConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertNull(config.slot());
        assertEquals("CHEST", config.icon());
    }

    @Test
    void savedConfigSurvivesAFreshRepositoryInstance(@TempDir Path tempDir) {
        Path file = tempDir.resolve("categories.yml");
        CategoryConfigRepository first = new CategoryConfigRepository(file);
        first.save(new QuestCategoryConfig("mining", "Questy o těžbě.", true, false, true, 4, "DIAMOND_PICKAXE"));

        CategoryConfigRepository reloaded = new CategoryConfigRepository(file);
        QuestCategoryConfig config = reloaded.get("mining");
        assertEquals("Questy o těžbě.", config.description());
        assertTrue(config.showActiveQuest());
        assertTrue(!config.showProgress());
        assertTrue(config.showDescription());
        assertEquals(4, config.slot());
        assertEquals("DIAMOND_PICKAXE", config.icon());
    }

    @Test
    void savingOneCategoryDoesNotAffectAnother(@TempDir Path tempDir) {
        Path file = tempDir.resolve("categories.yml");
        CategoryConfigRepository repository = new CategoryConfigRepository(file);
        repository.save(new QuestCategoryConfig("mining", "Mining stuff", true, true, true, null, "CHEST"));
        repository.save(new QuestCategoryConfig("combat", "Combat stuff", false, false, false, null, "CHEST"));

        assertEquals("Mining stuff", repository.get("mining").description());
        assertEquals("Combat stuff", repository.get("combat").description());
        assertEquals(QuestCategoryConfig.defaults("unrelated"), repository.get("unrelated"));
    }

    @Test
    void savingACategoryOntoAnAlreadyOccupiedSlotSwapsInsteadOfColliding(@TempDir Path tempDir) {
        Path file = tempDir.resolve("categories.yml");
        CategoryConfigRepository repository = new CategoryConfigRepository(file);
        repository.save(new QuestCategoryConfig("mining", "", true, true, true, 5, "CHEST"));

        // "combat" claims mining's slot 5 - mining should be bumped to whatever slot combat had before
        // (none, in this case), rather than the two silently ending up on the same slot.
        repository.save(new QuestCategoryConfig("combat", "", true, true, true, 5, "CHEST"));

        assertEquals(5, repository.get("combat").slot());
        assertNull(repository.get("mining").slot());
    }

    @Test
    void swapPreservesTheOtherCategorysPreviousSlotRatherThanClearingIt(@TempDir Path tempDir) {
        Path file = tempDir.resolve("categories.yml");
        CategoryConfigRepository repository = new CategoryConfigRepository(file);
        repository.save(new QuestCategoryConfig("mining", "", true, true, true, 5, "CHEST"));
        repository.save(new QuestCategoryConfig("combat", "", true, true, true, 9, "CHEST"));

        // combat moves onto mining's slot 5 - mining should take combat's old slot (9), a genuine swap.
        repository.save(new QuestCategoryConfig("combat", "", true, true, true, 5, "CHEST"));

        assertEquals(5, repository.get("combat").slot());
        assertEquals(9, repository.get("mining").slot());
    }
}
