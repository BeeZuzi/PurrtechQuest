package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.model.QuestStatus;
import org.bukkit.Material;

final class QuestIcons {

    private QuestIcons() {
    }

    static Material materialFor(QuestStatus status) {
        return switch (status) {
            case NOT_ACCEPTED -> Material.BOOK;
            case IN_PROGRESS -> Material.WRITABLE_BOOK;
            case COMPLETED -> Material.ENCHANTED_BOOK;
            case TURNED_IN -> Material.KNOWLEDGE_BOOK;
        };
    }

    static Material materialFor(ObjectiveType type) {
        return switch (type) {
            case KILL_ENTITY -> Material.IRON_SWORD;
            case BREAK_BLOCK -> Material.IRON_PICKAXE;
            case PLACE_BLOCK -> Material.BRICKS;
            case COLLECT_ITEM -> Material.CHEST;
            case CRAFT_ITEM -> Material.CRAFTING_TABLE;
            case FISH -> Material.FISHING_ROD;
            case REACH_LOCATION -> Material.COMPASS;
            case INTERACT_BLOCK -> Material.LEVER;
            case TALK_TO_NPC -> Material.VILLAGER_SPAWN_EGG;
            case SPEND_MONEY -> Material.EMERALD;
            case PLACEHOLDER_CHECK -> Material.COMPARATOR;
            case DEFEAT_BOSS -> Material.WITHER_SKELETON_SKULL;
            case EARN_MONEY -> Material.GOLD_INGOT;
            case CUSTOM -> Material.NETHER_STAR;
        };
    }
}
