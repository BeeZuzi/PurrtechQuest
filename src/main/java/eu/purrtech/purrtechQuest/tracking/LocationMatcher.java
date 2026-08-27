package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.QuestObjective;
import org.bukkit.Location;

/**
 * Checks a REACH_LOCATION objective against a player's current position. {@code target} holds the world
 * name; {@code x}/{@code y}/{@code z}/{@code radius} live in {@code meta} (radius defaults to 5 blocks).
 */
public final class LocationMatcher {

    private static final double DEFAULT_RADIUS = 5.0;

    private LocationMatcher() {
    }

    public static boolean isWithin(QuestObjective objective, Location playerLocation) {
        if (playerLocation.getWorld() == null
                || !playerLocation.getWorld().getName().equalsIgnoreCase(objective.target())) {
            return false;
        }

        double x = requireDouble(objective, "x");
        double y = requireDouble(objective, "y");
        double z = requireDouble(objective, "z");
        double radius = objective.meta().containsKey("radius") ? requireDouble(objective, "radius") : DEFAULT_RADIUS;

        double dx = playerLocation.getX() - x;
        double dy = playerLocation.getY() - y;
        double dz = playerLocation.getZ() - z;
        return (dx * dx + dy * dy + dz * dz) <= radius * radius;
    }

    private static double requireDouble(QuestObjective objective, String key) {
        String raw = objective.meta().get(key);
        if (raw == null) {
            throw new IllegalStateException(
                    "REACH_LOCATION objective (world '" + objective.target() + "') is missing meta key '" + key + "'");
        }
        return Double.parseDouble(raw);
    }
}
