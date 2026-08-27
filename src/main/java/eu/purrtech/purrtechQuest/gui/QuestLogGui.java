package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.model.PlayerQuestData;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.model.QuestStatus;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.service.QuestStatusText;
import eu.purrtech.purrtechQuest.service.QuestTrackingService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * A list of quests — every known quest in one category ({@link QuestCategoryGui}, the normal way in), or a
 * curated list an NPC gives ({@code npc.QuestGiverInteraction}, which has no category concept and passes
 * {@code onBack = null} for that reason, same as before categories existed). Sorted by
 * {@link Quest#DISPLAY_ORDER} (admin-assigned number first, alphabetical fallback/tie-break) regardless of
 * whatever order the caller's list happened to be in. The bottom row is always reserved for prev/back/next,
 * so how many quests fit per page scales with {@value #MENU_ID}'s size in {@code menus.yml} (54 slots, the
 * default, means 45 per page). Opens {@link QuestDetailGui} on click. Completed ({@code TURNED_IN}) and
 * not-yet-completed quests are listed together rather than split behind a tab — the different book material
 * per {@link QuestStatus} ({@link QuestIcons#materialFor(QuestStatus)}) is what tells them apart at a glance.
 */
public final class QuestLogGui extends Gui {

    private static final String MENU_ID = "quest-log";

    private final QuestService questService;
    private final PlayerQuestDataCache playerCache;
    private final QuestTrackingService trackingService;
    private final MenuLayoutConfig menuLayouts;
    private final MessagesConfig messages;
    private final Player player;
    private final List<Quest> quests;
    private final Runnable onBack;
    private final int pageSize;
    private final int prevSlot;
    private final int backSlot;
    private final int nextSlot;
    private int page;

    /** {@code onBack} may be {@code null} — no back button is shown then (the NPC-linked, category-less flow). */
    public QuestLogGui(QuestService questService, PlayerQuestDataCache playerCache, QuestTrackingService trackingService,
                        MenuLayoutConfig menuLayouts, MessagesConfig messages, Player player, List<Quest> quests,
                        Runnable onBack) {
        super(messages.render("quest.gui-log-title", player, Map.of()), menuLayouts.resolveSize(MENU_ID, 54));
        this.questService = questService;
        this.playerCache = playerCache;
        this.trackingService = trackingService;
        this.menuLayouts = menuLayouts;
        this.messages = messages;
        this.player = player;
        this.quests = quests.stream().sorted(Quest.DISPLAY_ORDER).toList();
        this.onBack = onBack;
        this.page = 0;
        int size = getInventory().getSize();
        // The bottom row is reserved for prev/back/next regardless of size, so the content area (and the
        // built-in fallback slots below, if menus.yml doesn't override them) scale with a custom size too
        // instead of only ever making sense at the default 54.
        this.pageSize = size - 9;
        this.prevSlot = menuLayouts.resolveSlot(MENU_ID, "prev-page", size - 9, size);
        this.backSlot = menuLayouts.resolveSlot(MENU_ID, "back", size - 7, size);
        this.nextSlot = menuLayouts.resolveSlot(MENU_ID, "next-page", size - 1, size);
        render();
    }

    private void render() {
        clear();
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        int from = page * pageSize;
        int to = Math.min(from + pageSize, quests.size());

        for (int i = from; i < to; i++) {
            Quest quest = quests.get(i);
            QuestProgress progress = data == null ? null : data.progress(quest.id());
            setItem(i - from, questIcon(quest, progress), event ->
                    new QuestDetailGui(questService, playerCache, trackingService, menuLayouts, messages, player,
                            quest.id(), this::reopen).open(player));
        }

        for (int slot = pageSize; slot < getInventory().getSize(); slot++) {
            setItem(slot, GuiItems.filler(), null);
        }
        if (page > 0) {
            setItem(prevSlot, GuiItems.icon(Material.ARROW,
                    messages.render("quest.gui-button-prev-page", player, Map.of()),
                    List.of(messages.render("quest.gui-button-prev-page-hint", player, Map.of()))),
                    event -> {
                        page--;
                        render();
                    });
        }
        if (to < quests.size()) {
            setItem(nextSlot, GuiItems.icon(Material.ARROW,
                    messages.render("quest.gui-button-next-page", player, Map.of()),
                    List.of(messages.render("quest.gui-button-next-page-hint", player, Map.of()))),
                    event -> {
                        page++;
                        render();
                    });
        }
        if (onBack != null) {
            setItem(backSlot, GuiItems.icon(Material.ARROW, messages.render("quest.gui-button-back", player, Map.of()),
                            List.of(messages.render("quest.gui-button-back-hint", player, Map.of()))),
                    event -> onBack.run());
        }
    }

    private ItemStack questIcon(Quest quest, QuestProgress progress) {
        QuestStatus status = progress == null ? QuestStatus.NOT_ACCEPTED : progress.status();
        Material material = QuestIcons.materialFor(status);
        String statusText = messages.get(QuestStatusText.key(progress), player.locale().getLanguage());

        Component name = Component.text(quest.displayName(), NamedTextColor.GOLD);
        List<Component> lore = List.of(Component.text(statusText, NamedTextColor.GRAY),
                messages.render("quest.gui-lore-quest-hint", player, Map.of()));
        return GuiItems.icon(material, name, lore);
    }

    private void reopen() {
        new QuestLogGui(questService, playerCache, trackingService, menuLayouts, messages, player, quests, onBack)
                .open(player);
    }
}
