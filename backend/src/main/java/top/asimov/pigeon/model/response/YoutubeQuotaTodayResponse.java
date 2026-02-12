package top.asimov.pigeon.model.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YoutubeQuotaTodayResponse {

  private String usageDatePt;
  private Integer dailyLimitUnits;
  private Integer requestCount;
  private Integer usedUnits;
  private Integer remainingUnits;
  private Boolean autoSyncBlocked;
  private String blockedReason;
  private Boolean warningReached;
  private List<YoutubeQuotaMethodUsageResponse> methodBreakdown;
}
