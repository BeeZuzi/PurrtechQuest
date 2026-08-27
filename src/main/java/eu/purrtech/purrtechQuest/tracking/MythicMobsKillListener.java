package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.service.QuestService;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Only registered when MythicMobs is installed (see {@code PurrtechQuest.registerSoftDependListeners}).
 * Reports kills with an {@code "mm:"}-prefixed target so a single KILL_ENTITY objective type can address
 * both vanilla mobs (plain {@code EntityType} name, via {@link KillEntityListener}) and MythicMobs custom
 * mobs without needing a separate objective type — see {@link EntityMatcher}.
 */
public final class MythicMobsKillListener implements Listener {

    private final QuestService questService;

    public MythicMobsKillListener(QuestService questService) {
        this.questService = questService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        LivingEntity killer = event.getKiller();
        if (!(killer instanceof Player player)) {
            return;
        }
        String internalName = event.getMobType().getInternalName();
        questService.updateProgress(player, ObjectiveType.KILL_ENTITY, "mm:" + internalName, 1);
    }
}
