package top.asimov.pigeon.util;

import top.asimov.pigeon.model.entity.Playlist;

public final class IndividualVideoPlaylistSupport {

  public static final String PLAYLIST_ID = "individual-videos-youtube";
  public static final String FEED_MODE = "SINGLE_VIDEO";
  public static final String DEFAULT_TITLE = "Individual Videos";
  public static final String DEFAULT_DESCRIPTION =
      "This is not a YouTube or BiliBili playlist. PigeonPod generated this playlist so you can "
          + "subscribe to individual videos as podcast episodes.";

  private IndividualVideoPlaylistSupport() {
  }

  public static boolean isSingleVideoPlaylist(Playlist playlist) {
    return playlist != null && FEED_MODE.equalsIgnoreCase(playlist.getFeedMode());
  }

  public static String buildConsoleUrl(String appBaseUrl, String playlistId) {
    if (appBaseUrl == null || appBaseUrl.isBlank() || playlistId == null || playlistId.isBlank()) {
      return "";
    }
    return appBaseUrl + "/playlist/" + playlistId;
  }
}
