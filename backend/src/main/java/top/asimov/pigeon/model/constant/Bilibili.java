package top.asimov.pigeon.model.constant;

public class Bilibili {

  public static final String API_BASE_URL = "https://api.bilibili.com";
  public static final String SPACE_URL = "https://space.bilibili.com/";
  public static final String VIDEO_URL = "https://www.bilibili.com/video/";

  public static final String UP_VIDEOS_URL_TEMPLATE = SPACE_URL + "%s/video";
  public static final String PLAYLIST_URL_TEMPLATE = SPACE_URL + "%s/lists/%s?type=%s";

  private Bilibili() {
  }
}

