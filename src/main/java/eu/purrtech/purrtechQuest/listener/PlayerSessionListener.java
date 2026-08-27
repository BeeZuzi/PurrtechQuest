package eu.purrtech.purrtechQuest.listener;

import eu.purrtech.purrtechQuest.player.PlayerQuestDataCache;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.service.QuestTrackingService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Loads a player's quest data into {@link PlayerQuestDataCache} on join (async I/O, then hops back to the
 * main thread to auto-start eligible quests) and flushes/evicts it on quit.
 */
public final class PlayerSessionListener implements Listener {

    private final PlayerQuestDataCache cache;
    private final QuestService questService;
    private final QuestTrackingService trackingService;
    private final JavaPlugin plugin;

    public PlayerSessionListener(PlayerQuestDataCache cache, QuestService questService,
                                  QuestTrackingService trackingService, JavaPlugin plugin) {
        this.cache = cache;
        this.questService = questService;
        this.trackingService = trackingService;
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        cache.load(player.getUniqueId())
                .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        questService.autoStartEligibleQuests(player);
                    }
                }))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Could not load quest data for " + player.getName(), ex);
                    return null;
                });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        trackingService.handleQuit(player);
        cache.saveAndUnload(player.getUniqueId())
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Could not save quest data for " + player.getName(), ex);
                    return null;
                });
    }
}
