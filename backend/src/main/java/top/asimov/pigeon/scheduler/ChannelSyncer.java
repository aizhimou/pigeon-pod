package top.asimov.pigeon.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.service.ChannelService;

@Log4j2
@Component
public class ChannelSyncer {

  private final ChannelService channelService;

  public ChannelSyncer(ChannelService channelService) {
    this.channelService = channelService;
  }

  /**
   * 每1小时执行一次，检查并同步需要更新的频道。
   */
  @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
  public void syncDueChannels() {
    log.info("开始执行定时同步任务...");
    // 查找所有需要更新的频道
    List<Channel> dueChannels = channelService.findDueForSync(LocalDateTime.now());

    if (dueChannels.isEmpty()) {
      log.info("没有需要同步的频道。");
      return;
    }

    log.info("发现 {} 个需要同步的频道。", dueChannels.size());
    for (Channel channel : dueChannels) {
      try {
        // 对每个频道执行单独的同步逻辑
        channelService.refreshChannel(channel);
      } catch (Exception e) {
        log.error("同步频道 {} (ID: {}) 时发生错误。", channel.getTitle(), channel.getId(), e);
        // 即使一个频道失败，也不应中断整个任务
      }
    }
    log.info("定时同步任务执行完毕。");
  }
}
