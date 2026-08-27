package eu.purrtech.purrtechQuest.npc;

import eu.purrtech.purrtechQuest.model.NpcProviderType;

/**
 * One NPC plugin PurrtechQuest can bind a quest giver to. Implementations ({@link CitizensNpcProvider},
 * {@link FancyNpcsNpcProvider}) are also Bukkit {@code Listener}s — this interface just carries the bit of
 * identity ({@link #type()}) and presence-detection ({@link #isAvailable()}) that's plugin-agnostic.
 */
public interface NpcProvider {

    NpcProviderType type();

    boolean isAvailable();
}
