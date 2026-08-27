package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.service.QuestService;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobDespawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEFEAT_BOSS credits every player who dealt a MythicMobs boss enough damage during its lifetime, not just
 * whoever landed the final blow — the point is "help kill this boss," not "get the kill." Only registered
 * when MythicMobs is installed (see {@code PurrtechQuest.registerSoftDependListeners}).
 * <p>
 * Deliberately listens for plain {@link EntityDamageByEntityEvent} rather than MythicMobs' own
 * {@code MythicDamageEvent} — the latter only fires for damage dealt *through a Mythic skill mechanic*
 * (e.g. a boss's own attack skills), not a player's ordinary weapon hit on it, so it never sees normal
 * combat at all. Bukkit's vanilla damage event fires for every hit regardless of source, and
 * {@link BukkitAPIHelper#isMythicMob} confirms the target is one MythicMobs is tracking.
 * <p>
 * Attackers are tracked per boss *instance* (its Bukkit entity UUID), not per mob type — two simultaneous
 * spawns of the same boss type are tracked independently, and a player only gets credit from the specific
 * instance they actually hit. Only players still online at the moment the boss dies get credit — quest
 * progress in this plugin is always applied to a live {@link Player}, so there's nowhere to record it for
 * someone who logged off mid-fight; this matches how every other objective type here already only tracks
 * online players. Entries are also cleared on despawn (a boss vanishing without dying — range, plugin
 * disable, etc.) so the tracking map never grows for bosses that never report a death at all.
 */
public final class BossDamageTracker implements Listener {

    /**
     * One player's contribution to a specific boss instance: cumulative damage dealt, and *when they first
     * hit it* — {@link BossDefeatMatcher} uses the latter to decide whether the boss died soon enough after
     * that for the contribution to still count.
     */
    private record Contribution(double damage, Instant firstHitAt) {
        Contribution plus(double extraDamage) {
            return new Contribution(damage + extraDamage, firstHitAt);
        }
    }

    private final QuestService questService;
    private final Map<UUID, Map<UUID, Contribution>> contributionsByBoss = new ConcurrentHashMap<>();

    public BossDamageTracker(QuestService questService) {
        this.questService = questService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        BukkitAPIHelper api = MythicBukkit.inst().getAPIHelper();
        if (!api.isMythicMob(event.getEntity())) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        UUID bossId = event.getEntity().getUniqueId();
        Contribution hit = new Contribution(event.getFinalDamage(), Instant.now());
        contributionsByBoss.computeIfAbsent(bossId, id -> new ConcurrentHashMap<>())
                .merge(attacker.getUniqueId(), hit, (existing, fresh) -> existing.plus(fresh.damage()));
    }

    /**
     * Direct hits are simple; a projectile (arrow, trident, thrown potion) attributes damage to whoever
     * shot it instead. Anything else (TNT, other mobs, environmental damage) isn't a player's contribution.
     */
    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        Map<UUID, Contribution> contributions = contributionsByBoss.remove(event.getEntity().getUniqueId());
        if (contributions == null || contributions.isEmpty()) {
            return;
        }
        String internalName = event.getMobType().getInternalName();
        Instant deathTime = Instant.now();
        for (Map.Entry<UUID, Contribution> entry : contributions.entrySet()) {
            Player attacker = Bukkit.getPlayer(entry.getKey());
            if (attacker == null) {
                continue;
            }
            Contribution contribution = entry.getValue();
            questService.checkBossDefeat(attacker, internalName, contribution.damage(), contribution.firstHitAt(), deathTime);
        }
    }

    @EventHandler
    public void onMythicMobDespawn(MythicMobDespawnEvent event) {
        contributionsByBoss.remove(event.getEntity().getUniqueId());
    }
}
