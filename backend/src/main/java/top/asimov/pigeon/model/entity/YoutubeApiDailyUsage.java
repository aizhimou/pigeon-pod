package top.asimov.pigeon.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
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
@TableName("youtube_api_daily_usage")
public class YoutubeApiDailyUsage {

  @TableId
  private String usageDatePt;
  private Integer requestCount;
  private Integer quotaUnits;
  private Integer autoSyncBlocked;
  private String blockedReason;
  private LocalDateTime blockedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
