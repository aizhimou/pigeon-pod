package top.asimov.pigeon.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.helper.DownloadTaskHelper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.service.EpisodeService;

@Log4j2
@Component
public class DownloadScheduler {

  private static final int DELAYED_PROMOTE_BATCH_SIZE = 100;

  @Qualifier("downloadTaskExecutor")
  private ThreadPoolTaskExecutor downloadTaskExecutor;
  private final EpisodeMapper episodeMapper;
  private final DownloadTaskHelper downloadTaskHelper;
  private final EpisodeService episodeService;

  public DownloadScheduler(ThreadPoolTaskExecutor downloadTaskExecutor, EpisodeMapper episodeMapper,
      DownloadTaskHelper downloadTaskHelper, EpisodeService episodeService) {
    this.downloadTaskExecutor = downloadTaskExecutor;
    this.episodeMapper = episodeMapper;
    this.downloadTaskHelper = downloadTaskHelper;
    this.episodeService = episodeService;
  }

  // 每30秒检查一次待下载任务
  @Scheduled(fixedDelay = 30000)
  public void processPendingDownloads() {
    int promotedCount = episodeService.promoteDueDelayedAutoDownloadEpisodes(
        DELAYED_PROMOTE_BATCH_SIZE);
    if (promotedCount > 0) {
      log.info("本轮已将 {} 个延迟自动下载任务提升为 PENDING", promotedCount);
    }

    // 获取线程池状态（无队列模式下仅按空闲线程数补位）
    int activeCount = downloadTaskExecutor.getActiveCount();
    int maxPoolSize = downloadTaskExecutor.getMaxPoolSize();

    // 可用槽位 = 最大线程数 - 活跃线程数
    int availableSlots = maxPoolSize - activeCount;

    log.debug("线程池状态检查: 活跃={}, 可用空位={}", activeCount, availableSlots);

    if (availableSlots > 0) {

      List<Episode> pendingEpisodes = episodeMapper.selectList(
          new QueryWrapper<Episode>()
              .eq("download_status", EpisodeStatus.PENDING.name())
              .orderByAsc("created_at")
              .last("LIMIT " + availableSlots)
      );
      List<Episode> episodesToProcess = new ArrayList<>(pendingEpisodes);

      int remainingSlots = availableSlots - episodesToProcess.size();
      if (remainingSlots > 0) {
        List<Episode> retryEpisodes = episodeMapper.selectList(
            new QueryWrapper<Episode>()
                .eq("download_status", EpisodeStatus.FAILED.name())
                .lt("retry_number", 3)
                .orderByAsc("created_at")
                .last("LIMIT " + remainingSlots)
        );
        episodesToProcess.addAll(retryEpisodes);
      }

      for (Episode episode : episodesToProcess) {
        boolean success = downloadTaskHelper.submitDownloadTask(episode.getId());
        if (!success) {
          break; // 提交失败，可能是队列满了，停止继续处理
        }
      }
    }
  }

}
