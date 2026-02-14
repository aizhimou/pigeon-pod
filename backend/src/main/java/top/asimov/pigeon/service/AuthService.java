package top.asimov.pigeon.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.UserMapper;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.model.request.LoginRequest;
import top.asimov.pigeon.util.PasswordUtil;

@Service
public class AuthService {

  private final UserMapper userMapper;
  private final MessageSource messageSource;
  private final CaptchaService captchaService;
  private final SystemConfigService systemConfigService;

  public AuthService(UserMapper userMapper, MessageSource messageSource,
      CaptchaService captchaService, SystemConfigService systemConfigService) {
    this.userMapper = userMapper;
    this.messageSource = messageSource;
    this.captchaService = captchaService;
    this.systemConfigService = systemConfigService;
  }

  public User login(LoginRequest request) {
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
    // Clear sensitive fields
    user.setPassword(null);
    user.setSalt(null);
    systemConfigService.fillSystemFields(user);
    return user;
  }

  public boolean isLoginCaptchaEnabled() {
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
    existUser.setHasCookie(org.springframework.util.StringUtils.hasText(
        systemConfigService.getCookiesContent()));
    return existUser;
  }

}
