package top.asimov.pigeon.helper;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.entity.Episode;

/**
 * 独立的Spring Bean，专门用于处理事务性状态变更，确保REQUIRES_NEW事务生效。
 */
@Log4j2
@Service
public class TaskStatusHelper {

  private final EpisodeMapper episodeMapper;

  public TaskStatusHelper(EpisodeMapper episodeMapper) {
    this.episodeMapper = episodeMapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Retryable(
      retryFor = {Exception.class},
      maxAttempts = 5,
      backoff = @Backoff(delay = 200, multiplier = 2, maxDelay = 2000))
  public boolean tryMarkDownloading(String episodeId) {
    try {
      Episode episode = episodeMapper.selectById(episodeId);
      if (episode == null) {
        return false;
      }
      if (!List.of(EpisodeStatus.READY.name(), EpisodeStatus.PENDING.name(),
              EpisodeStatus.FAILED.name())
          .contains(episode.getDownloadStatus())) {
        return false;
      }
      episodeMapper.updateDownloadStatusAndClearAutoDownloadAfter(
          episodeId, EpisodeStatus.DOWNLOADING.name());
      return true;
    } catch (Exception e) {
      log.warn("标记为DOWNLOADING失败: {}", episodeId, e);
      throw e;
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void rollbackFromDownloadingToPending(String episodeId) {
    try {
      Episode episode = episodeMapper.selectById(episodeId);
      if (episode != null && EpisodeStatus.DOWNLOADING.name()
          .equals(episode.getDownloadStatus())) {
        episodeMapper.updateDownloadStatusAndClearAutoDownloadAfter(
            episodeId, EpisodeStatus.PENDING.name());
      }
    } catch (Exception e) {
      log.error("从DOWNLOADING回滚到PENDING失败: {}", episodeId, e);
    }
  }
}
