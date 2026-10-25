package top.asimov.pigeon.util;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;

/**
 * A feed's format for episode descriptions in RSS. Placeholders in braces are replaced per
 * episode (see {@link #PLACEHOLDERS}); unknown ones are left as typed.
 */
public final class EpisodeDescriptionTemplate {

  public static final int MAX_LENGTH = 5000;
  public static final List<String> PLACEHOLDERS =
      List.of("description", "title", "channel", "date", "source_url", "source_link");

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");
  private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("\n{3,}");

  private EpisodeDescriptionTemplate() {
  }

  /** Returns the template to store, or {@code null} to keep PigeonPod's default format. */
  public static String normalize(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    String template = raw.replace("\r\n", "\n").strip();
    if (template.length() > MAX_LENGTH) {
      throw new BusinessException("Episode description format is too long");
    }
    return template;
  }

  /**
   * Fills the placeholders. Missing values render as empty text, and blank lines left behind by
   * empty placeholders are collapsed.
   */
  public static String render(String template, Map<String, String> values) {
    Matcher matcher = PLACEHOLDER.matcher(template);
    StringBuilder rendered = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      String replacement = PLACEHOLDERS.contains(name)
          ? values.getOrDefault(name, "")
          : matcher.group(0);
      matcher.appendReplacement(rendered,
          Matcher.quoteReplacement(replacement == null ? "" : replacement));
    }
    matcher.appendTail(rendered);
    return EXTRA_BLANK_LINES.matcher(rendered.toString()).replaceAll("\n\n").strip();
  }
}
