package top.asimov.pigeon.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.helper.YoutubeQuotaContextHolder;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.enums.FeedSource;
import top.asimov.pigeon.model.enums.YoutubeApiCallContext;
import top.asimov.pigeon.service.ChannelService;
import top.asimov.pigeon.service.YoutubeQuotaService;

@Slf4j
@Component
public class ChannelSyncer {

  private final ChannelService channelService;
  private final YoutubeQuotaService youtubeQuotaService;

  public ChannelSyncer(ChannelService channelService, YoutubeQuotaService youtubeQuotaService) {
    this.channelService = channelService;
    this.youtubeQuotaService = youtubeQuotaService;
  }

  /**
   * 每1小时执行一次，检查并同步需要更新的频道。
   */
  @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
  public void syncDueChannels() {
    YoutubeQuotaContextHolder.set(YoutubeApiCallContext.AUTO_SYNC);
    try {
      log.info("[scheduler] channel sync started");
      List<Channel> dueChannels = channelService.findDueForSync(LocalDateTime.now());

      if (dueChannels.isEmpty()) {
        log.info("[scheduler] channel sync skipped: reason=noDueChannels");
        return;
      }

      log.info("[scheduler] channel sync due channels found: count={}", dueChannels.size());
      for (Channel channel : dueChannels) {
        boolean isYoutube = FeedSource.YOUTUBE.name().equalsIgnoreCase(channel.getSource());
        if (isYoutube && youtubeQuotaService.isAutoSyncBlockedToday()) {
          log.warn("[feed-sync] channel sync skipped: channelId={} reason=youtubeQuotaBlocked",
              channel.getId());
          continue;
        }
        try {
          channelService.refreshChannel(channel);
        } catch (Exception e) {
          log.error("[feed-sync] channel sync failed: channelId={} title={}", channel.getId(),
              channel.getTitle(), e);
        }
      }
      log.info("[scheduler] channel sync completed: count={}", dueChannels.size());
    } finally {
      YoutubeQuotaContextHolder.clear();
    }
  }
}
