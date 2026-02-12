package top.asimov.pigeon.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.asimov.pigeon.model.entity.YoutubeApiDailyUsage;

public interface YoutubeApiDailyUsageMapper {

  @Insert("""
      INSERT INTO youtube_api_daily_usage
      (usage_date_pt, request_count, quota_units, auto_sync_blocked, created_at, updated_at)
      VALUES (#{usageDatePt}, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      ON CONFLICT(usage_date_pt) DO NOTHING
      """)
  int ensureDayRow(@Param("usageDatePt") String usageDatePt);

  @Update("""
      UPDATE youtube_api_daily_usage
      SET request_count = request_count + 1,
          quota_units = quota_units + #{quotaUnits},
          updated_at = CURRENT_TIMESTAMP
      WHERE usage_date_pt = #{usageDatePt}
      """)
  int incrementUsage(@Param("usageDatePt") String usageDatePt, @Param("quotaUnits") int quotaUnits);

  @Update("""
      UPDATE youtube_api_daily_usage
      SET request_count = request_count + 1,
          quota_units = quota_units + #{quotaUnits},
          updated_at = CURRENT_TIMESTAMP
      WHERE usage_date_pt = #{usageDatePt}
        AND quota_units + #{quotaUnits} <= #{limitUnits}
      """)
  int tryIncrementUsageWithinLimit(@Param("usageDatePt") String usageDatePt,
      @Param("quotaUnits") int quotaUnits, @Param("limitUnits") int limitUnits);

  @Update("""
      UPDATE youtube_api_daily_usage
      SET auto_sync_blocked = 1,
          blocked_reason = #{reason},
          blocked_at = CASE WHEN blocked_at IS NULL THEN CURRENT_TIMESTAMP ELSE blocked_at END,
          updated_at = CURRENT_TIMESTAMP
      WHERE usage_date_pt = #{usageDatePt}
      """)
  int blockAutoSync(@Param("usageDatePt") String usageDatePt, @Param("reason") String reason);

  @Select("SELECT * FROM youtube_api_daily_usage WHERE usage_date_pt = #{usageDatePt} LIMIT 1")
  YoutubeApiDailyUsage selectByDate(@Param("usageDatePt") String usageDatePt);

  @Select("SELECT * FROM youtube_api_daily_usage ORDER BY usage_date_pt DESC LIMIT #{limit}")
  List<YoutubeApiDailyUsage> selectLatest(@Param("limit") int limit);
}
