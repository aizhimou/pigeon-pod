package top.asimov.pigeon.model.response;

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
  private String version;
  private String channel;
  private Boolean updating;
  private YtDlpUpdateStatusResponse status;
}
