package eu.purrtech.purrtechQuest.tracking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlaceholderMatcher#matches} itself needs a live PlaceholderAPI/Bukkit instance (see the class
 * javadoc for why that's not available in plain JUnit here — same MockBukkit/{@code Bukkit.getServer()}
 * limitation as everywhere else in this test suite), so this exercises the pure comparison logic behind it
 * directly instead. That's the part {@code /questadmin edit}'s PLACEHOLDER_CHECK wizard actually builds —
 * an admin picks an operator and a value, and this is what decides whether a resolved placeholder satisfies
 * them.
 */
class PlaceholderMatcherTest {

    @Test
    void equalsOperatorMatchesTextCaseInsensitively() {
        assertTrue(PlaceholderMatcher.compare("Online", "=", "online"));
        assertTrue(PlaceholderMatcher.compare("SURVIVAL", "=", "survival"));
        assertFalse(PlaceholderMatcher.compare("offline", "=", "online"));
    }

    @Test
    void equalsOperatorDefaultsWhenOperatorMissingOrUnrecognized() {
        // QuestObjective.meta() falls back to "=" when a PLACEHOLDER_CHECK objective has no explicit
        // operator (see PlaceholderMatcher#matches) — compare() itself treats any unrecognized operator the
        // same way, via its switch's default branch.
        assertTrue(PlaceholderMatcher.compare("gold", "=", "gold"));
        assertTrue(PlaceholderMatcher.compare("gold", "nonsense", "gold"));
    }

    @Test
    void notEqualsOperatorWorksOnText() {
        assertTrue(PlaceholderMatcher.compare("survival", "!=", "creative"));
        assertFalse(PlaceholderMatcher.compare("survival", "!=", "survival"));
    }

    @Test
    void magnitudeOperatorsAreAlwaysFalseOnNonNumericText() {
        assertFalse(PlaceholderMatcher.compare("gold", ">", "silver"));
        assertFalse(PlaceholderMatcher.compare("gold", ">=", "silver"));
        assertFalse(PlaceholderMatcher.compare("gold", "<", "silver"));
        assertFalse(PlaceholderMatcher.compare("gold", "<=", "silver"));
    }

    @Test
    void numericComparisonStillTakesPriorityOverTextWhenBothSidesParse() {
        assertTrue(PlaceholderMatcher.compare("100", "=", "100"));
        assertTrue(PlaceholderMatcher.compare("100", ">", "50"));
        assertFalse(PlaceholderMatcher.compare("100", "=", "100.5"));
        // "100" and "100.0" parse to the same double, so this is still a numeric — not textual — equals.
        assertTrue(PlaceholderMatcher.compare("100", "=", "100.0"));
    }

    @Test
    void mixedNumericAndTextFallsBackToTextComparison() {
        // One side parses as a number and the other doesn't (e.g. an admin comparing against a rank name
        // rather than a number) — falls back to plain text comparison rather than treating the numeric side
        // specially.
        assertFalse(PlaceholderMatcher.compare("42", "=", "vip"));
        assertTrue(PlaceholderMatcher.compare("vip", "=", "vip"));
    }
}
