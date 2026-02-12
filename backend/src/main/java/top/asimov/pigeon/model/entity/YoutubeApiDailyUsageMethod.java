package top.asimov.pigeon.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("youtube_api_daily_usage_method")
public class YoutubeApiDailyUsageMethod {

  private String usageDatePt;
  private String apiMethod;
  private Integer requestCount;
  private Integer quotaUnits;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
