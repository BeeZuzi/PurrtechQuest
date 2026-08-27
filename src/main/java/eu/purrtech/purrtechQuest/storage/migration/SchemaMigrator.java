package eu.purrtech.purrtechQuest.storage.migration;

import eu.purrtech.purrtechQuest.storage.QuestStorageException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tiny hand-rolled migration runner (no Flyway/Liquibase — the schema is small and changes rarely).
 * Add a new {@code applyVN} step and bump the version check when the schema needs to change.
 */
public final class SchemaMigrator {

    private SchemaMigrator() {
    }

    public static void migrate(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER NOT NULL
                    )
                    """);

            int version = readVersion(statement);

            if (version < 1) {
                applyV1(statement);
                version = 1;
            }

            if (version < 2) {
                applyV2(statement);
                version = 2;
            }

            writeVersion(connection, version);
        } catch (SQLException e) {
            throw new QuestStorageException("Could not migrate database schema", e);
        }
    }

    private static int readVersion(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return resultSet.next() ? resultSet.getInt("version") : 0;
        }
    }

    private static void writeVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM schema_version");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema_version (version) VALUES (?)")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }

    /**
     * {@code VARCHAR(n)} rather than {@code TEXT} for the two primary-key columns — this DDL has to work
     * on both SQLite and MySQL/MariaDB (same schema, see {@code AbstractSqlPlayerDataRepository}), and
     * MySQL/InnoDB rejects a bare TEXT/BLOB column in a primary key without an explicit prefix length.
     * SQLite treats VARCHAR(n) as plain TEXT affinity (it doesn't enforce the length), so this is a no-op
     * change there.
     */
    private static void applyV1(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS player_quest_progress (
                    player_id VARCHAR(36) NOT NULL,
                    quest_id VARCHAR(190) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    objective_progress TEXT NOT NULL,
                    started_at INTEGER,
                    completed_at INTEGER,
                    times_completed INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_id, quest_id)
                )
                """);
    }

    /**
     * Adds sparse per-objective baseline storage — currently used by EARN_MONEY objectives to remember the
     * player's balance at quest-acceptance time (see {@code QuestService#startFreshProgress}). Nullable and
     * empty by default so existing rows don't need backfilling.
     */
    private static void applyV2(Statement statement) throws SQLException {
        statement.execute("ALTER TABLE player_quest_progress ADD COLUMN objective_baseline TEXT");
    }
}
