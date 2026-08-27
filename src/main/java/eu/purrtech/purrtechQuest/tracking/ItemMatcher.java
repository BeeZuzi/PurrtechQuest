package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.integration.ItemsAdderHook;
import eu.purrtech.purrtechQuest.integration.OraxenHook;
import org.bukkit.inventory.ItemStack;

/**
 * Decides whether an objective's {@code target} matches a block/item that was broken, placed, or picked
 * up. Bukkit's {@code Material} enum already covers both blocks and items, so one matcher serves
 * BREAK_BLOCK, PLACE_BLOCK and COLLECT_ITEM. Comparison itself is always a plain case-insensitive string
 * match — {@link #resolveTarget} is what encodes ItemsAdder/Oraxen custom items into a comparable target
 * string ({@code "ia:"}/{@code "oraxen:"} prefix) in the first place, so nothing downstream needs to know
 * custom items exist.
 */
public final class ItemMatcher {

    private ItemMatcher() {
    }

    public static boolean matches(String objectiveTarget, String actualMaterial) {
        return objectiveTarget.equalsIgnoreCase(actualMaterial);
    }

    /**
     * The target string a picked-up/placed/broken stack represents for objective matching: its ItemsAdder
     * or Oraxen custom id if it has one (whichever plugin is installed), otherwise its vanilla
     * {@code Material} name. Only {@link CollectItemListener} uses this today — BREAK_BLOCK/PLACE_BLOCK
     * stay vanilla-only, since custom *blocks* (as opposed to custom items) are a different, more involved
     * integration these plugins expose through separate events.
     */
    public static String resolveTarget(ItemStack stack) {
        String itemsAdderId = ItemsAdderHook.customIdOf(stack);
        if (itemsAdderId != null) {
            return "ia:" + itemsAdderId;
        }
        String oraxenId = OraxenHook.customIdOf(stack);
        if (oraxenId != null) {
            return "oraxen:" + oraxenId;
        }
        return stack.getType().name();
    }
}
