package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.NpcProviderType;
import eu.purrtech.purrtechQuest.model.QuestGiverRef;

import java.util.Locale;

/**
 * Builds/matches a TALK_TO_NPC objective's target string, {@code "<provider>:<npcId>"} (e.g.
 * {@code "citizens:12"}, {@code "fancynpcs:merchant"}) — lowercased so what the admin's editor wizard
 * stores and what a provider reports on interaction never mismatch on case.
 */
public final class NpcMatcher {

    private NpcMatcher() {
    }

    public static String targetFor(QuestGiverRef ref) {
        return targetFor(ref.provider(), ref.npcId());
    }

    public static String targetFor(NpcProviderType provider, String npcId) {
        return provider.name().toLowerCase(Locale.ROOT) + ":" + npcId.toLowerCase(Locale.ROOT);
    }

    public static boolean matches(String objectiveTarget, String actual) {
        return objectiveTarget.equalsIgnoreCase(actual);
    }
}
