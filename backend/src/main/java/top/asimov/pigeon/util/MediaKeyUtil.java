package top.asimov.pigeon.util;

import top.asimov.pigeon.model.enums.DownloadType;
import org.springframework.util.StringUtils;

public final class MediaKeyUtil {

  private static final String AUDIO_PREFIX = "audio/";
  private static final String VIDEO_PREFIX = "video/";
  private static final String FEED_PREFIX = "feeds/";

  private MediaKeyUtil() {
  }

  public static String buildEpisodeDirectory(DownloadType downloadType, String channelName) {
    String prefix = downloadType == DownloadType.VIDEO ? VIDEO_PREFIX : AUDIO_PREFIX;
    String safeChannelName = MediaFileNameUtil.sanitizeFileName(channelName);
    return prefix + safeChannelName + "/";
  }

  public static String buildEpisodeBaseName(String safeTitle, String episodeId) {
    return safeTitle + "-" + episodeId;
  }

  public static String buildEpisodeMediaKey(DownloadType downloadType, String channelName,
      String safeTitle, String episodeId, String extension) {
    return buildEpisodeDirectory(downloadType, channelName)
        + buildEpisodeBaseName(safeTitle, episodeId)
        + "." + normalizeExtension(extension);
  }

  public static String buildEpisodeSubtitleKeyByMediaKey(String mediaKey, String language,
      String extension) {
    String mediaStem = buildEpisodeAssetPrefixByMediaKey(mediaKey);
    return mediaStem + "." + language + "." + normalizeExtension(extension);
  }

  public static String buildEpisodeChaptersKeyByMediaKey(String mediaKey) {
    String mediaStem = buildEpisodeAssetPrefixByMediaKey(mediaKey);
    return mediaStem + ".chapters.json";
  }

  public static String buildEpisodeThumbnailKeyByMediaKey(String mediaKey, String extension) {
    String mediaStem = buildEpisodeAssetPrefixByMediaKey(mediaKey);
    return mediaStem + ".thumbnail." + normalizeExtension(extension);
  }

  public static String buildFeedCoverKey(String feedId, String extension) {
    return FEED_PREFIX + feedId + "." + extension;
  }

  public static String buildFeedCoverPrefix(String feedId) {
    return FEED_PREFIX + feedId + ".";
  }

  public static String extractBaseNameFromMediaKey(String mediaKey) {
    if (!StringUtils.hasText(mediaKey)) {
      return null;
    }
    int slashIndex = mediaKey.lastIndexOf('/');
    String filename = slashIndex >= 0 ? mediaKey.substring(slashIndex + 1) : mediaKey;
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex <= 0) {
      return filename;
    }
    return filename.substring(0, dotIndex);
  }

  public static String buildEpisodeAssetPrefixByMediaKey(String mediaKey) {
    if (!StringUtils.hasText(mediaKey)) {
      return null;
    }
    int dotIndex = mediaKey.lastIndexOf('.');
    if (dotIndex <= 0) {
      return mediaKey;
    }
    return mediaKey.substring(0, dotIndex);
  }

  public static String extractExtension(String objectKey) {
    if (!StringUtils.hasText(objectKey)) {
      return "";
    }
    int dot = objectKey.lastIndexOf('.');
    if (dot < 0 || dot == objectKey.length() - 1) {
      return "";
    }
    return objectKey.substring(dot + 1).toLowerCase();
  }

  private static String normalizeExtension(String extension) {
    if (!StringUtils.hasText(extension)) {
      return "";
    }
    String normalized = extension.startsWith(".") ? extension.substring(1) : extension;
    return normalized.toLowerCase();
  }
}
