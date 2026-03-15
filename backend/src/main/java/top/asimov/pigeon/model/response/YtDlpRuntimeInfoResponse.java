package top.asimov.pigeon.model.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YtDlpRuntimeInfoResponse {

  private String managedRoot;
  private Boolean managedReady;
  private String mode;
  private String version;
  private String channel;
  private String activeRuntimeKey;
  private Boolean updating;
  private List<YtDlpRuntimeOptionResponse> availableRuntimes;
  private YtDlpUpdateStatusResponse status;
}
