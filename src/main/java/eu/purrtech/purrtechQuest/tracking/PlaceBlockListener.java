package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.service.QuestService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public final class PlaceBlockListener implements Listener {

    private final QuestService questService;

    public PlaceBlockListener(QuestService questService) {
        this.questService = questService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        questService.updateProgress(event.getPlayer(), ObjectiveType.PLACE_BLOCK,
                event.getBlock().getType().name(), 1);
    }
}
