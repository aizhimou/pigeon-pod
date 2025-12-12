package top.asimov.pigeon.scheduler;

import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.mapper.EpisodeMapper;

@Log4j2
@Component
@ConditionalOnProperty(prefix = "pigeon.scheduler.episode-cleaner", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EpisodeCleaner {

  private final EpisodeMapper episodeMapper;

  public EpisodeCleaner(EpisodeMapper episodeMapper) {
    this.episodeMapper = episodeMapper;
  }

  /**
   * 每2小时执行一次，清理超过频道最大集数限制的剧集
   */
  @Scheduled(fixedRateString = "${pigeon.scheduler.episode-cleaner.fixed-rate:7200000}")
  public void syncDueChannels() {
    log.info("开始执行清理任务...");
    episodeMapper.deleteEpisodesOverChannelMaximum();
    log.info("清理任务执行完毕。");
  }
}
