package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Field-by-field edit menu for one existing objective (reached by left-clicking it in
 * {@link QuestEditorObjectivesGui}) — every value (label, type, target/value, amount, choice group) has its
 * own button and can be changed independently, instead of the remove-and-re-add-only model the rest of this
 * editor otherwise uses. "Type" and "Value" both delegate to {@link QuestEditorObjectiveTypeGui} (the same
 * wizard the "Add" flow uses) since target/amount/meta collection is inherently type-specific; "Type" shows
 * the picker so the admin can also change type, "Value" jumps straight to the current type's collection step.
 * Both replace the objective at {@code index} in place, preserving whichever of label/choice group wasn't
 * being edited.
 */
public final class QuestEditorObjectiveEditGui extends Gui {

    private static final String MENU_ID = "quest-editor-objective-edit";

    private final QuestEditorContext context;
    private final Player player;
    private final QuestDraft draft;
    private final int index;
    private final int labelSlot;
    private final int typeSlot;
    private final int valueSlot;
    private final int amountSlot;
    private final int choiceGroupSlot;
    private final int backSlot;

    public QuestEditorObjectiveEditGui(QuestEditorContext context, Player player, QuestDraft draft, int index) {
        super(context.messages().render("quest.editor-objective-edit-title", player, Map.of()),
                context.menuLayouts().resolveSize(MENU_ID, 27));
        this.context = context;
        this.player = player;
        this.draft = draft;
        this.index = index;
        int size = getInventory().getSize();
        var menuLayouts = context.menuLayouts();
        this.labelSlot = menuLayouts.resolveSlot(MENU_ID, "label", 1, size);
        this.typeSlot = menuLayouts.resolveSlot(MENU_ID, "type", 3, size);
        this.valueSlot = menuLayouts.resolveSlot(MENU_ID, "value", 5, size);
        this.amountSlot = menuLayouts.resolveSlot(MENU_ID, "amount", 7, size);
        this.choiceGroupSlot = menuLayouts.resolveSlot(MENU_ID, "choice-group", 11, size);
        this.backSlot = menuLayouts.resolveSlot(MENU_ID, "back", 18, size);
        render();
    }

    private void render() {
        clear();
        var messages = context.messages();
        var chatInput = context.chatInput();
        QuestObjective objective = draft.objectives().get(index);

        setItem(labelSlot, GuiItems.icon(Material.NAME_TAG,
                        messages.render("quest.editor-button-name", player, Map.of("%value%", objective.label())),
                        List.of(messages.render("quest.editor-objective-edit-label-hint", player, Map.of()))),
                event -> chatInput.prompt(player, "quest.editor-prompt-objective-label", raw -> {
                    String label = raw.trim();
                    if (label.isEmpty()) {
                        player.sendMessage(messages.render("quest.editor-invalid-label", player, Map.of()));
                        reopen();
                        return;
                    }
                    QuestObjective current = draft.objectives().get(index);
                    replace(current.type(), current.target(), current.amount(), current.meta(), current.choiceGroup(), label);
                }, this::reopen));

        setItem(typeSlot, GuiItems.icon(QuestIcons.materialFor(objective.type()),
                        messages.render("quest.editor-button-type", player, Map.of("%value%", objective.type().name())),
                        List.of(messages.render("quest.editor-objective-edit-type-hint", player, Map.of()))),
                event -> new QuestEditorObjectiveTypeGui(context, player, draft, index).open(player));

        setItem(valueSlot, GuiItems.icon(Material.COMPASS,
                        messages.render("quest.editor-button-value", player, Map.of("%value%", objective.target())),
                        List.of(messages.render("quest.editor-objective-edit-value-hint", player, Map.of()))),
                event -> QuestEditorObjectiveTypeGui.openValueWizard(context, player, draft, index));

        if (amountEditable(objective.type())) {
            setItem(amountSlot, GuiItems.icon(Material.HOPPER,
                            messages.render("quest.editor-button-amount", player, Map.of("%value%", String.valueOf(objective.amount()))),
                            List.of(messages.render("quest.editor-objective-edit-amount-hint", player, Map.of()))),
                    event -> chatInput.promptInt(player, "quest.editor-prompt-amount", value -> {
                        if (value <= 0) {
                            player.sendMessage(messages.render("quest.editor-invalid-number", player, Map.of()));
                            reopen();
                            return;
                        }
                        QuestObjective current = draft.objectives().get(index);
                        replace(current.type(), current.target(), value, current.meta(), current.choiceGroup(), current.label());
                    }, this::reopen));
        } else {
            setItem(amountSlot, GuiItems.filler(), null);
        }

        String choiceGroupValue = objective.choiceGroup() == null
                ? messages.get("quest.bool-no", player.locale().getLanguage())
                : objective.choiceGroup();
        setItem(choiceGroupSlot, GuiItems.icon(Material.LEAD,
                        messages.render("quest.editor-button-choice-group", player, Map.of("%value%", choiceGroupValue)),
                        List.of(messages.render("quest.editor-objective-edit-choice-group-hint", player, Map.of()))),
                event -> chatInput.prompt(player, "quest.editor-prompt-choice-group", raw -> {
                    String typed = raw.trim();
                    String choiceGroup = (typed.isEmpty() || typed.equalsIgnoreCase("none")) ? null : typed;
                    QuestObjective current = draft.objectives().get(index);
                    replace(current.type(), current.target(), current.amount(), current.meta(), choiceGroup, current.label());
                }, this::reopen));

        setItem(backSlot, GuiItems.icon(Material.ARROW, messages.render("quest.gui-button-back", player, Map.of()),
                        List.of(messages.render("quest.gui-button-back-hint", player, Map.of()))),
                event -> new QuestEditorObjectivesGui(context, player, draft).open(player));
    }

    /** REACH_LOCATION and PLACEHOLDER_CHECK are satisfied/not-satisfied checks — their amount is fixed at 1, not an independent value. */
    private static boolean amountEditable(ObjectiveType type) {
        return type != ObjectiveType.REACH_LOCATION && type != ObjectiveType.PLACEHOLDER_CHECK;
    }

    private void replace(ObjectiveType type, String target, int amount, Map<String, String> meta, String choiceGroup, String label) {
        draft.objectives().set(index, new QuestObjective(type, target, amount, meta, choiceGroup, label));
        reopen();
    }

    private void reopen() {
        new QuestEditorObjectiveEditGui(context, player, draft, index).open(player);
    }
}
