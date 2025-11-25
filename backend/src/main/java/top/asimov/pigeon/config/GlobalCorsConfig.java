package top.asimov.pigeon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class GlobalCorsConfig {

  @Bean
  public CorsFilter corsFilter() {
    CorsConfiguration config = new CorsConfiguration();

    // 1. 允许任何域名使用
    config.addAllowedOrigin("*");

    // 2. 允许任何头
    config.addAllowedHeader("*");

    // 3. 允许任何方法 (GET, POST, OPTIONS...)
    config.addAllowedMethod("*");

    // 4. 允许携带凭证 (可选，播客一般不需要，设为 false 也没事)
    config.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    // 对 /media 路径生效
    source.registerCorsConfiguration("/media/**", config);

    return new CorsFilter(source);
  }
}
