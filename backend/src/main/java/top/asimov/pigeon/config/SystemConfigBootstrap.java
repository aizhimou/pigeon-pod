package top.asimov.pigeon.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.entity.SystemConfig;
import top.asimov.pigeon.service.SystemConfigService;

@Log4j2
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SystemConfigBootstrap implements ApplicationRunner {

  private final SystemConfigService systemConfigService;
  private final StorageRuntimeConfigApplier runtimeConfigApplier;
  private final Environment environment;

  public SystemConfigBootstrap(SystemConfigService systemConfigService,
      StorageRuntimeConfigApplier runtimeConfigApplier,
      Environment environment) {
    this.systemConfigService = systemConfigService;
    this.runtimeConfigApplier = runtimeConfigApplier;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    SystemConfig config = systemConfigService.ensureExists();
    boolean changed = systemConfigService.applyLegacyEnvBackfill(
        config,
        env("PIGEON_BASE_URL"),
        env("PIGEON_AUDIO_FILE_PATH"),
        env("PIGEON_VIDEO_FILE_PATH"),
        env("PIGEON_COVER_FILE_PATH")
    );

    if (changed) {
      log.info("Detected legacy environment variables and backfilled into system_config");
      systemConfigService.updateSystemConfig(config);
      config = systemConfigService.getCurrentConfig();
    }

    runtimeConfigApplier.apply(config);
  }

  private String env(String key) {
    String value = environment.getProperty(key);
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
