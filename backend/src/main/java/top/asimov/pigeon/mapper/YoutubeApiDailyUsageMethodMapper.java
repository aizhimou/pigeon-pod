package top.asimov.pigeon.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.asimov.pigeon.model.entity.YoutubeApiDailyUsageMethod;

public interface YoutubeApiDailyUsageMethodMapper {

  @Insert("""
      INSERT INTO youtube_api_daily_usage_method
      (usage_date_pt, api_method, request_count, quota_units, created_at, updated_at)
      VALUES (#{usageDatePt}, #{apiMethod}, 1, #{quotaUnits}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      ON CONFLICT(usage_date_pt, api_method) DO UPDATE SET
      request_count = youtube_api_daily_usage_method.request_count + 1,
      quota_units = youtube_api_daily_usage_method.quota_units + excluded.quota_units,
      updated_at = CURRENT_TIMESTAMP
      """)
  int incrementUsage(@Param("usageDatePt") String usageDatePt, @Param("apiMethod") String apiMethod,
      @Param("quotaUnits") int quotaUnits);

  @Select("""
      SELECT * FROM youtube_api_daily_usage_method
      WHERE usage_date_pt = #{usageDatePt}
      ORDER BY quota_units DESC, request_count DESC, api_method ASC
      """)
  List<YoutubeApiDailyUsageMethod> selectByDate(@Param("usageDatePt") String usageDatePt);
}
