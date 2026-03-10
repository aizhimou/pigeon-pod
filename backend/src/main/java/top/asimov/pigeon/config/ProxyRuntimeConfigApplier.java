package top.asimov.pigeon.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.model.entity.SystemConfig;

@Log4j2
@Component
public class ProxyRuntimeConfigApplier {

  private final OutboundProxyHolder proxyHolder;

  public ProxyRuntimeConfigApplier(OutboundProxyHolder proxyHolder) {
    this.proxyHolder = proxyHolder;
  }

  public synchronized void apply(SystemConfig config) {
    proxyHolder.apply(config);
    OutboundProxyHolder.OutboundProxySettings settings = proxyHolder.current();
    log.info("Runtime proxy config applied: enabled={}, type={}, host={}, port={}",
        settings.enabled(), settings.type(), settings.host(), settings.port());
  }
}
