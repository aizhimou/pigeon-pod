package top.asimov.pigeon.util;

import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.constant.Bilibili;
import top.asimov.pigeon.model.constant.Youtube;
import top.asimov.pigeon.model.enums.FeedSource;

public final class FeedSourceUrlBuilder {

  private FeedSourceUrlBuilder() {
  }

  public static String buildEpisodeUrl(String source, String episodeId) {
    if (!StringUtils.hasText(episodeId)) {
      return "";
    }
    FeedSource feedSource = parseFeedSource(source);
    if (feedSource == FeedSource.BILIBILI) {
      return Bilibili.VIDEO_URL + episodeId;
    }
    return Youtube.VIDEO_URL + episodeId;
  }

  public static String buildChannelUrl(String source, String channelId) {
    FeedSource feedSource = parseFeedSource(source);
    if (feedSource == FeedSource.BILIBILI) {
      String mid = BilibiliIdUtil.extractMidFromChannelId(channelId);
      if (!StringUtils.hasText(mid)) {
        return Bilibili.SPACE_URL;
      }
      return Bilibili.UP_VIDEOS_URL_TEMPLATE.formatted(mid);
    }
    return Youtube.CHANNEL_URL + channelId;
  }

  public static String buildPlaylistUrl(String source, String playlistId, String ownerId) {
    FeedSource feedSource = parseFeedSource(source);
    if (feedSource == FeedSource.BILIBILI) {
      String type = BilibiliIdUtil.extractCollectionType(playlistId);
      String collectionId = BilibiliIdUtil.extractCollectionId(playlistId);
      if (!StringUtils.hasText(type) || !StringUtils.hasText(collectionId) || !StringUtils.hasText(ownerId)) {
        return Bilibili.SPACE_URL;
      }
      return Bilibili.PLAYLIST_URL_TEMPLATE.formatted(ownerId, collectionId, type);
    }
    return Youtube.PLAYLIST_URL + playlistId;
  }

  private static FeedSource parseFeedSource(String source) {
    if (!StringUtils.hasText(source)) {
      return FeedSource.YOUTUBE;
    }
    try {
      return FeedSource.valueOf(source.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return FeedSource.YOUTUBE;
    }
  }
}

