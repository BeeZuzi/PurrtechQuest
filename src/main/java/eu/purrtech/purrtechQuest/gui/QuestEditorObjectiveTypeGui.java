package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.api.objective.ObjectiveHandler;
import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import eu.purrtech.purrtechQuest.tracking.NpcMatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Add objective" screen: pick a type by cycling through the icon, then a chat wizard collects the
 * type-specific fields (target+amount for most types, no target at all for SPEND_MONEY, world/x/y/z/radius
 * for REACH_LOCATION, an in-world NPC click instead of typed text for TALK_TO_NPC). Only objective types the
 * {@code tracking} package actually implements are offered as vanilla — letting an admin create an
 * INTERACT_BLOCK objective today would produce a quest that can never progress. TALK_TO_NPC is additionally
 * offered only when Citizens or FancyNpcs is installed, and DEFEAT_BOSS only when MythicMobs is installed —
 * same conditional-offering treatment as CUSTOM, offered only when at least one {@link ObjectiveHandler} is
 * registered (see the objective handler slot, only shown once CUSTOM is selected). SPEND_MONEY and
 * EARN_MONEY are always offered, but only ever report progress if a suitable economy backend is actually
 * present (ExcellentShop for SPEND_MONEY, Vault + an economy plugin for EARN_MONEY) — that dependency isn't
 * checkable the same way as an NPC/mob plugin (there's no "is this plugin installed" analog for a
 * currency-agnostic objective), so it's on the admin to know.
 * <p>
 * Doubles as the "edit type/value" screen for an existing objective (see the {@code editIndex} constructor
 * and {@link #openValueWizard}), reached from {@link QuestEditorObjectiveEditGui}. In that mode the type
 * picker starts on the objective's current type, and finishing the wizard replaces it in place — keeping its
 * existing label and choice group — instead of prompting for those and appending a new one.
 */
public final class QuestEditorObjectiveTypeGui extends Gui {

    private static final ObjectiveType[] BUILT_IN_TYPES = {
            ObjectiveType.KILL_ENTITY, ObjectiveType.BREAK_BLOCK, ObjectiveType.PLACE_BLOCK,
            ObjectiveType.COLLECT_ITEM, ObjectiveType.CRAFT_ITEM, ObjectiveType.FISH,
            ObjectiveType.SPEND_MONEY, ObjectiveType.EARN_MONEY, ObjectiveType.PLACEHOLDER_CHECK,
            ObjectiveType.REACH_LOCATION
    };

    private static final Set<String> VALID_OPERATORS = Set.of(">", ">=", "<", "<=", "=", "!=");

    private static final String MENU_ID = "quest-editor-objective-type";

    private final QuestEditorContext context;
    private final Player player;
    private final QuestDraft draft;
    private final List<ObjectiveType> types;
    private final List<ObjectiveHandler> handlers;
    private final Integer editIndex;
    private final QuestObjective editing;
    private final int backSlot;
    private final int typeSlot;
    private final int handlerSlot;
    private final int continueSlot;
    private int typeIndex;
    private int handlerIndex;

    public QuestEditorObjectiveTypeGui(QuestEditorContext context, Player player, QuestDraft draft) {
        this(context, player, draft, null, false);
    }

    /** Edit mode: the type picker starts on {@code editIndex}'s current type; admin may change it or just hit Continue. */
    public QuestEditorObjectiveTypeGui(QuestEditorContext context, Player player, QuestDraft draft, int editIndex) {
        this(context, player, draft, editIndex, false);
    }

    /**
     * Edit mode, value-only: skips the type picker entirely and jumps straight into the current type's
     * target/amount/meta collection — for {@link QuestEditorObjectiveEditGui}'s "Value" button, which
     * shouldn't also offer a type change (that's the separate "Type" button). This constructs the wizard
     * purely for its chat-prompt side effect and never shows its (otherwise unused) inventory, so unlike
     * every other {@code Gui} in this package, callers must not follow it with {@code .open(player)}.
     */
    public static void openValueWizard(QuestEditorContext context, Player player, QuestDraft draft, int editIndex) {
        new QuestEditorObjectiveTypeGui(context, player, draft, editIndex, true);
    }

    private QuestEditorObjectiveTypeGui(QuestEditorContext context, Player player, QuestDraft draft,
                                         Integer editIndex, boolean jumpToValue) {
        super(context.messages().render(editIndex == null ? "quest.editor-button-add" : "quest.editor-button-type-edit",
                player, Map.of()), context.menuLayouts().resolveSize(MENU_ID, 9));
        this.context = context;
        this.player = player;
        this.draft = draft;
        this.editIndex = editIndex;
        this.editing = editIndex == null ? null : draft.objectives().get(editIndex);
        this.handlers = List.copyOf(context.objectiveHandlers().all());
        int size = getInventory().getSize();
        var menuLayouts = context.menuLayouts();
        this.backSlot = menuLayouts.resolveSlot(MENU_ID, "back", 0, size);
        this.typeSlot = menuLayouts.resolveSlot(MENU_ID, "type", 4, size);
        this.handlerSlot = menuLayouts.resolveSlot(MENU_ID, "handler", 5, size);
        this.continueSlot = menuLayouts.resolveSlot(MENU_ID, "continue", 8, size);
        this.types = new ArrayList<>(List.of(BUILT_IN_TYPES));
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens") || Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            types.add(ObjectiveType.TALK_TO_NPC);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            types.add(ObjectiveType.DEFEAT_BOSS);
        }
        if (!handlers.isEmpty()) {
            types.add(ObjectiveType.CUSTOM);
        }
        if (editing != null) {
            int foundType = types.indexOf(editing.type());
            this.typeIndex = Math.max(0, foundType);
            if (editing.type() == ObjectiveType.CUSTOM) {
                String prefix = editing.target().contains(":")
                        ? editing.target().substring(0, editing.target().indexOf(':')) : editing.target();
                int foundHandler = -1;
                for (int i = 0; i < handlers.size(); i++) {
                    if (handlers.get(i).id().equals(prefix)) {
                        foundHandler = i;
                        break;
                    }
                }
                this.handlerIndex = Math.max(0, foundHandler);
            }
        }
        if (jumpToValue) {
            startWizard(types.get(typeIndex));
        } else {
            render();
        }
    }

    private void render() {
        clear();
        var messages = context.messages();
        setItem(backSlot, GuiItems.icon(Material.ARROW, messages.render("quest.gui-button-back", player, Map.of()),
                        List.of(messages.render("quest.gui-button-back-hint", player, Map.of()))),
                event -> back());

        ObjectiveType type = types.get(typeIndex);
        String typeHintKey = "quest.editor-objective-type-hint-" + type.name().toLowerCase(Locale.ROOT);
        setItem(typeSlot, GuiItems.icon(QuestIcons.materialFor(type),
                        messages.render("quest.editor-button-type", player, Map.of("%value%", type.name())),
                        List.of(messages.render(typeHintKey, player, Map.of()))),
                event -> {
                    typeIndex = (typeIndex + 1) % types.size();
                    render();
                });

        if (type == ObjectiveType.CUSTOM) {
            ObjectiveHandler handler = handlers.get(handlerIndex);
            List<Component> lore = List.of(Component.text(handler.id(), NamedTextColor.DARK_GRAY),
                    messages.render("quest.editor-objective-handler-hint", player, Map.of()));
            setItem(handlerSlot, GuiItems.icon(Material.NETHER_STAR,
                            Component.text(handler.displayName(), NamedTextColor.AQUA), lore),
                    event -> {
                        handlerIndex = (handlerIndex + 1) % handlers.size();
                        render();
                    });
        } else {
            setItem(handlerSlot, GuiItems.filler(), null);
        }

        setItem(continueSlot, GuiItems.icon(Material.LIME_DYE, messages.render("quest.editor-button-continue", player, Map.of()),
                        List.of(messages.render("quest.editor-button-continue-hint", player, Map.of()))),
                event -> startWizard(type));
    }

    private void startWizard(ObjectiveType type) {
        if (type == ObjectiveType.REACH_LOCATION) {
            startLocationWizard();
            return;
        }
        if (type == ObjectiveType.CUSTOM) {
            startCustomWizard(handlers.get(handlerIndex));
            return;
        }
        if (type == ObjectiveType.SPEND_MONEY) {
            promptAmount(type, ObjectiveType.SPEND_MONEY_TARGET);
            return;
        }
        if (type == ObjectiveType.EARN_MONEY) {
            promptAmount(type, ObjectiveType.EARN_MONEY_TARGET);
            return;
        }
        if (type == ObjectiveType.PLACEHOLDER_CHECK) {
            startPlaceholderWizard();
            return;
        }
        if (type == ObjectiveType.TALK_TO_NPC) {
            startTalkToNpcWizard();
            return;
        }
        if (type == ObjectiveType.DEFEAT_BOSS) {
            startDefeatBossWizard();
            return;
        }
        var messages = context.messages();
        var chatInput = context.chatInput();
        chatInput.prompt(player, "quest.editor-prompt-target", target -> {
            String normalized = target.toUpperCase(Locale.ROOT);
            if (!validTarget(type, normalized)) {
                player.sendMessage(messages.render(
                        type == ObjectiveType.KILL_ENTITY ? "quest.editor-invalid-entity" : "quest.editor-invalid-material",
                        player, Map.of()));
                startWizard(type);
                return;
            }
            promptAmount(type, normalized);
        }, this::reopen);
    }

    private void startCustomWizard(ObjectiveHandler handler) {
        var messages = context.messages();
        context.chatInput().prompt(player, "quest.editor-prompt-target", subTarget -> {
            if (!handler.isValidTarget(subTarget)) {
                player.sendMessage(messages.render("quest.editor-invalid-custom-target", player,
                        Map.of("%handler%", handler.displayName())));
                startCustomWizard(handler);
                return;
            }
            promptAmount(ObjectiveType.CUSTOM, handler.id() + ":" + subTarget);
        }, this::reopen);
    }

    /**
     * PLACEHOLDER_CHECK's target is the placeholder itself, not an amount-countable thing — so instead of
     * the usual target-then-amount flow, this collects placeholder, comparison operator, and comparison
     * value, then saves with a fixed {@code amount} of 1 (satisfied/not-satisfied, like REACH_LOCATION).
     */
    private void startPlaceholderWizard() {
        context.chatInput().prompt(player, "quest.editor-prompt-placeholder", raw -> {
            String placeholder = raw.trim();
            if (!placeholder.startsWith("%")) {
                placeholder = "%" + placeholder;
            }
            if (!placeholder.endsWith("%")) {
                placeholder = placeholder + "%";
            }
            promptPlaceholderOperator(placeholder);
        }, this::reopen);
    }

    private void promptPlaceholderOperator(String placeholder) {
        context.chatInput().prompt(player, "quest.editor-prompt-placeholder-operator", raw -> {
            String operator = raw.trim().replace("==", "=");
            if (!VALID_OPERATORS.contains(operator)) {
                player.sendMessage(context.messages().render("quest.editor-invalid-operator", player, Map.of()));
                promptPlaceholderOperator(placeholder);
                return;
            }
            promptPlaceholderValue(placeholder, operator);
        }, this::reopen);
    }

    private void promptPlaceholderValue(String placeholder, String operator) {
        context.chatInput().prompt(player, "quest.editor-prompt-placeholder-value", raw -> {
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("operator", operator);
            meta.put("value", raw.trim());
            finishObjective(ObjectiveType.PLACEHOLDER_CHECK, placeholder, 1, meta);
        }, this::reopen);
    }

    /**
     * TALK_TO_NPC's target is an NPC id — meaningless for an admin to type from memory. Mirroring
     * {@code /questadmin npclink}'s flow, this closes the editor and asks the admin to right-click the NPC
     * in the world instead; whichever provider (Citizens/FancyNpcs) reports that click supplies the target
     * via {@link NpcMatcher}, and the wizard then continues into the normal amount prompt.
     */
    private void startTalkToNpcWizard() {
        player.closeInventory();
        player.sendMessage(context.messages().render("quest.editor-prompt-npc-click", player, Map.of()));
        context.npcLinkService().requestPick(player, ref ->
                promptAmount(ObjectiveType.TALK_TO_NPC, NpcMatcher.targetFor(ref)));
    }

    /**
     * DEFEAT_BOSS needs two extra numbers beyond the usual target+amount: how much cumulative damage a
     * player must deal to the boss instance ({@code min-damage}, 0 = any damage counts) and how many hours
     * after their first hit the boss has to die for that to still count ({@code window-hours}) — see
     * {@link eu.purrtech.purrtechQuest.tracking.BossDefeatMatcher}. Target is the MythicMobs internal mob
     * name, typed the same as the generic wizard's target step but not validated against anything — same
     * reasoning as the "MM:" KILL_ENTITY case: there's no registry to check it against here.
     */
    private void startDefeatBossWizard() {
        context.chatInput().prompt(player, "quest.editor-prompt-target", raw ->
                promptBossMinDamage(raw.trim()), this::reopen);
    }

    private void promptBossMinDamage(String target) {
        context.chatInput().promptDouble(player, "quest.editor-prompt-boss-min-damage", minDamage -> {
            if (minDamage < 0) {
                player.sendMessage(context.messages().render("quest.editor-invalid-number", player, Map.of()));
                promptBossMinDamage(target);
                return;
            }
            promptBossWindow(target, minDamage);
        }, this::reopen);
    }

    private void promptBossWindow(String target, double minDamage) {
        context.chatInput().promptDouble(player, "quest.editor-prompt-boss-window-hours", windowHours -> {
            if (windowHours <= 0) {
                player.sendMessage(context.messages().render("quest.editor-invalid-number", player, Map.of()));
                promptBossWindow(target, minDamage);
                return;
            }
            promptBossAmount(target, minDamage, windowHours);
        }, this::reopen);
    }

    private void promptBossAmount(String target, double minDamage, double windowHours) {
        context.chatInput().promptInt(player, "quest.editor-prompt-amount", amount -> {
            if (amount <= 0) {
                player.sendMessage(context.messages().render("quest.editor-invalid-number", player, Map.of()));
                promptBossAmount(target, minDamage, windowHours);
                return;
            }
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("min-damage", String.valueOf(minDamage));
            meta.put("window-hours", String.valueOf(windowHours));
            finishObjective(ObjectiveType.DEFEAT_BOSS, target, amount, meta);
        }, this::reopen);
    }

    private void promptAmount(ObjectiveType type, String target) {
        var messages = context.messages();
        context.chatInput().promptInt(player, "quest.editor-prompt-amount", amount -> {
            if (amount <= 0) {
                player.sendMessage(messages.render("quest.editor-invalid-number", player, Map.of()));
                promptAmount(type, target);
                return;
            }
            finishObjective(type, target, amount, Map.of());
        }, this::reopen);
    }

    /**
     * Terminal step for every wizard branch. In edit mode this replaces {@code editIndex} in place, keeping
     * its existing label and choice group untouched (those have their own buttons back on
     * {@link QuestEditorObjectiveEditGui}) — only type/target/amount/meta came from this wizard. In add mode
     * it continues on to collect the (required) display name and (optional) choice group before appending.
     */
    private void finishObjective(ObjectiveType type, String target, int amount, Map<String, String> meta) {
        if (editIndex != null) {
            draft.objectives().set(editIndex, new QuestObjective(type, target, amount, meta, editing.choiceGroup(), editing.label()));
            new QuestEditorObjectiveEditGui(context, player, draft, editIndex).open(player);
            return;
        }
        promptLabel(type, target, amount, meta);
    }

    private void promptLabel(ObjectiveType type, String target, int amount, Map<String, String> meta) {
        context.chatInput().prompt(player, "quest.editor-prompt-objective-label", raw -> {
            String label = raw.trim();
            if (label.isEmpty()) {
                player.sendMessage(context.messages().render("quest.editor-invalid-label", player, Map.of()));
                promptLabel(type, target, amount, meta);
                return;
            }
            promptChoiceGroup(type, target, amount, meta, label);
        }, this::reopen);
    }

    /**
     * Last step: an optional "choice group" name — objectives sharing the same group are alternatives,
     * satisfied as a group the moment any one of them is (see {@link QuestObjective}'s javadoc and
     * {@code QuestService#isChoiceLockedOut}). Leaving it blank (or typing "none") keeps the objective
     * required on its own, same as before this existed.
     */
    private void promptChoiceGroup(ObjectiveType type, String target, int amount, Map<String, String> meta, String label) {
        context.chatInput().prompt(player, "quest.editor-prompt-choice-group", raw -> {
            String typed = raw.trim();
            String choiceGroup = (typed.isEmpty() || typed.equalsIgnoreCase("none")) ? null : typed;
            draft.objectives().add(new QuestObjective(type, target, amount, meta, choiceGroup, label));
            back();
        }, this::reopen);
    }

    private void startLocationWizard() {
        var chatInput = context.chatInput();
        chatInput.prompt(player, "quest.editor-prompt-location-world", world ->
                chatInput.promptDouble(player, "quest.editor-prompt-location-x", x ->
                        chatInput.promptDouble(player, "quest.editor-prompt-location-y", y ->
                                chatInput.promptDouble(player, "quest.editor-prompt-location-z", z ->
                                        chatInput.promptDouble(player, "quest.editor-prompt-location-radius", radius -> {
                                            Map<String, String> meta = new LinkedHashMap<>();
                                            meta.put("x", String.valueOf(x));
                                            meta.put("y", String.valueOf(y));
                                            meta.put("z", String.valueOf(z));
                                            meta.put("radius", String.valueOf(radius));
                                            finishObjective(ObjectiveType.REACH_LOCATION, world, 1, meta);
                                        }, this::reopen),
                                this::reopen),
                        this::reopen),
                this::reopen),
        this::reopen);
    }

    /**
     * {@code target} has already been uppercased by the caller. {@code "MM:"} (MythicMobs) and
     * {@code "IA:"}/{@code "ORAXEN:"} (custom items) prefixed targets are accepted without further
     * validation — there's no way to check a custom mob/item id actually exists without that plugin
     * loaded, and it may not be installed on whatever server this admin is editing from.
     */
    private static boolean validTarget(ObjectiveType type, String target) {
        return switch (type) {
            case KILL_ENTITY -> target.startsWith("MM:") || isVanillaEntity(target);
            case COLLECT_ITEM, CRAFT_ITEM, FISH ->
                    target.startsWith("IA:") || target.startsWith("ORAXEN:") || Material.matchMaterial(target) != null;
            case BREAK_BLOCK, PLACE_BLOCK -> Material.matchMaterial(target) != null;
            default -> true;
        };
    }

    private static boolean isVanillaEntity(String target) {
        try {
            EntityType.valueOf(target);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void back() {
        if (editIndex != null) {
            new QuestEditorObjectiveEditGui(context, player, draft, editIndex).open(player);
        } else {
            new QuestEditorObjectivesGui(context, player, draft).open(player);
        }
    }

    private void reopen() {
        if (editIndex != null) {
            new QuestEditorObjectiveTypeGui(context, player, draft, editIndex).open(player);
        } else {
            new QuestEditorObjectiveTypeGui(context, player, draft).open(player);
        }
    }
}
