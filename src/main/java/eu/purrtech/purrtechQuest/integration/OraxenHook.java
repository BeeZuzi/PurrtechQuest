package eu.purrtech.purrtechQuest.integration;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Oraxen custom-item lookups. See {@link ItemsAdderHook}'s javadoc for why every method here is safe to
 * call unconditionally as long as the caller already checked {@link #isPresent()}.
 */
public final class OraxenHook {

    private OraxenHook() {
    }

    public static boolean isPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("Oraxen");
    }

    /**
     * The item's Oraxen id, or {@code null} if it isn't an Oraxen item (or the plugin isn't installed).
     */
    public static String customIdOf(ItemStack stack) {
        if (!isPresent()) {
            return null;
        }
        return OraxenItems.getIdByItem(stack);
    }

    /**
     * Builds a fresh stack of the given custom item, or {@code null} if the id doesn't exist (or the
     * plugin isn't installed).
     */
    public static ItemStack createItem(String id, int amount) {
        if (!isPresent()) {
            return null;
        }
        ItemBuilder builder = OraxenItems.getItemById(id);
        if (builder == null) {
            return null;
        }
        ItemStack stack = builder.build();
        stack.setAmount(amount);
        return stack;
    }
}
