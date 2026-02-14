package top.asimov.pigeon.service;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.dev33.satoken.apikey.template.SaApiKeyUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.config.AppBaseUrlResolver;
import top.asimov.pigeon.config.StorageRuntimeConfigApplier;
import top.asimov.pigeon.config.YoutubeApiKeyHolder;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.mapper.UserMapper;
import top.asimov.pigeon.model.constant.Youtube;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.entity.SystemConfig;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.enums.StorageType;
import top.asimov.pigeon.model.enums.FeedType;
import top.asimov.pigeon.model.request.ExportFeedsOpmlRequest;
import top.asimov.pigeon.model.response.StorageSwitchCheckResponse;
import top.asimov.pigeon.service.storage.S3StorageService;
import top.asimov.pigeon.util.PasswordUtil;
import top.asimov.pigeon.util.YtDlpArgsValidator;

@Service
@Transactional
public class AccountService {

  private final UserMapper userMapper;
  private final ChannelMapper channelMapper;
  private final EpisodeMapper episodeMapper;
  private final PlaylistMapper playlistMapper;
  private final MessageSource messageSource;
  private final ObjectMapper objectMapper;
  private final SystemConfigService systemConfigService;
  private final AppBaseUrlResolver appBaseUrlResolver;
  private final S3StorageService s3StorageService;
  private final StorageRuntimeConfigApplier runtimeConfigApplier;

  public AccountService(UserMapper userMapper, ChannelMapper channelMapper, EpisodeMapper episodeMapper,
      PlaylistMapper playlistMapper, MessageSource messageSource, ObjectMapper objectMapper,
      SystemConfigService systemConfigService, AppBaseUrlResolver appBaseUrlResolver,
      S3StorageService s3StorageService, StorageRuntimeConfigApplier runtimeConfigApplier) {
    this.userMapper = userMapper;
    this.channelMapper = channelMapper;
    this.episodeMapper = episodeMapper;
    this.playlistMapper = playlistMapper;
    this.messageSource = messageSource;
    this.objectMapper = objectMapper;
    this.systemConfigService = systemConfigService;
    this.appBaseUrlResolver = appBaseUrlResolver;
    this.s3StorageService = s3StorageService;
    this.runtimeConfigApplier = runtimeConfigApplier;
  }

  /**
   * 获取当前用户的 API Key，如果不存在则生成一个新的
   *
   * @return 用户的 API Key
   */
  public String getApiKey() {
    String loginId = (String) StpUtil.getLoginId();
    User user = userMapper.selectById(loginId);
    String apiKey = user.getApiKey();
    if (!ObjectUtils.isEmpty(apiKey)) {
      return apiKey;
    }
    return generateApiKey();
  }

  /**
   * 生成新的 API Key
   *
   * @return 新的 API Key
   */
  public String generateApiKey() {
    String loginId = (String) StpUtil.getLoginId();
    User user = userMapper.selectById(loginId);

    String previousApiKey = user.getApiKey();
    if (StringUtils.hasText(previousApiKey)) {
      // If the user already has an API key, delete it
      SaApiKeyUtil.deleteApiKey(previousApiKey);
    }

    ApiKeyModel akModel = SaApiKeyUtil
        .createApiKeyModel(loginId)
        .setTitle(user.getUsername())
        .setExpiresTime(-1);
    SaApiKeyUtil.saveApiKey(akModel);
    user.setApiKey(akModel.getApiKey());
    userMapper.updateById(user);
    return akModel.getApiKey();
  }

