package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestGiverRef;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import eu.purrtech.purrtechQuest.model.QuestReward;
import eu.purrtech.purrtechQuest.model.QuestRewardTier;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable in-progress edit of a {@link Quest}, held in memory for the duration of an admin's editor
 * session. {@link Quest} itself stays an immutable record — this is deliberately more permissive during
 * editing (e.g. it's fine to have zero objectives while building the list up) and only gets validated when
 * {@link #toQuest()} is called at save time, via {@code Quest}'s own constructor checks.
 */
public final class QuestDraft {

    /**
     * Mutable counterpart to {@link QuestRewardTier} for the duration of an editor session — the record
     * itself requires a non-empty reward list, which is too strict while an admin is still building one up
     * in the GUI (adding the permission node first, rewards after).
     */
    public static final class RewardTierDraft {
        private String permission;
        private String displayName;
        private final List<QuestReward> rewards = new ArrayList<>();

        public RewardTierDraft(String permission, String displayName) {
            this.permission = permission;
            this.displayName = displayName;
        }

        public String permission() {
            return permission;
        }

        public void permission(String permission) {
            this.permission = permission;
        }

        public String displayName() {
            return displayName;
        }

        public void displayName(String displayName) {
            this.displayName = displayName;
        }

        public List<QuestReward> rewards() {
            return rewards;
        }
    }

    private final String id;
    private String displayName;
    private String description = "";
    private String category = "default";
    private final List<QuestObjective> objectives = new ArrayList<>();
    private final List<QuestReward> rewards = new ArrayList<>();
    private final List<RewardTierDraft> rewardTiers = new ArrayList<>();
    private final List<String> requiredQuests = new ArrayList<>();
    private boolean repeatable;
    private long cooldownSeconds;
    private boolean autoStart;
    private boolean autoTurnIn;
    private QuestGiverRef questGiver;
    private String requiredPermission;
    private Integer sortOrder;

    private QuestDraft(String id) {
        this.id = id;
        this.displayName = id;
    }

    public static QuestDraft blank(String id) {
        return new QuestDraft(id);
    }

    public static QuestDraft from(Quest quest) {
        QuestDraft draft = new QuestDraft(quest.id());
        draft.displayName = quest.displayName();
        draft.description = quest.description();
        draft.category = quest.category();
        draft.objectives.addAll(quest.objectives());
        draft.rewards.addAll(quest.rewards());
        for (QuestRewardTier tier : quest.rewardTiers()) {
            RewardTierDraft tierDraft = new RewardTierDraft(tier.permission(), tier.displayName());
            tierDraft.rewards().addAll(tier.rewards());
            draft.rewardTiers.add(tierDraft);
        }
        draft.requiredQuests.addAll(quest.requiredQuests());
        draft.repeatable = quest.repeatable();
        draft.cooldownSeconds = quest.cooldownSeconds();
        draft.autoStart = quest.autoStart();
        draft.autoTurnIn = quest.autoTurnIn();
        draft.questGiver = quest.questGiver();
        draft.requiredPermission = quest.requiredPermission();
        draft.sortOrder = quest.sortOrder();
        return draft;
    }

    public Quest toQuest() {
        List<QuestRewardTier> tiers = rewardTiers.stream()
                .filter(tier -> !tier.rewards().isEmpty())
                .map(tier -> new QuestRewardTier(tier.permission(), tier.displayName(), tier.rewards()))
                .toList();
        return new Quest(id, displayName, description, category, objectives, rewards, tiers, requiredQuests,
                repeatable, cooldownSeconds, autoStart, autoTurnIn, questGiver, requiredPermission, sortOrder);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public void displayName(String displayName) {
        this.displayName = displayName;
    }

    public String description() {
        return description;
    }

    public void description(String description) {
        this.description = description;
    }

    public String category() {
        return category;
    }

    public void category(String category) {
        this.category = category;
    }

    public List<QuestObjective> objectives() {
        return objectives;
    }

    public List<QuestReward> rewards() {
        return rewards;
    }

    public List<RewardTierDraft> rewardTiers() {
        return rewardTiers;
    }

    /** Rewards across the base bucket and every rank tier combined — what the editor hub's button count shows. */
    public int totalRewardCount() {
        int count = rewards.size();
        for (RewardTierDraft tier : rewardTiers) {
            count += tier.rewards().size();
        }
        return count;
    }

    public List<String> requiredQuests() {
        return requiredQuests;
    }

    public boolean repeatable() {
        return repeatable;
    }

    public void repeatable(boolean repeatable) {
        this.repeatable = repeatable;
    }

    public long cooldownSeconds() {
        return cooldownSeconds;
    }

    public void cooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public boolean autoStart() {
        return autoStart;
    }

    public void autoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean autoTurnIn() {
        return autoTurnIn;
    }

    public void autoTurnIn(boolean autoTurnIn) {
        this.autoTurnIn = autoTurnIn;
    }

    public QuestGiverRef questGiver() {
        return questGiver;
    }

    public void questGiver(QuestGiverRef questGiver) {
        this.questGiver = questGiver;
    }

    public String requiredPermission() {
        return requiredPermission;
    }

    public void requiredPermission(String requiredPermission) {
        this.requiredPermission = (requiredPermission == null || requiredPermission.isBlank()) ? null : requiredPermission;
    }

    public Integer sortOrder() {
        return sortOrder;
    }

    public void sortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
