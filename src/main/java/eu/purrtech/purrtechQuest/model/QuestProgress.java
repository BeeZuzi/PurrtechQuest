package eu.purrtech.purrtechQuest.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * One player's progress through one {@link Quest}. Mutable by design (progress changes constantly while
 * in-game) — unlike {@code Quest} itself, this is never shared between players.
 */
public final class QuestProgress {

    private final String questId;
    private QuestStatus status;
    private int[] objectiveProgress;
    private Instant startedAt;
    private Instant completedAt;
    private int timesCompleted;
    /**
     * Sparse per-objective baseline values, keyed by objective index — currently only populated for
     * EARN_MONEY objectives (the Vault balance at quest-acceptance time, see {@code QuestService}); most
     * objectives never have an entry here.
     */
    private final Map<Integer, Double> objectiveBaselines;

    public QuestProgress(String questId, int objectiveCount) {
        this(questId, QuestStatus.NOT_ACCEPTED, new int[objectiveCount], null, null, 0, new HashMap<>());
    }

    public QuestProgress(String questId, QuestStatus status, int[] objectiveProgress,
                          Instant startedAt, Instant completedAt, int timesCompleted) {
        this(questId, status, objectiveProgress, startedAt, completedAt, timesCompleted, new HashMap<>());
    }

    public QuestProgress(String questId, QuestStatus status, int[] objectiveProgress,
                          Instant startedAt, Instant completedAt, int timesCompleted,
                          Map<Integer, Double> objectiveBaselines) {
        this.questId = questId;
        this.status = status;
        this.objectiveProgress = objectiveProgress;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.timesCompleted = timesCompleted;
        this.objectiveBaselines = objectiveBaselines;
    }

    public String questId() {
        return questId;
    }

    public QuestStatus status() {
        return status;
    }

    public void status(QuestStatus status) {
        this.status = status;
    }

    public int[] objectiveProgress() {
        return objectiveProgress;
    }

    public int objectiveProgress(int index) {
        ensureCapacity(index);
        return objectiveProgress[index];
    }

    public void incrementObjective(int index, int amount) {
        ensureCapacity(index);
        objectiveProgress[index] += amount;
    }

    /**
     * Self-heals a progress array that's shorter than the quest it belongs to now expects — the array is
     * sized to the objective count at accept time (or whenever it was last loaded), but an admin can add
     * objectives to a quest after players have already accepted or even turned it in. Without this, any
     * access to one of those newly-added indices throws {@link ArrayIndexOutOfBoundsException} instead of
     * treating the objective as simply not-yet-started (0 progress), which is the correct reading — it
     * didn't exist yet when this progress was created.
     */
    private void ensureCapacity(int index) {
        if (index >= objectiveProgress.length) {
            objectiveProgress = Arrays.copyOf(objectiveProgress, index + 1);
        }
    }

    /** The baseline captured for objective {@code index}, or {@code null} if none was ever recorded. */
    public Double baseline(int index) {
        return objectiveBaselines.get(index);
    }

    public void baseline(int index, double value) {
        objectiveBaselines.put(index, value);
    }

    /** Raw sparse map, for storage round-tripping — see {@code AbstractSqlPlayerDataRepository}. */
    public Map<Integer, Double> objectiveBaselines() {
        return objectiveBaselines;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public void startedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public void completedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int timesCompleted() {
        return timesCompleted;
    }

    public void incrementTimesCompleted() {
        this.timesCompleted++;
    }

    @Override
    public String toString() {
        return "QuestProgress{questId=%s, status=%s, progress=%s, timesCompleted=%d}"
                .formatted(questId, status, Arrays.toString(objectiveProgress), timesCompleted);
    }
}
