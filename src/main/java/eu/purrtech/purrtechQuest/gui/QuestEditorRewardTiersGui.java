package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.service.RewardService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Landing screen for a quest's rewards: every permission bucket a reward can be gated behind — the base
 * bucket ({@link RewardService#BASE_REWARD_PERMISSION}, always present, not removable) plus any number of
 * named tiers the admin adds (e.g. {@code "king"} → {@code purrtechquest.reward.king}, built automatically
 * from {@link RewardService#REWARD_TIER_PERMISSION_PREFIX} so admins only ever type the short suffix).
 * Clicking a bucket opens {@link QuestEditorRewardsGui} scoped to just that bucket's rewards; right-clicking
 * a named tier removes it (and its rewards) instead.
 */
public final class QuestEditorRewardTiersGui extends Gui {

    private static final String MENU_ID = "quest-editor-reward-tiers";

    private final QuestEditorContext context;
    private final Player player;
    private final QuestDraft draft;
    private final Runnable onBack;
    private final int baseSlot;
    private final int contentCap;
    private final int backSlot;
    private final int addSlot;

    public QuestEditorRewardTiersGui(QuestEditorContext context, Player player, QuestDraft draft, Runnable onBack) {
        super(context.messages().render("quest.editor-button-reward-tiers", player, Map.of()),
                context.menuLayouts().resolveSize(MENU_ID, 27));
        this.context = context;
        this.player = player;
        this.draft = draft;
        this.onBack = onBack;
        int size = getInventory().getSize();
        var menuLayouts = context.menuLayouts();
        this.baseSlot = menuLayouts.resolveSlot(MENU_ID, "base", 0, size);
        this.contentCap = size - 9;
        this.backSlot = menuLayouts.resolveSlot(MENU_ID, "back", size - 9, size);
        this.addSlot = menuLayouts.resolveSlot(MENU_ID, "add", size - 5, size);
        render();
    }

    private void render() {
        clear();
        var messages = context.messages();
        String baseLabel = messages.get("quest.editor-reward-tier-base", player.locale().getLanguage());

        setItem(baseSlot, bucketIcon(RewardService.BASE_REWARD_PERMISSION, baseLabel, draft.rewards().size()),
                event -> new QuestEditorRewardsGui(context, player, draft, null, this::reopen).open(player));

        List<QuestDraft.RewardTierDraft> tiers = draft.rewardTiers();
        for (int i = 0; i < tiers.size() && i < contentCap - 1; i++) {
            QuestDraft.RewardTierDraft tier = tiers.get(i);
            int index = i;
            setItem(baseSlot + 1 + i, bucketIcon(tier.permission(), tier.displayName(), tier.rewards().size()), event -> {
                if (event.isRightClick()) {
                    tiers.remove(index);
                    render();
                    return;
                }
                new QuestEditorRewardsGui(context, player, draft, tier, this::reopen).open(player);
            });
        }

        setItem(backSlot, GuiItems.icon(Material.ARROW, messages.render("quest.gui-button-back", player, Map.of()),
                        List.of(messages.render("quest.gui-button-back-hint", player, Map.of()))),
                event -> onBack.run());
        setItem(addSlot, GuiItems.icon(Material.LIME_DYE, messages.render("quest.editor-button-add", player, Map.of()),
                        List.of(messages.render("quest.editor-tiers-add-hint", player, Map.of()))),
                event -> promptNewTier());
    }

    private void promptNewTier() {
        context.chatInput().prompt(player, "quest.editor-prompt-reward-tier-permission", raw -> {
            String suffix = raw.trim();
            if (suffix.isBlank()) {
                player.sendMessage(context.messages().render("quest.editor-invalid-permission-node", player, Map.of()));
                promptNewTier();
                return;
            }
            String permission = RewardService.REWARD_TIER_PERMISSION_PREFIX + suffix;
            draft.rewardTiers().add(new QuestDraft.RewardTierDraft(permission, suffix));
            reopen();
        }, this::reopen);
    }

    private org.bukkit.inventory.ItemStack bucketIcon(String permission, String displayName, int rewardCount) {
        var messages = context.messages();
        boolean isBase = permission.equals(RewardService.BASE_REWARD_PERMISSION);
        Component name = Component.text(displayName, NamedTextColor.AQUA);
        Component permissionLine = messages.render("quest.editor-reward-bucket-line", player, Map.of("%bucket%", permission));
        Component countLine = messages.render("quest.editor-reward-tier-line", player,
                Map.of("%count%", String.valueOf(rewardCount)));
        Component openHint = messages.render("quest.editor-reward-tier-open-hint", player, Map.of());
        List<Component> lore = isBase
                ? List.of(countLine, messages.render("quest.editor-reward-tier-base-hint", player, Map.of()), openHint)
                : List.of(permissionLine, countLine, openHint, messages.render("quest.editor-remove-hint", player, Map.of()));
        return GuiItems.icon(isBase ? Material.IRON_INGOT : Material.DIAMOND, name, lore);
    }

    private void reopen() {
        new QuestEditorRewardTiersGui(context, player, draft, onBack).open(player);
    }
}
