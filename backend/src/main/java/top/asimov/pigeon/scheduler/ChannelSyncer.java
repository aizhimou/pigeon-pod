package top.asimov.pigeon.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.helper.YoutubeQuotaContextHolder;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.enums.FeedSource;
import top.asimov.pigeon.model.enums.YoutubeApiCallContext;
import top.asimov.pigeon.service.ChannelService;
import top.asimov.pigeon.service.YoutubeQuotaService;

@Log4j2
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
      log.info("开始执行定时同步任务...");
      List<Channel> dueChannels = channelService.findDueForSync(LocalDateTime.now());

      if (dueChannels.isEmpty()) {
        log.info("没有需要同步的频道。");
        return;
      }

      log.info("发现 {} 个需要同步的频道。", dueChannels.size());
      for (Channel channel : dueChannels) {
        boolean isYoutube = FeedSource.YOUTUBE.name().equalsIgnoreCase(channel.getSource());
        if (isYoutube && youtubeQuotaService.isAutoSyncBlockedToday()) {
          log.warn("YouTube 自动同步已阻断，跳过 YouTube 频道: {} ({})",
              channel.getTitle(), channel.getId());
          continue;
        }
        try {
          channelService.refreshChannel(channel);
        } catch (Exception e) {
          log.error("同步频道 {} (ID: {}) 时发生错误。", channel.getTitle(), channel.getId(), e);
        }
      }
      log.info("定时同步任务执行完毕。");
    } finally {
      YoutubeQuotaContextHolder.clear();
    }
  }
}
