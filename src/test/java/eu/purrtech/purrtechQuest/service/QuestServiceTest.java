package eu.purrtech.purrtechQuest.service;

import eu.purrtech.purrtechQuest.api.event.QuestAcceptEvent;
import eu.purrtech.purrtechQuest.api.event.QuestCompleteEvent;
import eu.purrtech.purrtechQuest.api.event.QuestObjectiveProgressEvent;
import eu.purrtech.purrtechQuest.api.event.QuestTurnInEvent;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.model.QuestReward;
import eu.purrtech.purrtechQuest.model.QuestStatus;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.storage.DatabaseManager;
import eu.purrtech.purrtechQuest.storage.PlayerDataRepository;
import eu.purrtech.purrtechQuest.storage.QuestDefinitionRepository;
import eu.purrtech.purrtechQuest.storage.SqlitePlayerDataRepository;
import eu.purrtech.purrtechQuest.storage.YamlQuestDefinitionRepository;
import eu.purrtech.purrtechQuest.storage.migration.SchemaMigrator;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link QuestService} against real storage (real YAML files, real SQLite via HikariCP) and only
 * mocks the thin Bukkit surface it touches ({@link Player}, and a {@link JavaPlugin} stand-in used purely
 * to load {@link MessagesConfig}'s bundled lang files). MockBukkit-v1.21:3.133.2 can't be used here — it
 * fails to boot its mock server against Paper 1.21.11 with an internal tag-parsing bug unrelated to this
 * plugin's code ("Invalid namespace key minecraft:chain").
 */
class QuestServiceTest {

    private QuestService questService;
    private QuestDefinitionRepository questRepository;
    private PlayerQuestDataCache playerCache;
    private MessagesConfig messages;
    private Player player;
    private UUID playerId;
    private List<Event> publishedEvents;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        messages = loadMessages(tempDir.resolve("plugin-data"));

        questRepository = new YamlQuestDefinitionRepository(tempDir.resolve("quests"), Logger.getAnonymousLogger());
        writeTestQuests(questRepository);

        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("test.db"));
        SchemaMigrator.migrate(databaseManager.dataSource());
        PlayerDataRepository playerRepository = new SqlitePlayerDataRepository(databaseManager.dataSource());
        playerCache = new PlayerQuestDataCache(playerRepository);

        RewardService rewardService = new RewardService(Logger.getAnonymousLogger());
        publishedEvents = new ArrayList<>();
        questService = new QuestService(questRepository, playerCache, rewardService, messages, publishedEvents::add);

        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.locale()).thenReturn(Locale.forLanguageTag("cs"));

        playerCache.load(playerId).join();
    }

    private static MessagesConfig loadMessages(Path dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getResource(anyString()))
                .thenAnswer(invocation -> QuestServiceTest.class.getClassLoader()
                        .getResourceAsStream(invocation.getArgument(0)));
        return MessagesConfig.load(plugin, "cs");
    }

    private static void writeTestQuests(QuestDefinitionRepository repository) {
        repository.save(new Quest(
                "mine_iron", "Sběr železa", "", "mining",
                List.of(new QuestObjective(ObjectiveType.COLLECT_ITEM, "RAW_IRON", 10)),
                List.of(new QuestReward.Experience(20)), List.of(),
                List.of(), false, 0, false, true, null));

        repository.save(new Quest(
                "kill_zombie", "Zabij zombíka", "", "combat",
                List.of(new QuestObjective(ObjectiveType.KILL_ENTITY, "ZOMBIE", 1)),
                List.of(new QuestReward.Experience(5)), List.of(),
                List.of("mine_iron"), true, 3600, false, false, null));

        repository.save(new Quest(
                "break_stone", "Test abandon", "", "test",
                List.of(new QuestObjective(ObjectiveType.BREAK_BLOCK, "STONE", 5)),
                List.of(new QuestReward.Experience(1)), List.of(),
                List.of(), false, 0, false, false, null));

        // Objectives 0 and 1 are alternatives ("path" choice group); objective 2 is mandatory on top of
        // whichever alternative the player picks.
        repository.save(new Quest(
                "choose_path", "Test choice", "", "test",
                List.of(
                        new QuestObjective(ObjectiveType.KILL_ENTITY, "ZOMBIE", 3, Map.of(), "path"),
                        new QuestObjective(ObjectiveType.BREAK_BLOCK, "STONE", 3, Map.of(), "path"),
                        new QuestObjective(ObjectiveType.COLLECT_ITEM, "RAW_IRON", 1)),
                List.of(new QuestReward.Experience(1)), List.of(),
                List.of(), false, 0, false, false, null));

        repository.save(new Quest(
                "welcome", "Welcome", "", "default",
                List.of(new QuestObjective(ObjectiveType.BREAK_BLOCK, "STONE", 1)),
                List.of(new QuestReward.Experience(1)), List.of(),
                List.of(), false, 0, true, false, null));

        // Only unlocks once "mine_iron" is turned in — used to verify auto-start fires the moment a
        // prerequisite is satisfied, not just on the next join.
        repository.save(new Quest(
                "chapter_two", "Chapter Two", "", "default",
                List.of(new QuestObjective(ObjectiveType.BREAK_BLOCK, "DIRT", 1)),
                List.of(new QuestReward.Experience(1)), List.of(),
                List.of("mine_iron"), false, 0, true, false, null));

        repository.save(new Quest(
                "vip_quest", "VIP Quest", "", "default",
                List.of(new QuestObjective(ObjectiveType.BREAK_BLOCK, "GOLD_BLOCK", 1)),
                List.of(new QuestReward.Experience(1)), List.of(),
                List.of(), false, 0, false, false, null, "purrtechquest.quest.vip"));
    }

    @Test
    void cannotAcceptQuestWithUnmetPrerequisites() {
        assertEquals(QuestService.AcceptResult.PREREQUISITES_NOT_MET, questService.acceptQuest(player, "kill_zombie"));
    }

    @Test
    void completingAllObjectivesAutoTurnsInWhenConfigured() {
        assertEquals(QuestService.AcceptResult.ACCEPTED, questService.acceptQuest(player, "mine_iron"));
        assertEquals(QuestService.AcceptResult.ALREADY_IN_PROGRESS, questService.acceptQuest(player, "mine_iron"));

        questService.updateProgress(player, ObjectiveType.COLLECT_ITEM, "RAW_IRON", 7);
        QuestProgress progress = playerCache.get(playerId).progress("mine_iron");
        assertEquals(QuestStatus.IN_PROGRESS, progress.status());
        assertEquals(7, progress.objectiveProgress(0));

        // Overshoots the required amount — progress must clamp at the objective's amount, not exceed it.
        questService.updateProgress(player, ObjectiveType.COLLECT_ITEM, "RAW_IRON", 5);
        assertEquals(QuestStatus.TURNED_IN, progress.status());
        assertEquals(10, progress.objectiveProgress(0));
        assertEquals(1, progress.timesCompleted());
    }

    @Test
    void prerequisitesUnlockAfterTurnInAndCooldownBlocksReacceptance() {
        questService.acceptQuest(player, "mine_iron");
        questService.updateProgress(player, ObjectiveType.COLLECT_ITEM, "RAW_IRON", 10);
        assertEquals(QuestStatus.TURNED_IN, playerCache.get(playerId).progress("mine_iron").status());

        assertEquals(QuestService.AcceptResult.ACCEPTED, questService.acceptQuest(player, "kill_zombie"));

        questService.updateProgress(player, ObjectiveType.KILL_ENTITY, "ZOMBIE", 1);
        QuestProgress zombieProgress = playerCache.get(playerId).progress("kill_zombie");
        assertEquals(QuestStatus.COMPLETED, zombieProgress.status());

        assertEquals(QuestService.TurnInResult.TURNED_IN, questService.turnIn(player, "kill_zombie"));
        assertEquals(QuestService.AcceptResult.ON_COOLDOWN, questService.acceptQuest(player, "kill_zombie"));
        assertTrue(questService.cooldownReadyAt(player, "kill_zombie").isPresent());
    }

    @Test
    void abandonResetsProgressAndAllowsReaccepting() {
        questService.acceptQuest(player, "break_stone");
        questService.updateProgress(player, ObjectiveType.BREAK_BLOCK, "STONE", 3);
        assertEquals(3, playerCache.get(playerId).progress("break_stone").objectiveProgress(0));

        assertEquals(QuestService.AbandonResult.ABANDONED, questService.abandonQuest(player, "break_stone"));
        assertEquals(QuestStatus.NOT_ACCEPTED, playerCache.get(playerId).progress("break_stone").status());

        assertEquals(QuestService.AcceptResult.ACCEPTED, questService.acceptQuest(player, "break_stone"));
        assertEquals(0, playerCache.get(playerId).progress("break_stone").objectiveProgress(0));
    }

    @Test
    void turnInFailsBeforeObjectivesComplete() {
        questService.acceptQuest(player, "break_stone");
        assertEquals(QuestService.TurnInResult.OBJECTIVES_INCOMPLETE, questService.turnIn(player, "break_stone"));
    }

    @Test
    void nonMatchingObjectiveTargetIsIgnored() {
        questService.acceptQuest(player, "break_stone");
        questService.updateProgress(player, ObjectiveType.BREAK_BLOCK, "DIRT", 5);
        assertEquals(0, playerCache.get(playerId).progress("break_stone").objectiveProgress(0));
    }

    @Test
    void actionsFailGracefullyWhenPlayerDataNotLoaded() {
        Player strangerPlayer = mock(Player.class);
        when(strangerPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        assertEquals(QuestService.AcceptResult.DATA_NOT_LOADED, questService.acceptQuest(strangerPlayer, "mine_iron"));
    }

    @Test
    void publishesAcceptProgressCompleteAndTurnInEventsInOrder() {
        questService.acceptQuest(player, "mine_iron");
        questService.updateProgress(player, ObjectiveType.COLLECT_ITEM, "RAW_IRON", 10);

        // Turning "mine_iron" in also triggers the auto-start cascade (see
        // autoStartFiresImmediatelyWhenTurningInAQuestUnlocksAnotherAutoStartQuest) — "welcome" and now-
        // unlocked "chapter_two" get their own QuestAcceptEvents right after, so this only pins down
        // "mine_iron"'s own four events at the front rather than the total count.
        assertTrue(publishedEvents.size() >= 4);
        assertTrue(publishedEvents.get(0) instanceof QuestAcceptEvent);
        assertTrue(publishedEvents.get(1) instanceof QuestObjectiveProgressEvent);
        assertTrue(publishedEvents.get(2) instanceof QuestCompleteEvent);
        assertTrue(publishedEvents.get(3) instanceof QuestTurnInEvent);
    }

    @Test
    void choiceGroupLocksOutTheOtherAlternativeOnceOneIsSatisfied() {
        questService.acceptQuest(player, "choose_path");
        QuestProgress progress = playerCache.get(playerId).progress("choose_path");

        // Satisfy the "path" choice group via objective 0 (KILL_ENTITY). The mandatory objective 2 is
        // still incomplete, so the quest stays IN_PROGRESS — this is what lets the next call actually
        // exercise the lockout instead of being skipped for an unrelated reason (quest already finished).
        questService.updateProgress(player, ObjectiveType.KILL_ENTITY, "ZOMBIE", 3);
        assertEquals(3, progress.objectiveProgress(0));
        assertEquals(QuestStatus.IN_PROGRESS, progress.status());

        // The other alternative (objective 1, BREAK_BLOCK) must not accept progress anymore now that its
        // choice group is already satisfied by objective 0.
        questService.updateProgress(player, ObjectiveType.BREAK_BLOCK, "STONE", 3);
        assertEquals(0, progress.objectiveProgress(1));
        assertEquals(QuestStatus.IN_PROGRESS, progress.status());

        // Finishing the mandatory objective completes the quest even though only one of the two
        // alternatives was ever satisfied — a choice group only needs one member done, not all of them.
        questService.updateProgress(player, ObjectiveType.COLLECT_ITEM, "RAW_IRON", 1);
        assertEquals(QuestStatus.COMPLETED, progress.status());
        assertEquals(0, progress.objectiveProgress(1));
    }

    @Test
    void autoStartAcceptsAnUntouchedAutoStartQuest() {
        questService.autoStartEligibleQuests(player);
        assertEquals(QuestStatus.IN_PROGRESS, playerCache.get(playerId).progress("welcome").status());
    }

    @Test
    void autoStartOffersTheQuestAgainAfterItWasAbandoned() {
        questService.autoStartEligibleQuests(player);
        assertEquals(QuestStatus.IN_PROGRESS, playerCache.get(playerId).progress("welcome").status());

        // Abandoning leaves behind a non-null NOT_ACCEPTED progress entry rather than clearing it — that
        // entry must not be mistaken for "already handled" on the next join, or auto-start would only ever
        // fire once per player, permanently, for a quest they're allowed to receive repeatedly.
        questService.abandonQuest(player, "welcome");
        assertEquals(QuestStatus.NOT_ACCEPTED, playerCache.get(playerId).progress("welcome").status());

        questService.autoStartEligibleQuests(player);
        assertEquals(QuestStatus.IN_PROGRESS, playerCache.get(playerId).progress("welcome").status());
    }

    @Test
    void autoStartFiresImmediatelyWhenTurningInAQuestUnlocksAnotherAutoStartQuest() {
        // "chapter_two" is auto-start but requires "mine_iron" turned in first — before that, it must not
        // have started (mirrors the join-time behavior: prerequisites still gate acceptance).
        questService.acceptQuest(player, "mine_iron");
        assertNull(playerCache.get(playerId).progress("chapter_two"));

        questService.updateProgress(player, ObjectiveType.COLLECT_ITEM, "RAW_IRON", 10);
        assertEquals(QuestStatus.TURNED_IN, playerCache.get(playerId).progress("mine_iron").status());

        // No relog, no manual autoStartEligibleQuests call — turning in "mine_iron" must have satisfied
        // "chapter_two"'s prerequisite and auto-accepted it right away.
        QuestProgress chapterTwo = playerCache.get(playerId).progress("chapter_two");
        assertEquals(QuestStatus.IN_PROGRESS, chapterTwo.status());
    }

    @Test
    void acceptingAPermissionGatedQuestFailsWithoutThePermission() {
        assertEquals(QuestService.AcceptResult.MISSING_PERMISSION, questService.acceptQuest(player, "vip_quest"));
        assertNull(playerCache.get(playerId).progress("vip_quest"));
    }

    @Test
    void acceptingAPermissionGatedQuestSucceedsWithThePermission() {
        when(player.hasPermission("purrtechquest.quest.vip")).thenReturn(true);
        assertEquals(QuestService.AcceptResult.ACCEPTED, questService.acceptQuest(player, "vip_quest"));
        assertEquals(QuestStatus.IN_PROGRESS, playerCache.get(playerId).progress("vip_quest").status());
    }

    @Test
    void categoriesReturnsEveryDistinctCategoryUsedByLoadedQuests() {
        assertEquals(Set.of("combat", "default", "mining", "test"), questService.categories());
    }

    @Test
    void categorySummaryTracksActiveQuestAndTurnedInCount() {
        QuestService.CategorySummary before = questService.categorySummary(player, "mining");
        assertNull(before.activeQuest());
        assertEquals(0, before.turnedIn());
        assertEquals(1, before.total());

        questService.acceptQuest(player, "mine_iron");
        QuestService.CategorySummary inProgress = questService.categorySummary(player, "mining");
        assertEquals("mine_iron", inProgress.activeQuest().id());
        assertEquals(0, inProgress.turnedIn());

        // mine_iron is autoTurnIn, so completing its objective also turns it in immediately.
        questService.updateProgress(player, ObjectiveType.COLLECT_ITEM, "RAW_IRON", 10);
        QuestService.CategorySummary done = questService.categorySummary(player, "mining");
        assertNull(done.activeQuest());
        assertEquals(1, done.turnedIn());
    }

    @Test
    void cancellingAcceptEventBlocksAcceptance() {
        QuestService cancellingService = new QuestService(questRepository, playerCache,
                new RewardService(Logger.getAnonymousLogger()), messages, event -> {
                    if (event instanceof Cancellable cancellable) {
                        cancellable.setCancelled(true);
                    }
                });

        assertEquals(QuestService.AcceptResult.CANCELLED, cancellingService.acceptQuest(player, "break_stone"));
        assertNull(playerCache.get(playerId).progress("break_stone"));
    }
}
