package top.asimov.pigeon.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.entity.NotificationConfig;

@Slf4j
@Service
public class WebhookNotificationSender implements NotificationSender {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public WebhookNotificationSender(ObjectMapper objectMapper) {
    this(RestClient.builder().build(), objectMapper);
  }

  WebhookNotificationSender(RestClient restClient, ObjectMapper objectMapper) {
    this.restClient = restClient;
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

    try {
      request.retrieve().toBodilessEntity();
      log.info("[notification-webhook] webhook delivered");
    } catch (HttpStatusCodeException ex) {
      String responseBody = ex.getResponseBodyAsString();
      String reason = StringUtils.hasText(responseBody) ? responseBody.trim() : ex.getStatusText();
      if (reason.length() > 300) {
        reason = reason.substring(0, 300) + "...";
      }
      log.warn("[notification-webhook] delivery failed: statusCode={} reason={}",
          ex.getStatusCode().value(), reason, ex);
      throw new BusinessException(
          "Webhook delivery failed (" + ex.getStatusCode().value() + "): " + reason);
    } catch (RestClientException ex) {
      log.warn("[notification-webhook] delivery failed: reason={}", ex.getMessage(), ex);
      throw new BusinessException("Webhook delivery failed: " + ex.getMessage());
    }
  }

  private void applyHeaders(HttpHeaders target, Map<String, String> headers) {
    headers.forEach((name, value) -> {
      if (!HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
        target.set(name, value);
      }
    });
  }
}
