package top.asimov.pigeon.scheduler;

import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.service.notification.FailedDownloadNotifyService;

@Log4j2
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
      log.info("Sent failed-download notification digest for {} episode(s)", notifiedCount);
    }
  }
}
