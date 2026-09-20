package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.model.QuestReward;
import eu.purrtech.purrtechQuest.tracking.ItemMatcher;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Add reward" screen for one fixed permission bucket (the base bucket, or one rank tier — see
 * {@link QuestEditorRewardTiersGui}, which is where {@code rewards} is scoped from). {@code Item} rewards
 * accept a typed material, an {@code "ia:"}/{@code "oraxen:"}-prefixed custom item id, or (via
 * {@code ITEM_FROM_HAND}) whatever's currently in the admin's hand. {@code Import} copies an existing
 * reward from *any other* bucket into this one, for ranks that should get the same reward as another rank
 * without retyping it — {@code draft} is only needed for that picker to see every bucket.
 */
public final class QuestEditorRewardTypeGui extends Gui {

    private enum Choice {
        MONEY(Material.EMERALD), ITEM(Material.CHEST), ITEM_FROM_HAND(Material.HOPPER),
        COMMAND(Material.COMMAND_BLOCK), EXPERIENCE(Material.EXPERIENCE_BOTTLE),
        PERMISSION(Material.PAPER), IMPORT(Material.ENDER_CHEST);

        private final Material icon;

        Choice(Material icon) {
            this.icon = icon;
        }
    }

    private static final String MENU_ID = "quest-editor-reward-type";

    private final QuestEditorContext context;
    private final Player player;
    private final QuestDraft draft;
    private final List<QuestReward> rewards;
    private final Runnable onBack;
    private final int backSlot;
    private final int typeSlot;
    private final int continueSlot;
    private int choiceIndex;

    public QuestEditorRewardTypeGui(QuestEditorContext context, Player player, QuestDraft draft,
                                     List<QuestReward> rewards, Runnable onBack) {
        super(context.messages().render("quest.editor-button-add", player, Map.of()),
                context.menuLayouts().resolveSize(MENU_ID, 9));
        this.context = context;
        this.player = player;
        this.draft = draft;
        this.rewards = rewards;
        this.onBack = onBack;
        int size = getInventory().getSize();
        var menuLayouts = context.menuLayouts();
        this.backSlot = menuLayouts.resolveSlot(MENU_ID, "back", 0, size);
        this.typeSlot = menuLayouts.resolveSlot(MENU_ID, "type", 4, size);
        this.continueSlot = menuLayouts.resolveSlot(MENU_ID, "continue", 8, size);
        render();
    }

    private void render() {
        clear();
        var messages = context.messages();
        setItem(backSlot, GuiItems.icon(Material.ARROW, messages.render("quest.gui-button-back", player, Map.of()),
                        List.of(messages.render("quest.gui-button-back-hint", player, Map.of()))),
                event -> onBack.run());

        Choice choice = Choice.values()[choiceIndex];
        String choiceHintKey = "quest.editor-reward-type-hint-" + choice.name().toLowerCase(Locale.ROOT);
        setItem(typeSlot, GuiItems.icon(choice.icon,
                        messages.render("quest.editor-button-type", player, Map.of("%value%", choice.name())),
                        List.of(messages.render(choiceHintKey, player, Map.of()))),
                event -> {
                    choiceIndex = (choiceIndex + 1) % Choice.values().length;
                    render();
                });

        setItem(continueSlot, GuiItems.icon(Material.LIME_DYE, messages.render("quest.editor-button-continue", player, Map.of()),
                        List.of(messages.render("quest.editor-button-continue-hint", player, Map.of()))),
                event -> startWizard(choice));
    }

