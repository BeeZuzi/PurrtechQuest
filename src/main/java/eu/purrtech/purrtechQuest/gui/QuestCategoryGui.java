package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.model.PlayerQuestData;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestCategoryConfig;
import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.service.QuestTrackingService;
import eu.purrtech.purrtechQuest.storage.CategoryConfigRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /quest gui}'s landing page: one button per distinct {@link Quest#category()} in use, each opening
 * {@link QuestLogGui} scoped to that category's quests. Quests left in {@link Quest#DEFAULT_CATEGORY} (never
 * explicitly categorized) either show as their own individual icons directly on this screen (the default —
 * see {@code categories.others-as-category} in {@code config.yml}) or get grouped behind their own "Others"
 * category button, same as any category an admin creates. With no categories in use at all, this degrades to
 * exactly a flat list of every quest, so nothing extra is added for servers that don't use categories.
 * <p>
 * Each category's slot and icon are admin-configurable per category id via {@code /questadmin category
 * <name>} (see {@link QuestCategoryEditorGui}, backed by {@link CategoryConfigRepository}) — a category with
 * no explicit slot set, or whose slot collides with another category's, auto-fills the next free slot
 * instead. Size and the close button's placement default to the values below but are overridable per
 * {@value #MENU_ID} in {@code menus.yml} — see {@link MenuLayoutConfig}.
 */
public final class QuestCategoryGui extends Gui {

    private static final String MENU_ID = "quest-category";

    private final QuestService questService;
    private final PlayerQuestDataCache playerCache;
    private final QuestTrackingService trackingService;
    private final MenuLayoutConfig menuLayouts;
    private final CategoryConfigRepository categoryConfigRepository;
    private final boolean othersAsCategory;
    private final MessagesConfig messages;
    private final Player player;
    private final int closeSlot;

    public QuestCategoryGui(QuestService questService, PlayerQuestDataCache playerCache, QuestTrackingService trackingService,
                             MenuLayoutConfig menuLayouts, CategoryConfigRepository categoryConfigRepository,
                             boolean othersAsCategory, MessagesConfig messages, Player player) {
        super(messages.render("quest.gui-category-title", player, Map.of()), menuLayouts.resolveSize(MENU_ID, 27));
        this.questService = questService;
        this.playerCache = playerCache;
        this.trackingService = trackingService;
        this.menuLayouts = menuLayouts;
        this.categoryConfigRepository = categoryConfigRepository;
        this.othersAsCategory = othersAsCategory;
        this.messages = messages;
        this.player = player;
        int size = getInventory().getSize();
        this.closeSlot = menuLayouts.resolveSlot(MENU_ID, "close", 22, size);
        render();
    }

    private void render() {
        clear();
        int size = getInventory().getSize();
        boolean[] occupied = new boolean[size];
        occupied[closeSlot] = true;

        Set<String> allCategories = questService.categories();
        List<String> shownCategories = new ArrayList<>();
        List<Quest> looseQuests = List.of();
        if (othersAsCategory) {
            shownCategories.addAll(allCategories);
        } else {
            for (String category : allCategories) {
                if (!category.equals(Quest.DEFAULT_CATEGORY)) {
                    shownCategories.add(category);
                }
            }
            looseQuests = questService.questsInCategory(Quest.DEFAULT_CATEGORY).stream()
                    .sorted(Quest.DISPLAY_ORDER).toList();
        }

        // Pass 1: categories with a valid, still-free explicit slot claim it. Pass 2 (below) auto-fills
        // everything else - an unset slot, or one that lost the race to another category's explicit claim.
        Map<String, Integer> placedSlots = new LinkedHashMap<>();
        List<String> needsAutoSlot = new ArrayList<>();
        for (String category : shownCategories) {
            Integer slot = categoryConfigRepository.get(category).slot();
            if (slot != null && slot >= 0 && slot < size && !occupied[slot]) {
                occupied[slot] = true;
                placedSlots.put(category, slot);
            } else {
                needsAutoSlot.add(category);
            }
        }
        int cursor = 0;
        for (String category : needsAutoSlot) {
            cursor = nextFreeSlot(occupied, cursor);
            if (cursor >= size) {
                break; // more categories than free slots - silently drop the overflow, same as other lists
            }
            occupied[cursor] = true;
            placedSlots.put(category, cursor);
        }

        for (Map.Entry<String, Integer> entry : placedSlots.entrySet()) {
            String category = entry.getKey();
            int slot = entry.getValue();
            QuestCategoryConfig config = categoryConfigRepository.get(category);
            setItem(slot, categoryIcon(category, config), event ->
                    new QuestLogGui(questService, playerCache, trackingService, menuLayouts, messages, player,
                            questService.questsInCategory(category), this::reopen).open(player));
        }

        if (!othersAsCategory) {
            PlayerQuestData data = playerCache.get(player.getUniqueId());
            for (Quest quest : looseQuests) {
                cursor = nextFreeSlot(occupied, cursor);
                if (cursor >= size) {
                    break;
                }
                occupied[cursor] = true;
                QuestProgress progress = data == null ? null : data.progress(quest.id());
                setItem(cursor, QuestLogGui.questIcon(quest, progress, messages, player), event ->
                        new QuestDetailGui(questService, playerCache, trackingService, menuLayouts, messages, player,
                                quest.id(), this::reopen).open(player));
            }
        }

        fillEmptySlots(menuLayouts, occupied);

        setItem(closeSlot, GuiItems.icon(Material.BARRIER, messages.render("quest.gui-button-close", player, Map.of()),
                        List.of(messages.render("quest.gui-button-close-hint", player, Map.of()))),
                event -> player.closeInventory());
    }

    private static int nextFreeSlot(boolean[] occupied, int from) {
        int i = from;
        while (i < occupied.length && occupied[i]) {
            i++;
        }
        return i;
    }

    /**
     * A category's button: its name (the raw category id, or the localized "Others" name for
     * {@link Quest#DEFAULT_CATEGORY}), then whichever of description/progress/active-quest lines
     * {@code config}'s toggles allow — the same content the old single "guide" item used to show globally,
     * now per category.
     */
    private ItemStack categoryIcon(String categoryId, QuestCategoryConfig config) {
        Component name = categoryId.equals(Quest.DEFAULT_CATEGORY)
                ? messages.render("quest.category-default-name", player, Map.of())
                : Component.text(categoryId, NamedTextColor.GOLD);

        List<Component> lore = new ArrayList<>();
        if (config.showDescription() && !config.description().isBlank()) {
            lore.add(Component.text(config.description(), NamedTextColor.GRAY));
        }
        QuestService.CategorySummary summary = questService.categorySummary(player, categoryId);
        if (config.showProgress()) {
            lore.add(messages.render("quest.category-progress", player, Map.of(
                    "%done%", String.valueOf(summary.turnedIn()), "%total%", String.valueOf(summary.total()))));
        }
        if (config.showActiveQuest()) {
            lore.add(summary.activeQuest() == null
                    ? messages.render("quest.category-no-active-quest", player, Map.of())
                    : messages.render("quest.category-active-quest", player, Map.of("%quest%", summary.activeQuest().displayName())));
        }
        lore.add(messages.render("quest.gui-lore-category-hint", player, Map.of()));

        return GuiItems.icon(config.icon(), name, lore);
    }

    private void reopen() {
        new QuestCategoryGui(questService, playerCache, trackingService, menuLayouts, categoryConfigRepository,
                othersAsCategory, messages, player).open(player);
    }
}
