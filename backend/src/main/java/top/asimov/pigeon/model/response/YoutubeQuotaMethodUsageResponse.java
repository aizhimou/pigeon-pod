package top.asimov.pigeon.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YoutubeQuotaMethodUsageResponse {

  private String apiMethod;
  private Integer requestCount;
  private Integer quotaUnits;
}
