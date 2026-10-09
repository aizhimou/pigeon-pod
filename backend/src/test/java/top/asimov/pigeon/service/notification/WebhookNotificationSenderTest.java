package top.asimov.pigeon.service.notification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.entity.NotificationConfig;

class WebhookNotificationSenderTest {

  private RestClient restClient;
  private RestClient.RequestBodyUriSpec uriSpec;
  private RestClient.RequestBodySpec bodySpec;
  private RestClient.ResponseSpec responseSpec;
  private WebhookNotificationSender sender;
  private NotificationConfig config;

  @BeforeEach
  void setUp() {
    restClient = mock(RestClient.class);
    uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    bodySpec = mock(RestClient.RequestBodySpec.class);
    responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(uriSpec);
    when(uriSpec.uri(anyString())).thenReturn(bodySpec);
    when(bodySpec.contentType(any())).thenReturn(bodySpec);
    when(bodySpec.headers(any())).thenReturn(bodySpec);
    when(bodySpec.body(anyString())).thenReturn(bodySpec);
    when(bodySpec.body(any())).thenReturn(bodySpec);
    when(bodySpec.retrieve()).thenReturn(responseSpec);

    sender = new WebhookNotificationSender(restClient, new ObjectMapper());

    config = new NotificationConfig();
    config.setWebhookEnabled(true);
    config.setWebhookUrl("https://discord.com/api/webhooks/123/abc");
  }

  @Test
  void send_successWhen200() {
    when(responseSpec.toBodilessEntity()).thenReturn(null);

    NotificationMessage message = new NotificationMessage(
        "title", "content", "html", Map.of(), Map.of("content", "hello"));

    assertDoesNotThrow(() -> sender.send(message, config));
  }

  @Test
  void send_throwsBusinessExceptionWhenHttpErrorOccurs() {
    byte[] responseBody = "{\"_misc\": [\"Only dictionaries may be used in a DictType\"]}"
        .getBytes(StandardCharsets.UTF_8);
    HttpClientErrorException badRequest = HttpClientErrorException.create(
        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, responseBody, StandardCharsets.UTF_8);

    when(responseSpec.toBodilessEntity()).thenThrow(badRequest);

    NotificationMessage message = new NotificationMessage(
        "title", "content", "html", Map.of(), Map.of("content", "hello"));

    BusinessException exception = assertThrows(BusinessException.class, () -> sender.send(message, config));
    assertTrue(exception.getMessage().contains("Webhook delivery failed (400)"));
    assertTrue(exception.getMessage().contains("Only dictionaries may be used in a DictType"));
  }

  @Test
  void send_throwsBusinessExceptionWhenNetworkFails() {
    when(responseSpec.toBodilessEntity()).thenThrow(new ResourceAccessException("Connection refused"));

    NotificationMessage message = new NotificationMessage(
        "title", "content", "html", Map.of(), Map.of("content", "hello"));

    BusinessException exception = assertThrows(BusinessException.class, () -> sender.send(message, config));
    assertEquals("Webhook delivery failed: Connection refused", exception.getMessage());
  }
}
