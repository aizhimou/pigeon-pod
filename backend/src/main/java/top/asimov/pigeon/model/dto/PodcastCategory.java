package top.asimov.pigeon.model.dto;

import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;

/**
 * A feed's podcast category for {@code <itunes:category>}, optionally with a subcategory. Stored
 * on the feed as {@code "Category"} or {@code "Category|Subcategory"}, using Apple Podcasts'
 * English category names.
 */
public record PodcastCategory(String name, String subcategory) {

  public static final PodcastCategory DEFAULT = new PodcastCategory("Technology", null);

  private static final String SEPARATOR = "|";
  private static final int MAX_NAME_LENGTH = 64;

  /** Reads a stored value; blank or malformed values fall back to {@link #DEFAULT}. */
  public static PodcastCategory parse(String stored) {
    try {
      String normalized = normalize(stored);
      if (normalized == null) {
        return DEFAULT;
      }
      int separator = normalized.indexOf(SEPARATOR);
      return separator < 0
          ? new PodcastCategory(normalized, null)
          : new PodcastCategory(normalized.substring(0, separator),
              normalized.substring(separator + 1));
    } catch (BusinessException e) {
      return DEFAULT;
    }
  }

  /** Validates user input and returns the value to store, or {@code null} for the default. */
  public static String normalize(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    String[] parts = raw.split("\\|", -1);
    if (parts.length > 2) {
      throw new BusinessException("Invalid podcast category");
    }
    String name = parts[0].strip();
    String subcategory = parts.length == 2 ? parts[1].strip() : "";
    if (!StringUtils.hasText(name) || name.length() > MAX_NAME_LENGTH
        || subcategory.length() > MAX_NAME_LENGTH) {
      throw new BusinessException("Invalid podcast category");
    }
    return subcategory.isEmpty() ? name : name + SEPARATOR + subcategory;
  }
}
