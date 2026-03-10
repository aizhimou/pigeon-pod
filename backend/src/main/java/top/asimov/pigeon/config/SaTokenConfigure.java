package top.asimov.pigeon.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

  private final AuthProperties authProperties;
  private final AutoLoginInterceptor autoLoginInterceptor;

  public SaTokenConfigure(AuthProperties authProperties, AutoLoginInterceptor autoLoginInterceptor) {
    this.authProperties = authProperties;
    this.autoLoginInterceptor = autoLoginInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    if (!authProperties.isEnabled()) {
      registry.addInterceptor(autoLoginInterceptor)
          .addPathPatterns("/api/**")
          .excludePathPatterns("/api/auth/**", "/api/rss/**", "/api/public/**");
    }
    registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
  }
}