  /**
   * 更改用户名
   *
   * @param userId      用户ID
   * @param newUsername 新用户名
   * @return 更新后的用户信息
   */
  public User changeUsername(String userId, String newUsername) {
    if (!StringUtils.hasText(newUsername)) {
      throw new BusinessException(
          messageSource.getMessage("user.empty.username", null, LocaleContextHolder.getLocale()));
    }
    User user = userMapper.selectById(userId);
    if (ObjectUtils.isEmpty(user)) {
      throw new BusinessException(
          messageSource.getMessage("user.not.found", null, LocaleContextHolder.getLocale()));
    }

    QueryWrapper<User> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("username", newUsername);
    if (userMapper.selectOne(queryWrapper) != null) {
      throw new BusinessException(
          messageSource.getMessage("user.username.taken", null, LocaleContextHolder.getLocale()));
    }

    user.setUsername(newUsername);
    user.setUpdatedAt(LocalDateTime.now());
    userMapper.updateById(user);
    return user;
  }

  /**
   * 重置用户密码
   *
   * @param userId      用户ID
   * @param oldPassword 旧密码
   * @param newPassword 新密码
   */
  public void resetPassword(String userId, String oldPassword, String newPassword) {
    User user = userMapper.selectById(userId);
    if (ObjectUtils.isEmpty(user)) {
      throw new BusinessException(
          messageSource.getMessage("user.not.found", null, LocaleContextHolder.getLocale()));
    }
    // Verify old password
    boolean verified = PasswordUtil.verifyPassword(oldPassword, user.getSalt(), user.getPassword());
    if (!verified) {
      throw new BusinessException(messageSource.getMessage("user.old.password.incorrect", null,
          LocaleContextHolder.getLocale()));
    }

    // Update to new password
    String salt = PasswordUtil.generateSalt(16);
    String encryptedPassword = PasswordUtil.generateEncryptedPassword(newPassword, salt);
    user.setPassword(encryptedPassword);
    user.setSalt(salt);
    user.setUpdatedAt(LocalDateTime.now());
    userMapper.updateById(user);
  }

  /**
   * 更新用户的 YouTube API Key 与每日配额上限配置。
   *
   * @param userId 用户ID
   * @param youtubeApiKey YouTube API Key
   * @param youtubeDailyLimitUnits 每日配额上限（为空表示不限制）
   * @return 更新后的系统配置
   */
  public SystemConfig updateYoutubeApiSettings(String userId, String youtubeApiKey,
      Integer youtubeDailyLimitUnits) {
    User user = userMapper.selectById(userId);
    if (ObjectUtils.isEmpty(user)) {
      throw new BusinessException(
          messageSource.getMessage("user.not.found", null, LocaleContextHolder.getLocale()));
    }

    SystemConfig config = systemConfigService.updateYoutubeApiSettings(youtubeApiKey,
        youtubeDailyLimitUnits);
    YoutubeApiKeyHolder.updateYoutubeApiKey(config.getYoutubeApiKey());
    return sanitizeSystemConfig(config);
  }

  /**
   * 更新用户的cookies内容
   *
   * @param userId         用户ID
   * @param cookiesContent cookies内容
   */
  public void updateUserCookies(String userId, String cookiesContent) {
    User user = userMapper.selectById(userId);
    if (user != null) {
      systemConfigService.updateCookiesContent(cookiesContent);
    }
  }

  /**
   * 删除用户的cookies内容
   *
   * @param userId 用户ID
   */
  public void deleteCookie(String userId) {
    User user = userMapper.selectById(userId);
    if (user != null) {
      systemConfigService.clearCookies();
    }
  }

  /**
   * 更新用户的日期格式偏好
   *
   * @param userId     用户ID
   * @param dateFormat 日期格式
   * @return 更新后的日期格式
   */
  public String updateDateFormat(String userId, String dateFormat) {
    User user = userMapper.selectById(userId);
    if (ObjectUtils.isEmpty(user)) {
      throw new BusinessException(
          messageSource.getMessage("user.not.found", null, LocaleContextHolder.getLocale()));
    }
    user.setDateFormat(dateFormat);
    user.setUpdatedAt(LocalDateTime.now());
    userMapper.updateById(user);
    return user.getDateFormat();
  }

