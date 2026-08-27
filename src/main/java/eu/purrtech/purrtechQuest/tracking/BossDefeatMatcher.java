package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.QuestObjective;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides whether a DEFEAT_BOSS objective is satisfied by one player's contribution to a boss kill.
 * {@code meta}'s {@code "min-damage"} (default {@value #DEFAULT_MIN_DAMAGE} — any damage counts) is the
 * minimum cumulative damage the player must have dealt to that boss instance; {@code "window-hours"}
 * (default {@value #DEFAULT_WINDOW_HOURS}) is how long after their *first* hit on it the boss has to die
 * for that contribution to still count — long enough for a real fight, short enough that tapping a boss
 * once and coming back hours later for an unrelated kill doesn't award credit.
 */
public final class BossDefeatMatcher {

    private static final double DEFAULT_MIN_DAMAGE = 0;
    private static final double DEFAULT_WINDOW_HOURS = 2;

    private BossDefeatMatcher() {
    }

    public static boolean satisfies(QuestObjective objective, double damageDealt, Instant firstHitAt, Instant deathTime) {
        double minDamage = parseDouble(objective.meta().get("min-damage"), DEFAULT_MIN_DAMAGE);
        double windowHours = parseDouble(objective.meta().get("window-hours"), DEFAULT_WINDOW_HOURS);
        if (damageDealt < minDamage) {
            return false;
        }
        Duration elapsed = Duration.between(firstHitAt, deathTime);
        Duration window = Duration.ofMillis(Math.round(windowHours * 3_600_000));
        return elapsed.compareTo(window) <= 0;
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
