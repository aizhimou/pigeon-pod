package top.asimov.pigeon.service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.nio.file.Path;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.SystemConfigMapper;
import top.asimov.pigeon.model.entity.SystemConfig;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.model.enums.StorageType;

@Service
public class SystemConfigService {

  private final SystemConfigMapper systemConfigMapper;
  private final MessageSource messageSource;

  public SystemConfigService(SystemConfigMapper systemConfigMapper, MessageSource messageSource) {
    this.systemConfigMapper = systemConfigMapper;
    this.messageSource = messageSource;
  }

  @Transactional(readOnly = true)
  public SystemConfig getCurrentConfig() {
    SystemConfig config = systemConfigMapper.selectById(SystemConfig.SINGLETON_ID);
    if (config == null) {
      return createDefaultConfig();
    }
    normalizeDefaults(config);
    return config;
  }

  @Transactional
  public SystemConfig ensureExists() {
    SystemConfig config = systemConfigMapper.selectById(SystemConfig.SINGLETON_ID);
    if (config != null) {
      normalizeDefaults(config);
      return config;
    }
    SystemConfig created = createDefaultConfig();
    systemConfigMapper.insert(created);
    return created;
  }

  @Transactional
  public SystemConfig updateSystemConfig(SystemConfig incoming) {
    SystemConfig candidate = buildCandidate(incoming);
    candidate.setUpdatedAt(LocalDateTime.now());
    systemConfigMapper.updateById(candidate);
    return getCurrentConfig();
  }

  @Transactional
  public SystemConfig buildCandidate(SystemConfig incoming) {
    if (incoming == null) {
      throw new BusinessException("system config is required");
    }

    SystemConfig existing = ensureExists();
    SystemConfig candidate = cloneConfig(existing);
    mergeSystemConfig(candidate, incoming);
    validate(candidate);
    return candidate;
  }

  @Transactional(readOnly = true)
  public void validateSystemConfig(SystemConfig config) {
    if (config == null) {
      throw new BusinessException("system config is required");
    }
    normalizeDefaults(config);
    validate(config);
  }

  @Transactional
  public SystemConfig updateYoutubeApiSettings(String youtubeApiKey, Integer youtubeDailyLimitUnits) {
    SystemConfig config = ensureExists();
    config.setYoutubeApiKey(StringUtils.hasText(youtubeApiKey) ? youtubeApiKey.trim() : null);
    if (youtubeDailyLimitUnits == null || youtubeDailyLimitUnits <= 0) {
      config.setYoutubeDailyLimitUnits(null);
    } else {
      config.setYoutubeDailyLimitUnits(youtubeDailyLimitUnits);
    }
    config.setUpdatedAt(LocalDateTime.now());
    systemConfigMapper.updateById(config);
    return getCurrentConfig();
  }

  @Transactional
  public boolean updateLoginCaptchaEnabled(Boolean enabled) {
    SystemConfig config = ensureExists();
    config.setLoginCaptchaEnabled(Boolean.TRUE.equals(enabled));
    config.setUpdatedAt(LocalDateTime.now());
    systemConfigMapper.updateById(config);
    return Boolean.TRUE.equals(config.getLoginCaptchaEnabled());
  }

  @Transactional
  public void updateCookiesContent(String cookiesContent) {
    SystemConfig config = ensureExists();
    config.setCookiesContent(cookiesContent);
    config.setUpdatedAt(LocalDateTime.now());
    systemConfigMapper.updateById(config);
  }

  @Transactional
  public void clearCookies() {
    SystemConfig config = ensureExists();
    config.setCookiesContent("");
    config.setUpdatedAt(LocalDateTime.now());
    systemConfigMapper.updateById(config);
  }

  @Transactional
  public String updateYtDlpArgs(String ytDlpArgs) {
    SystemConfig config = ensureExists();
    config.setYtDlpArgs(ytDlpArgs);
    config.setUpdatedAt(LocalDateTime.now());
    systemConfigMapper.updateById(config);
    return ytDlpArgs;
  }

  @Transactional(readOnly = true)
  public boolean isS3Mode() {
    return getCurrentConfig().getStorageType() == StorageType.S3;
  }

