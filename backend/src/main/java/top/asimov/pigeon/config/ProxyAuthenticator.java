package top.asimov.pigeon.config;

import jakarta.annotation.PostConstruct;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.enums.ProxyType;

@Log4j2
@Component
public class ProxyAuthenticator {

  private static final String TUNNELING_DISABLED_SCHEMES = "jdk.http.auth.tunneling.disabledSchemes";
  private static final String PROXYING_DISABLED_SCHEMES = "jdk.http.auth.proxying.disabledSchemes";

  private final OutboundProxyHolder proxyHolder;

  public ProxyAuthenticator(OutboundProxyHolder proxyHolder) {
    this.proxyHolder = proxyHolder;
  }

  @PostConstruct
  public void init() {
    enableHttpProxyBasicAuthentication();
    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        if (!isProxyAuthenticationRequest()) {
          return null;
        }
        OutboundProxyHolder.OutboundProxySettings settings = ProxyExecutionScope.current();
        if (settings == null || !settings.enabled()) {
          settings = proxyHolder.current();
        }
        if (settings == null || !settings.enabled() || !settings.hasAuthentication()) {
          return null;
        }
        if (settings.type() != ProxyType.SOCKS5
            && !matchesConfiguredProxy(settings)) {
          return null;
        }
        log.debug(
            "Providing proxy credentials: type={}, protocol={}, requestorType={}, host={}, port={}",
            settings.type(), getRequestingProtocol(), getRequestorType(), getRequestingHost(),
            getRequestingPort());
        char[] password = settings.password() == null ? new char[0] : settings.password().toCharArray();
        return new PasswordAuthentication(settings.username(), password);
      }

      private boolean isProxyAuthenticationRequest() {
        if (getRequestorType() == RequestorType.PROXY) {
          return true;
        }
        return StringUtils.hasText(getRequestingProtocol())
            && getRequestingProtocol().toUpperCase().startsWith("SOCKS");
      }

      private boolean matchesConfiguredProxy(OutboundProxyHolder.OutboundProxySettings settings) {
        if (!StringUtils.hasText(getRequestingHost()) || settings.host() == null
            || !settings.host().equalsIgnoreCase(getRequestingHost())) {
          return false;
        }
        return settings.port() == null || getRequestingPort() <= 0
            || settings.port().equals(getRequestingPort());
      }
    });
  }

  private void enableHttpProxyBasicAuthentication() {
    relaxDisabledSchemeProperty(TUNNELING_DISABLED_SCHEMES);
    relaxDisabledSchemeProperty(PROXYING_DISABLED_SCHEMES);
  }

  private void relaxDisabledSchemeProperty(String propertyName) {
    String currentValue = System.getProperty(propertyName);
    String updatedValue = "";
    if (StringUtils.hasText(currentValue)) {
      updatedValue = java.util.Arrays.stream(currentValue.split(","))
          .map(String::trim)
          .filter(StringUtils::hasText)
          .filter(value -> !"basic".equalsIgnoreCase(value))
          .reduce((left, right) -> left + "," + right)
          .orElse("");
    }
    if (java.util.Objects.equals(currentValue, updatedValue)) {
      return;
    }
    System.setProperty(propertyName, updatedValue);
    log.info("Updated JVM networking property {} from [{}] to [{}] to allow HTTP proxy Basic auth",
        propertyName, currentValue, updatedValue);
  }
}
