package top.asimov.pigeon.handler;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import top.asimov.pigeon.event.EpisodesCreatedEvent;
import top.asimov.pigeon.model.entity.Episode;

public final class FeedEpisodeHelper {

  private FeedEpisodeHelper() {
  }

  public static Optional<Episode> findLatestEpisode(List<Episode> episodes) {
    return episodes.stream()
        .max(Comparator.comparing(Episode::getPublishedAt));
  }

  public static List<String> extractEpisodeIds(List<Episode> episodes) {
    return episodes.stream()
        .map(Episode::getId)
        .collect(Collectors.toList());
  }

  public static void publishEpisodesCreated(ApplicationEventPublisher publisher, Object source,
      List<Episode> episodes) {
    List<String> episodeIds = extractEpisodeIds(episodes);
    if (!episodeIds.isEmpty()) {
      publisher.publishEvent(new EpisodesCreatedEvent(source, episodeIds));
    }
  }
}