  @Transactional(readOnly = true)
  public String getCookiesContent() {
    return getCurrentConfig().getCookiesContent();
  }

  @Transactional(readOnly = true)
  public String getYtDlpArgs() {
    return getCurrentConfig().getYtDlpArgs();
  }

  @Transactional(readOnly = true)
  public String getYoutubeApiKey() {
    return getCurrentConfig().getYoutubeApiKey();
  }

  @Transactional(readOnly = true)
  public Integer getYoutubeDailyLimitUnits() {
    return getCurrentConfig().getYoutubeDailyLimitUnits();
  }

  @Transactional(readOnly = true)
  public Boolean isLoginCaptchaEnabled() {
    return Boolean.TRUE.equals(getCurrentConfig().getLoginCaptchaEnabled());
  }

  @Transactional(readOnly = true)
  public String requireBaseUrl() {
    String baseUrl = normalizeBaseUrl(getCurrentConfig().getBaseUrl());
    if (!StringUtils.hasText(baseUrl)) {
      throw new BusinessException(messageSource.getMessage("system.base.url.empty", null,
          LocaleContextHolder.getLocale()));
    }
    return baseUrl;
  }

  public void fillSystemFields(User user) {
    if (user == null) {
      return;
    }
    user.setHasCookie(StringUtils.hasText(getCurrentConfig().getCookiesContent()));
  }

  public void normalizeDefaults(SystemConfig config) {
    if (config == null) {
      return;
    }

    if (config.getId() == null) {
      config.setId(SystemConfig.SINGLETON_ID);
    }
    if (config.getStorageType() == null) {
      config.setStorageType(StorageType.LOCAL);
    }
    if (!StringUtils.hasText(config.getStorageTempDir())) {
      config.setStorageTempDir(SystemConfig.DEFAULT_TEMP_DIR);
    }
    if (!StringUtils.hasText(config.getLocalAudioPath())) {
      config.setLocalAudioPath(SystemConfig.DEFAULT_LOCAL_AUDIO_PATH);
    }
    if (!StringUtils.hasText(config.getLocalVideoPath())) {
      config.setLocalVideoPath(SystemConfig.DEFAULT_LOCAL_VIDEO_PATH);
    }
    if (!StringUtils.hasText(config.getLocalCoverPath())) {
      config.setLocalCoverPath(SystemConfig.DEFAULT_LOCAL_COVER_PATH);
    }
    if (!StringUtils.hasText(config.getS3Region())) {
      config.setS3Region(SystemConfig.DEFAULT_S3_REGION);
    }
    if (config.getS3PathStyleAccess() == null) {
      config.setS3PathStyleAccess(true);
    }
    if (config.getS3ConnectTimeoutSeconds() == null || config.getS3ConnectTimeoutSeconds() <= 0) {
      config.setS3ConnectTimeoutSeconds(SystemConfig.DEFAULT_S3_CONNECT_TIMEOUT_SECONDS);
    }
    if (config.getS3SocketTimeoutSeconds() == null || config.getS3SocketTimeoutSeconds() <= 0) {
      config.setS3SocketTimeoutSeconds(SystemConfig.DEFAULT_S3_SOCKET_TIMEOUT_SECONDS);
    }
    if (config.getS3ReadTimeoutSeconds() == null || config.getS3ReadTimeoutSeconds() <= 0) {
      config.setS3ReadTimeoutSeconds(SystemConfig.DEFAULT_S3_READ_TIMEOUT_SECONDS);
    }
    if (config.getS3PresignExpireHours() == null || config.getS3PresignExpireHours() <= 0) {
      config.setS3PresignExpireHours(SystemConfig.DEFAULT_S3_PRESIGN_EXPIRE_HOURS);
    }
    config.setBaseUrl(normalizeBaseUrl(config.getBaseUrl()));
    config.setHasS3SecretKey(StringUtils.hasText(config.getS3SecretKey()));
  }

