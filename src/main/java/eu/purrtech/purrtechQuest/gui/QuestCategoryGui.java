package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.model.ChoiceGroups;
import eu.purrtech.purrtechQuest.model.PlayerQuestData;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.service.QuestTrackingService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /quest gui}'s landing page: a compact menu holding just the "guide" item — overall turned-in/total
 * across every quest, then a breakdown of whichever quest the player currently has in progress (name,
 * description, live objective progress) — plus a close button. Clicking the guide opens {@link QuestLogGui}
 * with every quest. Size and slot placement default to the values below but are overridable per
 * {@value #MENU_ID} in {@code menus.yml} — see {@link MenuLayoutConfig}.
 */
public final class QuestCategoryGui extends Gui {

    private static final String MENU_ID = "quest-category";

    private final QuestService questService;
    private final PlayerQuestDataCache playerCache;
    private final QuestTrackingService trackingService;
    private final MenuLayoutConfig menuLayouts;
    private final MessagesConfig messages;
    private final Player player;
    private final int guideSlot;
    private final int closeSlot;

    public QuestCategoryGui(QuestService questService, PlayerQuestDataCache playerCache, QuestTrackingService trackingService,
                             MenuLayoutConfig menuLayouts, MessagesConfig messages, Player player) {
        super(messages.render("quest.gui-category-title", player, Map.of()), menuLayouts.resolveSize(MENU_ID, 27));
        this.questService = questService;
        this.playerCache = playerCache;
        this.trackingService = trackingService;
        this.menuLayouts = menuLayouts;
        this.messages = messages;
        this.player = player;
        int size = getInventory().getSize();
        this.guideSlot = menuLayouts.resolveSlot(MENU_ID, "guide", 3, size);
        this.closeSlot = menuLayouts.resolveSlot(MENU_ID, "close", 22, size);
        render();
    }

    private void render() {
        clear();
        setItem(guideSlot, guideIcon(), event ->
                new QuestLogGui(questService, playerCache, trackingService, menuLayouts, messages, player,
                        List.copyOf(questService.allQuests()), this::reopen).open(player));
        setItem(closeSlot, GuiItems.icon(Material.BARRIER, messages.render("quest.gui-button-close", player, Map.of()),
                        List.of(messages.render("quest.gui-button-close-hint", player, Map.of()))),
                event -> player.closeInventory());
    }

    /**
     * Overall turned-in/total across every quest, then a breakdown of whichever quest the player currently
     * has in progress (first match, same as {@link QuestService#currentActiveQuest}) — its name,
     * description, and live objective progress.
     */
    private ItemStack guideIcon() {
        Component name = messages.render("quest.guide-name", player, Map.of());
        List<Component> lore = new ArrayList<>();
        lore.add(messages.render("quest.guide-intro", player, Map.of()));

        QuestService.OverallSummary overall = questService.overallSummary(player);
        lore.add(messages.render("quest.guide-completed", player, Map.of(
                "%done%", String.valueOf(overall.turnedIn()), "%total%", String.valueOf(overall.total()))));
        lore.add(Component.empty());
        lore.add(messages.render("quest.guide-current-quest-header", player, Map.of()));

        Quest active = questService.currentActiveQuest(player).orElse(null);
        if (active == null) {
            lore.add(messages.render("quest.guide-no-active-quest", player, Map.of()));
        } else {
            lore.add(messages.render("quest.guide-current-quest-name", player, Map.of("%quest%", active.displayName())));
            if (!active.description().isBlank()) {
                lore.add(messages.render("quest.guide-current-quest-description", player,
                        Map.of("%description%", active.description())));
            }
            lore.add(Component.empty());
            lore.add(messages.render("quest.guide-progress-header", player, Map.of()));
            lore.addAll(objectiveProgressLines(active));
        }
        lore.add(Component.empty());
        lore.add(messages.render("quest.gui-lore-guide-hint", player, Map.of()));

        return GuiItems.icon(Material.SPYGLASS, name, lore);
    }

    private List<Component> objectiveProgressLines(Quest quest) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        QuestProgress progress = data == null ? null : data.progress(quest.id());
        List<Component> lines = new ArrayList<>();
        if (progress == null) {
            return lines;
        }
        List<QuestObjective> objectives = quest.objectives();
        Set<String> satisfiedGroups = ChoiceGroups.satisfiedGroups(quest, progress);
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            int current = progress.objectiveProgress(i);
            boolean satisfied = current >= objective.amount();
            if (objective.choiceGroup() != null && !satisfied && satisfiedGroups.contains(objective.choiceGroup())) {
                continue;
            }
            lines.add(messages.render("quest.guide-progress-line", player, Map.of(
                    "%label%", objective.label(),
                    "%progress%", String.valueOf(current),
                    "%amount%", String.valueOf(objective.amount()))));
        }
        return lines;
    }

    private void reopen() {
        new QuestCategoryGui(questService, playerCache, trackingService, menuLayouts, messages, player).open(player);
    }
}
