package eu.purrtech.purrtechQuest.model;

import java.util.List;
import java.util.Objects;

/**
 * Extra {@link QuestReward}s granted on top of a quest's base rewards, gated behind a permission node
 * (typically a rank, e.g. {@code "purrtechquest.rank.vip"}). Evaluated with a plain
 * {@code player.hasPermission(...)} check, so it works against whatever permission plugin the server runs
 * without a direct dependency on any of them. Multiple tiers can match the same player at once — if a
 * server's ranks are additive (VIP+ also holds the VIP node) that grants both, which is deliberate rather
 * than a "highest tier only" pick, since exclusivity is easy to get with mutually exclusive nodes but the
 * reverse isn't.
 * <p>
 * {@code displayName} is purely cosmetic — shown to players/admins instead of the raw permission string
 * (e.g. {@code "King"} instead of {@code "purrtechquest.reward.king"}) — and falls back to the permission
 * itself when not set, so it's never blank.
 */
public record QuestRewardTier(String permission, String displayName, List<QuestReward> rewards) {

    public QuestRewardTier {
        Objects.requireNonNull(permission, "permission");
        if (permission.isBlank()) {
            throw new IllegalArgumentException("permission must not be blank");
        }
        displayName = (displayName == null || displayName.isBlank()) ? permission : displayName;
        if (rewards == null || rewards.isEmpty()) {
            throw new IllegalArgumentException("reward tier '" + permission + "' must have at least one reward");
        }
        rewards = List.copyOf(rewards);
    }
}
