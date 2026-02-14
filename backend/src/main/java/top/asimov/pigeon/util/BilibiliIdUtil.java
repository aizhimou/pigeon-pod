package top.asimov.pigeon.util;

import org.springframework.util.StringUtils;

public final class BilibiliIdUtil {

  public static final String CHANNEL_PREFIX = "bili-mid-";
  public static final String SEASON_PREFIX = "bili-season-";
  public static final String SERIES_PREFIX = "bili-series-";

  private BilibiliIdUtil() {
  }

  public static String buildChannelId(String mid) {
    return CHANNEL_PREFIX + sanitizeDigits(mid);
  }

  public static String extractMidFromChannelId(String channelId) {
    if (!StringUtils.hasText(channelId)) {
      return null;
    }
    String normalized = channelId.trim();
    if (normalized.startsWith(CHANNEL_PREFIX)) {
      return sanitizeDigits(normalized.substring(CHANNEL_PREFIX.length()));
    }
    if (normalized.matches("\\d+")) {
      return normalized;
    }
    return null;
  }

  public static String buildSeasonPlaylistId(String seasonId) {
    return SEASON_PREFIX + sanitizeDigits(seasonId);
  }

  public static String buildSeriesPlaylistId(String seriesId) {
    return SERIES_PREFIX + sanitizeDigits(seriesId);
  }

  public static String extractCollectionType(String playlistId) {
    if (!StringUtils.hasText(playlistId)) {
      return null;
    }
    String normalized = playlistId.trim();
    if (normalized.startsWith(SEASON_PREFIX)) {
      return "season";
    }
    if (normalized.startsWith(SERIES_PREFIX)) {
      return "series";
    }
    return null;
  }

  public static String extractCollectionId(String playlistId) {
    if (!StringUtils.hasText(playlistId)) {
      return null;
    }
    String normalized = playlistId.trim();
    if (normalized.startsWith(SEASON_PREFIX)) {
      return sanitizeDigits(normalized.substring(SEASON_PREFIX.length()));
    }
    if (normalized.startsWith(SERIES_PREFIX)) {
      return sanitizeDigits(normalized.substring(SERIES_PREFIX.length()));
    }
    if (normalized.matches("\\d+")) {
      return normalized;
    }
    return null;
  }

  public static String sanitizeDigits(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    String digits = raw.trim().replaceAll("[^0-9]", "");
    return StringUtils.hasText(digits) ? digits : null;
  }
}
