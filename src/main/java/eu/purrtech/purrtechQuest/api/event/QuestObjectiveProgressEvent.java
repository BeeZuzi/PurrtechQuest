package eu.purrtech.purrtechQuest.api.event;

import eu.purrtech.purrtechQuest.model.Quest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a single objective's progress increases (not per kill/pickup/etc. — only when it actually
 * moved the stored counter, already clamped to the objective's target amount). Not cancellable: the
 * progress has already been applied by the time this fires, purely a notification.
 */
public class QuestObjectiveProgressEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Quest quest;
    private final int objectiveIndex;
    private final int previousAmount;
    private final int newAmount;

    public QuestObjectiveProgressEvent(Player player, Quest quest, int objectiveIndex, int previousAmount, int newAmount) {
        this.player = player;
        this.quest = quest;
        this.objectiveIndex = objectiveIndex;
        this.previousAmount = previousAmount;
        this.newAmount = newAmount;
    }

    public Player getPlayer() {
        return player;
    }

    public Quest getQuest() {
        return quest;
    }

    /** Index into {@code getQuest().objectives()}. */
    public int getObjectiveIndex() {
        return objectiveIndex;
    }

    public int getPreviousAmount() {
        return previousAmount;
    }

    public int getNewAmount() {
        return newAmount;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
