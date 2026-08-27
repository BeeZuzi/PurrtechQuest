package eu.purrtech.purrtechQuest.service;

import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.config.TrackingDisplay;
import eu.purrtech.purrtechQuest.model.ChoiceGroups;
import eu.purrtech.purrtechQuest.model.PlayerQuestData;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.model.QuestStatus;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which quest (if any) each player wants a persistent progress readout for, and renders it every
 * tick as either an action bar line or a boss bar (see {@link TrackingDisplay}). Deliberately polls rather
 * than being pushed events by {@link QuestService} — there's no event bus yet (that's Phase 5's public
 * API), so "does the tracked quest still make sense" is just re-checked each tick and auto-untracked if
 * not (turned in, abandoned, reset by an admin, ...).
 */
public final class QuestTrackingService {

    private final QuestService questService;
    private final PlayerQuestDataCache playerCache;
    private final MessagesConfig messages;
    private final TrackingDisplay display;

    private final Map<UUID, String> tracked = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    public QuestTrackingService(QuestService questService, PlayerQuestDataCache playerCache,
                                 MessagesConfig messages, TrackingDisplay display) {
        this.questService = questService;
        this.playerCache = playerCache;
        this.messages = messages;
        this.display = display;
    }

    public void track(Player player, String questId) {
        tracked.put(player.getUniqueId(), questId);
    }

    public void untrack(Player player) {
        UUID id = player.getUniqueId();
        tracked.remove(id);
        BossBar bar = bossBars.remove(id);
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public Optional<String> trackedQuestId(Player player) {
        return Optional.ofNullable(tracked.get(player.getUniqueId()));
    }

    public boolean isTracking(Player player, String questId) {
        return questId.equals(tracked.get(player.getUniqueId()));
    }

    public void tick(Iterable<? extends Player> onlinePlayers) {
        for (Player player : onlinePlayers) {
            String questId = tracked.get(player.getUniqueId());
            if (questId == null) {
                continue;
            }

            Quest quest = questService.quest(questId).orElse(null);
            PlayerQuestData data = playerCache.get(player.getUniqueId());
            QuestProgress progress = data == null ? null : data.progress(questId);

            if (quest == null || progress == null || progress.status() != QuestStatus.IN_PROGRESS) {
                untrack(player);
                continue;
            }

            Component line = progressLine(quest, progress, player);
            if (display == TrackingDisplay.BOSS_BAR) {
                updateBossBar(player, line, quest, progress);
            } else {
                player.sendActionBar(line);
            }
        }
    }

    /**
     * Called on quit so a boss bar doesn't linger client-side for a player who's no longer connected.
     */
    public void handleQuit(Player player) {
        untrack(player);
    }

    /**
     * Called on shutdown to hide every boss bar still showing — action bar text needs no such cleanup.
     */
    public void clearAll() {
        for (Map.Entry<UUID, BossBar> entry : bossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bossBars.clear();
        tracked.clear();
    }

    private void updateBossBar(Player player, Component line, Quest quest, QuestProgress progress) {
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(line, 0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            bossBars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(line);
        }
        bar.progress(overallProgress(quest, progress));
    }

    /** Package-visible so {@code QuestTrackingServiceTest} can exercise the choice-group-aware logic directly. */
    static float overallProgress(Quest quest, QuestProgress progress) {
        int total = 0;
        int done = 0;
        List<QuestObjective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            // A locked-out alternative can never be completed — counting its amount toward the total would
            // cap the bar below 100% forever once its choice group was satisfied through a different member.
            if (ChoiceGroups.isLockedOut(quest, progress, i)) {
                continue;
            }
            int amount = objectives.get(i).amount();
            total += amount;
            done += Math.min(progress.objectiveProgress(i), amount);
        }
        return total == 0 ? 0f : Math.min(1f, (float) done / total);
    }

    /**
     * The first not-yet-satisfied objective, skipping any locked-out choice-group alternative — showing one
     * of those would tell the player to do something that can no longer count, instead of whatever's
     * actually next.
     */
    /** Package-visible so {@code QuestTrackingServiceTest} can exercise the choice-group-aware logic directly. */
    Component progressLine(Quest quest, QuestProgress progress, Player player) {
        List<QuestObjective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            if (progress.objectiveProgress(i) >= objective.amount()) {
                continue;
            }
            if (ChoiceGroups.isLockedOut(quest, progress, i)) {
                continue;
            }
            return messages.render("quest.tracking-line", player, Map.of(
                    "%quest%", quest.displayName(),
                    "%label%", objective.label(),
                    "%progress%", String.valueOf(progress.objectiveProgress(i)),
                    "%amount%", String.valueOf(objective.amount())));
        }
        return messages.render("quest.tracking-ready", player, Map.of("%quest%", quest.displayName()));
    }
}
