package top.asimov.pigeon.service.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;

public final class NotificationTemplateHelper {

  private static final String[] SUPPORTED_TOKENS = {
      "{title}", "{content}", "{generatedAt}", "{baseUrl}", "{total}"
  };

  private NotificationTemplateHelper() {
  }

  public static String renderText(String template, Map<String, String> variables) {
    if (!StringUtils.hasText(template)) {
      return template;
    }
    String rendered = template;
    for (String token : SUPPORTED_TOKENS) {
      String key = token.substring(1, token.length() - 1);
      String value = variables == null ? null : variables.get(key);
      rendered = rendered.replace(token, value == null ? "" : value);
    }
    return rendered;
  }

  public static Map<String, String> parseHeaders(String rawHeaders, Map<String, String> variables) {
    Map<String, String> headers = new LinkedHashMap<>();
    if (!StringUtils.hasText(rawHeaders)) {
      return headers;
    }

    String[] lines = rawHeaders.replace("\r\n", "\n").split("\n");
    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (line.isEmpty()) {
        continue;
      }
      int separatorIndex = line.indexOf(':');
      if (separatorIndex <= 0) {
        throw new BusinessException("notification webhook header must use 'Key: Value' format");
      }
      String name = line.substring(0, separatorIndex).trim();
      String value = line.substring(separatorIndex + 1).trim();
      if (!StringUtils.hasText(name)) {
        throw new BusinessException("notification webhook header name is required");
      }
      headers.put(name, renderText(value, variables));
    }
    return headers;
  }

  public static String renderJsonBody(String template, Map<String, String> variables,
      ObjectMapper objectMapper) {
    if (!StringUtils.hasText(template)) {
      return null;
    }
    try {
      JsonNode root = objectMapper.readTree(template);
      JsonNode rendered = renderJsonNode(root, variables);
      return objectMapper.writeValueAsString(rendered);
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BusinessException("notification webhook json body is invalid");
    }
  }

  private static JsonNode renderJsonNode(JsonNode node, Map<String, String> variables) {
    if (node == null || node.isNull()) {
      return node;
    }
    if (node.isObject()) {
      ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
      node.fields().forEachRemaining(entry ->
          objectNode.set(entry.getKey(), renderJsonNode(entry.getValue(), variables)));
      return objectNode;
    }
    if (node.isArray()) {
      ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
      node.forEach(child -> arrayNode.add(renderJsonNode(child, variables)));
      return arrayNode;
    }
    if (node.isTextual()) {
      return TextNode.valueOf(renderText(node.asText(), variables));
    }
    return node;
  }
}
