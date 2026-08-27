package eu.purrtech.purrtechQuest.npc;

import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.gui.QuestLogGui;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestGiverRef;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.service.QuestTrackingService;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * What happens when a player right-clicks an NPC, shared by both {@link CitizensNpcProvider} and
 * {@link FancyNpcsNpcProvider} so the two providers don't duplicate this logic. If the NPC isn't linked to
 * any quest, this does nothing — most NPCs on a server are just decoration, not quest givers.
 */
final class QuestGiverInteraction {

    private QuestGiverInteraction() {
    }

    static void handle(Player player, QuestGiverRef ref, QuestService questService, PlayerQuestDataCache playerCache,
                        QuestTrackingService trackingService, MenuLayoutConfig menuLayouts, MessagesConfig messages) {
        List<Quest> quests = questService.allQuests().stream()
                .filter(quest -> ref.equals(quest.questGiver()))
                .toList();
        if (quests.isEmpty()) {
            return;
        }
        new QuestLogGui(questService, playerCache, trackingService, menuLayouts, messages, player, quests, null).open(player);
    }
}
