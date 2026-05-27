package top.asimov.pigeon.scheduler;

import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.service.notification.FailedDownloadNotifyService;

@Slf4j
@Component
public class FailedDownloadNotifier {

  private final FailedDownloadNotifyService failedDownloadNotifyService;

  public FailedDownloadNotifier(
      FailedDownloadNotifyService failedDownloadNotifyService) {
    this.failedDownloadNotifyService = failedDownloadNotifyService;
  }

  @Scheduled(fixedDelay = 480, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
  public void sendFailedDownloadDigest() {
    int notifiedCount = failedDownloadNotifyService.notifyFailedDownloadsIfNeeded();
    if (notifiedCount > 0) {
      log.info("[notification] failed-download digest sent: count={}", notifiedCount);
    }
  }
}
