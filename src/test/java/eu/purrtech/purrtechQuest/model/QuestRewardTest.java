package eu.purrtech.purrtechQuest.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuestRewardTest {

    @Test
    void withNameReturnsACopyKeepingTheRestOfTheReward() {
        QuestReward renamed = new QuestReward.Item("DIAMOND", 3, null).withName("Stack of diamonds");

        assertEquals(new QuestReward.Item("DIAMOND", 3, null, "Stack of diamonds"), renamed);
    }

    @Test
    void renamingReplacesTheExistingName() {
        QuestReward renamed = new QuestReward.Money(10.0, "Old").withName("New");

        assertEquals("New", renamed.name());
    }

    @Test
    void aBlankNameIsTreatedAsNoName() {
        assertNull(new QuestReward.Command("say hi", "   ").name());
        assertNull(new QuestReward.Experience(5).withName("").name());
    }
}
