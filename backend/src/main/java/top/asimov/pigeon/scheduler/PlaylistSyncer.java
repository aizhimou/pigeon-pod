package top.asimov.pigeon.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.service.PlaylistService;

@Log4j2
@Component
public class PlaylistSyncer {

  private final PlaylistService playlistService;

  public PlaylistSyncer(PlaylistService playlistService) {
    this.playlistService = playlistService;
  }

  @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
  public void syncDuePlaylists() {
    log.info("开始执行播放列表定时同步任务...");
    List<Playlist> duePlaylists = playlistService.findDueForSync(LocalDateTime.now());

    if (duePlaylists.isEmpty()) {
      log.info("没有需要同步的播放列表。");
      return;
    }

    log.info("发现 {} 个需要同步的播放列表。", duePlaylists.size());
    for (Playlist playlist : duePlaylists) {
      try {
        playlistService.refreshPlaylist(playlist);
      } catch (Exception e) {
        log.error("同步播放列表 {} (ID: {}) 时发生错误。", playlist.getTitle(), playlist.getId(), e);
      }
    }
    log.info("播放列表定时同步任务执行完毕。");
  }
}

