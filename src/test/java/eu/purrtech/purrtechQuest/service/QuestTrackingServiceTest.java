package eu.purrtech.purrtechQuest.service;

import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.config.TrackingDisplay;
import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.model.QuestReward;
import eu.purrtech.purrtechQuest.model.QuestStatus;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reproduces the bug report this was written against: tracking a quest with a choice group used to keep
 * showing the *other*, now-unreachable alternative in the action bar/boss bar instead of moving on to
 * whatever's actually next — because {@code progressLine}/{@code overallProgress} picked the first
 * not-yet-satisfied objective by list order without knowing some of those can never be satisfied anymore.
 * {@code progressLine}/{@code overallProgress} are package-visible specifically so this can call them
 * directly instead of going through {@code tick()}'s Bukkit scheduler/action-bar/boss-bar plumbing.
 */
class QuestTrackingServiceTest {

    private QuestTrackingService trackingService;
    private Player player;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        MessagesConfig messages = loadMessages(tempDir);
        trackingService = new QuestTrackingService(
                mock(QuestService.class), mock(PlayerQuestDataCache.class), messages, TrackingDisplay.ACTION_BAR);

        player = mock(Player.class);
        when(player.locale()).thenReturn(Locale.forLanguageTag("cs"));
    }

    private static MessagesConfig loadMessages(Path tempDir) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.resolve("plugin-data").toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getResource(anyString()))
                .thenAnswer(invocation -> QuestTrackingServiceTest.class.getClassLoader()
                        .getResourceAsStream(invocation.getArgument(0)));
        return MessagesConfig.load(plugin, "cs");
    }

    private static Quest choosePathQuest() {
        return new Quest("choose_path", "Test choice", "", "test",
                List.of(
                        new QuestObjective(ObjectiveType.KILL_ENTITY, "ZOMBIE", 3, Map.of(), "path"),
                        new QuestObjective(ObjectiveType.BREAK_BLOCK, "STONE", 3, Map.of(), "path"),
                        new QuestObjective(ObjectiveType.COLLECT_ITEM, "RAW_IRON", 1)),
                List.of(new QuestReward.Experience(1)), List.of(),
                List.of(), false, 0, false, false, null);
    }

    @Test
    void progressLineSkipsLockedAlternativeAndShowsTheNextRealObjective() {
        Quest quest = choosePathQuest();
        QuestProgress progress = new QuestProgress("choose_path", 3);
        progress.status(QuestStatus.IN_PROGRESS);
        // Satisfy the "path" choice group via objective 0; objective 1 (the other alternative) is now
        // locked out and must never be what's shown as "next."
        progress.incrementObjective(0, 3);

        Component line = trackingService.progressLine(quest, progress, player);
        String text = PlainTextComponentSerializer.plainText().serialize(line);

        assertTrue(text.contains("RAW_IRON"), "should point at the mandatory objective that's actually next: " + text);
        assertFalse(text.contains("STONE"), "must not show the locked-out alternative: " + text);
    }

    @Test
    void overallProgressExcludesLockedAlternativeFromTheTotal() {
        Quest quest = choosePathQuest();
        QuestProgress progress = new QuestProgress("choose_path", 3);
        progress.status(QuestStatus.IN_PROGRESS);
        progress.incrementObjective(0, 3);

        // Without excluding the locked-out objective 1 (amount 3) from the total, this would be 3/7 instead
        // of 3/4 — the boss bar would stay capped well under "done" even once nothing else can be completed
        // through that alternative.
        float fraction = QuestTrackingService.overallProgress(quest, progress);
        assertEquals(3f / 4f, fraction, 0.0001f);
    }
}
