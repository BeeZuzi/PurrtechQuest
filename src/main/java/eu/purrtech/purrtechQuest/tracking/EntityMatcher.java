package eu.purrtech.purrtechQuest.tracking;

/**
 * Decides whether an objective's {@code target} matches an entity that was killed. Today it's a plain
 * vanilla {@code EntityType} name comparison — this is the seam where a MythicMobs internal-name check
 * gets added later (Phase 4) without touching {@code QuestService} or the listener that calls it.
 */
public final class EntityMatcher {

    private EntityMatcher() {
    }

    public static boolean matches(String objectiveTarget, String actualEntityType) {
        return objectiveTarget.equalsIgnoreCase(actualEntityType);
    }
}
