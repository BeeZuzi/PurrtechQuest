package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.service.QuestService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class BreakBlockListener implements Listener {

    private final QuestService questService;

    public BreakBlockListener(QuestService questService) {
        this.questService = questService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        questService.updateProgress(event.getPlayer(), ObjectiveType.BREAK_BLOCK,
                event.getBlock().getType().name(), 1);
    }
}
