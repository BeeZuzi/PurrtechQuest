package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.model.QuestReward;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Lists the rewards belonging to a single permission bucket (the base bucket, when {@code tier} is
 * {@code null}, or one named rank tier — see {@link QuestEditorRewardTiersGui}, which is where this screen
 * is reached from). Same remove-and-re-add editing model as {@link QuestEditorObjectivesGui}. Adding a
 * reward ({@link QuestEditorRewardTypeGui}) can also import an existing reward from a *different* bucket,
 * so {@code draft} is threaded through even though this screen itself only touches one bucket's rewards.
 */
public final class QuestEditorRewardsGui extends Gui {

    private static final String MENU_ID = "quest-editor-rewards";

    private final QuestEditorContext context;
    private final Player player;
    private final QuestDraft draft;
    private final QuestDraft.RewardTierDraft tier;
    private final List<QuestReward> rewards;
    private final Runnable onBack;
    private final int contentCap;
    private final int backSlot;
    private final int addSlot;
    private final int nameSlot;

    public QuestEditorRewardsGui(QuestEditorContext context, Player player, QuestDraft draft,
                                  QuestDraft.RewardTierDraft tier, Runnable onBack) {
        super(context.messages().render("quest.editor-button-rewards-for", player,
                Map.of("%bucket%", bucketLabel(context, player, tier), "%count%",
                        String.valueOf((tier == null ? draft.rewards() : tier.rewards()).size()))),
                context.menuLayouts().resolveSize(MENU_ID, 27));
        this.context = context;
        this.player = player;
        this.draft = draft;
        this.tier = tier;
        this.rewards = tier == null ? draft.rewards() : tier.rewards();
        this.onBack = onBack;
        int size = getInventory().getSize();
        var menuLayouts = context.menuLayouts();
        this.contentCap = size - 9;
        this.backSlot = menuLayouts.resolveSlot(MENU_ID, "back", size - 9, size);
        this.addSlot = menuLayouts.resolveSlot(MENU_ID, "add", size - 5, size);
        this.nameSlot = menuLayouts.resolveSlot(MENU_ID, "name", size - 1, size);
        render();
    }

    private static String bucketLabel(QuestEditorContext context, Player player, QuestDraft.RewardTierDraft tier) {
        return tier == null
                ? context.messages().get("quest.editor-reward-tier-base", player.locale().getLanguage())
                : tier.displayName();
    }

    private void render() {
        clear();
        var messages = context.messages();
        for (int i = 0; i < rewards.size() && i < contentCap; i++) {
            QuestReward reward = rewards.get(i);
            int index = i;
            setItem(i, rewardIcon(reward), event -> {
                if (event.isRightClick()) {
                    rewards.remove(index);
                    reopen();
                }
            });
        }

        setItem(backSlot, GuiItems.icon(Material.ARROW, messages.render("quest.gui-button-back", player, Map.of()),
                        List.of(messages.render("quest.gui-button-back-hint", player, Map.of()))),
                event -> onBack.run());
        setItem(addSlot, GuiItems.icon(Material.LIME_DYE, messages.render("quest.editor-button-add", player, Map.of()),
                        List.of(messages.render("quest.editor-rewards-add-hint", player, Map.of()))),
                event -> new QuestEditorRewardTypeGui(context, player, draft, rewards, this::reopen).open(player));

        if (tier != null) {
            setItem(nameSlot, GuiItems.icon(Material.NAME_TAG,
                            messages.render("quest.editor-button-reward-tier-name", player, Map.of("%value%", tier.displayName())),
                            List.of(messages.render("quest.editor-reward-bucket-line", player, Map.of("%bucket%", tier.permission())),
                                    messages.render("quest.editor-button-reward-tier-name-hint", player, Map.of()))),
                    event -> context.chatInput().prompt(player, "quest.editor-prompt-reward-tier-name", value -> {
                        tier.displayName(value);
                        reopen();
                    }, this::reopen));
        }
    }

    private org.bukkit.inventory.ItemStack rewardIcon(QuestReward reward) {
        var messages = context.messages();
        Material material;
        String labelKey;
        Map<String, String> placeholders;
        Component name;

        switch (reward) {
            case QuestReward.Money money -> {
                material = Material.EMERALD;
                labelKey = "quest.editor-reward-line-money";
                placeholders = Map.of("%amount%", String.valueOf(money.amount()));
                name = typeName("quest.reward-type-money");
            }
            case QuestReward.Item item -> {
                material = Material.CHEST;
                labelKey = "quest.editor-reward-line-item";
                placeholders = Map.of("%amount%", String.valueOf(item.amount()),
                        "%material%", item.customId() != null ? item.customId() : item.material());
                name = typeName("quest.reward-type-item");
            }
            case QuestReward.Command command -> {
                material = Material.COMMAND_BLOCK;
                labelKey = "quest.editor-reward-line-command";
                placeholders = Map.of("%command%", command.command());
                name = typeName("quest.reward-type-command");
            }
            case QuestReward.Experience experience -> {
                material = Material.EXPERIENCE_BOTTLE;
                labelKey = "quest.editor-reward-line-experience";
                placeholders = Map.of("%amount%", String.valueOf(experience.amount()));
                name = typeName("quest.reward-type-experience");
            }
            case QuestReward.Permission permission -> {
                material = Material.PAPER;
                labelKey = "quest.editor-reward-line-permission";
                placeholders = Map.of("%node%", permission.node());
                name = typeName("quest.reward-type-permission");
            }
        }

        List<Component> lore = List.of(
                messages.render(labelKey, player, placeholders),
                messages.render("quest.editor-remove-hint", player, Map.of()));
        return GuiItems.icon(material, name, lore);
    }

    private Component typeName(String key) {
        return Component.text(context.messages().get(key, player.locale().getLanguage()), NamedTextColor.GOLD);
    }

    private void reopen() {
        new QuestEditorRewardsGui(context, player, draft, tier, onBack).open(player);
    }
}
