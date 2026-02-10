package top.asimov.pigeon.service;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.dev33.satoken.apikey.template.SaApiKeyUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.config.YoutubeApiKeyHolder;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.mapper.UserMapper;
import top.asimov.pigeon.model.constant.Youtube;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.model.enums.FeedType;
import top.asimov.pigeon.model.request.ExportFeedsOpmlRequest;
import top.asimov.pigeon.util.PasswordUtil;
import top.asimov.pigeon.util.YtDlpArgsValidator;

@Service
@Transactional
public class AccountService {

  @Value("${pigeon.base-url}")
  private String appBaseUrl;

  private final UserMapper userMapper;
  private final ChannelMapper channelMapper;
  private final PlaylistMapper playlistMapper;
  private final MessageSource messageSource;
  private final ObjectMapper objectMapper;

  public AccountService(UserMapper userMapper, ChannelMapper channelMapper,
      PlaylistMapper playlistMapper, MessageSource messageSource, ObjectMapper objectMapper) {
    this.userMapper = userMapper;
    this.channelMapper = channelMapper;
    this.playlistMapper = playlistMapper;
    this.messageSource = messageSource;
    this.objectMapper = objectMapper;
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
   * 更新用户的 YouTube API Key
   *
   * @param userId        用户ID
   * @param youtubeApiKey YouTube API Key
   * @return 更新后的 YouTube API Key
   */
  public String updateYoutubeApiKey(String userId, String youtubeApiKey) {
    User user = userMapper.selectById(userId);
    if (ObjectUtils.isEmpty(user)) {
      throw new BusinessException(
          messageSource.getMessage("user.not.found", null, LocaleContextHolder.getLocale()));
    }
    user.setYoutubeApiKey(youtubeApiKey);
    user.setUpdatedAt(LocalDateTime.now());
    userMapper.updateById(user);
    YoutubeApiKeyHolder.updateYoutubeApiKey(youtubeApiKey);
    return user.getYoutubeApiKey();
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
      user.setCookiesContent(cookiesContent);
      userMapper.updateById(user);
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
      user.setCookiesContent("");
      userMapper.updateById(user);
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
    return userMapper.selectById(loginId);
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
    boolean target = Boolean.TRUE.equals(enabled);
    user.setLoginCaptchaEnabled(target);
    user.setUpdatedAt(LocalDateTime.now());
    userMapper.updateById(user);
    return target;
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

    user.setYtDlpArgs(serialized);
    user.setUpdatedAt(LocalDateTime.now());
    userMapper.updateById(user);
    return serialized;
  }

  public OpmlExportFile exportSubscriptionsOpml(ExportFeedsOpmlRequest request) {
    List<ExportFeedsOpmlRequest.FeedSelection> selectedFeeds = request == null ? null : request.getFeeds();
    if (CollectionUtils.isEmpty(selectedFeeds)) {
      throw new BusinessException("No feeds selected");
    }

    List<OpmlOutline> outlines = new ArrayList<>();
    String apiKey = getApiKey();
    String baseUrl = normalizeBaseUrl(appBaseUrl);
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

  private FeedType parseFeedType(String rawType) {
    try {
      return FeedType.valueOf(rawType.trim().toUpperCase());
    } catch (Exception e) {
      throw new BusinessException("Invalid feed type: " + rawType);
    }
  }

  private String normalizeBaseUrl(String baseUrl) {
    if (!StringUtils.hasText(baseUrl)) {
      throw new BusinessException("Invalid base URL configuration");
    }
    String normalized = baseUrl.trim();
    if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
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
