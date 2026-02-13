package top.asimov.pigeon.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.enums.StorageType;

@Data
@Component
@ConfigurationProperties(prefix = "pigeon.storage")
public class StorageProperties {

  private StorageType type = StorageType.LOCAL;
  private String tempDir = "/tmp/pigeon-pod";
  private S3 s3 = new S3();

  public boolean isS3Mode() {
    return type == StorageType.S3;
  }

  @Data
  public static class S3 {
    private String endpoint;
    private String region = "us-east-1";
    private String bucket;
    private String accessKey;
    private String secretKey;
    private boolean pathStyleAccess = true;
    private long connectTimeoutSeconds = 10;
    private long socketTimeoutSeconds = 30;
    private long readTimeoutSeconds = 60;
    private long presignExpireHours = 72;

    public Duration presignDuration() {
      long hours = presignExpireHours <= 0 ? 72 : presignExpireHours;
      return Duration.ofHours(hours);
    }

    public boolean hasStaticCredentials() {
      return StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
    }
  }
}

