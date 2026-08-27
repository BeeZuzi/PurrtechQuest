package eu.purrtech.purrtechQuest.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.gui.QuestCategoryGui;
import eu.purrtech.purrtechQuest.model.ChoiceGroups;
import eu.purrtech.purrtechQuest.model.PlayerQuestData;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.service.QuestFeedback;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.service.QuestStatusText;
import eu.purrtech.purrtechQuest.service.QuestTrackingService;
import eu.purrtech.purrtechQuest.storage.CategoryConfigRepository;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /quest} — the whole player-facing interface. The bare command and {@code gui} open the Quest Log
 * GUI (Phase 2); {@code list}/{@code info}/{@code accept}/{@code abandon}/{@code turnin} remain as the
 * Phase 1 chat-based interface (scripting, low-end clients). Every subcommand delegates the actual
 * decision to {@link QuestService} and only renders the result.
 */
public final class QuestCommand {

    private QuestCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(QuestService questService,
                                                                 PlayerQuestDataCache playerCache,
                                                                 QuestTrackingService trackingService,
                                                                 CategoryConfigRepository categoryConfigRepository,
                                                                 boolean othersAsCategory,
                                                                 MenuLayoutConfig menuLayouts,
                                                                 MessagesConfig messages) {
        return Commands.literal("quest")
                .requires(source -> source.getSender().hasPermission("purrtechquest.use"))
                .executes(ctx -> openGui(ctx.getSource().getSender(), questService, playerCache, trackingService,
                        categoryConfigRepository, othersAsCategory, menuLayouts, messages))
                .then(Commands.literal("gui")
                        .executes(ctx -> openGui(ctx.getSource().getSender(), questService, playerCache, trackingService,
                                categoryConfigRepository, othersAsCategory, menuLayouts, messages)))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource().getSender(), questService, playerCache, messages)))
                .then(Commands.literal("info")
                        .then(questIdArgument(questService)
                                .executes(ctx -> info(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "quest"), questService, playerCache, messages))))
                .then(Commands.literal("accept")
                        .then(questIdArgument(questService)
                                .executes(ctx -> accept(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "quest"), questService, messages))))
                .then(Commands.literal("abandon")
                        .then(questIdArgument(questService)
                                .executes(ctx -> abandon(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "quest"), questService, messages))))
                .then(Commands.literal("turnin")
                        .then(questIdArgument(questService)
                                .executes(ctx -> turnIn(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "quest"), questService, messages))))
                .then(Commands.literal("track")
                        .then(questIdArgument(questService)
                                .executes(ctx -> track(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "quest"), trackingService, messages))))
                .then(Commands.literal("untrack")
                        .executes(ctx -> untrack(ctx.getSource().getSender(), trackingService)))
                .build();
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> questIdArgument(QuestService questService) {
        return Commands.<String>argument("quest", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    String remaining = builder.getRemainingLowerCase();
                    for (Quest quest : questService.allQuests()) {
                        if (quest.id().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                            builder.suggest(quest.id());
                        }
                    }
                    return builder.buildFuture();
                });
    }

    private static int list(CommandSender sender, QuestService questService, PlayerQuestDataCache playerCache,
                             MessagesConfig messages) {
        Player player = requirePlayer(sender, messages);
        if (player == null) {
            return 0;
        }
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        player.sendMessage(messages.render("quest.list-header", player, Map.of()));
        for (Quest quest : questService.allQuests().stream().sorted(Quest.DISPLAY_ORDER).toList()) {
            QuestProgress progress = data == null ? null : data.progress(quest.id());
            String status = messages.get(QuestStatusText.key(progress), player.locale().getLanguage());
            Map<String, String> placeholders = new LinkedHashMap<>();
            placeholders.put("%id%", quest.id());
            placeholders.put("%name%", quest.displayName());
            placeholders.put("%status%", status);
            player.sendMessage(messages.render("quest.list-entry", player, placeholders));
        }
        return 1;
    }

    private static int info(CommandSender sender, String questId, QuestService questService,
                             PlayerQuestDataCache playerCache, MessagesConfig messages) {
        Player player = requirePlayer(sender, messages);
        if (player == null) {
            return 0;
        }
        Quest quest = questService.quest(questId).orElse(null);
        if (quest == null) {
            player.sendMessage(messages.render("quest.not-found", player, Map.of("%quest%", questId)));
            return 0;
        }

        player.sendMessage(messages.render("quest.info-header", player, Map.of("%quest%", quest.displayName())));
        if (!quest.description().isBlank()) {
            player.sendMessage(messages.render("quest.info-description", player,
                    Map.of("%description%", quest.description())));
        }

        PlayerQuestData data = playerCache.get(player.getUniqueId());
        QuestProgress progress = data == null ? null : data.progress(questId);
        String status = messages.get(QuestStatusText.key(progress), player.locale().getLanguage());
        player.sendMessage(messages.render("quest.info-status", player, Map.of("%status%", status)));

        List<QuestObjective> objectives = quest.objectives();
        Set<String> satisfiedGroups = progress == null ? Set.of() : ChoiceGroups.satisfiedGroups(quest, progress);
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            int current = progress == null ? 0 : progress.objectiveProgress(i);
            boolean satisfied = current >= objective.amount();
            if (objective.choiceGroup() != null && !satisfied && satisfiedGroups.contains(objective.choiceGroup())) {
                player.sendMessage(messages.render("quest.info-objective-locked", player, Map.of(
                        "%label%", objective.label())));
                continue;
            }
            Map<String, String> placeholders = new LinkedHashMap<>();
            placeholders.put("%label%", objective.label());
            placeholders.put("%progress%", String.valueOf(current));
            placeholders.put("%amount%", String.valueOf(objective.amount()));
            String key = objective.choiceGroup() != null ? "quest.info-objective-choice" : "quest.info-objective";
            player.sendMessage(messages.render(key, player, placeholders));
        }
        return 1;
    }

    private static int accept(CommandSender sender, String questId, QuestService questService, MessagesConfig messages) {
        Player player = requirePlayer(sender, messages);
        if (player == null) {
            return 0;
        }
        QuestService.AcceptResult result = questService.acceptQuest(player, questId);
        return QuestFeedback.report(player, result, questId, questService, messages) ? 1 : 0;
    }

    private static int abandon(CommandSender sender, String questId, QuestService questService, MessagesConfig messages) {
        Player player = requirePlayer(sender, messages);
        if (player == null) {
            return 0;
        }
        QuestService.AbandonResult result = questService.abandonQuest(player, questId);
        return QuestFeedback.report(player, result, messages) ? 1 : 0;
    }

    private static int turnIn(CommandSender sender, String questId, QuestService questService, MessagesConfig messages) {
        Player player = requirePlayer(sender, messages);
        if (player == null) {
            return 0;
        }
        QuestService.TurnInResult result = questService.turnIn(player, questId);
        return QuestFeedback.report(player, result, questId, messages) ? 1 : 0;
    }

    private static int openGui(CommandSender sender, QuestService questService, PlayerQuestDataCache playerCache,
                                QuestTrackingService trackingService, CategoryConfigRepository categoryConfigRepository,
                                boolean othersAsCategory, MenuLayoutConfig menuLayouts, MessagesConfig messages) {
        Player player = requirePlayer(sender, messages);
        if (player == null) {
            return 0;
        }
        new QuestCategoryGui(questService, playerCache, trackingService, menuLayouts, categoryConfigRepository,
                othersAsCategory, messages, player).open(player);
        return 1;
    }

    private static int track(CommandSender sender, String questId, QuestTrackingService trackingService,
                              MessagesConfig messages) {
        Player player = requirePlayer(sender, messages);
        if (player == null) {
            return 0;
        }
        trackingService.track(player, questId);
        return 1;
    }

    private static int untrack(CommandSender sender, QuestTrackingService trackingService) {
        if (sender instanceof Player player) {
            trackingService.untrack(player);
        }
        return 1;
    }

    private static Player requirePlayer(CommandSender sender, MessagesConfig messages) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(messages.render("quest.console-only", Map.of()));
        return null;
    }
}
