package top.asimov.pigeon.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Feed;

public final class FeedEpisodeVisibilityHelper {

  private FeedEpisodeVisibilityHelper() {
  }

  public static boolean matchesFeedFilter(Feed feed, Episode episode) {
    if (feed == null || episode == null) {
      return false;
    }
    if (KeywordExpressionMatcher.notMatchesKeywordFilter(
        episode.getTitle(), feed.getTitleContainKeywords(), feed.getTitleExcludeKeywords())) {
      return false;
    }
    if (KeywordExpressionMatcher.notMatchesKeywordFilter(
        episode.getDescription(), feed.getDescriptionContainKeywords(), feed.getDescriptionExcludeKeywords())) {
      return false;
    }

    Integer durationSeconds = episode.getDurationSeconds();
    if (durationSeconds == null) {
      durationSeconds = EpisodeDurationHelper.parseDurationSeconds(episode.getDuration());
    }
    if (durationSeconds == null) {
      return feed.getMinimumDuration() == null && feed.getMaximumDuration() == null;
    }
    if (feed.getMinimumDuration() != null && durationSeconds < feed.getMinimumDuration()) {
      return false;
    }
    if (feed.getMaximumDuration() != null && durationSeconds > feed.getMaximumDuration() * 60) {
      return false;
    }
    return true;
  }

  public static List<Episode> filterVisibleEpisodes(Feed feed, List<Episode> episodes) {
    if (episodes == null || episodes.isEmpty()) {
      return Collections.emptyList();
    }
    List<Episode> visible = new ArrayList<>();
    for (Episode episode : episodes) {
      if (matchesFeedFilter(feed, episode)) {
        visible.add(episode);
      }
    }
    return visible;
  }
}
