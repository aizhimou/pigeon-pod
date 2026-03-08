package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

@Log4j2
public class V37__Backfill_episode_duration_seconds extends BaseJavaMigration {

  private static final int BATCH_SIZE = 500;

  @Override
  public void migrate(Context context) throws Exception {
    long lastRowId = 0L;
    int scanned = 0;
    int updated = 0;
    int failed = 0;

    String selectSql = """
        SELECT rowid, id, duration
        FROM episode
        WHERE rowid > ?
          AND duration_seconds IS NULL
          AND duration IS NOT NULL
        ORDER BY rowid
        LIMIT ?
        """;
    String updateSql = "UPDATE episode SET duration_seconds = ? WHERE id = ?";

    while (true) {
      int batchCount = 0;
      int batchUpdates = 0;
      try (PreparedStatement select = context.getConnection().prepareStatement(selectSql)) {
        select.setLong(1, lastRowId);
        select.setInt(2, BATCH_SIZE);
        try (ResultSet rows = select.executeQuery()) {
          try (PreparedStatement update = context.getConnection().prepareStatement(updateSql)) {
            while (rows.next()) {
              batchCount++;
              scanned++;
              lastRowId = rows.getLong("rowid");
              String episodeId = rows.getString("id");
              String duration = rows.getString("duration");
              Integer durationSeconds = parseDurationSeconds(duration);
              if (durationSeconds == null) {
                failed++;
                continue;
              }
              update.setInt(1, durationSeconds);
              update.setString(2, episodeId);
              update.addBatch();
              batchUpdates++;
              updated++;
            }
            if (batchUpdates > 0) {
              update.executeBatch();
            }
          }
        }
      }

      if (batchCount < BATCH_SIZE) {
        break;
      }
    }

    log.info("[Flyway] episode.duration_seconds backfill finished: scanned={}, updated={}, failed={}",
        scanned, updated, failed);
  }

  private Integer parseDurationSeconds(String duration) {
    if (duration == null || duration.isBlank()) {
      return null;
    }
    try {
      return Math.toIntExact(Duration.parse(duration).toSeconds());
    } catch (Exception ex) {
      return null;
    }
  }
}
