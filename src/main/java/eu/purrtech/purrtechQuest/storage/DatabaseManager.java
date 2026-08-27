package eu.purrtech.purrtechQuest.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the SQLite connection pool. Pool size is intentionally 1: SQLite serializes writers anyway, and a
 * larger pool just invites "database is locked" errors under concurrent access — HikariCP still buys us
 * async-safe borrowing/queuing instead of blocking the caller thread directly on file I/O.
 */
public final class DatabaseManager implements AutoCloseable {

    private final HikariDataSource dataSource;

    public DatabaseManager(Path databaseFile) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath());
        // A class reference here (not a "org.sqlite.JDBC" string literal) so shadowJar's relocation of
        // org.sqlite rewrites this too — a hardcoded string would silently point at a class that no longer
        // exists once the driver is shaded into eu.purrtech.purrtechQuest.libs.sqlite.
        config.setDriverClassName(org.sqlite.JDBC.class.getName());
        config.setMaximumPoolSize(1);
        config.setPoolName("PurrtechQuest-SQLite");
        this.dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
        } catch (SQLException e) {
            throw new QuestStorageException("Could not initialize SQLite database at " + databaseFile, e);
        }
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
