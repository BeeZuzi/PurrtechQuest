package eu.purrtech.purrtechQuest.api.event;

import eu.purrtech.purrtechQuest.model.Quest;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired right before rewards are granted (quest is still {@code COMPLETED}, not yet {@code TURNED_IN}).
 * Cancelling blocks the whole turn-in — no rewards are granted, the quest stays {@code COMPLETED} for a
 * later turn-in attempt. {@code QuestService.turnIn} returns {@code TurnInResult.CANCELLED}.
 */
public class QuestTurnInEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Quest quest;
    private boolean cancelled;

    public QuestTurnInEvent(Player player, Quest quest) {
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
