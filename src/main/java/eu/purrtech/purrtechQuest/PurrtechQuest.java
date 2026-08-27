package eu.purrtech.purrtechQuest;

import eu.purrtech.purrtechQuest.api.PurrtechQuestApiImpl;
import eu.purrtech.purrtechQuest.api.PurrtechQuestProvider;
import eu.purrtech.purrtechQuest.api.objective.ObjectiveHandlerRegistry;
import eu.purrtech.purrtechQuest.api.objective.ObjectiveHandlerRegistryImpl;
import eu.purrtech.purrtechQuest.command.QuestAdminCommand;
import eu.purrtech.purrtechQuest.command.QuestCommand;
import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.config.PluginConfig;
import eu.purrtech.purrtechQuest.config.StorageType;
import eu.purrtech.purrtechQuest.gui.ChatInputService;
import eu.purrtech.purrtechQuest.gui.GuiListener;
import eu.purrtech.purrtechQuest.integration.PurrtechQuestPlaceholderExpansion;
import eu.purrtech.purrtechQuest.listener.PlayerSessionListener;
import eu.purrtech.purrtechQuest.metrics.MetricsSetup;
import eu.purrtech.purrtechQuest.npc.CitizensNpcProvider;
import eu.purrtech.purrtechQuest.npc.FancyNpcsNpcProvider;
import eu.purrtech.purrtechQuest.npc.NpcLinkService;
import eu.purrtech.purrtechQuest.permission.QuestPermissionRegistrar;
import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.service.QuestTrackingService;
import eu.purrtech.purrtechQuest.service.RewardService;
import eu.purrtech.purrtechQuest.storage.CategoryConfigRepository;
import eu.purrtech.purrtechQuest.storage.DatabaseManager;
import eu.purrtech.purrtechQuest.storage.MySqlDatabaseManager;
import eu.purrtech.purrtechQuest.storage.MySqlPlayerDataRepository;
import eu.purrtech.purrtechQuest.storage.PlayerDataRepository;
import eu.purrtech.purrtechQuest.storage.QuestDefinitionRepository;
import eu.purrtech.purrtechQuest.storage.SqlitePlayerDataRepository;
import eu.purrtech.purrtechQuest.storage.YamlQuestDefinitionRepository;
import eu.purrtech.purrtechQuest.storage.migration.SchemaMigrator;
import eu.purrtech.purrtechQuest.tracking.BossDamageTracker;
import eu.purrtech.purrtechQuest.tracking.ExcellentShopSpendListener;
import eu.purrtech.purrtechQuest.tracking.MythicMobsKillListener;
import eu.purrtech.purrtechQuest.tracking.TrackerRegistrationManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class PurrtechQuest extends JavaPlugin {

    private static final long AUTOSAVE_PERIOD_TICKS = 20L * 60 * 5;
    private static final long TRACKING_TICK_PERIOD_TICKS = 20L;
    private static final long PLACEHOLDER_CHECK_PERIOD_TICKS = 20L * 5;
    private static final long EARN_MONEY_CHECK_PERIOD_TICKS = 20L * 5;

    private PluginConfig pluginConfig;
    private MessagesConfig messagesConfig;
    private DatabaseManager databaseManager;
    private MySqlDatabaseManager mySqlDatabaseManager;
    private PlayerDataRepository playerDataRepository;
    private QuestDefinitionRepository questDefinitionRepository;
    private PlayerQuestDataCache playerQuestDataCache;
    private QuestService questService;
    private QuestTrackingService questTrackingService;
    private ChatInputService chatInputService;
    private NpcLinkService npcLinkService;
    private ObjectiveHandlerRegistry objectiveHandlerRegistry;
    private TrackerRegistrationManager trackerRegistrationManager;
    private QuestPermissionRegistrar questPermissionRegistrar;
    private CategoryConfigRepository categoryConfigRepository;
    private MenuLayoutConfig menuLayoutConfig;

    @Override
    public void onEnable() {
        this.pluginConfig = PluginConfig.load(this);
        this.messagesConfig = MessagesConfig.load(this, pluginConfig.defaultLocale());
        this.menuLayoutConfig = MenuLayoutConfig.load(this);

        if (pluginConfig.storageType() == StorageType.MYSQL) {
            this.mySqlDatabaseManager = new MySqlDatabaseManager(pluginConfig.mysql());
            SchemaMigrator.migrate(mySqlDatabaseManager.dataSource());
            this.playerDataRepository = new MySqlPlayerDataRepository(mySqlDatabaseManager.dataSource());
        } else {
            this.databaseManager = new DatabaseManager(getDataFolder().toPath().resolve(pluginConfig.sqliteFileName()));
            SchemaMigrator.migrate(databaseManager.dataSource());
            this.playerDataRepository = new SqlitePlayerDataRepository(databaseManager.dataSource());
        }
        this.playerQuestDataCache = new PlayerQuestDataCache(playerDataRepository);

        Path questsDirectory = getDataFolder().toPath().resolve(pluginConfig.questsDirectoryName());
        this.questDefinitionRepository = new YamlQuestDefinitionRepository(questsDirectory, getLogger());
        extractSampleQuestIfEmpty(questsDirectory);
        this.categoryConfigRepository = new CategoryConfigRepository(getDataFolder().toPath().resolve("categories.yml"));

        RewardService rewardService = new RewardService(getLogger());
        this.questService = new QuestService(questDefinitionRepository, playerQuestDataCache, rewardService,
                messagesConfig, getServer().getPluginManager()::callEvent);
        this.questTrackingService = new QuestTrackingService(questService, playerQuestDataCache, messagesConfig,
                pluginConfig.trackingDisplay());
        this.chatInputService = new ChatInputService(this, messagesConfig);
        this.npcLinkService = new NpcLinkService();
        this.objectiveHandlerRegistry = new ObjectiveHandlerRegistryImpl();
        this.trackerRegistrationManager = new TrackerRegistrationManager(this, questService);
        this.questPermissionRegistrar = new QuestPermissionRegistrar(this, questService);
        getLogger().info("Loaded " + questService.allQuests().size() + " quest definition(s).");

        PurrtechQuestProvider.register(new PurrtechQuestApiImpl(questService, objectiveHandlerRegistry,
                questDefinitionRepository, playerDataRepository));

        registerListeners();
        registerCommands();
        registerAutosaveTask();
        registerTrackingTask();
        registerPlaceholderCheckTask();
        registerEarnMoneyCheckTask();
        registerPlaceholderExpansion();
        MetricsSetup.init(this, questService, pluginConfig);

        getLogger().info("PurrtechQuest enabled (storage=" + pluginConfig.storageType()
                + ", default-locale=" + pluginConfig.defaultLocale() + ").");
    }

    @Override
    public void onDisable() {
        if (questTrackingService != null) {
            questTrackingService.clearAll();
        }
        if (playerQuestDataCache != null) {
            try {
                playerQuestDataCache.saveAll().get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Could not save all quest data on shutdown", e);
            }
        }
        PurrtechQuestProvider.unregister();
        if (playerDataRepository != null) {
            playerDataRepository.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (mySqlDatabaseManager != null) {
            mySqlDatabaseManager.close();
        }
    }

    public MessagesConfig messages() {
        return messagesConfig;
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public QuestService questService() {
        return questService;
    }

    public PlayerQuestDataCache playerQuestDataCache() {
        return playerQuestDataCache;
    }

    private void registerListeners() {
        trackerRegistrationManager.refresh();
        questPermissionRegistrar.refresh();
        getServer().getPluginManager().registerEvents(
                new PlayerSessionListener(playerQuestDataCache, questService, questTrackingService, this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(chatInputService, this);
        registerSoftDependListeners();
    }

    /**
     * Each of these references a soft-depend plugin's own event class, which Bukkit resolves via
     * reflection the moment {@code registerEvents} runs — so, unlike the item-lookup hooks, presence has
     * to be checked *before* registering, not just before using the class inside an always-on listener.
     * {@link #runIfPresent} additionally guards against a present-but-incompatible version of that plugin
     * (a renamed/moved class throwing {@link LinkageError}) taking down the rest of PurrtechQuest with it.
     */
    private void registerSoftDependListeners() {
        runIfPresent("Citizens", () -> getServer().getPluginManager().registerEvents(new CitizensNpcProvider(
                questService, playerQuestDataCache, questTrackingService, npcLinkService, menuLayoutConfig,
                messagesConfig), this));
        runIfPresent("FancyNpcs", () -> getServer().getPluginManager().registerEvents(new FancyNpcsNpcProvider(
                questService, playerQuestDataCache, questTrackingService, npcLinkService, menuLayoutConfig,
                messagesConfig), this));
        runIfPresent("MythicMobs", () -> getServer().getPluginManager().registerEvents(
                new MythicMobsKillListener(questService), this));
        runIfPresent("MythicMobs", () -> getServer().getPluginManager().registerEvents(
                new BossDamageTracker(questService), this));
        runIfPresent("ExcellentShop", () -> getServer().getPluginManager().registerEvents(
                new ExcellentShopSpendListener(questService), this));
    }

    /**
     * Runs {@code action} only if {@code pluginName} is enabled, and never lets a {@link LinkageError} from
     * it (present-but-incompatible version, e.g. an event class that moved between releases) escape —
     * an optional integration failing must never take the rest of the plugin down with it.
     */
    private void runIfPresent(String pluginName, Runnable action) {
        if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            return;
        }
        try {
            action.run();
        } catch (LinkageError e) {
            getLogger().warning("Could not hook into " + pluginName + " (incompatible version?): " + e
                    + " — continuing without that integration.");
        }
    }

    private void registerCommands() {
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register(QuestCommand.build(questService, playerQuestDataCache, questTrackingService,
                    pluginConfig.guideEnabled(), menuLayoutConfig, messagesConfig),
                    "PurrtechQuest player commands");
            registrar.register(QuestAdminCommand.build(questService, questDefinitionRepository, chatInputService,
                    npcLinkService, objectiveHandlerRegistry, trackerRegistrationManager, questPermissionRegistrar,
                    categoryConfigRepository, menuLayoutConfig, messagesConfig),
                    "PurrtechQuest admin commands");
        });
    }

    private void registerAutosaveTask() {
        getServer().getScheduler().runTaskTimer(this, () ->
                playerQuestDataCache.saveAll().exceptionally(ex -> {
                    getLogger().log(Level.WARNING, "Periodic quest data autosave failed", ex);
                    return null;
                }), AUTOSAVE_PERIOD_TICKS, AUTOSAVE_PERIOD_TICKS);
    }

    private void registerTrackingTask() {
        getServer().getScheduler().runTaskTimer(this,
                () -> questTrackingService.tick(getServer().getOnlinePlayers()),
                TRACKING_TICK_PERIOD_TICKS, TRACKING_TICK_PERIOD_TICKS);
    }

    /**
     * PLACEHOLDER_CHECK has no discrete Bukkit event to hook (a placeholder can change for basically any
     * reason), so it's evaluated on a timer instead — every 5s is frequent enough that a quest completes
     * without a noticeable delay, without re-resolving placeholders (which can call into other plugins)
     * every single tick like {@link #registerTrackingTask}'s boss-bar/action-bar refresh does.
     */
    private void registerPlaceholderCheckTask() {
        getServer().getScheduler().runTaskTimer(this,
                () -> getServer().getOnlinePlayers().forEach(questService::checkPlaceholderObjectives),
                PLACEHOLDER_CHECK_PERIOD_TICKS, PLACEHOLDER_CHECK_PERIOD_TICKS);
    }

    /**
     * EARN_MONEY has the same problem as PLACEHOLDER_CHECK — a Vault balance can change for basically any
     * reason (rewards, other plugins, player-to-player trades), so there's no single event to hook, and
     * this polls on the same 5s cadence for the same reason.
     */
    private void registerEarnMoneyCheckTask() {
        getServer().getScheduler().runTaskTimer(this,
                () -> getServer().getOnlinePlayers().forEach(questService::checkEarnMoneyObjectives),
                EARN_MONEY_CHECK_PERIOD_TICKS, EARN_MONEY_CHECK_PERIOD_TICKS);
    }

    private void registerPlaceholderExpansion() {
        runIfPresent("PlaceholderAPI", () -> new PurrtechQuestPlaceholderExpansion(
                questService, playerQuestDataCache, questTrackingService, messagesConfig).register());
    }

    private void extractSampleQuestIfEmpty(Path questsDirectory) {
        try (var files = Files.list(questsDirectory)) {
            if (files.findAny().isPresent()) {
                return;
            }
        } catch (IOException e) {
            getLogger().warning("Could not inspect quests directory: " + e.getMessage());
            return;
        }

        try (InputStream in = getResource("quests/sample_mine_iron.yml")) {
            if (in != null) {
                Files.copy(in, questsDirectory.resolve("sample_mine_iron.yml"));
                getLogger().info("Extracted sample quest 'sample_mine_iron' into the quests/ folder.");
            }
        } catch (IOException e) {
            getLogger().warning("Could not extract sample quest: " + e.getMessage());
        }
    }
}
