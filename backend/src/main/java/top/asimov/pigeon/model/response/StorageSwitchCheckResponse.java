package top.asimov.pigeon.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageSwitchCheckResponse {

  private Boolean canSwitch;
  private Long downloadingCount;
  private String message;
}
