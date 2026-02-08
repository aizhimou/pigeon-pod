package top.asimov.pigeon.service;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.dev33.satoken.apikey.template.SaApiKeyUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.config.YoutubeApiKeyHolder;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.mapper.UserMapper;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.util.PasswordUtil;
import top.asimov.pigeon.util.YtDlpArgsValidator;

@Service
@Transactional
public class AccountService {

  private final UserMapper userMapper;
  private final ChannelMapper channelMapper;
  private final PlaylistMapper playlistMapper;
  private final MessageSource messageSource;
  private final ObjectMapper objectMapper;
  private static final String APPLY_MODE_OVERRIDE_ALL = "override_all";
  private static final String APPLY_MODE_FILL_EMPTY = "fill_empty";

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
   * 更新用户的默认历史节目保留上限（用于新建 feed 的默认值）
   *
   * @param userId 用户ID
   * @param defaultMaximumEpisodes 默认保留上限，null 表示不限制
   * @return 规范化后的默认值
   */
  public Integer updateDefaultMaximumEpisodes(String userId, Integer defaultMaximumEpisodes) {
    User user = userMapper.selectById(userId);
    if (ObjectUtils.isEmpty(user)) {
      throw new BusinessException(
          messageSource.getMessage("user.not.found", null, LocaleContextHolder.getLocale()));
    }
    Integer normalized = defaultMaximumEpisodes;
    if (normalized != null && normalized <= 0) {
      normalized = null;
    }
    user.setDefaultMaximumEpisodes(normalized);
    user.setUpdatedAt(LocalDateTime.now());
    userMapper.updateById(user);
    return user.getDefaultMaximumEpisodes();
  }

  /**
   * 获取当前登录用户。
   */
  public User getCurrentUser() {
    String loginId = (String) StpUtil.getLoginId();
    return userMapper.selectById(loginId);
  }

  /**
   * 将默认 maximumEpisodes 一键应用到所有订阅源。
   *
   * @param userId 用户ID
   * @param mode   应用模式：override_all 或 fill_empty
   * @return 更新统计
   */
  public Map<String, Object> applyDefaultMaximumEpisodesToFeeds(String userId, String mode) {
    User user = userMapper.selectById(userId);
    if (ObjectUtils.isEmpty(user)) {
      throw new BusinessException(
          messageSource.getMessage("user.not.found", null, LocaleContextHolder.getLocale()));
    }

    String normalizedMode = normalizeApplyMode(mode);
    Integer targetValue = user.getDefaultMaximumEpisodes();

    int updatedChannels = updateChannelMaximumEpisodes(targetValue, normalizedMode);
    int updatedPlaylists = updatePlaylistMaximumEpisodes(targetValue, normalizedMode);

    Map<String, Object> result = new HashMap<>();
    result.put("mode", normalizedMode);
    result.put("updatedChannels", updatedChannels);
    result.put("updatedPlaylists", updatedPlaylists);
    result.put("updatedFeeds", updatedChannels + updatedPlaylists);
    result.put("defaultMaximumEpisodes", targetValue);
    return result;
  }

  private String normalizeApplyMode(String mode) {
    String normalized = mode == null ? "" : mode.trim().toLowerCase();
    if (APPLY_MODE_OVERRIDE_ALL.equals(normalized) || APPLY_MODE_FILL_EMPTY.equals(normalized)) {
      return normalized;
    }
    throw new BusinessException("Invalid apply mode");
  }

  private int updateChannelMaximumEpisodes(Integer targetValue, String mode) {
    LambdaUpdateWrapper<Channel> updateWrapper = new LambdaUpdateWrapper<>();
    if (APPLY_MODE_FILL_EMPTY.equals(mode)) {
      updateWrapper.and(
          w -> w.isNull(Channel::getMaximumEpisodes).or().le(Channel::getMaximumEpisodes, 0));
    }
    updateWrapper.set(Channel::getMaximumEpisodes, targetValue);
    return channelMapper.update(null, updateWrapper);
  }

  private int updatePlaylistMaximumEpisodes(Integer targetValue, String mode) {
    LambdaUpdateWrapper<Playlist> updateWrapper = new LambdaUpdateWrapper<>();
    if (APPLY_MODE_FILL_EMPTY.equals(mode)) {
      updateWrapper.and(
          w -> w.isNull(Playlist::getMaximumEpisodes).or().le(Playlist::getMaximumEpisodes, 0));
    }
    updateWrapper.set(Playlist::getMaximumEpisodes, targetValue);
    return playlistMapper.update(null, updateWrapper);
  }

  /**
   * 更新用户的字幕配置
   * 
   * @param userId 用户ID
   * @param subtitleLanguages 字幕语言（逗号分隔）
   * @param subtitleFormat 字幕格式
   */
  public void updateSubtitleSettings(String userId, String subtitleLanguages, 
                                      String subtitleFormat) {
    User user = userMapper.selectById(userId);
    if (user != null) {
      user.setSubtitleLanguages(subtitleLanguages);
      user.setSubtitleFormat(subtitleFormat);
      user.setUpdatedAt(LocalDateTime.now());
      userMapper.updateById(user);
    }
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

}
