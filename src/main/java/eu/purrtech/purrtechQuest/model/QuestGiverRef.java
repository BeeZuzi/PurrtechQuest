package eu.purrtech.purrtechQuest.model;

import java.util.Objects;

/**
 * Links a quest to an NPC managed by an external plugin (Citizens/FancyNPCs).
 * Resolving {@code npcId} into an actual in-world NPC is the {@code npc} package's job, not this model's.
 */
public record QuestGiverRef(NpcProviderType provider, String npcId) {

    public QuestGiverRef {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(npcId, "npcId");
        if (npcId.isBlank()) {
            throw new IllegalArgumentException("npcId must not be blank");
        }
    }
}
