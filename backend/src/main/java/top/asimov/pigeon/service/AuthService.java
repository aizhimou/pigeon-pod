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
import top.asimov.pigeon.util.PasswordUtil;

@Service
public class AuthService {

  private final UserMapper userMapper;
  private final MessageSource messageSource;

  public AuthService(UserMapper userMapper, MessageSource messageSource) {
    this.userMapper = userMapper;
    this.messageSource = messageSource;
  }

  public User login(String username, String password) {
    User user = checkUserCredentials(username, password);
    StpUtil.login(user.getId());
    // Clear sensitive fields
    user.setPassword(null);
    user.setSalt(null);
    return user;
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
    existUser.setHasCookie(!ObjectUtils.isEmpty(existUser.getCookiesContent()));
    return existUser;
  }

}
