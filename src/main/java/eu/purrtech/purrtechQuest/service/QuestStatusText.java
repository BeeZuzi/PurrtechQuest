package eu.purrtech.purrtechQuest.service;

import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.model.QuestStatus;

/**
 * Maps a quest status to its {@code lang/*.yml} message key. Shared by the chat command and the GUI so a
 * quest's status reads identically everywhere.
 */
public final class QuestStatusText {

    private QuestStatusText() {
    }

    public static String key(QuestProgress progress) {
        return key(progress == null ? QuestStatus.NOT_ACCEPTED : progress.status());
    }

    public static String key(QuestStatus status) {
        return switch (status) {
            case NOT_ACCEPTED -> "quest.status-available";
            case IN_PROGRESS -> "quest.status-in-progress";
            case COMPLETED -> "quest.status-completed";
            case TURNED_IN -> "quest.status-turned-in";
        };
    }
}
