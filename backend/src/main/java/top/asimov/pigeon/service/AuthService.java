package top.asimov.pigeon.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.config.AuthProperties;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.UserMapper;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.model.request.LoginRequest;
import top.asimov.pigeon.model.response.AuthStatusResponse;
import top.asimov.pigeon.util.PasswordUtil;

@Service
public class AuthService {

  private static final String DEFAULT_USER_ID = "0";

  private final UserMapper userMapper;
  private final MessageSource messageSource;
  private final CaptchaService captchaService;
  private final SystemConfigService systemConfigService;
  private final AuthProperties authProperties;

  public AuthService(UserMapper userMapper, MessageSource messageSource,
      CaptchaService captchaService, SystemConfigService systemConfigService,
      AuthProperties authProperties) {
    this.userMapper = userMapper;
    this.messageSource = messageSource;
    this.captchaService = captchaService;
    this.systemConfigService = systemConfigService;
    this.authProperties = authProperties;
  }

  public User login(LoginRequest request) {
    if (!isAuthEnabled()) {
      User user = getDefaultUser();
      StpUtil.login(user.getId());
      return user;
    }

    boolean captchaEnabled = isLoginCaptchaEnabled();
    if (captchaEnabled) {
      if (!org.springframework.util.StringUtils.hasText(request.getCaptchaId())
          || !org.springframework.util.StringUtils.hasText(request.getCaptchaCode())) {
        throw new BusinessException(
            messageSource.getMessage("captcha.required", null, LocaleContextHolder.getLocale()));
      }
      boolean valid = captchaService.validateCaptcha(request.getCaptchaId(),
          request.getCaptchaCode());
      if (!valid) {
        throw new BusinessException(
            messageSource.getMessage("captcha.invalid", null, LocaleContextHolder.getLocale()));
      }
    }

    User user = checkUserCredentials(request.getUsername(), request.getPassword());
    StpUtil.login(user.getId());
    return sanitizeUser(user);
  }

  public AuthStatusResponse getAuthStatus() {
    User user = null;
    if (!isAuthEnabled()) {
      user = getDefaultUser();
    } else if (StpUtil.isLogin()) {
      user = sanitizeUser(getUserById(StpUtil.getLoginIdAsString()));
    }
    return new AuthStatusResponse(isAuthEnabled(), isLoginCaptchaEnabled(), user);
  }

  public boolean isAuthEnabled() {
    return authProperties.isEnabled();
  }

  public boolean isLoginCaptchaEnabled() {
    if (!isAuthEnabled()) {
      return false;
    }
    return Boolean.TRUE.equals(systemConfigService.isLoginCaptchaEnabled());
  }

  private User checkUserCredentials(String username, String password) {
    QueryChainWrapper<User> query = new QueryChainWrapper<>(userMapper);
    query.eq("username", username);
    User existUser = query.one();
    if (ObjectUtils.isEmpty(existUser)) {
      throw new BusinessException(
          messageSource.getMessage("user.not.found", null, LocaleContextHolder.getLocale()));
    }

    boolean verified = PasswordUtil.verifyPassword(password, existUser.getSalt(),
        existUser.getPassword());
    if (!verified) {
      throw new BusinessException(
          messageSource.getMessage("user.invalid.password", null, LocaleContextHolder.getLocale()));
    }
    return existUser;
  }

  private User getDefaultUser() {
    User user = getUserById(DEFAULT_USER_ID);
    return sanitizeUser(user);
  }

  private User getUserById(String userId) {
    User user = userMapper.selectById(userId);
    if (ObjectUtils.isEmpty(user)) {
      throw new BusinessException(
          messageSource.getMessage("user.not.found", null, LocaleContextHolder.getLocale()));
    }
    return user;
  }

  private User sanitizeUser(User user) {
    if (user == null) {
      return null;
    }
    return User.builder()
        .id(user.getId())
        .username(user.getUsername())
        .apiKey(user.getApiKey())
        .dateFormat(user.getDateFormat())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
  }

}
