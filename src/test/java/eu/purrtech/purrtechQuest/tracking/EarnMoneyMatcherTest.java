package eu.purrtech.purrtechQuest.tracking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link EarnMoneyMatcher#balanceOf}/{@link EarnMoneyMatcher#isPresent} need a live Vault/Bukkit instance
 * (same limitation as everywhere else in this test suite), so this exercises {@link
 * EarnMoneyMatcher#clampedProgress} directly — the pure part that turns a live balance and an
 * acceptance-time baseline into an EARN_MONEY objective's progress value.
 */
class EarnMoneyMatcherTest {

    @Test
    void belowTargetProgressesTowardsIt() {
        // Baseline 1000, balance 1650 — 650 earned towards a 1000 target.
        assertEquals(650, EarnMoneyMatcher.clampedProgress(1650.0, 1000.0, 1000));
    }

    @Test
    void atOrAboveTargetClampsToTheTarget() {
        assertEquals(1000, EarnMoneyMatcher.clampedProgress(2000.0, 1000.0, 1000));
        assertEquals(1000, EarnMoneyMatcher.clampedProgress(6000.0, 1000.0, 1000));
    }

    @Test
    void zeroBaselineBehavesAsAnAbsoluteBalanceCheck() {
        // A player who never had a baseline recorded (e.g. pre-existing progress from before this feature)
        // falls back to baseline 0 — earning from nothing is the same as reaching the raw balance.
        assertEquals(650, EarnMoneyMatcher.clampedProgress(650.0, 0.0, 1000));
    }

    @Test
    void netLossSinceBaselineFloorsToZeroRatherThanNegativeProgress() {
        assertEquals(0, EarnMoneyMatcher.clampedProgress(750.0, 1000.0, 1000));
    }

    @Test
    void spendingBackBelowTargetGainReducesProgress() {
        // The whole point of EARN_MONEY is a live balance check against the baseline, not a cumulative
        // ledger — going from "reached the target" back down must be reflected, not stuck at its high-water
        // mark.
        int atTarget = EarnMoneyMatcher.clampedProgress(2000.0, 1000.0, 1000);
        int afterSpending = EarnMoneyMatcher.clampedProgress(1400.0, 1000.0, 1000);
        assertEquals(1000, atTarget);
        assertEquals(400, afterSpending);
    }

    @Test
    void fractionalGainTruncatesRatherThanRounds() {
        // Whole-number progress, same "good enough" precision tradeoff as SPEND_MONEY.
        assertEquals(650, EarnMoneyMatcher.clampedProgress(1650.9, 1000.0, 1000));
    }
}
