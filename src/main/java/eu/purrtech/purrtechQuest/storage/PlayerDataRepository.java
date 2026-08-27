package eu.purrtech.purrtechQuest.storage;

import eu.purrtech.purrtechQuest.model.PlayerQuestData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Where per-player quest progress lives. SQLite today; the interface is what lets a MySQL implementation
 * (Phase 4, for a server network with shared progress) drop in without touching {@code QuestService}.
 * All I/O is async — never call from the main thread and block on the future.
 */
public interface PlayerDataRepository {

    CompletableFuture<PlayerQuestData> load(UUID playerId);

    CompletableFuture<Void> save(PlayerQuestData data);

    void close();
}
