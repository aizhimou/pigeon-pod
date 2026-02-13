package top.asimov.pigeon.util;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;

public final class MediaFileNameUtil {

  private MediaFileNameUtil() {
  }

  public static String getSafeTitle(String title) {
    if (title == null) {
      return "untitled";
    }
    String clean = sanitizeFileName(title);
    byte[] bytes = clean.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= 200) {
      return clean;
    }

    int byteCount = 0;
    int i = 0;
    for (; i < clean.length(); i++) {
      int charBytes = String.valueOf(clean.charAt(i)).getBytes(StandardCharsets.UTF_8).length;
      if (byteCount + charBytes > 200) {
        break;
      }
      byteCount += charBytes;
    }
    return clean.substring(0, i) + "...";
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
    safe = safe.replaceAll("[\\\\/:*?\"<>|;&$`'()!{}]", "_");
    safe = safe.replaceAll("_+", "_");
    safe = safe.replaceAll("^[_.\\s]+|[_.\\s]+$", "");

    if (safe.isEmpty()) {
      return "sanitized_name";
    }
    return safe;
  }
}