  public String normalizeBaseUrl(String rawBaseUrl) {
    if (!StringUtils.hasText(rawBaseUrl)) {
      return null;
    }
    String normalized = rawBaseUrl.trim();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private void mergeSystemConfig(SystemConfig existing, SystemConfig incoming) {
    existing.setBaseUrl(normalizeBaseUrl(incoming.getBaseUrl()));
    existing.setStorageType(incoming.getStorageType() == null ? StorageType.LOCAL : incoming.getStorageType());
    existing.setStorageTempDir(incoming.getStorageTempDir());
    existing.setLocalAudioPath(incoming.getLocalAudioPath());
    existing.setLocalVideoPath(incoming.getLocalVideoPath());
    existing.setLocalCoverPath(incoming.getLocalCoverPath());
    existing.setS3Endpoint(incoming.getS3Endpoint());
    existing.setS3Region(incoming.getS3Region());
    existing.setS3Bucket(incoming.getS3Bucket());
    existing.setS3AccessKey(incoming.getS3AccessKey());
    if (incoming.getS3SecretKey() != null) {
      if (StringUtils.hasText(incoming.getS3SecretKey())) {
        existing.setS3SecretKey(incoming.getS3SecretKey());
      } else if (!Boolean.TRUE.equals(incoming.getHasS3SecretKey())) {
        existing.setS3SecretKey(null);
      }
    }
    existing.setS3PathStyleAccess(incoming.getS3PathStyleAccess());
    existing.setS3ConnectTimeoutSeconds(incoming.getS3ConnectTimeoutSeconds());
    existing.setS3SocketTimeoutSeconds(incoming.getS3SocketTimeoutSeconds());
    existing.setS3ReadTimeoutSeconds(incoming.getS3ReadTimeoutSeconds());
    existing.setS3PresignExpireHours(incoming.getS3PresignExpireHours());
    normalizeDefaults(existing);
  }

  private void validate(SystemConfig config) {
    if (config.getStorageType() == StorageType.LOCAL) {
      validateNonBlank(config.getLocalAudioPath(), "local audio path is required");
      validateNonBlank(config.getLocalVideoPath(), "local video path is required");
      validateNonBlank(config.getLocalCoverPath(), "local cover path is required");
      validateNonBlank(config.getStorageTempDir(), "temp dir is required");
      return;
    }

    validateNonBlank(config.getS3Endpoint(), "s3 endpoint is required");
    validateNonBlank(config.getS3Region(), "s3 region is required");
    validateNonBlank(config.getS3Bucket(), "s3 bucket is required");
    validateNonBlank(config.getS3AccessKey(), "s3 access key is required");
    validateNonBlank(config.getS3SecretKey(), "s3 secret key is required");
    validateNonBlank(config.getStorageTempDir(), "temp dir is required");
    validateLocalDiskPath(config.getStorageTempDir(),
        "temp dir must be a local disk directory path");

    validateRange(config.getS3ConnectTimeoutSeconds(), 1, 7200, "s3 connect timeout out of range");
    validateRange(config.getS3SocketTimeoutSeconds(), 1, 7200, "s3 socket timeout out of range");
    validateRange(config.getS3ReadTimeoutSeconds(), 1, 7200, "s3 read timeout out of range");
    validateRange(config.getS3PresignExpireHours(), 1, 720, "s3 presign expire hours out of range");
  }

  private void validateRange(Integer value, int min, int max, String message) {
    if (value == null || value < min || value > max) {
      throw new BusinessException(message);
    }
  }

  private void validateNonBlank(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new BusinessException(message);
    }
  }

  private void validateLocalDiskPath(String path, String message) {
    if (!StringUtils.hasText(path)) {
      throw new BusinessException(message);
    }
    String trimmed = path.trim();
    if (trimmed.contains("://")) {
      throw new BusinessException(message);
    }
    try {
      Path parsed = Path.of(trimmed);
      if (!parsed.isAbsolute()) {
        throw new BusinessException(message);
      }
    } catch (Exception ex) {
      if (ex instanceof BusinessException businessException) {
        throw businessException;
      }
      throw new BusinessException(message);
    }
  }

