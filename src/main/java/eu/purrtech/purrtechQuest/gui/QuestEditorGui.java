package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.model.Quest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Editor hub for one {@link QuestDraft}: name/description/category via chat prompts, links to the
 * objectives/rewards/prerequisites sub-screens, boolean toggles, and Save/Cancel. Nothing is written to
 * disk until Save — closing the inventory or disconnecting mid-edit just discards the draft. Size and slot
 * placement default to the values below but are overridable per {@value #MENU_ID} in {@code menus.yml}.
 */
public final class QuestEditorGui extends Gui {

    private static final String MENU_ID = "quest-editor";

    private final QuestEditorContext context;
    private final Player player;
    private final QuestDraft draft;
    private final int nameSlot;
    private final int descriptionSlot;
    private final int categorySlot;
    private final int sortOrderSlot;
    private final int objectivesSlot;
    private final int rewardsSlot;
    private final int prerequisitesSlot;
    private final int repeatableSlot;
    private final int cooldownSlot;
    private final int autostartSlot;
    private final int autoturninSlot;
    private final int npcSlot;
    private final int permissionSlot;
    private final int saveSlot;
    private final int cancelSlot;

    public QuestEditorGui(QuestEditorContext context, Player player, QuestDraft draft) {
        super(context.messages().render("quest.editor-title", player, Map.of("%quest%", draft.id())),
                context.menuLayouts().resolveSize(MENU_ID, 36));
        this.context = context;
        this.player = player;
        this.draft = draft;
        int size = getInventory().getSize();
        var menuLayouts = context.menuLayouts();
        this.nameSlot = menuLayouts.resolveSlot(MENU_ID, "name", 1, size);
        this.descriptionSlot = menuLayouts.resolveSlot(MENU_ID, "description", 3, size);
        this.categorySlot = menuLayouts.resolveSlot(MENU_ID, "category", 5, size);
        this.sortOrderSlot = menuLayouts.resolveSlot(MENU_ID, "sort-order", 7, size);
        this.objectivesSlot = menuLayouts.resolveSlot(MENU_ID, "objectives", 10, size);
        this.rewardsSlot = menuLayouts.resolveSlot(MENU_ID, "rewards", 13, size);
        this.prerequisitesSlot = menuLayouts.resolveSlot(MENU_ID, "prerequisites", 16, size);
        this.repeatableSlot = menuLayouts.resolveSlot(MENU_ID, "repeatable", 19, size);
        this.cooldownSlot = menuLayouts.resolveSlot(MENU_ID, "cooldown", 21, size);
        this.autostartSlot = menuLayouts.resolveSlot(MENU_ID, "autostart", 23, size);
        this.autoturninSlot = menuLayouts.resolveSlot(MENU_ID, "autoturnin", 25, size);
        this.npcSlot = menuLayouts.resolveSlot(MENU_ID, "npc", 27, size);
        this.permissionSlot = menuLayouts.resolveSlot(MENU_ID, "permission", 29, size);
        this.saveSlot = menuLayouts.resolveSlot(MENU_ID, "save", 31, size);
        this.cancelSlot = menuLayouts.resolveSlot(MENU_ID, "cancel", 33, size);
        render();
    }

    private void render() {
        clear();
        var messages = context.messages();
        var chatInput = context.chatInput();

        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            setItem(slot, GuiItems.filler(), null);
        }

        setItem(nameSlot, GuiItems.icon(Material.NAME_TAG,
                        messages.render("quest.editor-button-name", player, Map.of("%value%", draft.displayName())),
                        List.of(messages.render("quest.editor-button-name-hint", player, Map.of()))),
                event -> chatInput.prompt(player, "quest.editor-prompt-name", value -> {
                    draft.displayName(value);
                    reopen();
                }, this::reopen));

        setItem(descriptionSlot, GuiItems.icon(Material.WRITTEN_BOOK,
                        messages.render("quest.editor-button-description", player, Map.of()),
                        List.of(Component.text(draft.description(), NamedTextColor.GRAY),
                                messages.render("quest.editor-button-description-hint", player, Map.of()))),
                event -> chatInput.prompt(player, "quest.editor-prompt-description", value -> {
                    draft.description(value);
                    reopen();
                }, this::reopen));

        setItem(categorySlot, GuiItems.icon(Material.ITEM_FRAME,
                        messages.render("quest.editor-button-category", player, Map.of("%value%", draft.category())),
                        List.of(messages.render("quest.editor-button-category-hint", player, Map.of()))),
                event -> chatInput.prompt(player, "quest.editor-prompt-category", value -> {
                    draft.category(value);
                    reopen();
                }, this::reopen));

        setItem(sortOrderSlot, GuiItems.icon(Material.HOPPER,
                        messages.render("quest.editor-button-sort-order", player, Map.of("%value%", sortOrderValue())),
                        List.of(messages.render("quest.editor-button-sort-order-hint", player, Map.of()))),
                event -> chatInput.prompt(player, "quest.editor-prompt-sort-order", value -> {
                    String typed = value.trim();
                    if (typed.isEmpty() || typed.equalsIgnoreCase("none")) {
                        draft.sortOrder(null);
                        reopen();
                        return;
                    }
                    try {
                        draft.sortOrder(Integer.parseInt(typed));
                    } catch (NumberFormatException e) {
                        player.sendMessage(messages.render("quest.editor-invalid-number", player, Map.of()));
                    }
                    reopen();
                }, this::reopen));

        setItem(objectivesSlot, GuiItems.icon(Material.TARGET,
                        messages.render("quest.editor-button-objectives", player,
                                Map.of("%count%", String.valueOf(draft.objectives().size()))),
                        List.of(messages.render("quest.editor-button-objectives-hint", player, Map.of()))),
                event -> new QuestEditorObjectivesGui(context, player, draft).open(player));

        setItem(rewardsSlot, GuiItems.icon(Material.GOLD_INGOT,
                        messages.render("quest.editor-button-rewards", player,
                                Map.of("%count%", String.valueOf(draft.totalRewardCount()))),
                        List.of(messages.render("quest.editor-button-rewards-hint", player, Map.of()))),
                event -> new QuestEditorRewardTiersGui(context, player, draft, this::reopen).open(player));

        setItem(prerequisitesSlot, GuiItems.icon(Material.BOOK,
                        messages.render("quest.editor-button-prerequisites", player,
                                Map.of("%count%", String.valueOf(draft.requiredQuests().size()))),
                        List.of(messages.render("quest.editor-button-prerequisites-hint", player, Map.of()))),
                event -> new QuestEditorPrerequisitesGui(context, player, draft).open(player));

        setItem(repeatableSlot, boolToggle("quest.editor-button-repeatable", "quest.editor-button-repeatable-hint", draft.repeatable()),
                event -> {
                    draft.repeatable(!draft.repeatable());
                    reopen();
                });

        setItem(cooldownSlot, GuiItems.icon(Material.CLOCK,
                        messages.render("quest.editor-button-cooldown", player,
                                Map.of("%value%", String.valueOf(draft.cooldownSeconds()))),
                        List.of(messages.render("quest.editor-button-cooldown-hint", player, Map.of()))),
                event -> chatInput.promptInt(player, "quest.editor-prompt-cooldown", value -> {
                    draft.cooldownSeconds(Math.max(0, value));
                    reopen();
                }, this::reopen));

        setItem(autostartSlot, boolToggle("quest.editor-button-autostart", "quest.editor-button-autostart-hint", draft.autoStart()),
                event -> {
                    draft.autoStart(!draft.autoStart());
                    reopen();
                });

        setItem(autoturninSlot, boolToggle("quest.editor-button-autoturnin", "quest.editor-button-autoturnin-hint", draft.autoTurnIn()),
                event -> {
                    draft.autoTurnIn(!draft.autoTurnIn());
                    reopen();
                });

        setItem(npcSlot, GuiItems.icon(Material.VILLAGER_SPAWN_EGG,
                        messages.render("quest.editor-button-npc", player, Map.of("%value%", npcValue())),
                        List.of(messages.render("quest.editor-npc-hint", player, Map.of()))),
                event -> player.sendMessage(messages.render("quest.editor-npc-hint", player, Map.of())));

        setItem(permissionSlot, GuiItems.icon(Material.TRIPWIRE_HOOK,
                        messages.render("quest.editor-button-permission", player, Map.of("%value%", permissionValue())),
                        List.of(messages.render("quest.editor-button-permission-hint", player, Map.of()))),
                event -> chatInput.prompt(player, "quest.editor-prompt-permission", value -> {
                    String typed = value.trim();
                    draft.requiredPermission((typed.isEmpty() || typed.equalsIgnoreCase("none")) ? null : typed);
                    reopen();
                }, this::reopen));

        setItem(saveSlot, GuiItems.icon(Material.EMERALD_BLOCK, messages.render("quest.editor-button-save", player, Map.of()),
                        List.of(messages.render("quest.editor-button-save-hint", player, Map.of()))),
                event -> save());

        setItem(cancelSlot, GuiItems.icon(Material.BARRIER, messages.render("quest.editor-button-cancel", player, Map.of()),
                        List.of(messages.render("quest.editor-button-cancel-hint", player, Map.of()))),
                event -> player.closeInventory());
    }

    private String npcValue() {
        var giver = draft.questGiver();
        if (giver == null) {
            return context.messages().get("quest.npc-none", player.locale().getLanguage());
        }
        return giver.provider().name() + " #" + giver.npcId();
    }

    private String permissionValue() {
        return draft.requiredPermission() == null
                ? context.messages().get("quest.bool-no", player.locale().getLanguage())
                : draft.requiredPermission();
    }

    private String sortOrderValue() {
        return draft.sortOrder() == null
                ? context.messages().get("quest.bool-no", player.locale().getLanguage())
                : String.valueOf(draft.sortOrder());
    }

    private org.bukkit.inventory.ItemStack boolToggle(String labelKey, String hintKey, boolean value) {
        var messages = context.messages();
        String valueText = messages.get(value ? "quest.bool-yes" : "quest.bool-no", player.locale().getLanguage());
        Material material = value ? Material.LIME_DYE : Material.GRAY_DYE;
        return GuiItems.icon(material, messages.render(labelKey, player, Map.of("%value%", valueText)),
                List.of(messages.render(hintKey, player, Map.of())));
    }

    private void save() {
        MessagesConfig messages = context.messages();
        Quest quest;
        try {
            quest = draft.toQuest();
        } catch (IllegalArgumentException e) {
            player.sendMessage(messages.render("quest.editor-save-failed", player, Map.of("%reason%", String.valueOf(e.getMessage()))));
            return;
        }
        context.questRepository().save(quest);
        context.questService().reload();
        context.trackerRegistrationManager().refresh();
        context.questPermissionRegistrar().refresh();
        player.sendMessage(messages.render("quest.editor-saved", player, Map.of("%quest%", quest.displayName())));
        player.closeInventory();
    }

    private void reopen() {
        new QuestEditorGui(context, player, draft).open(player);
    }
}
