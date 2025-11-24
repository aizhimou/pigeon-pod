package top.asimov.pigeon.service;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.dev33.satoken.apikey.template.SaApiKeyUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.config.YoutubeApiKeyHolder;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.UserMapper;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.util.PasswordUtil;

@Service
@Transactional
public class AccountService {

  private final UserMapper userMapper;
  private final MessageSource messageSource;

  public AccountService(UserMapper userMapper, MessageSource messageSource) {
    this.userMapper = userMapper;
    this.messageSource = messageSource;
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

}
