package eu.purrtech.purrtechQuest.storage;

import eu.purrtech.purrtechQuest.model.PlayerQuestData;
import eu.purrtech.purrtechQuest.model.QuestProgress;
import eu.purrtech.purrtechQuest.model.QuestStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Shared JDBC logic for {@link PlayerDataRepository} implementations. SQLite and MySQL/MariaDB store the
 * identical schema and only differ in their upsert statement's conflict-resolution syntax
 * ({@code ON CONFLICT ... DO UPDATE} vs {@code ON DUPLICATE KEY UPDATE}), which subclasses provide via
 * {@link #upsertSql()}. All work happens on a small dedicated executor so callers on the main server
 * thread never block on I/O; results come back as {@link CompletableFuture}s that the caller is expected
 * to hop back onto the main thread with before touching Bukkit API.
 */
abstract class AbstractSqlPlayerDataRepository implements PlayerDataRepository {

    private final DataSource dataSource;
    private final ExecutorService executor;

    protected AbstractSqlPlayerDataRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "PurrtechQuest-DB-Worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    protected abstract String upsertSql();

    @Override
    public CompletableFuture<PlayerQuestData> load(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> loadBlocking(playerId), executor);
    }

    private PlayerQuestData loadBlocking(UUID playerId) {
        PlayerQuestData data = new PlayerQuestData(playerId);
        String sql = "SELECT quest_id, status, objective_progress, started_at, completed_at, times_completed, " +
                "objective_baseline FROM player_quest_progress WHERE player_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    data.put(rowToProgress(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new QuestStorageException("Could not load quest data for " + playerId, e);
        }
        return data;
    }

    private QuestProgress rowToProgress(ResultSet resultSet) throws SQLException {
        String questId = resultSet.getString("quest_id");
        QuestStatus status = QuestStatus.valueOf(resultSet.getString("status"));
        int[] progress = parseProgress(resultSet.getString("objective_progress"));

        long startedAtRaw = resultSet.getLong("started_at");
        Instant startedAt = resultSet.wasNull() ? null : Instant.ofEpochSecond(startedAtRaw);
        long completedAtRaw = resultSet.getLong("completed_at");
        Instant completedAt = resultSet.wasNull() ? null : Instant.ofEpochSecond(completedAtRaw);

        int timesCompleted = resultSet.getInt("times_completed");
        Map<Integer, Double> baselines = parseBaselines(resultSet.getString("objective_baseline"));
        return new QuestProgress(questId, status, progress, startedAt, completedAt, timesCompleted, baselines);
    }

    private static int[] parseProgress(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[0];
        }
        String[] parts = raw.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i]);
        }
        return result;
    }

    private static Map<Integer, Double> parseBaselines(String raw) {
        Map<Integer, Double> result = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String pair : raw.split(";")) {
            int separator = pair.indexOf(':');
            result.put(Integer.parseInt(pair.substring(0, separator)), Double.parseDouble(pair.substring(separator + 1)));
        }
        return result;
    }

    @Override
    public CompletableFuture<Void> save(PlayerQuestData data) {
        return CompletableFuture.runAsync(() -> saveBlocking(data), executor);
    }

    private void saveBlocking(PlayerQuestData data) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(upsertSql())) {
                for (QuestProgress progress : data.states().values()) {
                    statement.setString(1, data.playerId().toString());
                    statement.setString(2, progress.questId());
                    statement.setString(3, progress.status().name());
                    statement.setString(4, joinProgress(progress.objectiveProgress()));
                    setNullableEpochSeconds(statement, 5, progress.startedAt());
                    setNullableEpochSeconds(statement, 6, progress.completedAt());
                    statement.setInt(7, progress.timesCompleted());
                    statement.setString(8, joinBaselines(progress.objectiveBaselines()));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            throw new QuestStorageException("Could not save quest data for " + data.playerId(), e);
        }
    }

    private static void setNullableEpochSeconds(PreparedStatement statement, int index, Instant instant) throws SQLException {
        if (instant == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, instant.getEpochSecond());
        }
    }

    private static String joinProgress(int[] progress) {
        return Arrays.stream(progress).mapToObj(String::valueOf).collect(Collectors.joining(","));
    }

    private static String joinBaselines(Map<Integer, Double> baselines) {
        return baselines.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
