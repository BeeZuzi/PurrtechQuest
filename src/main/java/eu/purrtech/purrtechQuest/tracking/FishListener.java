package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.service.QuestService;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * FISH tracks a successful catch — {@code PlayerFishEvent.State.CAUGHT_FISH} covers vanilla fishing's full
 * loot table (actual fish, treasure like enchanted books/bows, and junk alike), so the objective's
 * {@code target} is just whichever material came up, matched the same way COLLECT_ITEM is (including
 * ItemsAdder/Oraxen custom catch tables via {@link ItemMatcher#resolveTarget}, if a data pack/plugin adds
 * one). An admin wanting "catch any fish" rather than one specific type just needs a separate objective per
 * fish type, or picks the most common one — there's no wildcard target, matching how every other
 * item-target objective type already works.
 */
public final class FishListener implements Listener {

    private final QuestService questService;

    public FishListener(QuestService questService) {
        this.questService = questService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!(event.getCaught() instanceof Item caughtItem)) {
            return;
        }
        ItemStack stack = caughtItem.getItemStack();
        String target = ItemMatcher.resolveTarget(stack);
        questService.updateProgress(event.getPlayer(), ObjectiveType.FISH, target, stack.getAmount());
    }
}
