package top.asimov.pigeon.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.entity.SystemConfig;
import top.asimov.pigeon.service.SystemConfigService;

@Slf4j
@Component
public class ServerCustomizer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

  private final SystemConfigService systemConfigService;
  private final Environment environment;

  public ServerCustomizer(SystemConfigService systemConfigService, Environment environment) {
    this.systemConfigService = systemConfigService;
    this.environment = environment;
  }

  @Override
  public void customize(ConfigurableServletWebServerFactory factory) {
    SystemConfig config;
    try {
      config = systemConfigService.getCurrentConfig();
    } catch (Exception e) {
      log.warn("[config] failed to load system config for server customization, skipping: {}", e.getMessage());
      return;
    }

    if (config == null) {
      return;
    }

    if (Boolean.TRUE.equals(config.getSslEnabled())) {
      if (StringUtils.hasText(config.getSslCertificatePath()) && StringUtils.hasText(config.getSslKeyPath())) {
        log.info("[config] HTTPS enabled on port {}", config.getSslPort());
        factory.setPort(config.getSslPort());

        Ssl ssl = new Ssl();
        ssl.setEnabled(true);
        ssl.setCertificate(config.getSslCertificatePath());
        ssl.setCertificatePrivateKey(config.getSslKeyPath());
        factory.setSsl(ssl);

        if (!Boolean.TRUE.equals(config.getHttpsOnly()) && factory instanceof TomcatServletWebServerFactory tomcatFactory) {
          Integer httpPort = environment.getProperty("server.port", Integer.class, 8080);
          log.info("[config] HTTP listener enabled on port {} (dual mode)", httpPort);
          Connector httpConnector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
          httpConnector.setPort(httpPort);
          tomcatFactory.addAdditionalTomcatConnectors(httpConnector);
        }
      } else {
        log.warn("[config] HTTPS enabled but certificate or key path is missing, falling back to HTTP");
      }
    }
  }
}
