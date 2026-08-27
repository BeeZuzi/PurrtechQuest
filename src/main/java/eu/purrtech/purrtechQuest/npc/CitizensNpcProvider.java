package eu.purrtech.purrtechQuest.npc;

import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.model.NpcProviderType;
import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.model.QuestGiverRef;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.service.QuestTrackingService;
import eu.purrtech.purrtechQuest.tracking.NpcMatcher;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Only registered when Citizens is installed. Right-clicking a Citizens NPC either completes a pending
 * {@code /questadmin npclink} (or TALK_TO_NPC wizard) pick request, or — for a normal interaction — both
 * reports TALK_TO_NPC progress for any objective targeting this NPC and, if the NPC is already linked to
 * one or more quests as a quest-giver, opens the quest-giver GUI for it. Those two aren't mutually
 * exclusive: an NPC can be both a quest-giver and the target of a different quest's TALK_TO_NPC objective.
 */
public final class CitizensNpcProvider implements NpcProvider, Listener {

    private final QuestService questService;
    private final PlayerQuestDataCache playerCache;
    private final QuestTrackingService trackingService;
    private final NpcLinkService npcLinkService;
    private final MenuLayoutConfig menuLayouts;
    private final MessagesConfig messages;

    public CitizensNpcProvider(QuestService questService, PlayerQuestDataCache playerCache,
                                QuestTrackingService trackingService, NpcLinkService npcLinkService,
                                MenuLayoutConfig menuLayouts, MessagesConfig messages) {
        this.questService = questService;
        this.playerCache = playerCache;
        this.trackingService = trackingService;
        this.npcLinkService = npcLinkService;
        this.menuLayouts = menuLayouts;
        this.messages = messages;
    }

    @Override
    public NpcProviderType type() {
        return NpcProviderType.CITIZENS;
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens");
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        QuestGiverRef ref = new QuestGiverRef(NpcProviderType.CITIZENS, String.valueOf(event.getNPC().getId()));
        if (npcLinkService.tryConsumePick(player, ref)) {
            return;
        }
        questService.updateProgress(player, ObjectiveType.TALK_TO_NPC, NpcMatcher.targetFor(ref), 1);
        QuestGiverInteraction.handle(player, ref, questService, playerCache, trackingService, menuLayouts, messages);
    }
}
