package top.asimov.pigeon.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.entity.NotificationConfig;
import top.asimov.pigeon.service.notification.FailedDownloadNotifyService;

@Service
public class NotificationMgmtService {

  private final NotificationConfigService notificationConfigService;
  private final FailedDownloadNotifyService failedDownloadNotifyService;

  public NotificationMgmtService(NotificationConfigService notificationConfigService,
      FailedDownloadNotifyService failedDownloadNotifyService) {
    this.notificationConfigService = notificationConfigService;
    this.failedDownloadNotifyService = failedDownloadNotifyService;
  }

  public NotificationConfig getNotificationConfig() {
    return sanitizeNotificationConfig(notificationConfigService.getCurrentConfig());
  }

  public NotificationConfig updateNotificationConfig(NotificationConfig incoming) {
    return sanitizeNotificationConfig(notificationConfigService.updateNotificationConfig(incoming));
  }

  public void testNotificationEmail(NotificationConfig incoming) {
    failedDownloadNotifyService.sendTestEmail(incoming);
  }

  public void testNotificationWebhook(NotificationConfig incoming) {
    failedDownloadNotifyService.sendTestWebhook(incoming);
  }

  private NotificationConfig sanitizeNotificationConfig(NotificationConfig config) {
    if (config == null) {
      return null;
    }
    config.setHasEmailPassword(StringUtils.hasText(config.getEmailPassword()));
    config.setEmailPassword(null);
    return config;
  }
}
