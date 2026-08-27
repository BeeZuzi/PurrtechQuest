package eu.purrtech.purrtechQuest.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * The one listener that makes {@link Gui} work: any inventory backed by a {@code Gui} holder is
 * click/drag-locked (players can only interact via the slot handlers registered on the GUI itself).
 */
public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Gui gui)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != gui.getInventory()) {
            return;
        }
        gui.handleClick(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Gui) {
            event.setCancelled(true);
        }
    }
}
