package top.asimov.pigeon.scheduler;

import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.helper.YoutubeQuotaContextHolder;
import top.asimov.pigeon.model.enums.YoutubeApiCallContext;
import top.asimov.pigeon.service.PlaylistService;
import top.asimov.pigeon.service.YoutubeQuotaService;

@Log4j2
@Component
public class PlaylistDetailRetrySyncer {

  private final PlaylistService playlistService;
  private final YoutubeQuotaService youtubeQuotaService;

  public PlaylistDetailRetrySyncer(PlaylistService playlistService,
      YoutubeQuotaService youtubeQuotaService) {
    this.playlistService = playlistService;
    this.youtubeQuotaService = youtubeQuotaService;
  }

  @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
  public void processRetryQueue() {
    YoutubeQuotaContextHolder.set(YoutubeApiCallContext.AUTO_SYNC);
    try {
      if (youtubeQuotaService.isAutoSyncBlockedToday()) {
        log.warn("YouTube 自动同步已因当日配额达到上限而阻断，跳过播放列表详情重试任务。");
        return;
      }

      int recovered = playlistService.processPlaylistDetailRetryQueue(100);
      if (recovered > 0) {
        log.info("播放列表详情重试任务完成，本轮恢复 {} 条节目。", recovered);
      }
    } catch (Exception e) {
      log.error("播放列表详情重试任务执行失败。", e);
    } finally {
      YoutubeQuotaContextHolder.clear();
    }
  }
}
