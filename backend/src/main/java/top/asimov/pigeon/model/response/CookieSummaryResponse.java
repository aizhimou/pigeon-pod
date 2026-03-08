package top.asimov.pigeon.model.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CookieSummaryResponse {

  private String platform;
  private LocalDateTime updatedAt;
}
