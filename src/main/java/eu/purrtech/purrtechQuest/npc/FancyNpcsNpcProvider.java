package eu.purrtech.purrtechQuest.npc;

import de.oliver.fancynpcs.api.actions.ActionTrigger;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.model.NpcProviderType;
import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.model.QuestGiverRef;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.service.QuestTrackingService;
import eu.purrtech.purrtechQuest.tracking.NpcMatcher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Only registered when FancyNpcs is installed. Same behavior as {@link CitizensNpcProvider}, for FancyNpcs
 * NPCs — only {@code RIGHT_CLICK} interactions are treated as "talk to this NPC" (FancyNpcs also fires
 * left-click and custom-action triggers we don't want double-counted as a second interaction).
 */
public final class FancyNpcsNpcProvider implements NpcProvider, Listener {

    private final QuestService questService;
    private final PlayerQuestDataCache playerCache;
    private final QuestTrackingService trackingService;
    private final NpcLinkService npcLinkService;
    private final MenuLayoutConfig menuLayouts;
    private final MessagesConfig messages;

    public FancyNpcsNpcProvider(QuestService questService, PlayerQuestDataCache playerCache,
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
        return NpcProviderType.FANCYNPCS;
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
    }

    @EventHandler
    public void onInteract(NpcInteractEvent event) {
        if (event.getInteractionType() != ActionTrigger.RIGHT_CLICK) {
            return;
        }
        Player player = event.getPlayer();
        QuestGiverRef ref = new QuestGiverRef(NpcProviderType.FANCYNPCS, event.getNpc().getData().getId());
        if (npcLinkService.tryConsumePick(player, ref)) {
            return;
        }
        questService.updateProgress(player, ObjectiveType.TALK_TO_NPC, NpcMatcher.targetFor(ref), 1);
        QuestGiverInteraction.handle(player, ref, questService, playerCache, trackingService, menuLayouts, messages);
    }
}
