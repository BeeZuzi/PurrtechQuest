package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.model.ChoiceGroups;
import eu.purrtech.purrtechQuest.model.PlayerQuestData;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.model.QuestReward;
import eu.purrtech.purrtechQuest.model.QuestRewardTier;
import eu.purrtech.purrtechQuest.model.QuestStatus;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.service.QuestFeedback;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.service.QuestStatusText;
import eu.purrtech.purrtechQuest.service.QuestTrackingService;
import eu.purrtech.purrtechQuest.util.DurationFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A single quest's detail view: description, objectives with live progress, rewards, and whichever
 * action buttons make sense for the player's current status (accept/abandon/turn in/track).
 */
public final class QuestDetailGui extends Gui {

    private static final String MENU_ID = "quest-detail";

    private final QuestService questService;
    private final PlayerQuestDataCache playerCache;
    private final QuestTrackingService trackingService;
    private final MenuLayoutConfig menuLayouts;
    private final MessagesConfig messages;
    private final Player player;
    private final String questId;
    private final Runnable onBack;
    private final int infoSlot;
    private final int rewardsSlot;
    private final int backSlot;
    private final int primaryActionSlot;
    private final int turnInSlot;
    private final int trackSlot;
    private final int closeSlot;

    public QuestDetailGui(QuestService questService, PlayerQuestDataCache playerCache,
                           QuestTrackingService trackingService, MenuLayoutConfig menuLayouts,
                           MessagesConfig messages, Player player, String questId, Runnable onBack) {
        super(messages.render("quest.gui-detail-title", player,
                        Map.of("%quest%", questService.quest(questId).map(Quest::displayName).orElse(questId))),
                menuLayouts.resolveSize(MENU_ID, 27));
        this.questService = questService;
        this.playerCache = playerCache;
        this.trackingService = trackingService;
        this.menuLayouts = menuLayouts;
        this.messages = messages;
        this.player = player;
        this.questId = questId;
        this.onBack = onBack;
        int size = getInventory().getSize();
        this.infoSlot = menuLayouts.resolveSlot(MENU_ID, "info", 11, size);
        this.rewardsSlot = menuLayouts.resolveSlot(MENU_ID, "rewards", 15, size);
        this.backSlot = menuLayouts.resolveSlot(MENU_ID, "back", 18, size);
        this.primaryActionSlot = menuLayouts.resolveSlot(MENU_ID, "primary-action", 20, size);
        this.turnInSlot = menuLayouts.resolveSlot(MENU_ID, "turn-in", 22, size);
        this.trackSlot = menuLayouts.resolveSlot(MENU_ID, "track", 24, size);
        this.closeSlot = menuLayouts.resolveSlot(MENU_ID, "close", 26, size);
        render();
    }

    private void render() {
        clear();
        Quest quest = questService.quest(questId).orElse(null);
        if (quest == null) {
            setItem(backSlot, backButtonIcon(), event -> onBack.run());
            return;
        }

        PlayerQuestData data = playerCache.get(player.getUniqueId());
        QuestProgress progress = data == null ? null : data.progress(questId);
        QuestStatus status = progress == null ? QuestStatus.NOT_ACCEPTED : progress.status();

        fillEmptySlots(menuLayouts, 0, getInventory().getSize());

        setItem(infoSlot, infoIcon(quest, progress, status), null);
        setItem(rewardsSlot, rewardsIcon(quest), null);
        setItem(backSlot, backButtonIcon(), event -> onBack.run());
        setItem(closeSlot,
                GuiItems.icon(Material.BARRIER, messages.render("quest.gui-button-close", player, Map.of()),
                        List.of(messages.render("quest.gui-button-close-hint", player, Map.of()))),
                event -> player.closeInventory());

        renderActions(quest, progress, status);
    }

    private void renderActions(Quest quest, QuestProgress progress, QuestStatus status) {
        switch (status) {
            case NOT_ACCEPTED -> setItem(primaryActionSlot,
                    GuiItems.icon(Material.LIME_DYE, messages.render("quest.gui-button-accept", player, Map.of()),
                            List.of(messages.render("quest.gui-button-accept-hint", player, Map.of()))),
                    event -> {
                        QuestService.AcceptResult result = questService.acceptQuest(player, questId);
                        QuestFeedback.report(player, result, questId, questService, messages);
                        render();
                    });
            case IN_PROGRESS -> {
                setItem(primaryActionSlot,
                        GuiItems.icon(Material.RED_DYE, messages.render("quest.gui-button-abandon", player, Map.of()),
                                List.of(messages.render("quest.gui-button-abandon-hint", player, Map.of()))),
                        event -> {
                            QuestService.AbandonResult result = questService.abandonQuest(player, questId);
                            QuestFeedback.report(player, result, messages);
                            render();
                        });
                boolean tracking = trackingService.isTracking(player, questId);
                String trackKey = tracking ? "quest.gui-button-untrack" : "quest.gui-button-track";
                String trackHintKey = tracking ? "quest.gui-button-untrack-hint" : "quest.gui-button-track-hint";
                setItem(trackSlot, GuiItems.icon(Material.COMPASS, messages.render(trackKey, player, Map.of()),
                                List.of(messages.render(trackHintKey, player, Map.of()))),
                        event -> {
                            if (tracking) {
                                trackingService.untrack(player);
                            } else {
                                trackingService.track(player, questId);
                            }
                            render();
                        });
            }
            case COMPLETED -> setItem(turnInSlot,
                    GuiItems.icon(Material.GOLD_INGOT, messages.render("quest.gui-button-turnin", player, Map.of()),
                            List.of(messages.render("quest.gui-button-turnin-hint", player, Map.of()))),
                    event -> {
                        QuestService.TurnInResult result = questService.turnIn(player, questId);
                        QuestFeedback.report(player, result, questId, messages);
                        render();
                    });
            case TURNED_IN -> renderReacceptOrCooldown(quest);
        }
    }

