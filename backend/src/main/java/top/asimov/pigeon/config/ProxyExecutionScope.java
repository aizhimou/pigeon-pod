package top.asimov.pigeon.config;

import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;

@Component
public class ProxyExecutionScope {

  private static final ThreadLocal<OutboundProxyHolder.OutboundProxySettings> CURRENT =
      new ThreadLocal<>();

  private final OutboundProxyHolder proxyHolder;

  public ProxyExecutionScope(OutboundProxyHolder proxyHolder) {
    this.proxyHolder = proxyHolder;
  }

  public static OutboundProxyHolder.OutboundProxySettings current() {
    return CURRENT.get();
  }

  public <T> T callWithCurrentProxy(Callable<T> callable) throws Exception {
    return callWithProxy(proxyHolder.current(), callable);
  }

  public <T> T callWithProxy(OutboundProxyHolder.OutboundProxySettings settings, Callable<T> callable)
      throws Exception {
    OutboundProxyHolder.OutboundProxySettings previous = CURRENT.get();
    try {
      CURRENT.set(settings);
      return callable.call();
    } finally {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }
}
