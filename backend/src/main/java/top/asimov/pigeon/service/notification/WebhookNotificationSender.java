package top.asimov.pigeon.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import top.asimov.pigeon.model.entity.NotificationConfig;

@Log4j2
@Service
public class WebhookNotificationSender implements NotificationSender {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public WebhookNotificationSender(ObjectMapper objectMapper) {
    this.restClient = RestClient.builder().build();
    this.objectMapper = objectMapper;
  }

  @Override
  public String channel() {
    return "WEBHOOK";
  }

  @Override
  public boolean isEnabled(NotificationConfig config) {
    return config != null && Boolean.TRUE.equals(config.getWebhookEnabled());
  }

  @Override
  public void send(NotificationMessage message, NotificationConfig config) {
    Map<String, String> headers = NotificationTemplateHelper.parseHeaders(
        config.getWebhookCustomHeaders(), message.templateVariables());

    String customJsonBody = NotificationTemplateHelper.renderJsonBody(
        config.getWebhookJsonBody(), message.templateVariables(), objectMapper);

    RestClient.RequestBodySpec request = restClient.post()
        .uri(config.getWebhookUrl())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(httpHeaders -> applyHeaders(httpHeaders, headers));

    if (StringUtils.hasText(customJsonBody)) {
      request.body(customJsonBody);
    } else {
      request.body(message.webhookPayload());
    }

    request.retrieve().toBodilessEntity();
    log.info("Notification webhook delivered to {}", config.getWebhookUrl());
  }

  private void applyHeaders(HttpHeaders target, Map<String, String> headers) {
    headers.forEach((name, value) -> {
      if (!HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
        target.set(name, value);
      }
    });
  }
}