  private SystemConfig createDefaultConfig() {
    SystemConfig config = SystemConfig.builder()
        .id(SystemConfig.SINGLETON_ID)
        .storageType(StorageType.LOCAL)
        .storageTempDir(SystemConfig.DEFAULT_TEMP_DIR)
        .localAudioPath(SystemConfig.DEFAULT_LOCAL_AUDIO_PATH)
        .localVideoPath(SystemConfig.DEFAULT_LOCAL_VIDEO_PATH)
        .localCoverPath(SystemConfig.DEFAULT_LOCAL_COVER_PATH)
        .s3Region(SystemConfig.DEFAULT_S3_REGION)
        .s3PathStyleAccess(true)
        .s3ConnectTimeoutSeconds(SystemConfig.DEFAULT_S3_CONNECT_TIMEOUT_SECONDS)
        .s3SocketTimeoutSeconds(SystemConfig.DEFAULT_S3_SOCKET_TIMEOUT_SECONDS)
        .s3ReadTimeoutSeconds(SystemConfig.DEFAULT_S3_READ_TIMEOUT_SECONDS)
        .s3PresignExpireHours(SystemConfig.DEFAULT_S3_PRESIGN_EXPIRE_HOURS)
        .loginCaptchaEnabled(false)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
    normalizeDefaults(config);
    return config;
  }

  private SystemConfig cloneConfig(SystemConfig source) {
    if (source == null) {
      return null;
    }
    return SystemConfig.builder()
        .id(source.getId())
        .baseUrl(source.getBaseUrl())
        .youtubeApiKey(source.getYoutubeApiKey())
        .cookiesContent(source.getCookiesContent())
        .ytDlpArgs(source.getYtDlpArgs())
        .loginCaptchaEnabled(source.getLoginCaptchaEnabled())
        .youtubeDailyLimitUnits(source.getYoutubeDailyLimitUnits())
        .storageType(source.getStorageType())
        .storageTempDir(source.getStorageTempDir())
        .localAudioPath(source.getLocalAudioPath())
        .localVideoPath(source.getLocalVideoPath())
        .localCoverPath(source.getLocalCoverPath())
        .s3Endpoint(source.getS3Endpoint())
        .s3Region(source.getS3Region())
        .s3Bucket(source.getS3Bucket())
        .s3AccessKey(source.getS3AccessKey())
        .s3SecretKey(source.getS3SecretKey())
        .s3PathStyleAccess(source.getS3PathStyleAccess())
        .s3ConnectTimeoutSeconds(source.getS3ConnectTimeoutSeconds())
        .s3SocketTimeoutSeconds(source.getS3SocketTimeoutSeconds())
        .s3ReadTimeoutSeconds(source.getS3ReadTimeoutSeconds())
        .s3PresignExpireHours(source.getS3PresignExpireHours())
        .createdAt(source.getCreatedAt())
        .updatedAt(source.getUpdatedAt())
        .hasS3SecretKey(source.getHasS3SecretKey())
        .build();
  }

  public boolean applyLegacyEnvBackfill(SystemConfig config, String baseUrl, String audioPath,
      String videoPath, String coverPath) {
    boolean changed = false;
    if (StringUtils.hasText(baseUrl) && !StringUtils.hasText(config.getBaseUrl())) {
      config.setBaseUrl(normalizeBaseUrl(baseUrl));
      changed = true;
    }
    if (StringUtils.hasText(audioPath) && shouldBackfillPath(config.getLocalAudioPath(),
        SystemConfig.DEFAULT_LOCAL_AUDIO_PATH)) {
      config.setLocalAudioPath(audioPath);
      changed = true;
    }
    if (StringUtils.hasText(videoPath) && shouldBackfillPath(config.getLocalVideoPath(),
        SystemConfig.DEFAULT_LOCAL_VIDEO_PATH)) {
      config.setLocalVideoPath(videoPath);
      changed = true;
    }
    if (StringUtils.hasText(coverPath) && shouldBackfillPath(config.getLocalCoverPath(),
        SystemConfig.DEFAULT_LOCAL_COVER_PATH)) {
      config.setLocalCoverPath(coverPath);
      changed = true;
    }
    return changed;
  }

  private boolean shouldBackfillPath(String current, String defaultValue) {
    if (!StringUtils.hasText(current)) {
      return true;
    }
    return Objects.equals(current.trim(), defaultValue);
  }
}
