package eu.purrtech.purrtechQuest.permission;

import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestRewardTier;
import eu.purrtech.purrtechQuest.service.QuestService;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

/**
 * Registers every quest's {@link Quest#requiredPermission()} and reward-tier permission
 * ({@link QuestRewardTier#permission()}) as a genuine Bukkit {@link Permission} — without this, they only
 * ever work by coincidence of {@code hasPermission(String)} accepting arbitrary, unregistered node names;
 * registering them properly is what makes them show up in a permission plugin's "known permissions" listing
 * (e.g. LuckPerms' editor, tab-completion) instead of only working invisibly. Defaults to
 * {@link PermissionDefault#OP} — nobody gets a quest-gating or reward-tier permission just by existing, an
 * admin (or their permission plugin) has to grant it explicitly.
 * <p>
 * {@link #refresh()} is called once at startup and again after any quest save/delete/reload, mirroring
 * {@code tracking.TrackerRegistrationManager}. {@code registered} only ever tracks nodes this class itself
 * added, so a node that happens to collide with one some other plugin already registered is never touched
 * in either direction — added or removed.
 */
public final class QuestPermissionRegistrar {

    private final JavaPlugin plugin;
    private final QuestService questService;
    private final Set<String> registered = new HashSet<>();

    public QuestPermissionRegistrar(JavaPlugin plugin, QuestService questService) {
        this.plugin = plugin;
        this.questService = questService;
    }

    public void refresh() {
        Set<String> wanted = new HashSet<>();
        for (Quest quest : questService.allQuests()) {
            if (quest.requiredPermission() != null) {
                wanted.add(quest.requiredPermission());
            }
            for (QuestRewardTier tier : quest.rewardTiers()) {
                wanted.add(tier.permission());
            }
        }

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        for (String node : wanted) {
            if (registered.contains(node) || pluginManager.getPermission(node) != null) {
                continue;
            }
            pluginManager.addPermission(new Permission(node, PermissionDefault.OP));
            registered.add(node);
        }
        for (String node : Set.copyOf(registered)) {
            if (!wanted.contains(node)) {
                pluginManager.removePermission(node);
                registered.remove(node);
            }
        }
    }
}
