package eu.purrtech.purrtechQuest.model;

import java.util.Objects;

/**
 * Admin-configurable display settings for one quest category (see {@link Quest#category()}). Categories
 * themselves aren't a separately-created entity — they emerge from whatever category string quests use —
 * this only holds the *optional* extra presentation an admin layers on top via {@code /questadmin category}:
 * a description shown in {@code QuestCategoryGui}, independent toggles for each of the three lore lines
 * that screen can show, which slot the category's button sits on ({@code null} = auto-assigned), and which
 * item represents it. A category nobody has configured yet just uses {@link #defaults(String)}.
 * <p>
 * {@code icon} is either a vanilla {@link org.bukkit.Material} name or an {@code "ia:"}/{@code "oraxen:"}
 * -prefixed custom item id — the same convention {@link QuestReward.Item#customId()} and
 * {@code eu.purrtech.purrtechQuest.tracking.ItemMatcher#resolveTarget} use.
 */
public record QuestCategoryConfig(String id, String description, boolean showActiveQuest,
                                   boolean showProgress, boolean showDescription,
                                   Integer slot, String icon) {

    public QuestCategoryConfig {
        Objects.requireNonNull(id, "id");
        description = description == null ? "" : description;
        slot = (slot != null && slot < 0) ? null : slot;
        icon = (icon == null || icon.isBlank()) ? "CHEST" : icon;
    }

    /** Everything on, no description, no explicit slot/icon — what a category looks like before any admin has touched it. */
    public static QuestCategoryConfig defaults(String id) {
        return new QuestCategoryConfig(id, "", true, true, true, null, "CHEST");
    }
}
