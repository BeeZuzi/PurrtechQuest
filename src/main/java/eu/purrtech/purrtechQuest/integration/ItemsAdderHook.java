package eu.purrtech.purrtechQuest.integration;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * ItemsAdder custom-item lookups. Every method here is only ever called from behind an
 * {@link #isPresent()} check higher up the call stack (matching {@link VaultEconomyHook}'s pattern) — the
 * JVM only resolves {@code CustomStack} when this class is actually loaded, which only happens on that
 * guarded path, so ItemsAdder being absent never throws.
 */
public final class ItemsAdderHook {

    private ItemsAdderHook() {
    }

    public static boolean isPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    /**
     * The item's ItemsAdder namespaced id (e.g. {@code "mypack:ruby_ore"}), or {@code null} if it isn't
     * an ItemsAdder item (or the plugin isn't installed).
     */
    public static String customIdOf(ItemStack stack) {
        if (!isPresent()) {
            return null;
        }
        CustomStack customStack = CustomStack.byItemStack(stack);
        return customStack == null ? null : customStack.getNamespacedID();
    }

    /**
     * Builds a fresh stack of the given custom item, or {@code null} if the id doesn't exist (or the
     * plugin isn't installed).
     */
    public static ItemStack createItem(String namespacedId, int amount) {
        if (!isPresent()) {
            return null;
        }
        CustomStack customStack = CustomStack.getInstance(namespacedId);
        if (customStack == null) {
            return null;
        }
        ItemStack stack = customStack.getItemStack().clone();
        stack.setAmount(amount);
        return stack;
    }
}