    private void renderReacceptOrCooldown(Quest quest) {
        if (!quest.repeatable()) {
            return;
        }
        Instant readyAt = questService.cooldownReadyAt(player, questId).orElse(null);
        if (readyAt == null || !Instant.now().isBefore(readyAt)) {
            setItem(primaryActionSlot,
                    GuiItems.icon(Material.LIME_DYE, messages.render("quest.gui-button-accept", player, Map.of()),
                            List.of(messages.render("quest.gui-button-accept-hint", player, Map.of()))),
                    event -> {
                        QuestService.AcceptResult result = questService.acceptQuest(player, questId);
                        QuestFeedback.report(player, result, questId, questService, messages);
                        render();
                    });
        } else {
            String time = DurationFormat.humanReadable(Duration.between(Instant.now(), readyAt));
            setItem(primaryActionSlot,
                    GuiItems.icon(Material.CLOCK, messages.render("quest.gui-button-accept", player, Map.of()),
                            List.of(messages.render("quest.gui-lore-cooldown", player, Map.of("%time%", time)))),
                    null);
        }
    }

    private ItemStack backButtonIcon() {
        return GuiItems.icon(Material.ARROW, messages.render("quest.gui-button-back", player, Map.of()),
                List.of(messages.render("quest.gui-button-back-hint", player, Map.of())));
    }

    private ItemStack infoIcon(Quest quest, QuestProgress progress, QuestStatus status) {
        Material material = QuestIcons.materialFor(status);
        Component name = Component.text(quest.displayName(), NamedTextColor.GOLD);

        List<Component> lore = new ArrayList<>();
        if (!quest.description().isBlank()) {
            lore.add(Component.text(quest.description(), NamedTextColor.GRAY));
            lore.add(Component.empty());
        }
        String statusText = messages.get(QuestStatusText.key(progress), player.locale().getLanguage());
        lore.add(messages.render("quest.gui-lore-status", player, Map.of("%status%", statusText)));
        lore.add(Component.empty());
        lore.add(messages.render("quest.gui-lore-objectives-header", player, Map.of()));

        List<QuestObjective> objectives = quest.objectives();
        Set<String> satisfiedGroups = progress == null ? Set.of() : ChoiceGroups.satisfiedGroups(quest, progress);
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            int current = progress == null ? 0 : progress.objectiveProgress(i);
            boolean satisfied = current >= objective.amount();
            if (objective.choiceGroup() != null && !satisfied && satisfiedGroups.contains(objective.choiceGroup())) {
                lore.add(messages.render("quest.gui-lore-objective-locked", player, Map.of(
                        "%label%", objective.label())));
                continue;
            }
            Map<String, String> placeholders = Map.of(
                    "%label%", objective.label(),
                    "%progress%", String.valueOf(current),
                    "%amount%", String.valueOf(objective.amount()));
            String lineKey = objective.choiceGroup() != null
                    ? "quest.gui-lore-objective-choice-line"
                    : "quest.gui-lore-objective-line";
            lore.add(messages.render(lineKey, player, placeholders));
        }

        return GuiItems.icon(material, name, lore);
    }

    /**
     * Base rewards are always listed under their own header; each rank tier the player currently holds the
     * permission for gets its own header (using that tier's admin-set {@code displayName}, not the raw
     * permission node) plus its actual reward lines — a tier the player doesn't qualify for isn't shown at
     * all, so this never spoils rewards they can't get.
     */
    private ItemStack rewardsIcon(Quest quest) {
        Component name = messages.render("quest.gui-lore-rewards-header", player, Map.of());
        List<Component> lore = new ArrayList<>();

        List<Component> baseLines = quest.rewards().stream().map(this::rewardLine).toList();
        if (!baseLines.isEmpty()) {
            lore.add(messages.render("quest.gui-lore-reward-section-base", player, Map.of()));
            lore.addAll(baseLines);
        }

        for (QuestRewardTier tier : quest.rewardTiers()) {
            if (!player.hasPermission(tier.permission())) {
                continue;
            }
            List<Component> tierLines = tier.rewards().stream().map(this::rewardLine).toList();
            if (tierLines.isEmpty()) {
                continue;
            }
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.add(messages.render("quest.gui-lore-reward-section-tier", player, Map.of("%tier%", tier.displayName())));
            lore.addAll(tierLines);
        }

        if (lore.isEmpty()) {
            lore.add(messages.render("quest.gui-lore-reward-bonus", player, Map.of()));
        }
        return GuiItems.icon(Material.CHEST, name, lore);
    }

    private Component rewardLine(QuestReward reward) {
        return switch (reward) {
            case QuestReward.Money money ->
                    messages.render("quest.gui-lore-reward-money", player, Map.of("%amount%", String.valueOf(money.amount())));
            case QuestReward.Item item ->
                    messages.render("quest.gui-lore-reward-item", player,
                            Map.of("%amount%", String.valueOf(item.amount()), "%material%", item.material()));
            case QuestReward.Experience experience ->
                    messages.render("quest.gui-lore-reward-experience", player, Map.of("%amount%", String.valueOf(experience.amount())));
            case QuestReward.Command ignored -> messages.render("quest.gui-lore-reward-bonus", player, Map.of());
            case QuestReward.Permission ignored -> messages.render("quest.gui-lore-reward-bonus", player, Map.of());
        };
    }
}
