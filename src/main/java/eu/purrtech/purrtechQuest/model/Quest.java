package eu.purrtech.purrtechQuest.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Static, immutable definition of a quest. Never mutated per-player — a player's progress through it lives
 * in a separate {@link QuestProgress} instance so this record can be freely shared/cached.
 * <p>
 * {@code requiredPermission}, when set, gates who can even accept the quest — {@code null} (the default)
 * means anyone can. Unlike {@link QuestRewardTier}'s permission (which the admin is expected to already
 * have set up in their permission plugin), this one is registered as a genuine Bukkit permission on reload
 * (see {@code permission.QuestPermissionRegistrar}) so it shows up as a real, known node rather than only
 * working by coincidence of {@code hasPermission} accepting arbitrary strings.
 * <p>
 * {@code sortOrder}, when set, is a manual display-order weight for player-facing quest lists (see
 * {@link #DISPLAY_ORDER}) — lower sorts first. {@code null} (the default) means "no preference".
 */
public record Quest(
        String id,
        String displayName,
        String description,
        String category,
        List<QuestObjective> objectives,
        List<QuestReward> rewards,
        List<QuestRewardTier> rewardTiers,
        List<String> requiredQuests,
        boolean repeatable,
        long cooldownSeconds,
        boolean autoStart,
        boolean autoTurnIn,
        QuestGiverRef questGiver,
        String requiredPermission,
        Integer sortOrder
) {

    /**
     * How {@code QuestLogGui} (and anywhere else quests are listed to a player) orders them: quests with an
     * explicit {@link #sortOrder} come first, lowest number first; quests with none set sort after all of
     * those, as their own group. Within either group — including two quests that share the same explicit
     * number — the tie-break is alphabetical by {@link #displayName}.
     */
    public static final Comparator<Quest> DISPLAY_ORDER = Comparator
            .comparing((Quest quest) -> quest.sortOrder() == null ? Integer.MAX_VALUE : quest.sortOrder())
            .thenComparing(Quest::displayName, String.CASE_INSENSITIVE_ORDER);

    public Quest {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(displayName, "displayName");
        description = description == null ? "" : description;
        category = category == null || category.isBlank() ? "default" : category;
        if (objectives == null || objectives.isEmpty()) {
            throw new IllegalArgumentException("quest '" + id + "' must have at least one objective");
        }
        objectives = List.copyOf(objectives);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        rewardTiers = rewardTiers == null ? List.of() : List.copyOf(rewardTiers);
        requiredQuests = requiredQuests == null ? List.of() : List.copyOf(requiredQuests);
        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException("cooldownSeconds must not be negative");
        }
        requiredPermission = (requiredPermission == null || requiredPermission.isBlank()) ? null : requiredPermission;
    }

    /** Convenience constructor for callers that predate {@code requiredPermission}/{@code sortOrder} — both default to unset. */
    public Quest(String id, String displayName, String description, String category,
                 List<QuestObjective> objectives, List<QuestReward> rewards, List<QuestRewardTier> rewardTiers,
                 List<String> requiredQuests, boolean repeatable, long cooldownSeconds, boolean autoStart,
                 boolean autoTurnIn, QuestGiverRef questGiver) {
        this(id, displayName, description, category, objectives, rewards, rewardTiers, requiredQuests,
                repeatable, cooldownSeconds, autoStart, autoTurnIn, questGiver, null, null);
    }

    /** Convenience constructor for callers that predate {@code sortOrder} — defaults to unset (no preference). */
    public Quest(String id, String displayName, String description, String category,
                 List<QuestObjective> objectives, List<QuestReward> rewards, List<QuestRewardTier> rewardTiers,
                 List<String> requiredQuests, boolean repeatable, long cooldownSeconds, boolean autoStart,
                 boolean autoTurnIn, QuestGiverRef questGiver, String requiredPermission) {
        this(id, displayName, description, category, objectives, rewards, rewardTiers, requiredQuests,
                repeatable, cooldownSeconds, autoStart, autoTurnIn, questGiver, requiredPermission, null);
    }
}
