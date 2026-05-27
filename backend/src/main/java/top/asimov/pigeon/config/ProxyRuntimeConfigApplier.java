package top.asimov.pigeon.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.model.entity.SystemConfig;

@Slf4j
@Component
public class ProxyRuntimeConfigApplier {

  private final OutboundProxyHolder proxyHolder;

  public ProxyRuntimeConfigApplier(OutboundProxyHolder proxyHolder) {
    this.proxyHolder = proxyHolder;
  }

  public synchronized void apply(SystemConfig config) {
    proxyHolder.apply(config);
    OutboundProxyHolder.OutboundProxySettings settings = proxyHolder.current();
    log.info("[config] runtime proxy config applied: enabled={} type={} host={} port={}",
        settings.enabled(), settings.type(), settings.host(), settings.port());
  }
}
