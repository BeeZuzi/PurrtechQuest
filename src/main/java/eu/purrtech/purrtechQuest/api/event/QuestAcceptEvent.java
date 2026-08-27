package eu.purrtech.purrtechQuest.api.event;

import eu.purrtech.purrtechQuest.model.Quest;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired right before a player's quest progress is created, once prerequisites/cooldown have already
 * passed. Cancelling blocks the accept — {@code QuestService.acceptQuest} returns
 * {@code AcceptResult.CANCELLED} and nothing is written. Not fired for {@code QuestService.adminForceAccept}
 * — an admin's explicit override isn't meant to be vetoable by another plugin.
 */
public class QuestAcceptEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Quest quest;
    private boolean cancelled;

    public QuestAcceptEvent(Player player, Quest quest) {
        this.player = player;
        this.quest = quest;
    }

    public Player getPlayer() {
        return player;
    }

    public Quest getQuest() {
        return quest;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
