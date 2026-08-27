package eu.purrtech.purrtechQuest.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers {@link Quest#DISPLAY_ORDER}: quests with an explicit {@code sortOrder} come first (lowest number
 * first), quests with none set come after as their own group, and either group breaks ties alphabetically
 * by display name.
 */
class QuestTest {

    @Test
    void explicitlyNumberedQuestsSortBeforeUnnumberedOnesByTheirNumber() {
        Quest first = quest("z_quest", "Z Quest", 1);
        Quest second = quest("a_quest", "A Quest", 2);
        Quest unnumbered = quest("m_quest", "M Quest", null);

        List<Quest> sorted = List.of(unnumbered, second, first).stream().sorted(Quest.DISPLAY_ORDER).toList();

        assertEquals(List.of(first, second, unnumbered), sorted);
    }

    @Test
    void sameNumberBreaksTiesAlphabeticallyByDisplayName() {
        Quest zebra = quest("zebra_id", "Zebra", 5);
        Quest apple = quest("apple_id", "Apple", 5);

        List<Quest> sorted = List.of(zebra, apple).stream().sorted(Quest.DISPLAY_ORDER).toList();

        assertEquals(List.of(apple, zebra), sorted);
    }

    @Test
    void noNumberAtAllFallsBackToPlainAlphabeticalOrder() {
        Quest zebra = quest("zebra_id", "Zebra", null);
        Quest apple = quest("apple_id", "Apple", null);

        List<Quest> sorted = List.of(zebra, apple).stream().sorted(Quest.DISPLAY_ORDER).toList();

        assertEquals(List.of(apple, zebra), sorted);
    }

    private static Quest quest(String id, String displayName, Integer sortOrder) {
        return new Quest(id, displayName, "", "default",
                List.of(new QuestObjective(ObjectiveType.BREAK_BLOCK, "STONE", 1)),
                List.of(), List.of(), List.of(), false, 0, false, false, null, null, sortOrder);
    }
}
