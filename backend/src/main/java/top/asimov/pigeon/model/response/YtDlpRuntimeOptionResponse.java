package top.asimov.pigeon.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YtDlpRuntimeOptionResponse {

  private String key;
  private String mode;
  private String version;
  private String modulePath;
  private String label;
}
