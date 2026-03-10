package top.asimov.pigeon.config;

import java.net.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.entity.SystemConfig;
import top.asimov.pigeon.model.enums.ProxyType;

@Component
public class OutboundProxyHolder {

  private final AtomicReference<OutboundProxySettings> current =
      new AtomicReference<>(OutboundProxySettings.disabled());

  public void apply(SystemConfig config) {
    current.set(from(config));
  }

  public OutboundProxySettings current() {
    return current.get();
  }

  public OutboundProxySettings from(SystemConfig config) {
    if (config == null || !Boolean.TRUE.equals(config.getProxyEnabled())
        || config.getProxyType() == null || !StringUtils.hasText(config.getProxyHost())
        || config.getProxyPort() == null || config.getProxyPort() <= 0) {
      return OutboundProxySettings.disabled();
    }
    return new OutboundProxySettings(
        true,
        config.getProxyType(),
        config.getProxyHost().trim(),
        config.getProxyPort(),
        normalize(config.getProxyUsername()),
        normalize(config.getProxyPassword())
    );
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  public record OutboundProxySettings(
      boolean enabled,
      ProxyType type,
      String host,
      Integer port,
      String username,
      String password
  ) {

    public static OutboundProxySettings disabled() {
      return new OutboundProxySettings(false, null, null, null, null, null);
    }

    public boolean hasAuthentication() {
      return StringUtils.hasText(username);
    }

    public Proxy toJavaNetProxy() {
      if (!enabled || type == null || !StringUtils.hasText(host) || port == null) {
        return Proxy.NO_PROXY;
      }
      Proxy.Type proxyType = type == ProxyType.SOCKS5 ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
      return new Proxy(proxyType, new java.net.InetSocketAddress(host, port));
    }

    public String toYtDlpProxyUrl() {
      if (!enabled || type == null || !StringUtils.hasText(host) || port == null) {
        return null;
      }
      String scheme = type == ProxyType.SOCKS5 ? "socks5" : "http";
      StringBuilder builder = new StringBuilder(scheme).append("://");
      if (hasAuthentication()) {
        builder.append(encode(username));
        if (password != null) {
          builder.append(':').append(encode(password));
        }
        builder.append('@');
      }
      builder.append(formatHost(host)).append(':').append(port);
      return builder.toString();
    }

    public String toMaskedYtDlpProxyUrl() {
      if (!enabled || type == null || !StringUtils.hasText(host) || port == null) {
        return null;
      }
      String scheme = type == ProxyType.SOCKS5 ? "socks5" : "http";
      StringBuilder builder = new StringBuilder(scheme).append("://");
      if (hasAuthentication()) {
        builder.append("***");
        if (password != null) {
          builder.append(":***");
        }
        builder.append('@');
      }
      builder.append(formatHost(host)).append(':').append(port);
      return builder.toString();
    }

    private String formatHost(String rawHost) {
      if (rawHost != null && rawHost.contains(":") && !rawHost.startsWith("[") && !rawHost.endsWith("]")) {
        return "[" + rawHost + "]";
      }
      return rawHost;
    }

    private String encode(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
  }
}
