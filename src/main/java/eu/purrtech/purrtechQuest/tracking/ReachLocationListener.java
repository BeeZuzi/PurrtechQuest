package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.service.QuestService;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Evaluates REACH_LOCATION objectives on movement. Throttled to block-coordinate changes only — checking
 * on every {@code PlayerMoveEvent} (which also fires on pure head movement) would mean a distance
 * calculation per tick per moving player for no benefit.
 */
public final class ReachLocationListener implements Listener {

    private final QuestService questService;

    public ReachLocationListener(QuestService questService) {
        this.questService = questService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        questService.checkLocationObjectives(event.getPlayer());
    }
}
