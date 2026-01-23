package top.asimov.pigeon.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;

public final class YtDlpArgsValidator {

  private static final int MAX_TOKENS = 10;
  private static final int MAX_TOKEN_LENGTH = 128;

  private static final Set<String> BLOCKED_FLAGS = Set.of(
      "--exec",
      "--output",
      "-o",
      "--paths",
      "--config",
      "--config-locations",
      "--cookies",
      "--cookiefile",
      "--ffmpeg-location",
      "--external-downloader",
      "--external-downloader-args",
      "--batch-file"
  );

  public static List<String> blockedArgs() {
    return List.copyOf(BLOCKED_FLAGS);
  }

  private YtDlpArgsValidator() {
  }

  public static List<String> validate(List<String> rawArgs) {
    if (rawArgs == null || rawArgs.isEmpty()) {
      return List.of();
    }

    List<String> tokens = new ArrayList<>();
    for (String token : rawArgs) {
      if (!StringUtils.hasText(token)) {
        continue;
      }
      String trimmed = token.trim();
      if (trimmed.length() > MAX_TOKEN_LENGTH) {
        throw new BusinessException("yt-dlp argument too long");
      }
      tokens.add(trimmed);
      if (tokens.size() > MAX_TOKENS) {
        throw new BusinessException("too many yt-dlp arguments");
      }
    }

    List<String> validated = new ArrayList<>();

    for (int i = 0; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (token.startsWith("-") && isBlocked(token)) {
        throw new BusinessException("blocked yt-dlp argument: " + token);
      }
      validated.add(token);
    }

    return validated;
  }

  private static boolean isBlocked(String token) {
    if (BLOCKED_FLAGS.contains(token)) {
      return true;
    }
    for (String blocked : BLOCKED_FLAGS) {
      if (blocked.startsWith("--") && token.startsWith(blocked + "=")) {
        return true;
      }
    }
    return token.startsWith("--config-") || token.startsWith("--paths=");
  }
}
