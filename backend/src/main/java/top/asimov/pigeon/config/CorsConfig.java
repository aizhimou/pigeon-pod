package top.asimov.pigeon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 配置
 * 允许 Web 播放器访问媒体和字幕文件
 */
@Configuration
public class CorsConfig {

  @Bean
  public CorsFilter corsFilter() {
    CorsConfiguration config = new CorsConfiguration();
    
    // 允许所有来源访问媒体和字幕资源
    config.addAllowedOriginPattern("*");
    
    // 允许的 HTTP 方法
    config.addAllowedMethod("GET");
    config.addAllowedMethod("HEAD");
    config.addAllowedMethod("OPTIONS");
    
    // 允许的请求头
    config.addAllowedHeader("*");
    
    // 允许发送 Cookie
    config.setAllowCredentials(true);
    
    // CORS 预检请求的缓存时间（秒）
    config.setMaxAge(3600L);
    
    // 暴露的响应头
    config.addExposedHeader("Content-Length");
    config.addExposedHeader("Content-Range");
    config.addExposedHeader("Content-Type");
    config.addExposedHeader("Accept-Ranges");
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    
    // 对 /media/** 路径启用 CORS
    source.registerCorsConfiguration("/media/**", config);
    
    return new CorsFilter(source);
  }
}

