package top.asimov.pigeon.helper;

import com.google.api.client.googleapis.GoogleUtils;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.config.OutboundProxyHolder;
import top.asimov.pigeon.config.ProxyExecutionScope;

@Log4j2
@Component
public class YoutubeServiceFactory {

  private static final String APPLICATION_NAME = "My YouTube App";
  private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  private final OutboundProxyHolder proxyHolder;

  public YoutubeServiceFactory(OutboundProxyHolder proxyHolder) {
    this.proxyHolder = proxyHolder;
  }

  public YouTube createCurrentClient() {
    OutboundProxyHolder.OutboundProxySettings settings = ProxyExecutionScope.current();
    if (settings == null) {
      settings = proxyHolder.current();
    }
    return createClient(settings);
  }

  public YouTube createClient(OutboundProxyHolder.OutboundProxySettings settings) {
    try {
      NetHttpTransport transport = buildTransport(settings);
      return new YouTube.Builder(transport, JSON_FACTORY, null)
          .setApplicationName(APPLICATION_NAME)
          .build();
    } catch (GeneralSecurityException | IOException e) {
      log.error("Failed to initialize YouTube service", e);
      throw new RuntimeException("Failed to initialize YouTube service", e);
    }
  }

  private NetHttpTransport buildTransport(OutboundProxyHolder.OutboundProxySettings settings)
      throws GeneralSecurityException, IOException {
    if (settings == null || !settings.enabled()) {
      return GoogleNetHttpTransport.newTrustedTransport();
    }
    return new NetHttpTransport.Builder()
        .trustCertificates(GoogleUtils.getCertificateTrustStore())
        .setProxy(settings.toJavaNetProxy())
        .build();
  }
}
