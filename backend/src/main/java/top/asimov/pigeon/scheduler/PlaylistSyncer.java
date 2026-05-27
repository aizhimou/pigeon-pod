package top.asimov.pigeon.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.helper.YoutubeQuotaContextHolder;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.enums.FeedSource;
import top.asimov.pigeon.model.enums.YoutubeApiCallContext;
import top.asimov.pigeon.service.PlaylistService;
import top.asimov.pigeon.service.YoutubeQuotaService;

@Slf4j
@Component
public class PlaylistSyncer {

  private final PlaylistService playlistService;
  private final YoutubeQuotaService youtubeQuotaService;

  public PlaylistSyncer(PlaylistService playlistService, YoutubeQuotaService youtubeQuotaService) {
    this.playlistService = playlistService;
    this.youtubeQuotaService = youtubeQuotaService;
  }

  // 每小时检查一次播放列表，由每个 playlist 的 syncIntervalHours 决定是否执行全量同步。
  @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
  public void syncDuePlaylists() {
    YoutubeQuotaContextHolder.set(YoutubeApiCallContext.AUTO_SYNC);
    try {
      log.info("[scheduler] playlist sync started");
      List<Playlist> duePlaylists = playlistService.findDueForSync(LocalDateTime.now());

      if (duePlaylists.isEmpty()) {
        log.info("[scheduler] playlist sync skipped: reason=noDuePlaylists");
        return;
      }

      log.info("[scheduler] playlist sync due playlists found: count={}", duePlaylists.size());
      for (Playlist playlist : duePlaylists) {
        boolean isYoutube = FeedSource.YOUTUBE.name().equalsIgnoreCase(playlist.getSource());
        if (isYoutube && youtubeQuotaService.isAutoSyncBlockedToday()) {
          log.warn("[feed-sync] playlist sync skipped: playlistId={} reason=youtubeQuotaBlocked",
              playlist.getId());
          continue;
        }
        try {
          playlistService.refreshPlaylist(playlist);
        } catch (Exception e) {
          log.error("[feed-sync] playlist sync failed: playlistId={} title={}", playlist.getId(),
              playlist.getTitle(), e);
        }
      }
      log.info("[scheduler] playlist sync completed: count={}", duePlaylists.size());
    } finally {
      YoutubeQuotaContextHolder.clear();
    }
  }
}
