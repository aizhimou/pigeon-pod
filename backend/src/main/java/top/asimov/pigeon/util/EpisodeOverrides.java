package top.asimov.pigeon.util;

import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.entity.Episode;

/** The title and description an episode shows, after the user's per-episode edits. */
public final class EpisodeOverrides {

  private EpisodeOverrides() {
  }

  public static String title(Episode episode) {
    if (episode == null) {
      return null;
    }
    return StringUtils.hasText(episode.getCustomTitle())
        ? episode.getCustomTitle()
        : episode.getTitle();
  }

  public static String description(Episode episode) {
    if (episode == null) {
      return null;
    }
    return StringUtils.hasText(episode.getCustomDescription())
        ? episode.getCustomDescription()
        : episode.getDescription();
  }
}
