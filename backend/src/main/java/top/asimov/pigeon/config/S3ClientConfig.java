package top.asimov.pigeon.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(prefix = "pigeon.storage", name = "type", havingValue = "S3")
public class S3ClientConfig {

  @Bean
  public S3Client s3Client(StorageProperties storageProperties) {
    StorageProperties.S3 s3 = storageProperties.getS3();
    AwsCredentialsProvider credentialsProvider = buildCredentialsProvider(s3);
    long apiCallAttemptTimeoutSeconds = Math.max(1,
        Math.max(s3.getSocketTimeoutSeconds(), s3.getReadTimeoutSeconds()));
    long apiCallTimeoutSeconds = Math.max(apiCallAttemptTimeoutSeconds, 1);

    S3ClientBuilder builder = S3Client.builder()
        .region(Region.of(s3.getRegion()))
        .credentialsProvider(credentialsProvider)
        .serviceConfiguration(S3Configuration.builder()
            .pathStyleAccessEnabled(s3.isPathStyleAccess())
            // Cloudflare R2 recommends disabling chunked encoding for aws-sdk-java v2 putObject.
            .chunkedEncodingEnabled(false)
            .build())
        .overrideConfiguration(ClientOverrideConfiguration.builder()
            .apiCallAttemptTimeout(Duration.ofSeconds(apiCallAttemptTimeoutSeconds))
            .apiCallTimeout(Duration.ofSeconds(apiCallTimeoutSeconds))
            .build());

    if (StringUtils.hasText(s3.getEndpoint())) {
      builder.endpointOverride(URI.create(s3.getEndpoint()));
    }
    return builder.build();
  }

  @Bean
  public S3Presigner s3Presigner(StorageProperties storageProperties) {
    StorageProperties.S3 s3 = storageProperties.getS3();
    AwsCredentialsProvider credentialsProvider = buildCredentialsProvider(s3);

    S3Presigner.Builder builder = S3Presigner.builder()
        .region(Region.of(s3.getRegion()))
        .credentialsProvider(credentialsProvider)
        .serviceConfiguration(S3Configuration.builder()
            .pathStyleAccessEnabled(s3.isPathStyleAccess())
            .chunkedEncodingEnabled(false)
            .build());
    if (StringUtils.hasText(s3.getEndpoint())) {
      builder.endpointOverride(URI.create(s3.getEndpoint()));
    }
    return builder.build();
  }

  private AwsCredentialsProvider buildCredentialsProvider(StorageProperties.S3 s3) {
    if (s3.hasStaticCredentials()) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey()));
    }
    return DefaultCredentialsProvider.create();
  }
}
