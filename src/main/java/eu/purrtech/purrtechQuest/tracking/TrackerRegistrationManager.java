package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.service.QuestService;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Registers/unregisters the core objective trackers based on which {@link ObjectiveType}s any loaded quest
 * actually uses — a server with only KILL_ENTITY quests doesn't pay for a {@code BlockBreakEvent} listener
 * that would never do anything but bail out immediately. {@link #refresh()} is called once at startup and
 * again after {@code /questadmin reload}, so adding a quest that introduces a previously-unused objective
 * type picks up its tracker without a server restart. SPEND_MONEY isn't here — it depends on ExcellentShop
 * being installed at all, so it's registered unconditionally alongside the other soft-depend integrations
 * in {@code PurrtechQuest.registerSoftDependListeners}, not gated on quest content like these core-Bukkit
 * ones are.
 */
public final class TrackerRegistrationManager {

    private final JavaPlugin plugin;
    private final QuestService questService;
    private final Map<ObjectiveType, Listener> trackers;
    private final Set<ObjectiveType> registered = EnumSet.noneOf(ObjectiveType.class);

    public TrackerRegistrationManager(JavaPlugin plugin, QuestService questService) {
        this.plugin = plugin;
        this.questService = questService;
        this.trackers = Map.of(
                ObjectiveType.KILL_ENTITY, new KillEntityListener(questService),
                ObjectiveType.BREAK_BLOCK, new BreakBlockListener(questService),
                ObjectiveType.PLACE_BLOCK, new PlaceBlockListener(questService),
                ObjectiveType.COLLECT_ITEM, new CollectItemListener(questService),
                ObjectiveType.CRAFT_ITEM, new CraftItemListener(questService),
                ObjectiveType.FISH, new FishListener(questService),
                ObjectiveType.REACH_LOCATION, new ReachLocationListener(questService));
    }

    public void refresh() {
        Set<ObjectiveType> used = questService.usedObjectiveTypes();
        for (Map.Entry<ObjectiveType, Listener> entry : trackers.entrySet()) {
            ObjectiveType type = entry.getKey();
            Listener listener = entry.getValue();
            boolean shouldBeRegistered = used.contains(type);
            boolean isRegistered = registered.contains(type);

            if (shouldBeRegistered && !isRegistered) {
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
                registered.add(type);
            } else if (!shouldBeRegistered && isRegistered) {
                HandlerList.unregisterAll(listener);
                registered.remove(type);
            }
        }
    }
}
