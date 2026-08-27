package eu.purrtech.purrtechQuest.service;

import eu.purrtech.purrtechQuest.api.event.QuestAbandonEvent;
import eu.purrtech.purrtechQuest.api.event.QuestAcceptEvent;
import eu.purrtech.purrtechQuest.api.event.QuestCompleteEvent;
import eu.purrtech.purrtechQuest.api.event.QuestObjectiveProgressEvent;
import eu.purrtech.purrtechQuest.api.event.QuestTurnInEvent;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.model.ChoiceGroups;
import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.model.PlayerQuestData;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.model.QuestStatus;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.storage.QuestDefinitionRepository;
import eu.purrtech.purrtechQuest.tracking.BossDefeatMatcher;
import eu.purrtech.purrtechQuest.tracking.EarnMoneyMatcher;
import eu.purrtech.purrtechQuest.tracking.EntityMatcher;
import eu.purrtech.purrtechQuest.tracking.ItemMatcher;
import eu.purrtech.purrtechQuest.tracking.LocationMatcher;
import eu.purrtech.purrtechQuest.tracking.NpcMatcher;
import eu.purrtech.purrtechQuest.tracking.PlaceholderMatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Core business logic: quest acceptance, objective progress, prerequisites/cooldowns, completion and
 * turn-in. Deliberately Bukkit-light — {@link Player} is only used for its {@link java.util.UUID} to key
 * into {@link PlayerQuestDataCache} and, at the very end of a successful action, to hand off to
 * {@link RewardService}. Everything here runs on the main thread (it mutates live {@link QuestProgress}
 * objects in place; see {@link PlayerQuestData#snapshot()} for why that's safe with async saves).
 * <p>
 * Success messages (accepted/abandoned/completed/turned-in) are sent from here rather than by callers,
 * because completion can be triggered from a listener with no command context at all (e.g. an
 * {@code autoTurnIn} quest finishing the moment the last mob dies) — command handlers only need to render
 * the error-path messages for their specific result codes.
 */
public final class QuestService {

    private final QuestDefinitionRepository questRepository;
    private final PlayerQuestDataCache playerCache;
    private final RewardService rewardService;
    private final MessagesConfig messages;
    private final EventPublisher eventPublisher;

    private static final Title.Times NOTIFICATION_TIMES =
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1600), Duration.ofMillis(400));

    // Raw sound keys rather than the org.bukkit.Sound enum: on modern Paper, Sound is registry-backed and
    // its static initializer needs a live server (Bukkit.getServer()), which makes it unusable from plain
    // unit tests. The string overload of Player#playSound resolves the same vanilla sounds at runtime
    // without that dependency.
    private static final String SOUND_ACCEPTED = "entity.experience_orb.pickup";
    private static final String SOUND_COMPLETED = "entity.player.levelup";
    private static final String SOUND_TURNED_IN = "ui.toast.challenge_complete";

    private volatile Map<String, Quest> questsById = Map.of();

    public QuestService(QuestDefinitionRepository questRepository, PlayerQuestDataCache playerCache,
                         RewardService rewardService, MessagesConfig messages, EventPublisher eventPublisher) {
        this.questRepository = questRepository;
        this.playerCache = playerCache;
        this.rewardService = rewardService;
        this.messages = messages;
        this.eventPublisher = eventPublisher;
        reload();
    }

    public void reload() {
        Map<String, Quest> map = new LinkedHashMap<>();
        for (Quest quest : questRepository.loadAll()) {
            map.put(quest.id(), quest);
        }
        this.questsById = Map.copyOf(map);
    }

    public Collection<Quest> allQuests() {
        return questsById.values();
    }

    public Optional<Quest> quest(String id) {
        return Optional.ofNullable(questsById.get(id));
    }

    /**
     * Every {@link ObjectiveType} used by at least one loaded quest — drives
     * {@link eu.purrtech.purrtechQuest.tracking.TrackerRegistrationManager}'s decision about which
     * trackers are worth having registered at all.
     */
    public Set<ObjectiveType> usedObjectiveTypes() {
        Set<ObjectiveType> types = EnumSet.noneOf(ObjectiveType.class);
        for (Quest quest : questsById.values()) {
            for (QuestObjective objective : quest.objectives()) {
                types.add(objective.type());
            }
        }
        return types;
    }

    /** Every category any loaded quest uses, sorted for deterministic listing — see {@code QuestCategoryGui}. */
    public Set<String> categories() {
        Set<String> categories = new TreeSet<>();
        for (Quest quest : questsById.values()) {
            categories.add(quest.category());
        }
        return categories;
    }

    /** All quests belonging to one category, in the same order {@link #allQuests()} would report them. */
    public List<Quest> questsInCategory(String category) {
        return questsById.values().stream().filter(quest -> quest.category().equals(category)).toList();
    }

    /**
     * What {@code QuestCategoryGui} shows for one category: the first quest the player currently has
     * {@code IN_PROGRESS} in it (or {@code null} if none), and how many of the category's quests they've
     * turned in versus the category's total quest count.
     */
    public record CategorySummary(Quest activeQuest, int turnedIn, int total) {
    }

    public CategorySummary categorySummary(Player player, String category) {
        List<Quest> quests = questsInCategory(category);
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        Quest active = null;
        int turnedIn = 0;
        for (Quest quest : quests) {
            QuestProgress progress = data == null ? null : data.progress(quest.id());
            QuestStatus status = progress == null ? QuestStatus.NOT_ACCEPTED : progress.status();
            if (status == QuestStatus.IN_PROGRESS && active == null) {
                active = quest;
            }
            if (status == QuestStatus.TURNED_IN) {
                turnedIn++;
            }
        }
        return new CategorySummary(active, turnedIn, quests.size());
    }

    /**
     * The first quest the player currently has {@code IN_PROGRESS}, across every loaded quest rather than
     * one category — exposed via {@link eu.purrtech.purrtechQuest.api.PurrtechQuestAPI} for integrations
     * that want "what should this player do next" without caring about categories.
     */
    public Optional<Quest> currentActiveQuest(Player player) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null) {
            return Optional.empty();
        }
        for (Quest quest : questsById.values()) {
            QuestProgress progress = data.progress(quest.id());
            if (progress != null && progress.status() == QuestStatus.IN_PROGRESS) {
                return Optional.of(quest);
            }
        }
        return Optional.empty();
    }

    /** Turned-in count vs. total across every loaded quest, regardless of category. */
    public record OverallSummary(int turnedIn, int total) {
    }

    public OverallSummary overallSummary(Player player) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        int turnedIn = 0;
        for (Quest quest : questsById.values()) {
            QuestProgress progress = data == null ? null : data.progress(quest.id());
            if (progress != null && progress.status() == QuestStatus.TURNED_IN) {
                turnedIn++;
            }
        }
        return new OverallSummary(turnedIn, questsById.size());
    }

    public enum AcceptResult {
        ACCEPTED, NOT_FOUND, ALREADY_IN_PROGRESS, ALREADY_COMPLETED, PREREQUISITES_NOT_MET, ON_COOLDOWN,
        MISSING_PERMISSION, DATA_NOT_LOADED, CANCELLED
    }

    public enum AbandonResult {
        ABANDONED, NOT_IN_PROGRESS, DATA_NOT_LOADED
    }

    public enum TurnInResult {
        TURNED_IN, NOT_FOUND, NOT_ACCEPTED, OBJECTIVES_INCOMPLETE, ALREADY_TURNED_IN, DATA_NOT_LOADED, CANCELLED
    }

    public AcceptResult acceptQuest(Player player, String questId) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null) {
            return AcceptResult.DATA_NOT_LOADED;
        }
        Quest quest = questsById.get(questId);
        if (quest == null) {
            return AcceptResult.NOT_FOUND;
        }

        QuestProgress existing = data.progress(questId);
        int timesCompleted = existing == null ? 0 : existing.timesCompleted();

        if (existing != null) {
            if (existing.status() == QuestStatus.IN_PROGRESS || existing.status() == QuestStatus.COMPLETED) {
                return AcceptResult.ALREADY_IN_PROGRESS;
            }
            if (existing.status() == QuestStatus.TURNED_IN) {
                if (!quest.repeatable()) {
                    return AcceptResult.ALREADY_COMPLETED;
                }
                if (quest.cooldownSeconds() > 0 && existing.completedAt() != null) {
                    Instant readyAt = existing.completedAt().plusSeconds(quest.cooldownSeconds());
                    if (Instant.now().isBefore(readyAt)) {
                        return AcceptResult.ON_COOLDOWN;
                    }
                }
            }
        }

        if (quest.requiredPermission() != null && !player.hasPermission(quest.requiredPermission())) {
            return AcceptResult.MISSING_PERMISSION;
        }

        if (!prerequisitesMet(data, quest)) {
            return AcceptResult.PREREQUISITES_NOT_MET;
        }

        QuestAcceptEvent acceptEvent = new QuestAcceptEvent(player, quest);
        eventPublisher.publish(acceptEvent);
        if (acceptEvent.isCancelled()) {
            return AcceptResult.CANCELLED;
        }

        startFreshProgress(player, data, quest, timesCompleted);
        Component message = messages.render("quest.accepted", player, Map.of("%quest%", quest.displayName()));
        player.sendMessage(message);
        notify(player, message, SOUND_ACCEPTED);
        return AcceptResult.ACCEPTED;
    }

    /**
     * Force-starts a quest for an admin, bypassing prerequisites/cooldown (but not "already in progress" —
     * re-running {@code /questadmin give} on an active quest is a no-op, use {@code reset} first).
     */
    public boolean adminForceAccept(Player player, String questId) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        Quest quest = questsById.get(questId);
        if (data == null || quest == null) {
            return false;
        }
        QuestProgress existing = data.progress(questId);
        if (existing != null && (existing.status() == QuestStatus.IN_PROGRESS || existing.status() == QuestStatus.COMPLETED)) {
            return false;
        }
        int timesCompleted = existing == null ? 0 : existing.timesCompleted();
        startFreshProgress(player, data, quest, timesCompleted);
        return true;
    }

    /**
     * Wipes a player's progress on a quest entirely (as if they'd never touched it). Admin/testing tool.
     */
    public boolean adminResetProgress(Player player, String questId) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null || !questsById.containsKey(questId)) {
            return false;
        }
        data.states().remove(questId);
        data.markDirty();
        return true;
    }

    /**
     * When {@link AcceptResult#ON_COOLDOWN} is returned, this recomputes when the quest will be
     * acceptable again — split out purely so the command layer can render it into the cooldown message.
     */
    public Optional<Instant> cooldownReadyAt(Player player, String questId) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null) {
            return Optional.empty();
        }
        Quest quest = questsById.get(questId);
        QuestProgress progress = data.progress(questId);
        if (quest == null || progress == null || progress.completedAt() == null || quest.cooldownSeconds() <= 0) {
            return Optional.empty();
        }
        return Optional.of(progress.completedAt().plusSeconds(quest.cooldownSeconds()));
    }

    private void startFreshProgress(Player player, PlayerQuestData data, Quest quest, int timesCompleted) {
        QuestProgress fresh = new QuestProgress(quest.id(), QuestStatus.IN_PROGRESS,
                new int[quest.objectives().size()], Instant.now(), null, timesCompleted);
        captureEarnMoneyBaselines(player, quest, fresh);
        data.put(fresh);
    }

    /**
     * EARN_MONEY objectives complete once the player's balance rises by {@code amount} above whatever it
     * was when they accepted the quest (see the class-level note on {@link #checkEarnMoneyObjectives}) — so
     * that starting balance has to be snapshotted right here, once, at acceptance. Only touches Vault at all
     * if the quest actually has an EARN_MONEY objective, so quests without one never pay for (or require) an
     * economy plugin.
     */
    private void captureEarnMoneyBaselines(Player player, Quest quest, QuestProgress fresh) {
        List<QuestObjective> objectives = quest.objectives();
        boolean hasEarnMoneyObjective = objectives.stream().anyMatch(o -> o.type() == ObjectiveType.EARN_MONEY);
        if (!hasEarnMoneyObjective || !EarnMoneyMatcher.isPresent()) {
            return;
        }
        double balance = EarnMoneyMatcher.balanceOf(player);
        for (int i = 0; i < objectives.size(); i++) {
            if (objectives.get(i).type() == ObjectiveType.EARN_MONEY) {
                fresh.baseline(i, balance);
            }
        }
    }

    public AbandonResult abandonQuest(Player player, String questId) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null) {
            return AbandonResult.DATA_NOT_LOADED;
        }
        QuestProgress progress = data.progress(questId);
        if (progress == null || progress.status() != QuestStatus.IN_PROGRESS) {
            return AbandonResult.NOT_IN_PROGRESS;
        }
        data.put(new QuestProgress(questId, QuestStatus.NOT_ACCEPTED,
                new int[progress.objectiveProgress().length], null, null, progress.timesCompleted()));
        Quest quest = questsById.get(questId);
        String displayName = quest != null ? quest.displayName() : questId;
        player.sendMessage(messages.render("quest.abandoned", player, Map.of("%quest%", displayName)));
        if (quest != null) {
            eventPublisher.publish(new QuestAbandonEvent(player, quest));
        }
        return AbandonResult.ABANDONED;
    }

    public TurnInResult turnIn(Player player, String questId) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null) {
            return TurnInResult.DATA_NOT_LOADED;
        }
        Quest quest = questsById.get(questId);
        if (quest == null) {
            return TurnInResult.NOT_FOUND;
        }
        QuestProgress progress = data.progress(questId);
        if (progress == null || progress.status() == QuestStatus.NOT_ACCEPTED) {
            return TurnInResult.NOT_ACCEPTED;
        }
        if (progress.status() == QuestStatus.TURNED_IN) {
            return TurnInResult.ALREADY_TURNED_IN;
        }
        if (progress.status() == QuestStatus.IN_PROGRESS && !isFullyComplete(quest, progress)) {
            return TurnInResult.OBJECTIVES_INCOMPLETE;
        }

        QuestTurnInEvent turnInEvent = new QuestTurnInEvent(player, quest);
        eventPublisher.publish(turnInEvent);
        if (turnInEvent.isCancelled()) {
            return TurnInResult.CANCELLED;
        }

        rewardService.grant(player, quest);
        progress.status(QuestStatus.TURNED_IN);
        progress.incrementTimesCompleted();
        if (progress.completedAt() == null) {
            progress.completedAt(Instant.now());
        }
        data.markDirty();
        player.sendMessage(messages.render("quest.turned-in", player, Map.of("%quest%", quest.displayName())));
        player.playSound(player.getLocation(), SOUND_TURNED_IN, 1f, 1f);

        // Turning this quest in may have just satisfied another quest's required-quests prerequisite — if
        // that other quest is auto-start, it shouldn't need a relog to kick in. Runs after markDirty() above
        // so this quest's own TURNED_IN state is already visible to prerequisitesMet(). Covers both manual
        // /quest turnin and autoTurnIn (handlePotentialCompletion calls turnIn() internally).
        autoStartEligibleQuests(player);
        return TurnInResult.TURNED_IN;
    }

    /**
     * Called by objective trackers (kill/break/place/collect listeners) whenever something happened that
     * might count towards an objective. {@code target} is a raw Bukkit name (EntityType/Material) — the
     * matcher used to compare it against each objective's definition depends on {@code type}.
     */
    public void updateProgress(Player player, ObjectiveType type, String target, int amount) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        for (QuestProgress progress : List.copyOf(data.states().values())) {
            if (progress.status() != QuestStatus.IN_PROGRESS) {
                continue;
            }
            Quest quest = questsById.get(progress.questId());
            if (quest == null) {
                continue;
            }
            if (applyProgress(player, progress, quest, type, target, amount)) {
                data.markDirty();
                handlePotentialCompletion(player, quest, progress);
            }
        }
    }

    /**
     * Called on (throttled) player movement to evaluate REACH_LOCATION objectives, which aren't tied to a
     * single discrete Bukkit event the way the others are.
     */
    public void checkLocationObjectives(Player player) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        for (QuestProgress progress : List.copyOf(data.states().values())) {
            if (progress.status() != QuestStatus.IN_PROGRESS) {
                continue;
            }
            Quest quest = questsById.get(progress.questId());
            if (quest == null) {
                continue;
            }
            boolean changed = false;
            List<QuestObjective> objectives = quest.objectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                if (objective.type() != ObjectiveType.REACH_LOCATION) {
                    continue;
                }
                if (progress.objectiveProgress(i) >= objective.amount()) {
                    continue;
                }
                if (isChoiceLockedOut(quest, progress, i)) {
                    continue;
                }
                if (LocationMatcher.isWithin(objective, player.getLocation())) {
                    int previous = progress.objectiveProgress(i);
                    progress.incrementObjective(i, objective.amount() - previous);
                    changed = true;
                    eventPublisher.publish(
                            new QuestObjectiveProgressEvent(player, quest, i, previous, progress.objectiveProgress(i)));
                }
            }
            if (changed) {
                data.markDirty();
                handlePotentialCompletion(player, quest, progress);
            }
        }
    }

    /**
     * Called on a timer (see {@code PurrtechQuest.registerPlaceholderCheckTask}) to evaluate
     * PLACEHOLDER_CHECK objectives — like REACH_LOCATION, there's no single Bukkit event that fires when a
     * placeholder's underlying value changes, so this polls instead. Bails out immediately when
     * PlaceholderAPI isn't installed, so an idle server without it pays only the presence check per tick.
     */
    public void checkPlaceholderObjectives(Player player) {
        if (!PlaceholderMatcher.isPresent()) {
            return;
        }
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        for (QuestProgress progress : List.copyOf(data.states().values())) {
            if (progress.status() != QuestStatus.IN_PROGRESS) {
                continue;
            }
            Quest quest = questsById.get(progress.questId());
            if (quest == null) {
                continue;
            }
            boolean changed = false;
            List<QuestObjective> objectives = quest.objectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                if (objective.type() != ObjectiveType.PLACEHOLDER_CHECK) {
                    continue;
                }
                if (progress.objectiveProgress(i) >= objective.amount()) {
                    continue;
                }
                if (isChoiceLockedOut(quest, progress, i)) {
                    continue;
                }
                if (PlaceholderMatcher.matches(objective, player)) {
                    int previous = progress.objectiveProgress(i);
                    progress.incrementObjective(i, objective.amount() - previous);
                    changed = true;
                    eventPublisher.publish(
                            new QuestObjectiveProgressEvent(player, quest, i, previous, progress.objectiveProgress(i)));
                }
            }
            if (changed) {
                data.markDirty();
                handlePotentialCompletion(player, quest, progress);
            }
        }
    }

    /**
     * Called by {@code tracking.BossDamageTracker} once per (player, boss instance) when a MythicMobs boss
     * dies, for every player who dealt it any damage — {@code damageDealt}/{@code firstHitAt} are that one
     * player's cumulative contribution to that specific boss instance. Each matching DEFEAT_BOSS objective
     * decides for itself (via {@link BossDefeatMatcher}, using its own {@code min-damage}/{@code window-hours}
     * meta) whether this contribution was enough — a quest requiring heavier participation and one requiring
     * just a scratch can watch the same boss type differently.
     */
    public void checkBossDefeat(Player player, String bossName, double damageDealt, Instant firstHitAt, Instant deathTime) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        for (QuestProgress progress : List.copyOf(data.states().values())) {
            if (progress.status() != QuestStatus.IN_PROGRESS) {
                continue;
            }
            Quest quest = questsById.get(progress.questId());
            if (quest == null) {
                continue;
            }
            boolean changed = false;
            List<QuestObjective> objectives = quest.objectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                if (objective.type() != ObjectiveType.DEFEAT_BOSS || !EntityMatcher.matches(objective.target(), bossName)) {
                    continue;
                }
                if (progress.objectiveProgress(i) >= objective.amount()) {
                    continue;
                }
                if (isChoiceLockedOut(quest, progress, i)) {
                    continue;
                }
                if (!BossDefeatMatcher.satisfies(objective, damageDealt, firstHitAt, deathTime)) {
                    continue;
                }
                int previous = progress.objectiveProgress(i);
                progress.incrementObjective(i, 1);
                changed = true;
                eventPublisher.publish(
                        new QuestObjectiveProgressEvent(player, quest, i, previous, progress.objectiveProgress(i)));
            }
            if (changed) {
                data.markDirty();
                handlePotentialCompletion(player, quest, progress);
            }
        }
    }

    /**
     * Called on a timer (see {@code PurrtechQuest.registerEarnMoneyCheckTask}) to evaluate EARN_MONEY
     * objectives against the player's live Vault balance — like PLACEHOLDER_CHECK, a balance can change for
     * any reason (rewards, other plugins, other players trading with them), so there's no single event to
     * hook and this polls instead. Progress is the gain over each objective's baseline (the balance at
     * acceptance, captured by {@link #captureEarnMoneyBaselines}), and unlike every other objective type it
     * can move *backwards*: spending money back below the target gain while still in progress genuinely
     * means less progress, not a bug — see {@link EarnMoneyMatcher}.
     */
    public void checkEarnMoneyObjectives(Player player) {
        if (!EarnMoneyMatcher.isPresent()) {
            return;
        }
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        double balance = EarnMoneyMatcher.balanceOf(player);
        for (QuestProgress progress : List.copyOf(data.states().values())) {
            if (progress.status() != QuestStatus.IN_PROGRESS) {
                continue;
            }
            Quest quest = questsById.get(progress.questId());
            if (quest == null) {
                continue;
            }
            boolean changed = false;
            List<QuestObjective> objectives = quest.objectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                if (objective.type() != ObjectiveType.EARN_MONEY) {
                    continue;
                }
                if (isChoiceLockedOut(quest, progress, i)) {
                    continue;
                }
                double baseline = progress.baseline(i) == null ? 0.0 : progress.baseline(i);
                int previous = progress.objectiveProgress(i);
                int current = EarnMoneyMatcher.clampedProgress(balance, baseline, objective.amount());
                if (current == previous) {
                    continue;
                }
                progress.incrementObjective(i, current - previous);
                changed = true;
                eventPublisher.publish(new QuestObjectiveProgressEvent(player, quest, i, previous, current));
            }
            if (changed) {
                data.markDirty();
                handlePotentialCompletion(player, quest, progress);
            }
        }
    }

    /**
     * Auto-accepts any {@code autoStart} quest the player hasn't touched yet. Intended to be called once
     * after a player's data has finished loading (on join).
     */
    public void autoStartEligibleQuests(Player player) {
        PlayerQuestData data = playerCache.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        for (Quest quest : questsById.values()) {
            if (!quest.autoStart()) {
                continue;
            }
            QuestProgress existing = data.progress(quest.id());
            // NOT_ACCEPTED is a real, non-null progress entry (left behind by abandonQuest) rather than the
            // "never touched" state its name suggests — skipping on it being merely non-null would
            // permanently block auto-start after a single abandon. acceptQuest() below still guards every
            // other status (IN_PROGRESS/COMPLETED/TURNED_IN/cooldown) correctly on its own.
            if (existing != null && existing.status() != QuestStatus.NOT_ACCEPTED) {
                continue;
            }
            acceptQuest(player, quest.id());
        }
    }

    private boolean applyProgress(Player player, QuestProgress progress, Quest quest, ObjectiveType type, String target, int amount) {
        boolean changed = false;
        List<QuestObjective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            if (objective.type() != type || !targetMatches(type, objective.target(), target)) {
                continue;
            }
            int current = progress.objectiveProgress(i);
            if (current >= objective.amount()) {
                continue;
            }
            if (isChoiceLockedOut(quest, progress, i)) {
                continue;
            }
            int delta = Math.min(amount, objective.amount() - current);
            if (delta <= 0) {
                continue;
            }
            progress.incrementObjective(i, delta);
            changed = true;
            eventPublisher.publish(
                    new QuestObjectiveProgressEvent(player, quest, i, current, current + delta));
        }
        return changed;
    }

    /**
     * Both call sites already call {@code data.markDirty()} before reaching here (they have to — they're
     * the ones who just mutated {@code progress}), so the further status/completedAt mutation below doesn't
     * need its own markDirty call. Keep that in mind if a third caller shows up later.
     */
    private void handlePotentialCompletion(Player player, Quest quest, QuestProgress progress) {
        if (!isFullyComplete(quest, progress)) {
            return;
        }
        progress.status(QuestStatus.COMPLETED);
        progress.completedAt(Instant.now());
        Component message = messages.render("quest.completed", player, Map.of("%quest%", quest.displayName()));
        player.sendMessage(message);
        notify(player, message, SOUND_COMPLETED);
        eventPublisher.publish(new QuestCompleteEvent(player, quest));
        if (quest.autoTurnIn()) {
            turnIn(player, quest.id());
        }
    }

    /**
     * Title + sound alongside the (already sent) chat message, for the two moments worth a bigger beat:
     * accepting and fully completing a quest. Turn-in gets a sound only — a title here too would mean two
     * titles back-to-back for {@code autoTurnIn} quests.
     */
    private static void notify(Player player, Component message, String soundKey) {
        player.showTitle(Title.title(message, Component.empty(), NOTIFICATION_TIMES));
        player.playSound(player.getLocation(), soundKey, 1f, 1f);
    }

    /**
     * A plain (ungrouped) objective must individually reach its amount. A {@code choiceGroup} is satisfied
     * as a whole once *any one* of its members does — the group name only exists to say "these are
     * alternatives," not "all of these," so the other members not reaching their amount doesn't block
     * completion.
     */
    private static boolean isFullyComplete(Quest quest, QuestProgress progress) {
        List<QuestObjective> objectives = quest.objectives();
        Map<String, Boolean> groupSatisfied = new LinkedHashMap<>();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            boolean satisfied = progress.objectiveProgress(i) >= objective.amount();
            String group = objective.choiceGroup();
            if (group == null) {
                if (!satisfied) {
                    return false;
                }
                continue;
            }
            groupSatisfied.merge(group, satisfied, Boolean::logicalOr);
        }
        return groupSatisfied.values().stream().allMatch(Boolean::booleanValue);
    }

    /**
     * True once some *other* member of {@code objectives[index]}'s {@code choiceGroup} has already reached
     * its own required amount — the point of a choice group is picking one alternative, so once one is
     * satisfied the rest stop accepting progress instead of letting a player finish every option.
     */
    private static boolean isChoiceLockedOut(Quest quest, QuestProgress progress, int index) {
        return ChoiceGroups.isLockedOut(quest, progress, index);
    }

    private static boolean targetMatches(ObjectiveType type, String objectiveTarget, String actual) {
        return switch (type) {
            case KILL_ENTITY -> EntityMatcher.matches(objectiveTarget, actual);
            case BREAK_BLOCK, PLACE_BLOCK, COLLECT_ITEM, CRAFT_ITEM, FISH -> ItemMatcher.matches(objectiveTarget, actual);
            case TALK_TO_NPC -> NpcMatcher.matches(objectiveTarget, actual);
            // CUSTOM objectives are reported by a 3rd-party plugin calling updateProgress() itself (see the
            // ObjectiveHandler SPI) — a plain string match is all QuestService needs to know about them.
            // SPEND_MONEY has no meaningful target to match on — every instance uses the same fixed
            // placeholder (see ObjectiveType.SPEND_MONEY_TARGET), so this is really just an equality no-op.
            case CUSTOM, SPEND_MONEY -> objectiveTarget.equalsIgnoreCase(actual);
            default -> false;
        };
    }

    private boolean prerequisitesMet(PlayerQuestData data, Quest quest) {
        for (String requiredId : quest.requiredQuests()) {
            QuestProgress requiredProgress = data.progress(requiredId);
            if (requiredProgress == null || requiredProgress.status() != QuestStatus.TURNED_IN) {
                return false;
            }
        }
        return true;
    }
}
