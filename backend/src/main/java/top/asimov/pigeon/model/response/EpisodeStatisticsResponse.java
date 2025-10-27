package top.asimov.pigeon.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EpisodeStatisticsResponse {

  private Long pendingCount;
  private Long downloadingCount;
  private Long completedCount;
  private Long failedCount;

}