    private void startWizard(Choice choice) {
        var chatInput = context.chatInput();
        switch (choice) {
            case MONEY -> chatInput.promptDouble(player, "quest.editor-prompt-money-amount", amount -> {
                if (amount <= 0) {
                    invalidNumberThenRetry(() -> startWizard(Choice.MONEY));
                    return;
                }
                addNamed(new QuestReward.Money(amount));
            }, this::reopen);
            case ITEM -> chatInput.prompt(player, "quest.editor-prompt-item-material", raw -> {
                String normalized = raw.trim();
                String lower = normalized.toLowerCase(Locale.ROOT);
                if (lower.startsWith("ia:") || lower.startsWith("oraxen:")) {
                    promptItemAmountCustom(normalized);
                    return;
                }
                Material material = Material.matchMaterial(normalized);
                if (material == null) {
                    player.sendMessage(context.messages().render("quest.editor-invalid-material", player, Map.of()));
                    startWizard(Choice.ITEM);
                    return;
                }
                promptItemAmount(material);
            }, this::reopen);
            case ITEM_FROM_HAND -> startItemFromHand();
            case COMMAND -> chatInput.prompt(player, "quest.editor-prompt-command", command -> {
                addNamed(new QuestReward.Command(command));
            }, this::reopen);
            case EXPERIENCE -> chatInput.promptInt(player, "quest.editor-prompt-experience-amount", amount -> {
                if (amount <= 0) {
                    invalidNumberThenRetry(() -> startWizard(Choice.EXPERIENCE));
                    return;
                }
                addNamed(new QuestReward.Experience(amount));
            }, this::reopen);
            case PERMISSION -> chatInput.prompt(player, "quest.editor-prompt-permission-node", node -> {
                addNamed(new QuestReward.Permission(node, null));
            }, this::reopen);
            case IMPORT -> new QuestEditorRewardImportGui(context, player, draft, rewards, onBack, this::reopen).open(player);
        }
    }

    private void startItemFromHand() {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            player.sendMessage(context.messages().render("quest.editor-invalid-hand-item", player, Map.of()));
            return;
        }
        String resolved = ItemMatcher.resolveTarget(hand.clone());
        context.chatInput().promptInt(player, "quest.editor-prompt-item-amount", amount -> {
            if (amount <= 0) {
                invalidNumberThenRetry(this::startItemFromHand);
                return;
            }
            if (resolved.startsWith("ia:") || resolved.startsWith("oraxen:")) {
                addNamed(new QuestReward.Item("AIR", amount, resolved));
            } else {
                addNamed(new QuestReward.Item(resolved, amount, null));
            }
        }, this::reopen);
    }

    private void promptItemAmount(Material material) {
        context.chatInput().promptInt(player, "quest.editor-prompt-item-amount", amount -> {
            if (amount <= 0) {
                invalidNumberThenRetry(() -> promptItemAmount(material));
                return;
            }
            addNamed(new QuestReward.Item(material.name(), amount, null));
        }, this::reopen);
    }

    private void promptItemAmountCustom(String customId) {
        context.chatInput().promptInt(player, "quest.editor-prompt-item-amount", amount -> {
            if (amount <= 0) {
                invalidNumberThenRetry(() -> promptItemAmountCustom(customId));
                return;
            }
            addNamed(new QuestReward.Item("AIR", amount, customId));
        }, this::reopen);
    }

    /**
     * Last step for every reward type: the admin must give it a display name (shown to players in the quest
     * menu instead of a raw amount/material line) before it's actually added. Blank is rejected and
     * re-prompted; cancelling drops the half-built reward and returns to this screen.
     */
    private void addNamed(QuestReward reward) {
        context.chatInput().prompt(player, "quest.editor-prompt-reward-name", raw -> {
            String name = raw.trim();
            if (name.isEmpty()) {
                player.sendMessage(context.messages().render("quest.editor-invalid-reward-name", player, Map.of()));
                addNamed(reward);
                return;
            }
            rewards.add(reward.withName(name));
            onBack.run();
        }, this::reopen);
    }

    private void invalidNumberThenRetry(Runnable retry) {
        player.sendMessage(context.messages().render("quest.editor-invalid-number", player, Map.of()));
        retry.run();
    }

    private void reopen() {
        new QuestEditorRewardTypeGui(context, player, draft, rewards, onBack).open(player);
    }
}
