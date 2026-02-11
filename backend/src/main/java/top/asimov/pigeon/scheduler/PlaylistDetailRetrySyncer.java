package top.asimov.pigeon.scheduler;

import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.service.PlaylistService;

@Log4j2
@Component
public class PlaylistDetailRetrySyncer {

  private final PlaylistService playlistService;

  public PlaylistDetailRetrySyncer(PlaylistService playlistService) {
    this.playlistService = playlistService;
  }

  @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
  public void processRetryQueue() {
    try {
      int recovered = playlistService.processPlaylistDetailRetryQueue(100);
      if (recovered > 0) {
        log.info("播放列表详情重试任务完成，本轮恢复 {} 条节目。", recovered);
      }
    } catch (Exception e) {
      log.error("播放列表详情重试任务执行失败。", e);
    }
  }
}
