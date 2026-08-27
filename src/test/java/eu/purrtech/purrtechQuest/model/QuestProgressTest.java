package eu.purrtech.purrtechQuest.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the scenario that used to throw {@link ArrayIndexOutOfBoundsException}: a quest is edited to add
 * objectives after a player already accepted (or even fully turned in) it, so their stored
 * {@code objectiveProgress} array is shorter than the quest's current objective count — every GUI/tracker
 * that iterates by the quest's live objective list (not the array's own length) hits this.
 */
class QuestProgressTest {

    @Test
    void readingAnIndexBeyondTheStoredArrayReturnsZeroInsteadOfThrowing() {
        QuestProgress progress = new QuestProgress("quest", 1);
        assertEquals(0, progress.objectiveProgress(0));
        assertEquals(0, progress.objectiveProgress(3));
    }

    @Test
    void incrementingAnIndexBeyondTheStoredArrayGrowsItInPlace() {
        QuestProgress progress = new QuestProgress("quest", 1);
        progress.incrementObjective(3, 2);
        assertEquals(2, progress.objectiveProgress(3));
        // Growing to fit index 3 must not disturb the already-tracked objective 0.
        progress.incrementObjective(0, 5);
        assertEquals(5, progress.objectiveProgress(0));
        assertEquals(2, progress.objectiveProgress(3));
    }

    @Test
    void statusAndTimesCompletedSurviveArrayGrowth() {
        QuestProgress progress = new QuestProgress("quest", 1);
        progress.status(QuestStatus.TURNED_IN);
        progress.incrementTimesCompleted();

        progress.objectiveProgress(5);

        assertEquals(QuestStatus.TURNED_IN, progress.status());
        assertEquals(1, progress.timesCompleted());
    }
}
