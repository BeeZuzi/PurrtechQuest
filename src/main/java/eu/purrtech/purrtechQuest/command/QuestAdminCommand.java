package eu.purrtech.purrtechQuest.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import eu.purrtech.purrtechQuest.api.objective.ObjectiveHandlerRegistry;
import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.gui.ChatInputService;
import eu.purrtech.purrtechQuest.gui.QuestCategoryEditorGui;
import eu.purrtech.purrtechQuest.gui.QuestDraft;
import eu.purrtech.purrtechQuest.gui.QuestEditorContext;
import eu.purrtech.purrtechQuest.gui.QuestEditorGui;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.npc.NpcLinkService;
import eu.purrtech.purrtechQuest.permission.QuestPermissionRegistrar;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.storage.CategoryConfigRepository;
import eu.purrtech.purrtechQuest.storage.QuestDefinitionRepository;
import eu.purrtech.purrtechQuest.tracking.TrackerRegistrationManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /questadmin} — {@code reload}/{@code give}/{@code reset} are testing/administration tools;
 * {@code create}/{@code edit}/{@code delete} open the in-game quest editor GUI (Phase 3), which is now the
 * primary way to author quests — hand-editing {@code quests/*.yml} still works too, the editor writes the
 * exact same format.
 */
public final class QuestAdminCommand {

    private QuestAdminCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(QuestService questService,
                                                                 QuestDefinitionRepository questRepository,
                                                                 ChatInputService chatInput,
                                                                 NpcLinkService npcLinkService,
                                                                 ObjectiveHandlerRegistry objectiveHandlers,
                                                                 TrackerRegistrationManager trackerRegistrationManager,
                                                                 QuestPermissionRegistrar questPermissionRegistrar,
                                                                 CategoryConfigRepository categoryConfigRepository,
                                                                 MenuLayoutConfig menuLayouts,
                                                                 MessagesConfig messages) {
        QuestEditorContext context = new QuestEditorContext(messages, chatInput, questService, questRepository,
                objectiveHandlers, trackerRegistrationManager, npcLinkService, questPermissionRegistrar,
                categoryConfigRepository, menuLayouts);
        return Commands.literal("questadmin")
                .requires(source -> source.getSender().hasPermission("purrtechquest.admin"))
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource().getSender(), questService, trackerRegistrationManager,
                                questPermissionRegistrar, messages)))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(questIdArgument(questService)
                                        .executes(ctx -> give(ctx, questService, messages)))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(questIdArgument(questService)
                                        .executes(ctx -> reset(ctx, questService, messages)))))
                .then(Commands.literal("create")
                        .then(Commands.argument("quest", StringArgumentType.word())
                                .executes(ctx -> create(ctx, questService, context, messages))))
                .then(Commands.literal("edit")
                        .then(questIdArgument(questService)
                                .executes(ctx -> edit(ctx, questService, context, messages))))
                .then(Commands.literal("delete")
                        .then(questIdArgument(questService)
                                .executes(ctx -> delete(ctx, questService, questRepository, trackerRegistrationManager,
                                        questPermissionRegistrar, messages))))
                .then(Commands.literal("npclink")
                        .then(questIdArgument(questService)
                                .executes(ctx -> npcLink(ctx, questService, questRepository, npcLinkService,
                                        questPermissionRegistrar, messages))))
                .then(Commands.literal("npcunlink")
                        .then(questIdArgument(questService)
                                .executes(ctx -> npcUnlink(ctx, questService, questRepository, npcLinkService,
                                        trackerRegistrationManager, questPermissionRegistrar, messages))))
                .then(Commands.literal("category")
                        .then(categoryArgument(questService)
                                .executes(ctx -> category(ctx, context, messages))))
                .build();
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> categoryArgument(QuestService questService) {
        return Commands.<String>argument("category", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    String remaining = builder.getRemainingLowerCase();
                    for (String category : questService.categories()) {
                        if (category.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                            builder.suggest(category);
                        }
                    }
                    return builder.buildFuture();
                });
    }

    private static int category(CommandContext<CommandSourceStack> ctx, QuestEditorContext context, MessagesConfig messages) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("quest.console-only", Map.of()));
            return 0;
        }
        String categoryId = StringArgumentType.getString(ctx, "category");
        new QuestCategoryEditorGui(context, player, categoryId).open(player);
        return 1;
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

    private static int reload(CommandSender sender, QuestService questService,
                               TrackerRegistrationManager trackerRegistrationManager,
                               QuestPermissionRegistrar questPermissionRegistrar, MessagesConfig messages) {
        questService.reload();
        trackerRegistrationManager.refresh();
        questPermissionRegistrar.refresh();
        sender.sendMessage(messages.render("quest.reloaded",
                Map.of("%count%", String.valueOf(questService.allQuests().size()))));
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, QuestService questService, MessagesConfig messages) {
        CommandSender sender = ctx.getSource().getSender();
        String questId = StringArgumentType.getString(ctx, "quest");
        Player target = resolveSinglePlayer(ctx);
        if (target == null) {
            sender.sendMessage(messages.render("quest.admin-give-failure", Map.of()));
            return 0;
        }
        boolean success = questService.adminForceAccept(target, questId);
        String key = success ? "quest.admin-give-success" : "quest.admin-give-failure";
        sender.sendMessage(messages.render(key, Map.of("%player%", target.getName(), "%quest%", questId)));
        return success ? 1 : 0;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, QuestService questService, MessagesConfig messages) {
        CommandSender sender = ctx.getSource().getSender();
        String questId = StringArgumentType.getString(ctx, "quest");
        Player target = resolveSinglePlayer(ctx);
        if (target == null) {
            sender.sendMessage(messages.render("quest.admin-reset-failure", Map.of()));
            return 0;
        }
        boolean success = questService.adminResetProgress(target, questId);
        String key = success ? "quest.admin-reset-success" : "quest.admin-reset-failure";
        sender.sendMessage(messages.render(key, Map.of("%player%", target.getName(), "%quest%", questId)));
        return success ? 1 : 0;
    }

    private static int create(CommandContext<CommandSourceStack> ctx, QuestService questService,
                               QuestEditorContext context, MessagesConfig messages) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("quest.console-only", Map.of()));
            return 0;
        }
        String questId = StringArgumentType.getString(ctx, "quest");
        if (questService.quest(questId).isPresent()) {
            player.sendMessage(messages.render("quest.editor-id-taken", player, Map.of("%id%", questId)));
            return 0;
        }
        new QuestEditorGui(context, player, QuestDraft.blank(questId)).open(player);
        return 1;
    }

    private static int edit(CommandContext<CommandSourceStack> ctx, QuestService questService,
                             QuestEditorContext context, MessagesConfig messages) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("quest.console-only", Map.of()));
            return 0;
        }
        String questId = StringArgumentType.getString(ctx, "quest");
        Quest quest = questService.quest(questId).orElse(null);
        if (quest == null) {
            player.sendMessage(messages.render("quest.editor-not-found", player, Map.of("%id%", questId)));
            return 0;
        }
        new QuestEditorGui(context, player, QuestDraft.from(quest)).open(player);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx, QuestService questService,
                               QuestDefinitionRepository questRepository,
                               TrackerRegistrationManager trackerRegistrationManager,
                               QuestPermissionRegistrar questPermissionRegistrar, MessagesConfig messages) {
        CommandSender sender = ctx.getSource().getSender();
        String questId = StringArgumentType.getString(ctx, "quest");
        if (questService.quest(questId).isEmpty()) {
            sender.sendMessage(messages.render("quest.editor-delete-failed", Map.of("%id%", questId)));
            return 0;
        }
        questRepository.delete(questId);
        questService.reload();
        trackerRegistrationManager.refresh();
        questPermissionRegistrar.refresh();
        sender.sendMessage(messages.render("quest.editor-deleted", Map.of("%id%", questId)));
        return 1;
    }

    private static int npcLink(CommandContext<CommandSourceStack> ctx, QuestService questService,
                                QuestDefinitionRepository questRepository, NpcLinkService npcLinkService,
                                QuestPermissionRegistrar questPermissionRegistrar, MessagesConfig messages) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("quest.console-only", Map.of()));
            return 0;
        }
        String questId = StringArgumentType.getString(ctx, "quest");
        if (questService.quest(questId).isEmpty()) {
            player.sendMessage(messages.render("quest.editor-not-found", player, Map.of("%id%", questId)));
            return 0;
        }
        boolean citizens = Bukkit.getPluginManager().isPluginEnabled("Citizens");
        boolean fancyNpcs = Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
        if (!citizens && !fancyNpcs) {
            player.sendMessage(messages.render("quest.npc-no-provider", player, Map.of()));
            return 0;
        }
        npcLinkService.requestPick(player, ref -> {
            Quest quest = questService.quest(questId).orElse(null);
            if (quest == null) {
                player.sendMessage(messages.render("quest.editor-not-found", player, Map.of("%id%", questId)));
                return;
            }
            Quest updated = new Quest(quest.id(), quest.displayName(), quest.description(), quest.category(),
                    quest.objectives(), quest.rewards(), quest.rewardTiers(), quest.requiredQuests(), quest.repeatable(),
                    quest.cooldownSeconds(), quest.autoStart(), quest.autoTurnIn(), ref, quest.requiredPermission(),
                    quest.sortOrder());
            questRepository.save(updated);
            questService.reload();
            questPermissionRegistrar.refresh();
            player.sendMessage(messages.render("quest.npc-linked", player, Map.of(
                    "%quest%", quest.displayName(), "%provider%", ref.provider().name(), "%npcid%", ref.npcId())));
        });
        player.sendMessage(messages.render("quest.npc-link-prompt", player, Map.of("%quest%", questId)));
        return 1;
    }

    private static int npcUnlink(CommandContext<CommandSourceStack> ctx, QuestService questService,
                                  QuestDefinitionRepository questRepository, NpcLinkService npcLinkService,
                                  TrackerRegistrationManager trackerRegistrationManager,
                                  QuestPermissionRegistrar questPermissionRegistrar, MessagesConfig messages) {
        CommandSender sender = ctx.getSource().getSender();
        String questId = StringArgumentType.getString(ctx, "quest");
        Quest quest = questService.quest(questId).orElse(null);
        if (quest == null) {
            sender.sendMessage(messages.render("quest.editor-not-found", Map.of("%id%", questId)));
            return 0;
        }
        if (sender instanceof Player player) {
            npcLinkService.cancel(player);
        }
        Quest updated = new Quest(quest.id(), quest.displayName(), quest.description(), quest.category(),
                quest.objectives(), quest.rewards(), quest.rewardTiers(), quest.requiredQuests(), quest.repeatable(),
                quest.cooldownSeconds(), quest.autoStart(), quest.autoTurnIn(), null, quest.requiredPermission(),
                quest.sortOrder());
        questRepository.save(updated);
        questService.reload();
        trackerRegistrationManager.refresh();
        questPermissionRegistrar.refresh();
        sender.sendMessage(messages.render("quest.npc-unlinked", Map.of("%quest%", quest.displayName())));
        return 1;
    }

    private static Player resolveSinglePlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            List<Player> players = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
            return players.isEmpty() ? null : players.get(0);
        } catch (CommandSyntaxException e) {
            return null;
        }
    }
}
