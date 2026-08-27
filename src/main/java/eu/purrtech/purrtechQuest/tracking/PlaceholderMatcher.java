package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.QuestObjective;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Checks a PLACEHOLDER_CHECK objective by resolving {@code target} (a PlaceholderAPI placeholder, e.g.
 * {@code "%vault_eco_balance%"}) for the player and comparing the result against {@code meta}'s
 * {@code operator} ({@code >}, {@code >=}, {@code <}, {@code <=}, {@code =}, {@code !=}) and {@code value}.
 * <p>
 * Numeric comparison is tried first — that covers the overwhelming majority of quest-worthy placeholders
 * (balances, levels, playtime, stats). If either side doesn't parse as a plain number (no thousands
 * separators or currency symbols — pick a placeholder variant that returns a raw number, most plugins offer
 * one), it falls back to a case-insensitive string comparison, which only {@code =}/{@code !=} can
 * meaningfully answer: comparing non-numeric text with a magnitude operator isn't well-defined, so
 * {@code >}/{@code >=}/{@code <}/{@code <=} against non-numeric values are always false rather than
 * guessing.
 */
public final class PlaceholderMatcher {

    private PlaceholderMatcher() {
    }

    public static boolean isPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public static boolean matches(QuestObjective objective, Player player) {
        if (!isPresent()) {
            return false;
        }
        String resolved = PlaceholderAPI.setPlaceholders(player, objective.target());
        String operator = objective.meta().getOrDefault("operator", "=");
        String expected = objective.meta().getOrDefault("value", "");
        return compare(resolved, operator, expected);
    }

    /**
     * Package-visible so {@code PlaceholderMatcherTest} can exercise the comparison logic directly —
     * {@link #matches} needs a live PlaceholderAPI/Bukkit instance that plain JUnit doesn't have, but this
     * part is pure.
     */
    static boolean compare(String actual, String operator, String expected) {
        Double actualNumber = tryParse(actual);
        Double expectedNumber = tryParse(expected);
        if (actualNumber != null && expectedNumber != null) {
            int cmp = Double.compare(actualNumber, expectedNumber);
            return switch (operator) {
                case ">" -> cmp > 0;
                case ">=" -> cmp >= 0;
                case "<" -> cmp < 0;
                case "<=" -> cmp <= 0;
                case "!=" -> cmp != 0;
                default -> cmp == 0;
            };
        }
        return switch (operator) {
            case "!=" -> !actual.equalsIgnoreCase(expected);
            case ">", ">=", "<", "<=" -> false;
            default -> actual.equalsIgnoreCase(expected);
        };
    }

    private static Double tryParse(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
