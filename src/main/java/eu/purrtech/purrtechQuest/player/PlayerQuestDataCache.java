package eu.purrtech.purrtechQuest.player;

import eu.purrtech.purrtechQuest.model.PlayerQuestData;
import eu.purrtech.purrtechQuest.storage.PlayerDataRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link PlayerQuestData} for online players, backed by {@link PlayerDataRepository}.
 * <p>
 * Every method here is expected to be called from the main server thread. Saves take a
 * {@link PlayerQuestData#snapshot()} synchronously before handing the copy off to the repository's async
 * executor — see that method's javadoc for why that matters.
 */
public final class PlayerQuestDataCache {

    private final PlayerDataRepository repository;
    private final Map<UUID, PlayerQuestData> cache = new ConcurrentHashMap<>();

    public PlayerQuestDataCache(PlayerDataRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<PlayerQuestData> load(UUID playerId) {
        return repository.load(playerId).thenApply(data -> {
            cache.put(playerId, data);
            return data;
        });
    }

    public PlayerQuestData get(UUID playerId) {
        return cache.get(playerId);
    }

    public CompletableFuture<Void> saveAndUnload(UUID playerId) {
        PlayerQuestData data = cache.remove(playerId);
        if (data == null) {
            return CompletableFuture.completedFuture(null);
        }
        return repository.save(data.snapshot());
    }

    /**
     * Periodic autosave — only writes players whose data actually changed since the last save
     * ({@link PlayerQuestData#isDirty()}), so an idle player isn't re-saved every cycle for no reason.
     * {@code clearDirty()} happens synchronously right after the snapshot is taken (main thread, before
     * the async write starts) — any mutation landing after that point re-dirties for the next cycle rather
     * than being lost, since it happened after this cycle's snapshot was already taken.
     */
    public CompletableFuture<Void> saveAll() {
        List<CompletableFuture<Void>> saves = cache.values().stream()
                .filter(PlayerQuestData::isDirty)
                .map(data -> {
                    PlayerQuestData snapshot = data.snapshot();
                    data.clearDirty();
                    return repository.save(snapshot);
                })
                .toList();
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }
}
