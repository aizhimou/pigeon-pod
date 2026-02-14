package top.asimov.pigeon.service.storage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import top.asimov.pigeon.config.StorageProperties;
import top.asimov.pigeon.model.entity.SystemConfig;

@Log4j2
@Service
public class S3StorageService {

  private final StorageProperties storageProperties;
  private final Object lock = new Object();
  private volatile ResolvedS3Config cachedConfig;
  private volatile S3Client cachedClient;
  private volatile S3Presigner cachedPresigner;

  public S3StorageService(StorageProperties storageProperties) {
    this.storageProperties = storageProperties;
  }

  public UploadResult uploadFile(Path localFile, String objectKey, String contentType) {
    S3Client client = requireClient();
    String bucket = requireBucket();
    PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
        .bucket(bucket)
        .key(objectKey);
    if (StringUtils.hasText(contentType)) {
      requestBuilder.contentType(contentType);
    }
    var response = client.putObject(requestBuilder.build(), RequestBody.fromFile(localFile));
    long size = localFile.toFile().length();
    return new UploadResult(objectKey, size, response.eTag());
  }

  public UploadResult uploadBytes(byte[] bytes, String objectKey, String contentType) {
    S3Client client = requireClient();
    String bucket = requireBucket();
    PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
        .bucket(bucket)
        .key(objectKey);
    if (StringUtils.hasText(contentType)) {
      requestBuilder.contentType(contentType);
    }
    var response = client.putObject(requestBuilder.build(), RequestBody.fromBytes(bytes));
    return new UploadResult(objectKey, bytes.length, response.eTag());
  }

  public long headContentLength(String objectKey) {
    S3Client client = requireClient();
    String bucket = requireBucket();
    var headResponse = client.headObject(HeadObjectRequest.builder()
        .bucket(bucket)
        .key(objectKey)
        .build());
    return headResponse.contentLength();
  }

  public String headEtag(String objectKey) {
    S3Client client = requireClient();
    String bucket = requireBucket();
    var headResponse = client.headObject(HeadObjectRequest.builder()
        .bucket(bucket)
        .key(objectKey)
        .build());
    return headResponse.eTag();
  }

  public String generatePresignedGetUrl(String objectKey, Duration duration, String contentDisposition) {
    S3Presigner presigner = requirePresigner();
    String bucket = requireBucket();
    GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
        .bucket(bucket)
        .key(objectKey);
    if (StringUtils.hasText(contentDisposition)) {
      requestBuilder.responseContentDisposition(contentDisposition);
    }
    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(duration)
        .getObjectRequest(requestBuilder.build())
        .build();
    return presigner.presignGetObject(presignRequest).url().toString();
  }

  public void deleteObjectQuietly(String objectKey) {
    if (!StringUtils.hasText(objectKey)) {
      return;
    }
    try {
      requireClient().deleteObject(DeleteObjectRequest.builder()
          .bucket(requireBucket())
          .key(objectKey)
          .build());
    } catch (Exception e) {
      log.warn("删除 S3 对象失败（忽略）: key={}", objectKey, e);
    }
  }

  public void deleteObjectsByPrefixQuietly(String prefix) {
    if (!StringUtils.hasText(prefix)) {
      return;
    }
    List<String> keys = listKeysByPrefix(prefix);
    for (String key : keys) {
      deleteObjectQuietly(key);
    }
  }

  public List<String> listKeysByPrefix(String prefix) {
    S3Client client = requireClient();
    String bucket = requireBucket();
    String continuationToken = null;
    List<String> keys = new ArrayList<>();
    do {
      ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
          .bucket(bucket)
          .prefix(prefix)
          .maxKeys(1000);
      if (StringUtils.hasText(continuationToken)) {
        requestBuilder.continuationToken(continuationToken);
      }
      var response = client.listObjectsV2(requestBuilder.build());
      for (S3Object object : response.contents()) {
        keys.add(object.key());
      }
      continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
    } while (StringUtils.hasText(continuationToken));
    return keys;
  }

