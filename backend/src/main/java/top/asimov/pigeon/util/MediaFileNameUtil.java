package top.asimov.pigeon.util;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;

public final class MediaFileNameUtil {

  private static final int MAX_FILE_NAME_BYTES = 200;

  private MediaFileNameUtil() {
  }

  public static String getSafeTitle(String title) {
    if (title == null) {
      return "untitled";
    }
    String clean = sanitizeFileName(title);
    byte[] bytes = clean.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= MAX_FILE_NAME_BYTES) {
      return clean;
    }
    return trimToMaxBytes(clean, MAX_FILE_NAME_BYTES) + "...";
  }

  public static String appendNumericSuffix(String baseName, int suffixNumber) {
    if (suffixNumber <= 0) {
      return getSafeTitle(baseName);
    }
    String safeBaseName = getSafeTitle(baseName);
    String suffix = "-" + suffixNumber;
    int maxBaseBytes = MAX_FILE_NAME_BYTES - suffix.getBytes(StandardCharsets.UTF_8).length;
    if (maxBaseBytes <= 0) {
      return trimToMaxBytes(String.valueOf(suffixNumber), MAX_FILE_NAME_BYTES);
    }
    return trimToMaxBytes(safeBaseName, maxBaseBytes) + suffix;
  }

  public static String sanitizeFileName(String name) {
    if (name == null || name.trim().isEmpty()) {
      return "untitled";
    }

    String safe = name.replaceAll("[–—―]", "-");
    safe = safe.replaceAll("\\s+", " ").trim();
    safe = Normalizer.normalize(safe, Normalizer.Form.NFD);

    Pattern accentPattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    safe = accentPattern.matcher(safe).replaceAll("");
    safe = Normalizer.normalize(safe, Normalizer.Form.NFC);

    safe = safe.replaceAll("[\\\\/:*?\"<>|;&$`'()!{}]", "_");
    safe = safe.replaceAll("_+", "_");
    safe = safe.replaceAll("\\s*(_)\\s*", "$1");
    safe = safe.replaceAll("^[_.\\s]+|[_.\\s]+$", "");

    if (safe.isEmpty()) {
      return "sanitized_name";
    }
    return safe;
  }

  private static String trimToMaxBytes(String value, int maxBytes) {
    if (value == null) {
      return "";
    }
    int byteCount = 0;
    int i = 0;
    for (; i < value.length(); i++) {
      int charBytes = String.valueOf(value.charAt(i)).getBytes(StandardCharsets.UTF_8).length;
      if (byteCount + charBytes > maxBytes) {
        break;
      }
      byteCount += charBytes;
    }
    return value.substring(0, i);
  }
}