  /**
   * 获取当前登录用户。
   */
  public User getCurrentUser() {
    String loginId = (String) StpUtil.getLoginId();
    User user = userMapper.selectById(loginId);
    systemConfigService.fillSystemFields(user);
    return user;
  }

  /**
   * 更新登录验证码配置（单用户系统：用户配置即全局配置）
   *
   * @param userId 用户ID
   * @param enabled 是否启用
   * @return 是否启用
   */
  public boolean updateLoginCaptchaEnabled(String userId, Boolean enabled) {
    User user = userMapper.selectById(userId);
    if (ObjectUtils.isEmpty(user)) {
      throw new BusinessException(
          messageSource.getMessage("user.not.found", null, LocaleContextHolder.getLocale()));
    }
    return systemConfigService.updateLoginCaptchaEnabled(enabled);
  }

  /**
   * 更新用户的 yt-dlp 自定义参数
   *
   * @param userId 用户ID
   * @param ytDlpArgs 用户自定义参数列表
   * @return 更新后的参数 JSON 字符串
   */
  public String updateYtDlpArgs(String userId, List<String> ytDlpArgs) {
    User user = userMapper.selectById(userId);
    if (ObjectUtils.isEmpty(user)) {
      throw new BusinessException(
          messageSource.getMessage("user.not.found", null, LocaleContextHolder.getLocale()));
    }

    List<String> validated = YtDlpArgsValidator.validate(ytDlpArgs);
    String serialized;
    try {
      serialized = objectMapper.writeValueAsString(validated);
    } catch (JsonProcessingException e) {
      throw new BusinessException("Failed to serialize yt-dlp args");
    }

    systemConfigService.updateYtDlpArgs(serialized);
    return serialized;
  }

  public OpmlExportFile exportSubscriptionsOpml(ExportFeedsOpmlRequest request) {
    List<ExportFeedsOpmlRequest.FeedSelection> selectedFeeds = request == null ? null : request.getFeeds();
    if (CollectionUtils.isEmpty(selectedFeeds)) {
      throw new BusinessException("No feeds selected");
    }

    List<OpmlOutline> outlines = new ArrayList<>();
    String apiKey = getApiKey();
    String baseUrl = appBaseUrlResolver.requireBaseUrl();
    for (ExportFeedsOpmlRequest.FeedSelection selection : selectedFeeds) {
      if (selection == null || !StringUtils.hasText(selection.getId())
          || !StringUtils.hasText(selection.getType())) {
        continue;
      }
      FeedType feedType = parseFeedType(selection.getType());
      String feedId = selection.getId().trim();
      if (feedType == FeedType.CHANNEL) {
        Channel channel = channelMapper.selectById(feedId);
        if (channel == null) {
          continue;
        }
        outlines.add(
            OpmlOutline.builder()
                .title(resolveFeedTitle(channel.getCustomTitle(), channel.getTitle(), feedId))
                .xmlUrl(buildRssUrl(feedType, feedId, baseUrl, apiKey))
                .htmlUrl(Youtube.CHANNEL_URL + feedId)
                .category("youtube/channel")
                .build());
      } else if (feedType == FeedType.PLAYLIST) {
        Playlist playlist = playlistMapper.selectById(feedId);
        if (playlist == null) {
          continue;
        }
        outlines.add(
            OpmlOutline.builder()
                .title(resolveFeedTitle(playlist.getCustomTitle(), playlist.getTitle(), feedId))
                .xmlUrl(buildRssUrl(feedType, feedId, baseUrl, apiKey))
                .htmlUrl(Youtube.PLAYLIST_URL + feedId)
                .category("youtube/playlist")
                .build());
      }
    }

    if (outlines.isEmpty()) {
      throw new BusinessException("No valid feeds selected");
    }

    User currentUser = getCurrentUser();
    String ownerName = currentUser != null ? currentUser.getUsername() : "";
    String content = buildOpmlDocument(outlines, ownerName);
    String fileName = "pigeonpod-subscriptions-"
        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".opml";
    return OpmlExportFile.builder()
        .fileName(fileName)
        .content(content)
        .build();
  }

