package eu.purrtech.purrtechQuest.npc;

import eu.purrtech.purrtechQuest.model.QuestGiverRef;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Remembers that an admin asked to pick an NPC, then consumes that request on their *next* NPC interaction
 * (whichever provider fires it) instead of that interaction being treated as a normal click. Used by both
 * {@code /questadmin npclink <quest>} (picks a quest's quest-giver) and the quest editor's TALK_TO_NPC
 * objective wizard (picks which NPC the objective targets) — picking an NPC by walking up and clicking it
 * doesn't fit an inventory screen or a typed command argument, so both stay a "request, then click" flow.
 */
public final class NpcLinkService {

    private final Map<UUID, Consumer<QuestGiverRef>> pending = new ConcurrentHashMap<>();

    public void requestPick(Player admin, Consumer<QuestGiverRef> onPicked) {
        pending.put(admin.getUniqueId(), onPicked);
    }

    public void cancel(Player admin) {
        pending.remove(admin.getUniqueId());
    }

    /**
     * @return {@code true} if this interaction was consumed as a pending pick — callers should not also
     * treat it as a normal NPC interaction in that case.
     */
    public boolean tryConsumePick(Player player, QuestGiverRef ref) {
        Consumer<QuestGiverRef> onPicked = pending.remove(player.getUniqueId());
        if (onPicked == null) {
            return false;
        }
        onPicked.accept(ref);
        return true;
    }
}
