package eu.purrtech.purrtechQuest.storage;

import javax.sql.DataSource;

public final class MySqlPlayerDataRepository extends AbstractSqlPlayerDataRepository {

    private static final String UPSERT_SQL = "INSERT INTO player_quest_progress " +
            "(player_id, quest_id, status, objective_progress, started_at, completed_at, times_completed, objective_baseline) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "status = VALUES(status), objective_progress = VALUES(objective_progress), " +
            "started_at = VALUES(started_at), completed_at = VALUES(completed_at), " +
            "times_completed = VALUES(times_completed), objective_baseline = VALUES(objective_baseline)";

    public MySqlPlayerDataRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected String upsertSql() {
        return UPSERT_SQL;
    }
}
