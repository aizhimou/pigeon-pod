package top.asimov.pigeon.model.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YtDlpUpdateStatusResponse {

  private String state;
  private String channel;
  private String beforeVersion;
  private String afterVersion;
  private String error;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
}
