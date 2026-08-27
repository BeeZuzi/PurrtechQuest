package eu.purrtech.purrtechQuest.api.objective;

import eu.purrtech.purrtechQuest.model.ObjectiveType;

/**
 * Registered by a 3rd-party plugin to make its own custom objective type available on
 * {@link ObjectiveType#CUSTOM} objectives — admins can then pick it in the in-game editor, and the
 * registering plugin is responsible for actually detecting when it's satisfied and reporting progress
 * itself (there's nothing here for PurrtechQuest to "run"; see the API docs for the full flow).
 * <p>
 * The objective's {@code target} string is always {@code "<handlerId>:<subTarget>"} — this handler only
 * ever sees the {@code subTarget} part, never the {@code "<handlerId>:"} prefix that routes to it.
 */
public interface ObjectiveHandler {

    /** Short, stable, unique id (e.g. {@code "visit_city"}) — becomes the target string's prefix. */
    String id();

    /** Shown in the editor's type picker and objective list, e.g. {@code "Visit a City"}. */
    String displayName();

    /**
     * Validates a sub-target the admin typed while authoring a quest in the editor (e.g. {@code "paris"}).
     * PurrtechQuest itself never interprets this string — only this handler and whatever plugin registered
     * it know what a valid value looks like.
     */
    boolean isValidTarget(String subTarget);
}
