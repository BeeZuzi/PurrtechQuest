package eu.purrtech.purrtechQuest.service;

import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.util.DurationFormat;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Renders the error-path messages for {@link QuestService}'s result enums. Shared by the chat command and
 * the GUI so both present identical, single-sourced wording — success messages aren't handled here because
 * {@link QuestService} already sends those itself (see its class javadoc for why).
 */
public final class QuestFeedback {

    private QuestFeedback() {
    }

    public static boolean report(Player player, QuestService.AcceptResult result, String questId,
                                  QuestService questService, MessagesConfig messages) {
        return switch (result) {
            case ACCEPTED -> true;
            case NOT_FOUND -> fail(player, messages, "quest.not-found", Map.of("%quest%", questId));
            case ALREADY_IN_PROGRESS -> fail(player, messages, "quest.already-in-progress", Map.of());
            case ALREADY_COMPLETED -> fail(player, messages, "quest.already-completed", Map.of());
            case PREREQUISITES_NOT_MET -> fail(player, messages, "quest.prerequisites-not-met", Map.of());
            case MISSING_PERMISSION -> fail(player, messages, "quest.missing-permission", Map.of());
            case DATA_NOT_LOADED -> fail(player, messages, "quest.data-not-loaded", Map.of());
            case ON_COOLDOWN -> {
                String time = questService.cooldownReadyAt(player, questId)
                        .map(readyAt -> DurationFormat.humanReadable(Duration.between(Instant.now(), readyAt)))
                        .orElse("?");
                yield fail(player, messages, "quest.on-cooldown", Map.of("%time%", time));
            }
            case CANCELLED -> fail(player, messages, "quest.action-cancelled", Map.of());
        };
    }

    public static boolean report(Player player, QuestService.AbandonResult result, MessagesConfig messages) {
        return switch (result) {
            case ABANDONED -> true;
            case NOT_IN_PROGRESS -> fail(player, messages, "quest.not-in-progress", Map.of());
            case DATA_NOT_LOADED -> fail(player, messages, "quest.data-not-loaded", Map.of());
        };
    }

    public static boolean report(Player player, QuestService.TurnInResult result, String questId, MessagesConfig messages) {
        return switch (result) {
            case TURNED_IN -> true;
            case NOT_FOUND -> fail(player, messages, "quest.not-found", Map.of("%quest%", questId));
            case NOT_ACCEPTED -> fail(player, messages, "quest.not-accepted", Map.of());
            case OBJECTIVES_INCOMPLETE -> fail(player, messages, "quest.objectives-incomplete", Map.of());
            case ALREADY_TURNED_IN -> fail(player, messages, "quest.already-turned-in", Map.of());
            case DATA_NOT_LOADED -> fail(player, messages, "quest.data-not-loaded", Map.of());
            case CANCELLED -> fail(player, messages, "quest.action-cancelled", Map.of());
        };
    }

    private static boolean fail(Player player, MessagesConfig messages, String key, Map<String, String> placeholders) {
        player.sendMessage(messages.render(key, player, placeholders));
        return false;
    }
}
