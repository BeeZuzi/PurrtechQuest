package eu.purrtech.purrtechQuest.api.event;

import eu.purrtech.purrtechQuest.model.Quest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired the moment all of a quest's objectives are met (status moves to {@code COMPLETED}). For
 * {@code autoTurnIn} quests, {@code QuestTurnInEvent} follows immediately after. Not cancellable — the
 * objectives are already done, there's nothing left to veto.
 */
public class QuestCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Quest quest;

    public QuestCompleteEvent(Player player, Quest quest) {
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
