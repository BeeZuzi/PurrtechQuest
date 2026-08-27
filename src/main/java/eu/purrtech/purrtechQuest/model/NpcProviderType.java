package eu.purrtech.purrtechQuest.model;

/**
 * NPC plugins PurrtechQuest can bind a quest giver to. Both are optional soft-depends —
 * see {@code eu.purrtech.purrtechQuest.npc} (added in a later phase) for the provider abstraction.
 */
public enum NpcProviderType {
    CITIZENS,
    FANCYNPCS
}
