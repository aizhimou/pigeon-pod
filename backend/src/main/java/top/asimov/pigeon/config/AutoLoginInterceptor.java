package top.asimov.pigeon.config;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AutoLoginInterceptor implements HandlerInterceptor {

  private static final String DEFAULT_USER_ID = "0";

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!StpUtil.isLogin() || !DEFAULT_USER_ID.equals(StpUtil.getLoginIdAsString())) {
      StpUtil.login(DEFAULT_USER_ID);
    }
    return true;
  }
}
