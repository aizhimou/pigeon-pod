package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.config.StorageProperties;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.CookieConfigMapper;
import top.asimov.pigeon.model.entity.CookieConfig;
import top.asimov.pigeon.model.enums.CookiePlatform;
import top.asimov.pigeon.model.response.CookieSummaryResponse;

@Log4j2
@Service
public class CookieService {

  private static final int MAX_COOKIE_CONTENT_LENGTH = 1_000_000;
  private static final List<CookiePlatform> MANAGED_PLATFORMS = List.of(
      CookiePlatform.YOUTUBE,
      CookiePlatform.BILIBILI
  );

  private final CookieConfigMapper cookieConfigMapper;
  private final StorageProperties storageProperties;

  public CookieService(CookieConfigMapper cookieConfigMapper,
      StorageProperties storageProperties) {
    this.cookieConfigMapper = cookieConfigMapper;
    this.storageProperties = storageProperties;
  }

  @Transactional(readOnly = true)
  public List<CookieSummaryResponse> listSummaries() {
    return cookieConfigMapper.selectList(new LambdaQueryWrapper<CookieConfig>()
            .in(CookieConfig::getPlatform, MANAGED_PLATFORMS.stream().map(Enum::name).toList()))
        .stream()
        .filter(config -> StringUtils.hasText(config.getPlatform()))
        .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
        .map(config -> CookieSummaryResponse.builder()
            .platform(config.getPlatform())
            .updatedAt(config.getUpdatedAt())
            .build())
        .toList();
  }

  @Transactional
  public void upsert(CookiePlatform platform, String cookiesContent) {
    validateManagedPlatform(platform);
    String normalizedContent = normalizeCookiesContent(cookiesContent);
    validateCookiesContent(platform, normalizedContent);

    CookieConfig existing = getCookieConfig(platform);
    if (existing == null) {
      CookieConfig created = CookieConfig.builder()
          .platform(platform.name())
          .cookiesContent(normalizedContent)
          .enabled(true)
          .sourceType("UPLOAD")
          .createdAt(LocalDateTime.now())
          .updatedAt(LocalDateTime.now())
          .build();
      cookieConfigMapper.insert(created);
      return;
    }

    existing.setCookiesContent(normalizedContent);
    existing.setEnabled(true);
    existing.setSourceType("UPLOAD");
    existing.setUpdatedAt(LocalDateTime.now());
    cookieConfigMapper.updateById(existing);
  }

  @Transactional
  public void delete(CookiePlatform platform) {
    validateManagedPlatform(platform);
    cookieConfigMapper.delete(new LambdaQueryWrapper<CookieConfig>()
        .eq(CookieConfig::getPlatform, platform.name()));
  }

  public String createTempCookiesFile(CookiePlatform platform, String userId) {
    if (platform == null) {
      return null;
    }

    CookieConfig cookieConfig = getCookieConfig(platform);
    if (cookieConfig == null || !Boolean.TRUE.equals(cookieConfig.getEnabled())
        || !StringUtils.hasText(cookieConfig.getCookiesContent())) {
      return null;
    }

    try {
      Path directory = Path.of(storageProperties.getTempDir(), "cookies");
      Files.createDirectories(directory);
      String fileName = "cookies_" + platform.name().toLowerCase() + "_" + userId + "_"
          + System.currentTimeMillis() + ".txt";
      Path filePath = directory.resolve(fileName);
      Files.writeString(filePath, cookieConfig.getCookiesContent(), StandardCharsets.UTF_8);

      // Best-effort local permission tightening.
      filePath.toFile().setReadable(false, false);
      filePath.toFile().setWritable(false, false);
      filePath.toFile().setReadable(true, true);
      filePath.toFile().setWritable(true, true);

      log.debug("创建平台 cookies 文件: platform={}, path={}", platform, filePath);
      return filePath.toString();
    } catch (IOException e) {
      log.error("创建平台 cookies 文件失败: platform={}", platform, e);
      throw new RuntimeException("Failed to create temporary cookies file", e);
    }
  }

  public void deleteTempCookiesFile(String filePath) {
    if (!StringUtils.hasText(filePath)) {
      return;
    }
    try {
      Files.deleteIfExists(Path.of(filePath));
      log.debug("删除平台 cookies 文件: {}", filePath);
    } catch (IOException e) {
      log.warn("删除平台 cookies 文件失败: {}", filePath, e);
    }
  }

  private CookieConfig getCookieConfig(CookiePlatform platform) {
    if (platform == null) {
      return null;
    }
    return cookieConfigMapper.selectOne(new LambdaQueryWrapper<CookieConfig>()
        .eq(CookieConfig::getPlatform, platform.name())
        .last("LIMIT 1"));
  }

  private void validateManagedPlatform(CookiePlatform platform) {
    if (platform == null || !MANAGED_PLATFORMS.contains(platform)) {
      throw new BusinessException("unsupported cookie platform: " + platform);
    }
  }

  private String normalizeCookiesContent(String cookiesContent) {
    if (!StringUtils.hasText(cookiesContent)) {
      throw new BusinessException("cookies content is required");
    }
    String normalized = cookiesContent.strip();
    if (!StringUtils.hasText(normalized)) {
      throw new BusinessException("cookies content is required");
    }
    if (normalized.length() > MAX_COOKIE_CONTENT_LENGTH) {
      throw new BusinessException("cookies content is too large");
    }
    return normalized;
  }

  private void validateCookiesContent(CookiePlatform platform, String cookiesContent) {
    String lowered = cookiesContent.toLowerCase();
    if (!lowered.contains("# http cookie file") && !lowered.contains("# netscape http cookie file")) {
      throw new BusinessException("cookies file must be in Netscape format");
    }

    if (platform == CookiePlatform.YOUTUBE && !lowered.contains("youtube.com")) {
      throw new BusinessException("cookies file does not appear to be for youtube.com");
    }
    if (platform == CookiePlatform.BILIBILI && !lowered.contains("bilibili.com")) {
      throw new BusinessException("cookies file does not appear to be for bilibili.com");
    }
  }
}
