package eu.purrtech.purrtechQuest.storage;

import javax.sql.DataSource;

public final class SqlitePlayerDataRepository extends AbstractSqlPlayerDataRepository {

    private static final String UPSERT_SQL = "INSERT INTO player_quest_progress " +
            "(player_id, quest_id, status, objective_progress, started_at, completed_at, times_completed, objective_baseline) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(player_id, quest_id) DO UPDATE SET " +
            "status = excluded.status, objective_progress = excluded.objective_progress, " +
            "started_at = excluded.started_at, completed_at = excluded.completed_at, " +
            "times_completed = excluded.times_completed, objective_baseline = excluded.objective_baseline";

    public SqlitePlayerDataRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected String upsertSql() {
        return UPSERT_SQL;
    }
}
