package eu.purrtech.purrtechQuest.service;

import eu.purrtech.purrtechQuest.integration.ItemsAdderHook;
import eu.purrtech.purrtechQuest.integration.OraxenHook;
import eu.purrtech.purrtechQuest.integration.VaultEconomyHook;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestReward;
import eu.purrtech.purrtechQuest.model.QuestRewardTier;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Actually grants {@link QuestReward}s. Must be called from the main thread (inventory access, Vault
 * calls, and command dispatch all require it). {@code Permission} rewards still aren't wired up — nothing
 * in this codebase depends on a permissions plugin's API, and that's a bigger integration than the
 * RPG/NPC scope this reward type was added for.
 */
public final class RewardService {

    /**
     * Base rewards are gated behind this node too (not just tiers) — it defaults to {@code true} for
     * everyone in {@code paper-plugin.yml}, so in practice every player gets them, but an admin can revoke
     * it from a specific player to cut off quest rewards entirely without touching every quest file.
     */
    public static final String BASE_REWARD_PERMISSION = "purrtechquest.reward";

    /**
     * Rank-tier permissions are namespaced under this prefix — the editor only prompts an admin for the
     * short suffix (e.g. {@code "king"}) and builds the full node from this, so every quest's tiers stay
     * consistently named without relying on admins typing the full node correctly each time.
     */
    public static final String REWARD_TIER_PERMISSION_PREFIX = "purrtechquest.reward.";

    private final Logger logger;

    public RewardService(Logger logger) {
        this.logger = logger;
    }

    /**
     * Grants a quest's base rewards (if the player holds {@link #BASE_REWARD_PERMISSION}) plus, for each
     * {@link QuestRewardTier} whose permission the player holds, that tier's rewards on top — a player
     * matching multiple tiers (e.g. inheriting ranks) gets all of them, not just the best match.
     */
    public void grant(Player player, Quest quest) {
        if (player.hasPermission(BASE_REWARD_PERMISSION)) {
            grant(player, quest.rewards());
        }
        for (QuestRewardTier tier : quest.rewardTiers()) {
            if (player.hasPermission(tier.permission())) {
                grant(player, tier.rewards());
            }
        }
    }

    public void grant(Player player, List<QuestReward> rewards) {
        for (QuestReward reward : rewards) {
            switch (reward) {
                case QuestReward.Money money -> grantMoney(player, money);
                case QuestReward.Item item -> grantItem(player, item);
                case QuestReward.Command command -> grantCommand(player, command);
                case QuestReward.Experience experience -> player.giveExp(experience.amount());
                case QuestReward.Permission ignored ->
                        logger.warning("Permission rewards aren't supported yet, skipping for " + player.getName());
            }
        }
    }

    private void grantMoney(Player player, QuestReward.Money money) {
        Economy economy = VaultEconomyHook.resolve();
        if (economy == null) {
            logger.warning("Quest tried to grant a money reward but no Vault economy is registered "
                    + "(reward skipped for " + player.getName() + ")");
            return;
        }
        economy.depositPlayer(player, money.amount());
    }

    private void grantItem(Player player, QuestReward.Item item) {
        ItemStack stack = item.customId() != null
                ? resolveCustomItem(item.customId(), item.amount())
                : vanillaItem(item);
        if (stack == null) {
            logger.warning("Could not resolve item reward (" +
                    (item.customId() != null ? item.customId() : item.material())
                    + "), skipping for " + player.getName());
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private static ItemStack vanillaItem(QuestReward.Item item) {
        Material material = Material.matchMaterial(item.material());
        return material == null ? null : new ItemStack(material, item.amount());
    }

    /**
     * {@code customId} follows the same {@code "ia:"}/{@code "oraxen:"} prefix convention as
     * {@link eu.purrtech.purrtechQuest.tracking.ItemMatcher#resolveTarget} uses for objective targets.
     */
    private static ItemStack resolveCustomItem(String customId, int amount) {
        if (customId.startsWith("ia:")) {
            return ItemsAdderHook.createItem(customId.substring("ia:".length()), amount);
        }
        if (customId.startsWith("oraxen:")) {
            return OraxenHook.createItem(customId.substring("oraxen:".length()), amount);
        }
        return null;
    }

    private void grantCommand(Player player, QuestReward.Command command) {
        String parsed = command.command().replace("%player%", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
    }
}
