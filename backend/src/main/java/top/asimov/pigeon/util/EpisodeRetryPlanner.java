package top.asimov.pigeon.util;

import java.time.LocalDateTime;
import lombok.extern.log4j.Log4j2;
import top.asimov.pigeon.model.entity.Episode;

@Log4j2
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
      log.info("已安排失败任务自动重试: episodeId={}, retryNumber={}, nextRetryAt={}",
          episode.getId(), nextRetry, nextRetryAt);
      return;
    }
    log.warn("失败任务已耗尽自动重试次数: episodeId={}, retryNumber={}",
        episode.getId(), nextRetry);
  }
}
