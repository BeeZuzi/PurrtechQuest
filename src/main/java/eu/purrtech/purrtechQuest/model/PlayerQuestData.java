package eu.purrtech.purrtechQuest.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All of one player's quest progress, cached in memory for the duration of their session.
 * {@code ConcurrentHashMap} because it's read/written from both the main thread (gameplay) and the async
 * storage worker thread (load/save).
 */
public final class PlayerQuestData {

    private final UUID playerId;
    private final Map<String, QuestProgress> states = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public PlayerQuestData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public QuestProgress progress(String questId) {
        return states.get(questId);
    }

    public void put(QuestProgress progress) {
        states.put(progress.questId(), progress);
        dirty = true;
    }

    public Map<String, QuestProgress> states() {
        return states;
    }

    /**
     * Marks this data as needing a save. {@link #put} already does this; call it directly when a
     * {@link QuestProgress} already in the map is mutated in place (its own setters don't know about the
     * {@link PlayerQuestData} that holds them) — see {@code QuestService} for the call sites.
     */
    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    /**
     * Deep copy for handing off to the async storage worker. QuestProgress is only ever mutated on the
     * main thread; taking this copy synchronously on the main thread (never inside the async save task)
     * is what keeps the DB write race-free without needing to synchronize QuestProgress itself.
     */
    public PlayerQuestData snapshot() {
        PlayerQuestData copy = new PlayerQuestData(playerId);
        for (QuestProgress progress : states.values()) {
            copy.put(new QuestProgress(progress.questId(), progress.status(), progress.objectiveProgress().clone(),
                    progress.startedAt(), progress.completedAt(), progress.timesCompleted(),
                    new HashMap<>(progress.objectiveBaselines())));
        }
        return copy;
    }
}
