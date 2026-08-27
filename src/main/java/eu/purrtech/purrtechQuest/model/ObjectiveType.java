package eu.purrtech.purrtechQuest.model;

public enum ObjectiveType {
    KILL_ENTITY,
    BREAK_BLOCK,
    PLACE_BLOCK,
    COLLECT_ITEM,
    CRAFT_ITEM,
    FISH,
    REACH_LOCATION,
    INTERACT_BLOCK,
    TALK_TO_NPC,
    /**
     * Cumulative in-game currency spent, reported by {@code tracking.ExcellentShopSpendListener}. There's
     * no meaningful "target" to match on (a currency amount isn't tied to a specific item) so every
     * objective of this type uses the same fixed {@link #SPEND_MONEY_TARGET} placeholder.
     */
    SPEND_MONEY,
    /**
     * Satisfied when a PlaceholderAPI placeholder ({@code target}, e.g. {@code "%vault_eco_balance%"})
     * compares favorably against a value, per {@code tracking.PlaceholderMatcher}. {@code meta} holds
     * {@code "operator"} (one of {@code >}, {@code >=}, {@code <}, {@code <=}, {@code =}, {@code !=}) and
     * {@code "value"} (the right-hand side of the comparison). Polled periodically rather than event-driven
     * — a placeholder's underlying value can change from basically anything, so there's no single Bukkit
     * event to hook — same reasoning as {@link #REACH_LOCATION}, just on a timer instead of on movement.
     */
    PLACEHOLDER_CHECK,
    /**
     * A MythicMobs boss ({@code target} = its internal mob name) dying counts for every player who dealt it
     * any damage during its lifetime, not just whoever landed the final blow — see
     * {@code tracking.BossDamageTracker}. This is deliberately a separate type from {@link #KILL_ENTITY}
     * (which only credits the killer): a group boss fight where only the tank ever lands the kill shouldn't
     * lock everyone else out of the quest.
     */
    DEFEAT_BOSS,
    /**
     * Satisfied once the player's Vault economy balance rises by {@code amount} above whatever it was when
     * they accepted the quest — that starting balance is snapshotted once at acceptance as the objective's
     * baseline (see {@code QuestService#captureEarnMoneyBaselines}), then compared against the live balance
     * on each poll (see {@code tracking.EarnMoneyMatcher}). Not a cumulative income ledger: spend back below
     * the target gain after reaching it and progress reflects that, same as SPEND_MONEY, target has no
     * meaning here either, so every objective of this type uses the same fixed {@link #EARN_MONEY_TARGET}
     * placeholder.
     */
    EARN_MONEY,
    CUSTOM;

    public static final String SPEND_MONEY_TARGET = "ANY";
    public static final String EARN_MONEY_TARGET = "ANY";
}
