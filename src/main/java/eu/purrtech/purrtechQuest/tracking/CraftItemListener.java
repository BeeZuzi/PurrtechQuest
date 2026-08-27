package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.service.QuestService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * CRAFT_ITEM tracks taking a crafted item out of the crafting table's result slot — separate from
 * COLLECT_ITEM (picking up a dropped item) since "craft this" and "find/loot this" are different
 * objectives worth telling apart. Reports {@link ItemMatcher#resolveTarget} like COLLECT_ITEM, so custom
 * ItemsAdder/Oraxen crafted items are addressable by their own id.
 * <p>
 * Only counts the single craft yield ({@code getRecipe().getResult()}'s amount) per click, not however many
 * a shift-click bulk-crafts — Bukkit's {@link CraftItemEvent} doesn't expose how many repeats a shift-click
 * actually performed, only the per-craft result, so a shift-click undercounts here. Documented rather than
 * worked around: reproducing vanilla's shift-craft repeat count means re-deriving it from the crafting
 * matrix's ingredient counts, which is a lot of code for a progress-tracking nicety.
 */
public final class CraftItemListener implements Listener {

    private final QuestService questService;

    public CraftItemListener(QuestService questService) {
        this.questService = questService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        if (result.getType().isAir()) {
            return;
        }
        String target = ItemMatcher.resolveTarget(result);
        questService.updateProgress(player, ObjectiveType.CRAFT_ITEM, target, result.getAmount());
    }
}
