package eu.purrtech.purrtechQuest.service;

import org.bukkit.event.Event;

/**
 * Thin seam over {@code Bukkit.getPluginManager()::callEvent} so {@link QuestService} can fire its public
 * API events without needing a live Bukkit server to unit test — {@code Bukkit.getServer()} is null outside
 * a running server (and MockBukkit is unusable in this project against Paper 1.21.11; see
 * {@code QuestServiceTest}'s javadoc for why). Production wiring passes
 * {@code Bukkit.getPluginManager()::callEvent}; tests pass whatever they need.
 */
@FunctionalInterface
public interface EventPublisher {

    void publish(Event event);
}
