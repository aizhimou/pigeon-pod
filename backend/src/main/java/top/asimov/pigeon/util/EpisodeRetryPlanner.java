package top.asimov.pigeon.util;

import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import top.asimov.pigeon.model.entity.Episode;

@Slf4j
public final class EpisodeRetryPlanner {

  private EpisodeRetryPlanner() {
  }

  public static void scheduleNextRetry(Episode episode, LocalDateTime failedAt) {
    if (episode == null) {
      return;
    }

    Integer current = episode.getRetryNumber();
    int nextRetry = current == null ? 1 : current + 1;
    episode.setRetryNumber(nextRetry);

    LocalDateTime nextRetryAt = EpisodeRetryPolicy.calculateNextRetryAt(nextRetry, failedAt);
    episode.setNextRetryAt(nextRetryAt);
    if (nextRetryAt != null) {
      log.info("[download] automatic retry scheduled: episodeId={} retryNumber={} nextRetryAt={}",
          episode.getId(), nextRetry, nextRetryAt);
      return;
    }
    log.warn("[download] automatic retries exhausted: episodeId={} retryNumber={}",
        episode.getId(), nextRetry);
  }
}
