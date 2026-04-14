package top.asimov.pigeon.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class DownloadFileNamePatternUtil {

  public static final String DEFAULT_PATTERN = "{title}-{id}";

  private static final Set<String> SUPPORTED_VARIABLES =
      Set.of("channel", "title", "id", "date");
  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([^{}]+)}");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

  private DownloadFileNamePatternUtil() {
  }

  public static String normalizePattern(String rawPattern) {
    if (!StringUtils.hasText(rawPattern)) {
      return DEFAULT_PATTERN;
    }
    return rawPattern.trim();
  }

  public static void validatePattern(String rawPattern) {
    String normalized = normalizePattern(rawPattern);

    Matcher matcher = VARIABLE_PATTERN.matcher(normalized);
    while (matcher.find()) {
      String variable = matcher.group(1);
      if (!SUPPORTED_VARIABLES.contains(variable)) {
        throw new IllegalArgumentException("unsupported file name variable: {" + variable + "}");
      }
    }

    String unresolved = matcher.replaceAll("");
    if (unresolved.contains("{") || unresolved.contains("}")) {
      throw new IllegalArgumentException("invalid file name pattern");
    }
  }

  public static String buildBaseName(String rawPattern, String channelName, String title,
      String episodeId, LocalDateTime publishedAt) {
    String normalizedPattern = normalizePattern(rawPattern);
    String rendered = normalizedPattern
        .replace("{channel}", normalizeVariableValue(channelName))
        .replace("{title}", normalizeVariableValue(title))
        .replace("{id}", normalizeVariableValue(episodeId))
        .replace("{date}", resolveDateValue(publishedAt));
    return MediaFileNameUtil.getSafeTitle(rendered);
  }

  private static String normalizeVariableValue(String rawValue) {
    if (!StringUtils.hasText(rawValue)) {
      return "";
    }
    return rawValue.trim();
  }

  private static String resolveDateValue(LocalDateTime publishedAt) {
    if (publishedAt == null) {
      return "";
    }
    return publishedAt.format(DATE_FORMATTER);
  }
}
