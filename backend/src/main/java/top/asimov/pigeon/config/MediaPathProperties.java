package top.asimov.pigeon.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pigeon")
public class MediaPathProperties {

  private String audioFilePath = "/data/audio/";
  private String videoFilePath = "/data/video/";
  private String coverFilePath = "/data/cover/";
}
