package top.asimov.pigeon.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.model.entity.SystemConfig;
import top.asimov.pigeon.model.enums.StorageType;
import top.asimov.pigeon.service.SystemConfigService;

@Log4j2
@Component
public class StorageRuntimeConfigApplier {

  private final StorageProperties storageProperties;
  private final MediaPathProperties mediaPathProperties;
  private final SystemConfigService systemConfigService;

  public StorageRuntimeConfigApplier(StorageProperties storageProperties,
      MediaPathProperties mediaPathProperties, SystemConfigService systemConfigService) {
    this.storageProperties = storageProperties;
    this.mediaPathProperties = mediaPathProperties;
    this.systemConfigService = systemConfigService;
  }

  public synchronized void apply(SystemConfig config) {
    if (config == null) {
      return;
    }
    systemConfigService.normalizeDefaults(config);

    storageProperties.setType(
        config.getStorageType() == null ? StorageType.LOCAL : config.getStorageType());
    storageProperties.setTempDir(config.getStorageTempDir());
    storageProperties.getS3().setEndpoint(config.getS3Endpoint());
    storageProperties.getS3().setRegion(config.getS3Region());
    storageProperties.getS3().setBucket(config.getS3Bucket());
    storageProperties.getS3().setAccessKey(config.getS3AccessKey());
    storageProperties.getS3().setSecretKey(config.getS3SecretKey());
    storageProperties.getS3().setPathStyleAccess(Boolean.TRUE.equals(config.getS3PathStyleAccess()));
    storageProperties.getS3().setConnectTimeoutSeconds(config.getS3ConnectTimeoutSeconds());
    storageProperties.getS3().setSocketTimeoutSeconds(config.getS3SocketTimeoutSeconds());
    storageProperties.getS3().setReadTimeoutSeconds(config.getS3ReadTimeoutSeconds());
    storageProperties.getS3().setPresignExpireHours(config.getS3PresignExpireHours());

    mediaPathProperties.setAudioFilePath(config.getLocalAudioPath());
    mediaPathProperties.setVideoFilePath(config.getLocalVideoPath());
    mediaPathProperties.setCoverFilePath(config.getLocalCoverPath());

    log.info("Runtime storage config applied: storageType={}, tempDir={}",
        storageProperties.getType(), storageProperties.getTempDir());
  }
}
