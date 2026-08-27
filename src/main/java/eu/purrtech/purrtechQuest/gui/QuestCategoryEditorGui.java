package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.model.QuestCategoryConfig;
import eu.purrtech.purrtechQuest.tracking.ItemMatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /questadmin category <name>} — toggles which of the three lore lines {@link QuestCategoryGui}
 * shows for one category, its description text, which slot its button sits on, and which item represents
 * it. Mutates a local in-memory draft (mirroring {@link QuestEditorGui}'s pattern) that's only written to
 * {@code categories.yml} on Save; closing the inventory or hitting Cancel discards it.
 */
public final class QuestCategoryEditorGui extends Gui {

    private static final String MENU_ID = "quest-category-editor";

    private final QuestEditorContext context;
    private final Player player;
    private final String categoryId;
    private final int descriptionSlot;
    private final int showActiveQuestSlot;
    private final int showProgressSlot;
    private final int showDescriptionSlot;
    private final int slotButtonSlot;
    private final int iconButtonSlot;
    private final int saveSlot;
    private final int cancelSlot;
    private String description;
    private boolean showActiveQuest;
    private boolean showProgress;
    private boolean showDescription;
    private Integer categorySlot;
    private String icon;

    public QuestCategoryEditorGui(QuestEditorContext context, Player player, String categoryId) {
        super(context.messages().render("quest.category-editor-title", player, Map.of("%category%", categoryId)),
                context.menuLayouts().resolveSize(MENU_ID, 18));
        this.context = context;
        this.player = player;
        this.categoryId = categoryId;
        QuestCategoryConfig config = context.categoryConfigRepository().get(categoryId);
        this.description = config.description();
        this.showActiveQuest = config.showActiveQuest();
        this.showProgress = config.showProgress();
        this.showDescription = config.showDescription();
        this.categorySlot = config.slot();
        this.icon = config.icon();
        int size = getInventory().getSize();
        var menuLayouts = context.menuLayouts();
        this.descriptionSlot = menuLayouts.resolveSlot(MENU_ID, "description", 1, size);
        this.showActiveQuestSlot = menuLayouts.resolveSlot(MENU_ID, "show-active-quest", 3, size);
        this.showProgressSlot = menuLayouts.resolveSlot(MENU_ID, "show-progress", 5, size);
        this.showDescriptionSlot = menuLayouts.resolveSlot(MENU_ID, "show-description", 7, size);
        this.slotButtonSlot = menuLayouts.resolveSlot(MENU_ID, "slot", 9, size);
        this.saveSlot = menuLayouts.resolveSlot(MENU_ID, "save", 11, size);
        this.iconButtonSlot = menuLayouts.resolveSlot(MENU_ID, "icon", 13, size);
        this.cancelSlot = menuLayouts.resolveSlot(MENU_ID, "cancel", 15, size);
        render();
    }

    private void render() {
        clear();
        var messages = context.messages();
        var chatInput = context.chatInput();

        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            setItem(slot, GuiItems.filler(), null);
        }

        setItem(descriptionSlot, GuiItems.icon(Material.WRITTEN_BOOK,
                        messages.render("quest.editor-button-description", player, Map.of()),
                        List.of(Component.text(description, NamedTextColor.GRAY),
                                messages.render("quest.category-editor-description-hint", player, Map.of()))),
                event -> chatInput.prompt(player, "quest.category-editor-prompt-description", value -> {
                    description = value;
                    reopen();
                }, this::reopen));

        setItem(showActiveQuestSlot, boolToggle("quest.category-editor-button-show-active-quest",
                        "quest.category-editor-show-active-quest-hint", showActiveQuest),
                event -> {
                    showActiveQuest = !showActiveQuest;
                    reopen();
                });

        setItem(showProgressSlot, boolToggle("quest.category-editor-button-show-progress",
                        "quest.category-editor-show-progress-hint", showProgress),
                event -> {
                    showProgress = !showProgress;
                    reopen();
                });

        setItem(showDescriptionSlot, boolToggle("quest.category-editor-button-show-description",
                        "quest.category-editor-show-description-hint", showDescription),
                event -> {
                    showDescription = !showDescription;
                    reopen();
                });

        String slotValue = categorySlot == null
                ? messages.get("quest.bool-no", player.locale().getLanguage())
                : String.valueOf(categorySlot);
        setItem(slotButtonSlot, GuiItems.icon(Material.HOPPER,
                        messages.render("quest.category-editor-button-slot", player, Map.of("%value%", slotValue)),
                        List.of(messages.render("quest.category-editor-slot-hint", player, Map.of()))),
                event -> chatInput.promptInt(player, "quest.category-editor-prompt-slot", value -> {
                    int menuSize = context.menuLayouts().resolveSize("quest-category", 27);
                    if (value < 0 || value >= menuSize) {
                        player.sendMessage(messages.render("quest.editor-invalid-number", player, Map.of()));
                        reopen();
                        return;
                    }
                    categorySlot = value;
                    reopen();
                }, this::reopen));

        setItem(iconButtonSlot, GuiItems.icon(icon,
                        messages.render("quest.category-editor-button-icon", player, Map.of("%value%", icon)),
                        List.of(messages.render("quest.category-editor-icon-hint", player, Map.of()))),
                event -> {
                    if (event.isRightClick()) {
                        ItemStack hand = player.getInventory().getItemInMainHand();
                        if (hand.getType() == Material.AIR) {
                            player.sendMessage(messages.render("quest.editor-invalid-hand-item", player, Map.of()));
                            return;
                        }
                        icon = ItemMatcher.resolveTarget(hand.clone());
                        reopen();
                        return;
                    }
                    chatInput.prompt(player, "quest.category-editor-prompt-icon", raw -> {
                        String typed = raw.trim();
                        String lower = typed.toLowerCase(Locale.ROOT);
                        if (lower.startsWith("ia:") || lower.startsWith("oraxen:")) {
                            icon = lower;
                            reopen();
                            return;
                        }
                        Material material = Material.matchMaterial(typed);
                        if (material == null) {
                            player.sendMessage(messages.render("quest.editor-invalid-material", player, Map.of()));
                            reopen();
                            return;
                        }
                        icon = material.name();
                        reopen();
                    }, this::reopen);
                });

        setItem(saveSlot, GuiItems.icon(Material.EMERALD_BLOCK, messages.render("quest.editor-button-save", player, Map.of()),
                        List.of(messages.render("quest.editor-button-save-hint", player, Map.of()))),
                event -> save());

        setItem(cancelSlot, GuiItems.icon(Material.BARRIER, messages.render("quest.editor-button-cancel", player, Map.of()),
                        List.of(messages.render("quest.editor-button-cancel-hint", player, Map.of()))),
                event -> player.closeInventory());
    }

    private org.bukkit.inventory.ItemStack boolToggle(String labelKey, String hintKey, boolean value) {
        var messages = context.messages();
        String valueText = messages.get(value ? "quest.bool-yes" : "quest.bool-no", player.locale().getLanguage());
        Material material = value ? Material.LIME_DYE : Material.GRAY_DYE;
        return GuiItems.icon(material, messages.render(labelKey, player, Map.of("%value%", valueText)),
                List.of(messages.render(hintKey, player, Map.of())));
    }

    private void save() {
        context.categoryConfigRepository().save(
                new QuestCategoryConfig(categoryId, description, showActiveQuest, showProgress, showDescription,
                        categorySlot, icon));
        player.sendMessage(context.messages().render("quest.category-editor-saved", player, Map.of("%category%", categoryId)));
        player.closeInventory();
    }

    private void reopen() {
        new QuestCategoryEditorGui(context, player, categoryId).open(player);
    }
}
