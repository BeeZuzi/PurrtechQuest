package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal chest-inventory GUI base class. There's no external GUI library dependency here on purpose (see
 * the analysis doc's open-risks section) — this is deliberately small: an {@link Inventory} plus a
 * per-slot click handler map. {@link GuiListener} is what actually wires this into Bukkit events.
 */
public abstract class Gui implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    protected Gui(Component title, int size) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        inventory.setItem(slot, item);
        if (onClick != null) {
            handlers.put(slot, onClick);
        } else {
            handlers.remove(slot);
        }
    }

    protected void clear() {
        inventory.clear();
        handlers.clear();
    }

    /**
     * Fills every slot in {@code [fromInclusive, toExclusive)} with {@link GuiItems#filler()}, unless
     * {@link MenuLayoutConfig#fillEmptySlots()} is off — a technician's global "no filler" preference in
     * {@code menus.yml}. For screens whose leftover empty space isn't one contiguous range, see the
     * {@code boolean[]} overload below.
     */
    protected void fillEmptySlots(MenuLayoutConfig menuLayouts, int fromInclusive, int toExclusive) {
        if (!menuLayouts.fillEmptySlots()) {
            return;
        }
        for (int slot = fromInclusive; slot < toExclusive; slot++) {
            setItem(slot, GuiItems.filler(), null);
        }
    }

    /** Same as {@link #fillEmptySlots(MenuLayoutConfig, int, int)}, for a screen tracking occupancy per slot directly. */
    protected void fillEmptySlots(MenuLayoutConfig menuLayouts, boolean[] occupied) {
        if (!menuLayouts.fillEmptySlots()) {
            return;
        }
        for (int slot = 0; slot < occupied.length; slot++) {
            if (!occupied[slot]) {
                setItem(slot, GuiItems.filler(), null);
            }
        }
    }

    void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = handlers.get(event.getSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }
}