  public boolean keyExists(String objectKey) {
    try {
      headContentLength(objectKey);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public Duration getDefaultPresignDuration() {
    return storageProperties.getS3().presignDuration();
  }

  private S3Client requireClient() {
    return runtimeClient(resolveRuntimeConfig());
  }

  private S3Presigner requirePresigner() {
    return runtimePresigner(resolveRuntimeConfig());
  }

  private S3Client runtimeClient(ResolvedS3Config config) {
    if (cachedClient != null && config.equals(cachedConfig)) {
      return cachedClient;
    }
    synchronized (lock) {
      if (cachedClient != null && config.equals(cachedConfig)) {
        return cachedClient;
      }
      closeQuietly(cachedClient);
      closeQuietly(cachedPresigner);
      cachedClient = buildClient(config);
      cachedPresigner = buildPresigner(config);
      cachedConfig = config;
      return cachedClient;
    }
  }

  private S3Presigner runtimePresigner(ResolvedS3Config config) {
    if (cachedPresigner != null && config.equals(cachedConfig)) {
      return cachedPresigner;
    }
    synchronized (lock) {
      if (cachedPresigner != null && config.equals(cachedConfig)) {
        return cachedPresigner;
      }
      closeQuietly(cachedClient);
      closeQuietly(cachedPresigner);
      cachedClient = buildClient(config);
      cachedPresigner = buildPresigner(config);
      cachedConfig = config;
      return cachedPresigner;
    }
  }

  private void closeQuietly(AutoCloseable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (Exception e) {
      log.debug("Close resource failed (ignored)", e);
    }
  }

  private ResolvedS3Config resolveRuntimeConfig() {
    StorageProperties.S3 s3 = storageProperties.getS3();
    return new ResolvedS3Config(
        s3.getEndpoint(),
        StringUtils.hasText(s3.getRegion()) ? s3.getRegion() : "us-east-1",
        s3.getBucket(),
        s3.getAccessKey(),
        s3.getSecretKey(),
        s3.isPathStyleAccess(),
        toPositiveInt(s3.getConnectTimeoutSeconds(), 30),
        toPositiveInt(s3.getSocketTimeoutSeconds(), 1800),
        toPositiveInt(s3.getReadTimeoutSeconds(), 1800)
    );
  }

  public void testConnection(SystemConfig config) {
    ResolvedS3Config resolved = new ResolvedS3Config(
        config.getS3Endpoint(),
        config.getS3Region(),
        config.getS3Bucket(),
        config.getS3AccessKey(),
        config.getS3SecretKey(),
        Boolean.TRUE.equals(config.getS3PathStyleAccess()),
        toPositiveInt(config.getS3ConnectTimeoutSeconds(), 30),
        toPositiveInt(config.getS3SocketTimeoutSeconds(), 1800),
        toPositiveInt(config.getS3ReadTimeoutSeconds(), 1800)
    );

    try (S3Client client = buildClient(resolved)) {
      client.headBucket(builder -> builder.bucket(resolved.bucket()));
      String testKey = "pigeonpod-test/" + System.currentTimeMillis() + ".txt";
      client.putObject(builder -> builder.bucket(resolved.bucket()).key(testKey),
          RequestBody.fromString("pigeonpod-storage-test"));
      client.deleteObject(builder -> builder.bucket(resolved.bucket()).key(testKey));
    }
  }

  private S3Client buildClient(ResolvedS3Config config) {
    AwsCredentialsProvider credentialsProvider = buildCredentialsProvider(config);
    int apiCallAttemptTimeoutSeconds = Math.max(1, Math.max(config.socketTimeoutSeconds(),
        config.readTimeoutSeconds()));
    int apiCallTimeoutSeconds = Math.max(apiCallAttemptTimeoutSeconds, 1);

    S3ClientBuilder builder = S3Client.builder()
        .region(Region.of(config.region()))
        .credentialsProvider(credentialsProvider)
        .serviceConfiguration(S3Configuration.builder()
            .pathStyleAccessEnabled(config.pathStyleAccess())
            .chunkedEncodingEnabled(false)
            .build())
        .overrideConfiguration(ClientOverrideConfiguration.builder()
            .apiCallAttemptTimeout(Duration.ofSeconds(apiCallAttemptTimeoutSeconds))
            .apiCallTimeout(Duration.ofSeconds(apiCallTimeoutSeconds))
            .build());
    if (StringUtils.hasText(config.endpoint())) {
      builder.endpointOverride(java.net.URI.create(config.endpoint()));
    }
    return builder.build();
  }

  private S3Presigner buildPresigner(ResolvedS3Config config) {
    S3Presigner.Builder builder = S3Presigner.builder()
        .region(Region.of(config.region()))
        .credentialsProvider(buildCredentialsProvider(config))
        .serviceConfiguration(S3Configuration.builder()
            .pathStyleAccessEnabled(config.pathStyleAccess())
            .chunkedEncodingEnabled(false)
            .build());
    if (StringUtils.hasText(config.endpoint())) {
      builder.endpointOverride(java.net.URI.create(config.endpoint()));
    }
    return builder.build();
  }

  private AwsCredentialsProvider buildCredentialsProvider(ResolvedS3Config config) {
    if (StringUtils.hasText(config.accessKey()) && StringUtils.hasText(config.secretKey())) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(config.accessKey(), config.secretKey()));
    }
    return DefaultCredentialsProvider.create();
  }

  private String requireBucket() {
    String bucket = storageProperties.getS3().getBucket();
    if (!StringUtils.hasText(bucket)) {
      throw new IllegalStateException("S3 bucket is not configured");
    }
    return bucket;
  }

  public record UploadResult(String key, long size, String etag) {
  }

  private int toPositiveInt(long value, int fallback) {
    if (value <= 0 || value > Integer.MAX_VALUE) {
      return fallback;
    }
    return (int) value;
  }

  private int toPositiveInt(Integer value, int fallback) {
    if (value == null || value <= 0) {
      return fallback;
    }
    return value;
  }

  private record ResolvedS3Config(
      String endpoint,
      String region,
      String bucket,
      String accessKey,
      String secretKey,
      boolean pathStyleAccess,
      int connectTimeoutSeconds,
      int socketTimeoutSeconds,
      int readTimeoutSeconds
  ) {
  }
}
