package top.asimov.pigeon.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.helper.YoutubeQuotaContextHolder;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.enums.YoutubeApiCallContext;
import top.asimov.pigeon.service.PlaylistService;
import top.asimov.pigeon.service.YoutubeQuotaService;

@Log4j2
@Component
public class PlaylistSyncer {

  private final PlaylistService playlistService;
  private final YoutubeQuotaService youtubeQuotaService;

  public PlaylistSyncer(PlaylistService playlistService, YoutubeQuotaService youtubeQuotaService) {
    this.playlistService = playlistService;
    this.youtubeQuotaService = youtubeQuotaService;
  }

  // 播放列表每 3 小时执行一次全量检查，以降低对 YouTube API 的压力。
  @Scheduled(fixedRate = 3, timeUnit = TimeUnit.HOURS)
  public void syncDuePlaylists() {
    YoutubeQuotaContextHolder.set(YoutubeApiCallContext.AUTO_SYNC);
    try {
      if (youtubeQuotaService.isAutoSyncBlockedToday()) {
        log.warn("YouTube 自动同步已因当日配额达到上限而阻断，跳过播放列表定时任务。");
        return;
      }

      log.info("开始执行播放列表定时同步任务...");
      List<Playlist> duePlaylists = playlistService.findDueForSync(LocalDateTime.now());

      if (duePlaylists.isEmpty()) {
        log.info("没有需要同步的播放列表。");
        return;
      }

      log.info("发现 {} 个需要同步的播放列表。", duePlaylists.size());
      for (Playlist playlist : duePlaylists) {
        if (youtubeQuotaService.isAutoSyncBlockedToday()) {
          log.warn("YouTube 自动同步在本轮任务中触发阻断，停止继续同步剩余播放列表。");
          break;
        }
        try {
          playlistService.refreshPlaylist(playlist);
        } catch (Exception e) {
          log.error("同步播放列表 {} (ID: {}) 时发生错误。", playlist.getTitle(), playlist.getId(), e);
        }
      }
      log.info("播放列表定时同步任务执行完毕。");
    } finally {
      YoutubeQuotaContextHolder.clear();
    }
  }
}
