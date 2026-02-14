package top.asimov.pigeon.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.constant.Bilibili;

@Log4j2
@Component
public class BilibiliApiClient {

  private static final int MAX_RETRIES = 3;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
  private static final String DEFAULT_ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9";
  private static final String DEFAULT_USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public BilibiliApiClient(ObjectMapper objectMapper) {
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    this.objectMapper = objectMapper;
  }

  public JsonNode getData(String path, Map<String, String> queryParams) {
    Map<String, String> params = queryParams == null ? Map.of() : queryParams;
    String url = buildUrl(path, params);
    int attempts = 0;
    while (attempts < MAX_RETRIES) {
      attempts++;
      try {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .GET()
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .header("Accept-Language", DEFAULT_ACCEPT_LANGUAGE)
            .header("Origin", "https://www.bilibili.com")
            .header("Referer", "https://www.bilibili.com/")
            .header("User-Agent", DEFAULT_USER_AGENT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 500 && attempts < MAX_RETRIES) {
          sleepBackoff(attempts);
          continue;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          throw new BusinessException(
              "Bilibili API request failed: HTTP " + response.statusCode() + ", path=" + path);
        }

        JsonNode root = objectMapper.readTree(response.body());
        int code = root.path("code").asInt(-1);
        String message = root.path("message").asText();
        if (code == 0) {
          return root.path("data");
        }
        if (isRetryableCode(code) && attempts < MAX_RETRIES) {
          sleepBackoff(attempts);
          continue;
        }
        throw new BusinessException(
            "Bilibili API request failed: code=" + code + ", message=" + message + ", path=" + path);
      } catch (BusinessException ex) {
        throw ex;
      } catch (IOException | InterruptedException ex) {
        if (ex instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        if (attempts >= MAX_RETRIES) {
          throw new BusinessException("Bilibili API request failed: " + ex.getMessage());
        }
        sleepBackoff(attempts);
      }
    }

    throw new BusinessException("Bilibili API request failed after retries");
  }

  private boolean isRetryableCode(int code) {
    return code == -500 || code == -509;
  }

  private void sleepBackoff(int attempts) {
    long delayMs = Math.min(2000L, 300L * (1L << Math.max(0, attempts - 1)));
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Bilibili API retry sleep interrupted");
    }
  }

  private String buildUrl(String path, Map<String, String> queryParams) {
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    Map<String, String> cleanParams = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : queryParams.entrySet()) {
      if (!StringUtils.hasText(entry.getKey())) {
        continue;
      }
      String value = entry.getValue();
      cleanParams.put(entry.getKey(), value == null ? "" : value.trim());
    }

    StringBuilder builder = new StringBuilder(Bilibili.API_BASE_URL).append(normalizedPath);
    if (cleanParams.isEmpty()) {
      return builder.toString();
    }
    builder.append("?");
    boolean first = true;
    for (Map.Entry<String, String> entry : cleanParams.entrySet()) {
      if (!first) {
        builder.append("&");
      }
      first = false;
      builder
          .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
          .append("=")
          .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }
    return builder.toString();
  }
}
