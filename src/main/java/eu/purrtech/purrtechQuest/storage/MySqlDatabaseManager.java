package eu.purrtech.purrtechQuest.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import eu.purrtech.purrtechQuest.config.MySqlSettings;

/**
 * Owns the MySQL/MariaDB connection pool — for a network of servers sharing player quest progress. Unlike
 * {@link DatabaseManager} (SQLite), MySQL handles concurrent writers fine, so the pool size is configurable
 * instead of hardcoded to 1.
 */
public final class MySqlDatabaseManager implements AutoCloseable {

    private final HikariDataSource dataSource;

    public MySqlDatabaseManager(MySqlSettings settings) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + settings.host() + ":" + settings.port() + "/" + settings.database()
                + "?useSsl=" + settings.useSsl());
        // Class reference, not a string literal — see DatabaseManager's javadoc for why that matters once
        // this driver is shaded/relocated.
        config.setDriverClassName(org.mariadb.jdbc.Driver.class.getName());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.poolSize());
        config.setPoolName("PurrtechQuest-MySQL");
        this.dataSource = new HikariDataSource(config);
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
