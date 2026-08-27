package eu.purrtech.purrtechQuest.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared logic for {@link QuestObjective#choiceGroup()} — every place that interprets a quest's live
 * progress (deciding what still needs doing, what to show in a tracker, whether the quest is fully done)
 * needs to agree on "a choice group is satisfied once any one member is, and its other members then stop
 * counting," so that rule only has to be gotten right once instead of separately in each of those places.
 */
public final class ChoiceGroups {

    private ChoiceGroups() {
    }

    /** Group names among {@code quest}'s objectives that already have at least one satisfied member. */
    public static Set<String> satisfiedGroups(Quest quest, QuestProgress progress) {
        Set<String> satisfied = new HashSet<>();
        List<QuestObjective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            if (objective.choiceGroup() != null && progress.objectiveProgress(i) >= objective.amount()) {
                satisfied.add(objective.choiceGroup());
            }
        }
        return satisfied;
    }

    /**
     * True when {@code objectives[index]} belongs to a choice group already satisfied by a *different*
     * member — it can never be completed from here on, so nothing should keep waiting on it: it should stop
     * accepting further progress, and shouldn't be picked as "the next thing to do" by anything summarizing
     * a quest's remaining work.
     */
    public static boolean isLockedOut(Quest quest, QuestProgress progress, int index) {
        QuestObjective objective = quest.objectives().get(index);
        if (objective.choiceGroup() == null || progress.objectiveProgress(index) >= objective.amount()) {
            return false;
        }
        return satisfiedGroups(quest, progress).contains(objective.choiceGroup());
    }
}
