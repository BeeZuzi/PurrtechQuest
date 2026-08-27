package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.integration.VaultEconomyHook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

/**
 * Resolves a player's current Vault balance for EARN_MONEY objectives — "earn" here means gaining
 * {@code target} on top of the balance the player had when they accepted the quest (that starting balance
 * is snapshotted once, at acceptance, as the objective's baseline; see
 * {@code QuestService#captureEarnMoneyBaselines}), checked live on each poll (see
 * {@code QuestService#checkEarnMoneyObjectives}), not a one-time cumulative income ledger: if a player
 * spends back below the target after reaching it, progress reflects that, the same way asking "how much
 * have I gained since I started" would answer differently before and after a purchase.
 */
public final class EarnMoneyMatcher {

    private EarnMoneyMatcher() {
    }

    public static boolean isPresent() {
        return VaultEconomyHook.resolve() != null;
    }

    /** Only call once {@link #isPresent()} is true — there's no economy to ask otherwise. */
    public static double balanceOf(Player player) {
        return VaultEconomyHook.resolve().getBalance(player);
    }

    /**
     * How much of an EARN_MONEY objective's {@code target} gain is satisfied right now — {@code baseline}
     * is the player's balance at quest acceptance, so {@code balance - baseline} is what they've earned
     * since then. A net loss (balance below baseline) floors to 0 rather than counting as negative
     * progress, and a gain past the target clamps to the target rather than overshooting it.
     */
    public static int clampedProgress(double balance, double baseline, int target) {
        return (int) Math.min(Math.max(balance - baseline, 0), target);
    }
}
