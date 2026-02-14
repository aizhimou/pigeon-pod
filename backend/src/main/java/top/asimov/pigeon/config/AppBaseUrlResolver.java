package top.asimov.pigeon.config;

import org.springframework.stereotype.Component;
import top.asimov.pigeon.service.SystemConfigService;

@Component
public class AppBaseUrlResolver {

  private final SystemConfigService systemConfigService;

  public AppBaseUrlResolver(SystemConfigService systemConfigService) {
    this.systemConfigService = systemConfigService;
  }

  public String requireBaseUrl() {
    return systemConfigService.requireBaseUrl();
  }
}
