package eu.purrtech.purrtechQuest.model;

import java.util.Map;
import java.util.Objects;

/**
 * A single objective inside a {@link Quest}. {@code target} is deliberately a plain string rather than a
 * {@code Material}/{@code EntityType}: it can also hold a custom-item id (ItemsAdder/Oraxen) or a MythicMobs
 * internal name, and it's the {@code tracking} package's job (later phase) to interpret it correctly.
 * <p>
 * {@code choiceGroup}, when set, marks this objective as one of a set of *alternatives* — every objective in
 * the quest sharing the same group name is satisfied as a group the moment any *one* of them is; the others
 * then stop accepting further progress (see {@code QuestService#isChoiceLockedOut}). {@code null} (the
 * default) means this objective is required on its own, same as before this field existed.
 * <p>
 * {@code label} is the human-readable name shown to players (tracker, quest log, {@code /quest info}) instead
 * of the raw {@code target} — for most objective types the target reads fine on its own (e.g. {@code ZOMBIE}),
 * but for others (a placeholder expression, an NPC id, a MythicMobs internal name) it's not something a
 * player should ever see. The editor wizard requires it for every newly created objective; it defaults to
 * {@code target} only for objectives saved before this field existed, so old quest files keep loading with
 * their previous (less pretty) display rather than breaking.
 */
public record QuestObjective(ObjectiveType type, String target, int amount, Map<String, String> meta,
                              String choiceGroup, String label) {

    public QuestObjective {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        if (target.isBlank()) {
            throw new IllegalArgumentException("target must not be blank");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, got " + amount);
        }
        meta = meta == null ? Map.of() : Map.copyOf(meta);
        choiceGroup = (choiceGroup == null || choiceGroup.isBlank()) ? null : choiceGroup;
        label = (label == null || label.isBlank()) ? target : label;
    }

    public QuestObjective(ObjectiveType type, String target, int amount) {
        this(type, target, amount, Map.of(), null, null);
    }

    public QuestObjective(ObjectiveType type, String target, int amount, Map<String, String> meta) {
        this(type, target, amount, meta, null, null);
    }

    public QuestObjective(ObjectiveType type, String target, int amount, Map<String, String> meta, String choiceGroup) {
        this(type, target, amount, meta, choiceGroup, null);
    }
}
