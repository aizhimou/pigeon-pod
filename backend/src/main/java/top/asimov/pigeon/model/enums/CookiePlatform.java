package top.asimov.pigeon.model.enums;

public enum CookiePlatform {

  YOUTUBE,
  BILIBILI,
  RUMBLE;

  public static CookiePlatform fromFeedSource(String rawSource) {
    if (FeedSource.YOUTUBE.name().equals(rawSource)) {
      return YOUTUBE;
    }
    if (FeedSource.BILIBILI.name().equals(rawSource)) {
      return BILIBILI;
    }
    return null;
  }
}
