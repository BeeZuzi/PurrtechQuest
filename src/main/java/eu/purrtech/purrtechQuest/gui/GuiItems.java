package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.integration.ItemsAdderHook;
import eu.purrtech.purrtechQuest.integration.OraxenHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Small builder for GUI icons. Bukkit item display names/lore render with an implicit italic + white
 * default that looks off for our purposes, so every name/lore line is forced non-italic here.
 */
public final class GuiItems {

    private GuiItems() {
    }

    public static ItemStack icon(Material material, Component name, List<Component> lore) {
        return icon(new ItemStack(material), name, lore);
    }

    /**
     * Same as {@link #icon(Material, Component, List)}, overlaying the name/lore onto an existing base
     * stack instead of a plain material — used for a category icon that might be a custom item (ItemsAdder/
     * Oraxen) rather than a vanilla material, where the base stack already carries the custom texture/model.
     */
    public static ItemStack icon(ItemStack base, Component name, List<Component> lore) {
        ItemStack stack = base.clone();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Same as {@link #icon(Material, Component, List)}, but {@code iconSpec} is a vanilla {@link Material}
     * name or an {@code "ia:"}/{@code "oraxen:"}-prefixed custom item id (the convention
     * {@code QuestReward.Item}/{@code ItemMatcher} already use) — see {@link #resolveIconBase}.
     */
    public static ItemStack icon(String iconSpec, Component name, List<Component> lore) {
        return icon(resolveIconBase(iconSpec), name, lore);
    }

    /**
     * Resolves an icon spec to a base stack, falling back to {@link Material#CHEST} whenever the spec is
     * blank, an unrecognized material name, or a custom item that can't actually be built right now (its
     * provider plugin isn't installed, or the id doesn't exist) — a bad/stale {@code icon} value in
     * {@code categories.yml} degrades to a generic chest icon rather than breaking the menu.
     */
    private static ItemStack resolveIconBase(String iconSpec) {
        if (iconSpec != null) {
            if (iconSpec.startsWith("ia:")) {
                ItemStack custom = ItemsAdderHook.createItem(iconSpec.substring("ia:".length()), 1);
                if (custom != null) {
                    return custom;
                }
            } else if (iconSpec.startsWith("oraxen:")) {
                ItemStack custom = OraxenHook.createItem(iconSpec.substring("oraxen:".length()), 1);
                if (custom != null) {
                    return custom;
                }
            } else {
                Material material = Material.matchMaterial(iconSpec);
                if (material != null) {
                    return new ItemStack(material);
                }
            }
        }
        return new ItemStack(Material.CHEST);
    }

    public static ItemStack filler() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
