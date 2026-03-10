package top.asimov.pigeon.config;

import jakarta.annotation.PostConstruct;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProxyAuthenticator {

  private final OutboundProxyHolder proxyHolder;

  public ProxyAuthenticator(OutboundProxyHolder proxyHolder) {
    this.proxyHolder = proxyHolder;
  }

  @PostConstruct
  public void init() {
    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        if (getRequestorType() != RequestorType.PROXY) {
          return null;
        }
        OutboundProxyHolder.OutboundProxySettings settings = ProxyExecutionScope.current();
        if (settings == null || !settings.enabled()) {
          settings = proxyHolder.current();
        }
        if (settings == null || !settings.enabled() || !settings.hasAuthentication()) {
          return null;
        }
        if (!StringUtils.hasText(getRequestingHost()) || settings.host() == null
            || !settings.host().equalsIgnoreCase(getRequestingHost())) {
          return null;
        }
        if (settings.port() != null && getRequestingPort() > 0
            && !settings.port().equals(getRequestingPort())) {
          return null;
        }
        char[] password = settings.password() == null ? new char[0] : settings.password().toCharArray();
        return new PasswordAuthentication(settings.username(), password);
      }
    });
  }
}
