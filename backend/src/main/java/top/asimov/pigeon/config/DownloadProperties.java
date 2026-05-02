package top.asimov.pigeon.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pigeon.download")
public class DownloadProperties {

  private long processTimeoutMinutes = 60L;

  public long getProcessTimeoutMinutes() {
    return processTimeoutMinutes > 0 ? processTimeoutMinutes : 60L;
  }

  public long staleDownloadingTimeoutMinutes() {
    return getProcessTimeoutMinutes() + 10L;
  }
}
