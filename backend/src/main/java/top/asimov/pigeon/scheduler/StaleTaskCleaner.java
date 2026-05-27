package top.asimov.pigeon.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.enums.EpisodeStatus;

@Slf4j
@Component
public class StaleTaskCleaner implements ApplicationRunner {

  private final EpisodeMapper episodeMapper;

  public StaleTaskCleaner(EpisodeMapper episodeMapper) {
    this.episodeMapper = episodeMapper;
  }

  @Override
  public void run(ApplicationArguments args) {
    log.info("[scheduler] startup stale downloading cleanup started");
    QueryWrapper<Episode> query = new QueryWrapper<>();
    query.eq("download_status", EpisodeStatus.DOWNLOADING.name());

    List<Episode> staleEpisodes = episodeMapper.selectList(query);

    if (staleEpisodes.isEmpty()) {
      log.info("[scheduler] startup stale downloading cleanup skipped: reason=noStaleEpisodes");
      return;
    }

    log.warn("[scheduler] stale downloading episodes found: count={}", staleEpisodes.size());
    for (Episode episode : staleEpisodes) {
      log.debug("[scheduler] stale downloading episode reset: episodeId={} title={}",
          episode.getId(), episode.getTitle());
      episode.setDownloadStatus(EpisodeStatus.PENDING.name());
      episode.setNextRetryAt(null);
      episode.setFailureNotifiedAt(null);
      episode.setDownloadStartedAt(null);
      episodeMapper.updateById(episode);
    }
    log.info("[scheduler] startup stale downloading cleanup completed: count={}",
        staleEpisodes.size());
  }
}
