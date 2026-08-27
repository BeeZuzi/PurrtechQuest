package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.service.QuestService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * COLLECT_ITEM tracks dropped-item pickups (mining, mob loot, ...). Crafting is a separate objective type
 * (CRAFT_ITEM, not implemented yet) since "collect" and "craft" have different failure modes worth telling
 * apart. Reports {@link ItemMatcher#resolveTarget} rather than the raw material, so ItemsAdder/Oraxen
 * custom items are addressable by their own id instead of whatever vanilla material they're built on.
 */
public final class CollectItemListener implements Listener {

    private final QuestService questService;

    public CollectItemListener(QuestService questService) {
        this.questService = questService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        String target = ItemMatcher.resolveTarget(stack);
        questService.updateProgress(player, ObjectiveType.COLLECT_ITEM, target, stack.getAmount());
    }
}
