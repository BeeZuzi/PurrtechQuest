package eu.purrtech.purrtechQuest.model;

import java.util.Objects;

/**
 * Admin-configurable display settings for one quest category (see {@link Quest#category()}). Categories
 * themselves aren't a separately-created entity — they emerge from whatever category string quests use —
 * this only holds the *optional* extra presentation an admin layers on top via {@code /questadmin category}:
 * a description shown in {@code QuestCategoryGui}, and independent toggles for each of the three lore lines
 * that screen can show. A category nobody has configured yet just uses {@link #defaults(String)}.
 */
public record QuestCategoryConfig(String id, String description, boolean showActiveQuest,
                                   boolean showProgress, boolean showDescription) {

    public QuestCategoryConfig {
        Objects.requireNonNull(id, "id");
        description = description == null ? "" : description;
    }

    /** Everything on, no description — what a category looks like before any admin has touched it. */
    public static QuestCategoryConfig defaults(String id) {
        return new QuestCategoryConfig(id, "", true, true, true);
    }
}