  public SystemConfig getSystemConfig() {
    return sanitizeSystemConfig(systemConfigService.getCurrentConfig());
  }

  public SystemConfig updateSystemConfig(SystemConfig incoming) {
    SystemConfig current = systemConfigService.getCurrentConfig();
    SystemConfig candidate = systemConfigService.buildCandidate(incoming);
    if (current.getStorageType() != candidate.getStorageType()) {
      ensureNoDownloadingTasksForStorageSwitch();
    }
    SystemConfig updated = systemConfigService.updateSystemConfig(incoming);
    runtimeConfigApplier.apply(updated);
    return sanitizeSystemConfig(updated);
  }

  public StorageSwitchCheckResponse checkStorageSwitchAllowed(StorageType targetType) {
    if (targetType == null) {
      return StorageSwitchCheckResponse.builder()
          .canSwitch(false)
          .downloadingCount(0L)
          .message("target storage type is required")
          .build();
    }

    StorageType currentType = systemConfigService.getCurrentConfig().getStorageType();
    if (currentType == targetType) {
      return StorageSwitchCheckResponse.builder()
          .canSwitch(true)
          .downloadingCount(0L)
          .message(null)
          .build();
    }

    Long downloadingCount = countDownloadingTasks();
    if (downloadingCount != null && downloadingCount > 0) {
      return StorageSwitchCheckResponse.builder()
          .canSwitch(false)
          .downloadingCount(downloadingCount)
          .message(messageSource.getMessage("system.storage.switch.blocked.downloading", null,
              LocaleContextHolder.getLocale()))
          .build();
    }

    return StorageSwitchCheckResponse.builder()
        .canSwitch(true)
        .downloadingCount(0L)
        .message(null)
        .build();
  }

  public void testSystemStorageConfig(SystemConfig incoming) {
    SystemConfig candidate = systemConfigService.buildCandidate(incoming);
    try {
      if (candidate.getStorageType() == StorageType.S3) {
        s3StorageService.testConnection(candidate);
        return;
      }

      testLocalDirectoryWritable(candidate.getStorageTempDir(), "temp-dir");
      testLocalDirectoryWritable(candidate.getLocalAudioPath(), "audio-path");
      testLocalDirectoryWritable(candidate.getLocalVideoPath(), "video-path");
      testLocalDirectoryWritable(candidate.getLocalCoverPath(), "cover-path");
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(resolveStorageTestErrorMessage(candidate, e));
    }
  }

  private void testLocalDirectoryWritable(String rawPath, String fieldName) {
    if (!StringUtils.hasText(rawPath)) {
      throw new BusinessException(fieldName + " is required");
    }
    try {
      Path directory = Path.of(rawPath);
      Files.createDirectories(directory);
      Path probe = directory.resolve(".pigeonpod-write-test-" + System.currentTimeMillis());
      Files.writeString(probe, "ok", StandardCharsets.UTF_8);
      Files.deleteIfExists(probe);
    } catch (Exception e) {
      throw new BusinessException("local storage path is not writable: " + rawPath);
    }
  }

  private void ensureNoDownloadingTasksForStorageSwitch() {
    Long downloadingCount = countDownloadingTasks();
    if (downloadingCount != null && downloadingCount > 0) {
      throw new BusinessException(messageSource.getMessage(
          "system.storage.switch.blocked.downloading", null, LocaleContextHolder.getLocale()));
    }
  }

