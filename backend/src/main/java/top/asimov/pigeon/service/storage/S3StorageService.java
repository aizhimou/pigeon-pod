package top.asimov.pigeon.service.storage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import top.asimov.pigeon.config.StorageProperties;

@Log4j2
@Service
public class S3StorageService {

  private final StorageProperties storageProperties;
  private final ObjectProvider<S3Client> s3ClientProvider;
  private final ObjectProvider<S3Presigner> s3PresignerProvider;

  public S3StorageService(StorageProperties storageProperties,
      ObjectProvider<S3Client> s3ClientProvider,
      ObjectProvider<S3Presigner> s3PresignerProvider) {
    this.storageProperties = storageProperties;
    this.s3ClientProvider = s3ClientProvider;
    this.s3PresignerProvider = s3PresignerProvider;
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
    S3Client client = s3ClientProvider.getIfAvailable();
    if (client == null) {
      throw new IllegalStateException("S3Client is not configured");
    }
    return client;
  }

  private S3Presigner requirePresigner() {
    S3Presigner presigner = s3PresignerProvider.getIfAvailable();
    if (presigner == null) {
      throw new IllegalStateException("S3Presigner is not configured");
    }
    return presigner;
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
}
