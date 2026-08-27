package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.model.QuestReward;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Picker for the {@code IMPORT} reward type in {@link QuestEditorRewardTypeGui}: lists every reward
 * already defined anywhere on the quest (base bucket + every rank tier) and copies whichever one the admin
 * clicks into the bucket that was selected before choosing Import. {@link QuestReward} records are
 * immutable, so "copy" is just adding the same instance to a second list — both buckets keep working
 * independently (e.g. removing it from one later doesn't touch the other).
 */
public final class QuestEditorRewardImportGui extends Gui {

    private static final String MENU_ID = "quest-editor-reward-import";

    private record Entry(QuestReward reward, String bucketLabel) {
    }

    private final QuestEditorContext context;
    private final Player player;
    private final QuestDraft draft;
    private final List<QuestReward> targetList;
    private final Runnable onImported;
    private final Runnable onBack;
    private final int contentCap;
    private final int backSlot;

    public QuestEditorRewardImportGui(QuestEditorContext context, Player player, QuestDraft draft,
                                       List<QuestReward> targetList, Runnable onImported, Runnable onBack) {
        super(context.messages().render("quest.editor-button-import", player, Map.of()),
                context.menuLayouts().resolveSize(MENU_ID, 27));
        this.context = context;
        this.player = player;
        this.draft = draft;
        this.targetList = targetList;
        this.onImported = onImported;
        this.onBack = onBack;
        int size = getInventory().getSize();
        this.contentCap = size - 9;
        this.backSlot = context.menuLayouts().resolveSlot(MENU_ID, "back", size - 9, size);
        render();
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        String baseLabel = context.messages().get("quest.editor-reward-tier-base", player.locale().getLanguage());
        for (QuestReward reward : draft.rewards()) {
            entries.add(new Entry(reward, baseLabel));
        }
        for (QuestDraft.RewardTierDraft tier : draft.rewardTiers()) {
            for (QuestReward reward : tier.rewards()) {
                entries.add(new Entry(reward, tier.displayName()));
            }
        }
        return entries;
    }

    private void render() {
        clear();
        var messages = context.messages();
        List<Entry> entries = entries();
        if (entries.isEmpty()) {
            setItem(4, GuiItems.icon(Material.BARRIER, messages.render("quest.editor-import-empty", player, Map.of()), List.of()), null);
        }
        for (int i = 0; i < entries.size() && i < contentCap; i++) {
            Entry entry = entries.get(i);
            setItem(i, icon(entry), event -> {
                targetList.add(entry.reward());
                onImported.run();
            });
        }

        setItem(backSlot, GuiItems.icon(Material.ARROW, messages.render("quest.gui-button-back", player, Map.of()),
                        List.of(messages.render("quest.gui-button-back-hint", player, Map.of()))),
                event -> onBack.run());
    }

    private org.bukkit.inventory.ItemStack icon(Entry entry) {
        var messages = context.messages();
        QuestReward reward = entry.reward();
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
                messages.render("quest.editor-reward-bucket-line", player, Map.of("%bucket%", entry.bucketLabel())),
                messages.render("quest.editor-reward-import-hint", player, Map.of()));
        return GuiItems.icon(material, name, lore);
    }

    private Component typeName(String key) {
        return Component.text(context.messages().get(key, player.locale().getLanguage()), NamedTextColor.GOLD);
    }
}
