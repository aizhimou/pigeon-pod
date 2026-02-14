package top.asimov.pigeon.helper;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.util.BilibiliIdUtil;

@Component
public class BilibiliResolverHelper {

  private static final Pattern MID_PATH_PATTERN = Pattern.compile("space\\.bilibili\\.com/(\\d+)");
  private static final Pattern LIST_PATH_PATTERN = Pattern.compile("/lists/(\\d+)");

  public boolean isBilibiliInput(String input) {
    if (!StringUtils.hasText(input)) {
      return false;
    }
    String normalized = input.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("season:") || normalized.startsWith("series:")) {
      return true;
    }
    if (normalized.startsWith(BilibiliIdUtil.CHANNEL_PREFIX)
        || normalized.startsWith(BilibiliIdUtil.SEASON_PREFIX)
        || normalized.startsWith(BilibiliIdUtil.SERIES_PREFIX)) {
      return true;
    }
    if (normalized.contains("bilibili.com")) {
      return true;
    }
    return normalized.matches("\\d+");
  }

  public boolean isBilibiliPlaylistInput(String input) {
    if (!StringUtils.hasText(input)) {
      return false;
    }
    String normalized = input.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("season:") || normalized.startsWith("series:")) {
      return true;
    }
    if (normalized.startsWith(BilibiliIdUtil.SEASON_PREFIX)
        || normalized.startsWith(BilibiliIdUtil.SERIES_PREFIX)) {
      return true;
    }
    if (normalized.contains("type=season") || normalized.contains("type=series")) {
      return true;
    }
    return normalized.contains("/lists/") && normalized.contains("bilibili.com");
  }

  public String resolveChannelMid(String input) {
    if (!StringUtils.hasText(input)) {
      throw new BusinessException("Bilibili channel source is empty");
    }
    String normalized = input.trim();
    String fromChannelId = BilibiliIdUtil.extractMidFromChannelId(normalized);
    if (StringUtils.hasText(fromChannelId)) {
      return fromChannelId;
    }
    if (normalized.matches("\\d+")) {
      return normalized;
    }
    Matcher matcher = MID_PATH_PATTERN.matcher(normalized);
    if (matcher.find()) {
      return matcher.group(1);
    }
    throw new BusinessException("Cannot resolve Bilibili UP mid from source: " + input);
  }

  public PlaylistResolveResult resolvePlaylist(String input) {
    if (!StringUtils.hasText(input)) {
      throw new BusinessException("Bilibili playlist source is empty");
    }
    String normalized = input.trim();
    String lower = normalized.toLowerCase(Locale.ROOT);

    if (lower.startsWith(BilibiliIdUtil.SEASON_PREFIX)) {
      String seasonId = BilibiliIdUtil.extractCollectionId(normalized);
      if (!StringUtils.hasText(seasonId)) {
        throw new BusinessException("Cannot resolve Bilibili season id from source: " + input);
      }
      return new PlaylistResolveResult("season", seasonId, null);
    }
    if (lower.startsWith(BilibiliIdUtil.SERIES_PREFIX)) {
      String seriesId = BilibiliIdUtil.extractCollectionId(normalized);
      if (!StringUtils.hasText(seriesId)) {
        throw new BusinessException("Cannot resolve Bilibili series id from source: " + input);
      }
      return new PlaylistResolveResult("series", seriesId, null);
    }
    if (lower.startsWith("season:")) {
      String payload = normalized.substring("season:".length()).trim();
      String[] parts = payload.split(":");
      String seasonId = BilibiliIdUtil.sanitizeDigits(parts.length > 0 ? parts[0] : null);
      if (!StringUtils.hasText(seasonId)) {
        throw new BusinessException("Cannot resolve Bilibili season id from source: " + input);
      }
      String mid = BilibiliIdUtil.sanitizeDigits(parts.length > 1 ? parts[1] : null);
      return new PlaylistResolveResult("season", seasonId, mid);
    }
    if (lower.startsWith("series:")) {
      String payload = normalized.substring("series:".length()).trim();
      String[] parts = payload.split(":");
      String seriesId = BilibiliIdUtil.sanitizeDigits(parts.length > 0 ? parts[0] : null);
      if (!StringUtils.hasText(seriesId)) {
        throw new BusinessException("Cannot resolve Bilibili series id from source: " + input);
      }
      String mid = BilibiliIdUtil.sanitizeDigits(parts.length > 1 ? parts[1] : null);
      return new PlaylistResolveResult("series", seriesId, mid);
    }

    URI uri = parseUri(normalized);
    Map<String, String> query = parseQuery(uri == null ? null : uri.getRawQuery());
    String type = firstNonBlank(query.get("type"), detectTypeFromPath(uri == null ? null : uri.getPath()));
    if (!StringUtils.hasText(type)) {
      throw new BusinessException("Cannot resolve Bilibili playlist type from source: " + input);
    }
    type = type.toLowerCase(Locale.ROOT);
    if (!"season".equals(type) && !"series".equals(type)) {
      throw new BusinessException("Unsupported Bilibili playlist type: " + type);
    }

    String path = uri == null ? null : uri.getPath();
    String collectionId = firstNonBlank(
        query.get(type + "_id"),
        query.get("sid"),
        query.get("series_id"),
        query.get("season_id"),
        extractListIdFromPath(path));
    collectionId = BilibiliIdUtil.sanitizeDigits(collectionId);
    if (!StringUtils.hasText(collectionId)) {
      throw new BusinessException("Cannot resolve Bilibili playlist id from source: " + input);
    }

    String mid = firstNonBlank(query.get("mid"), extractMidFromPath(path));
    mid = BilibiliIdUtil.sanitizeDigits(mid);
    return new PlaylistResolveResult(type, collectionId, mid);
  }

  private URI parseUri(String input) {
    try {
      return URI.create(input);
    } catch (Exception ex) {
      return null;
    }
  }

  private Map<String, String> parseQuery(String rawQuery) {
    Map<String, String> result = new LinkedHashMap<>();
    if (!StringUtils.hasText(rawQuery)) {
      return result;
    }
    String[] parts = rawQuery.split("&");
    for (String part : parts) {
      if (!StringUtils.hasText(part)) {
        continue;
      }
      String[] kv = part.split("=", 2);
      String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
      String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
      result.put(key, value);
    }
    return result;
  }

  private String detectTypeFromPath(String path) {
    if (!StringUtils.hasText(path)) {
      return null;
    }
    String normalized = path.toLowerCase(Locale.ROOT);
    if (normalized.contains("season")) {
      return "season";
    }
    if (normalized.contains("series")) {
      return "series";
    }
    return null;
  }

  private String extractListIdFromPath(String path) {
    if (!StringUtils.hasText(path)) {
      return null;
    }
    Matcher matcher = LIST_PATH_PATTERN.matcher(path);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  private String extractMidFromPath(String path) {
    if (!StringUtils.hasText(path)) {
      return null;
    }
    Matcher matcher = Pattern.compile("/(\\d+)").matcher(path);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  private String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value.trim();
      }
    }
    return null;
  }

  public record PlaylistResolveResult(String type, String collectionId, String mid) {

  }
}
