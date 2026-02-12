package top.asimov.pigeon.model.enums;

public enum YoutubeApiMethod {
  SEARCH_LIST("search.list", 100),
  CHANNELS_LIST("channels.list", 1),
  PLAYLISTS_LIST("playlists.list", 1),
  PLAYLIST_ITEMS_LIST("playlistItems.list", 1),
  VIDEOS_LIST("videos.list", 1);

  private final String methodName;
  private final int quotaCost;

  YoutubeApiMethod(String methodName, int quotaCost) {
    this.methodName = methodName;
    this.quotaCost = quotaCost;
  }

  public String methodName() {
    return methodName;
  }

  public int quotaCost() {
    return quotaCost;
  }
}