  private Long countDownloadingTasks() {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getDownloadStatus, EpisodeStatus.DOWNLOADING.name());
    return episodeMapper.selectCount(queryWrapper);
  }

  private String resolveStorageTestErrorMessage(SystemConfig config, Exception exception) {
    String message = exception == null ? null : exception.getMessage();
    String lower = message == null ? "" : message.toLowerCase();

    if (lower.contains("access denied")
        || lower.contains("invalidaccesskeyid")
        || lower.contains("signaturedoesnotmatch")) {
      return localize("system.storage.test.s3.access.denied");
    }
    if (lower.contains("timeout")) {
      return localize("system.storage.test.s3.timeout");
    }
    if (lower.contains("unknownhost")
        || lower.contains("name or service not known")
        || lower.contains("failed to connect")
        || lower.contains("connection refused")) {
      return localize("system.storage.test.s3.endpoint.unreachable");
    }

    if (config != null && config.getStorageType() == StorageType.S3) {
      if (StringUtils.hasText(message)) {
        return localize("system.storage.test.s3.failed.with.reason", message);
      }
      return localize("system.storage.test.s3.failed");
    }

    if (StringUtils.hasText(message)) {
      return localize("system.storage.test.local.failed.with.reason", message);
    }
    return localize("system.storage.test.local.failed");
  }

  private String localize(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  private SystemConfig sanitizeSystemConfig(SystemConfig config) {
    if (config == null) {
      return null;
    }
    config.setHasCookie(StringUtils.hasText(config.getCookiesContent()));
    config.setCookiesContent(null);
    config.setHasS3SecretKey(StringUtils.hasText(config.getS3SecretKey()));
    config.setS3SecretKey(null);
    return config;
  }

  private FeedType parseFeedType(String rawType) {
    try {
      return FeedType.valueOf(rawType.trim().toUpperCase());
    } catch (Exception e) {
      throw new BusinessException("Invalid feed type: " + rawType);
    }
  }

  private String buildRssUrl(FeedType feedType, String feedId, String baseUrl, String apiKey) {
    if (feedType == FeedType.PLAYLIST) {
      return baseUrl + "/api/rss/playlist/" + feedId + ".xml?apikey=" + apiKey;
    }
    return baseUrl + "/api/rss/" + feedId + ".xml?apikey=" + apiKey;
  }

  private String resolveFeedTitle(String customTitle, String title, String fallbackId) {
    if (StringUtils.hasText(customTitle)) {
      return customTitle.trim();
    }
    if (StringUtils.hasText(title)) {
      return title.trim();
    }
    return fallbackId;
  }

  private String buildOpmlDocument(List<OpmlOutline> outlines, String ownerName) {
    String now = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));

    Element root = new Element("opml").setAttribute("version", "2.0");
    Document document = new Document(root);

    Element head = new Element("head");
    head.addContent(new Element("title").setText("PigeonPod Subscriptions"));
    head.addContent(new Element("dateCreated").setText(now));
    head.addContent(new Element("dateModified").setText(now));
    if (StringUtils.hasText(ownerName)) {
      head.addContent(new Element("ownerName").setText(ownerName));
    }
    head.addContent(new Element("docs").setText("https://2005.opml.org/spec2.html"));
    root.addContent(head);

    Element body = new Element("body");
    for (OpmlOutline outline : outlines) {
      Element outlineElement = new Element("outline");
      outlineElement.setAttribute("text", outline.getTitle());
      outlineElement.setAttribute("title", outline.getTitle());
      outlineElement.setAttribute("type", "rss");
      outlineElement.setAttribute("xmlUrl", outline.getXmlUrl());
      outlineElement.setAttribute("htmlUrl", outline.getHtmlUrl());
      outlineElement.setAttribute("category", outline.getCategory());
      body.addContent(outlineElement);
    }
    root.addContent(body);

    Format format = Format.getPrettyFormat();
    format.setEncoding(StandardCharsets.UTF_8.name());
    return new XMLOutputter(format).outputString(document);
  }

  @Data
  @Builder
  public static class OpmlExportFile {
    private String fileName;
    private String content;
  }

  @Data
  @Builder
  private static class OpmlOutline {
    private String title;
    private String xmlUrl;
    private String htmlUrl;
    private String category;
  }

}
