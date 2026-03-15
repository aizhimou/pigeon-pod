package top.asimov.pigeon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.util.SaResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.entity.NotificationConfig;
import top.asimov.pigeon.service.NotificationMgmtService;

@SaCheckLogin
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

  private final NotificationMgmtService notificationMgmtService;

  public NotificationController(NotificationMgmtService notificationMgmtService) {
    this.notificationMgmtService = notificationMgmtService;
  }

  @GetMapping("/config")
  public SaResult getNotificationConfig() {
    return SaResult.data(notificationMgmtService.getNotificationConfig());
  }

  @PostMapping("/config")
  public SaResult updateNotificationConfig(@RequestBody NotificationConfig config) {
    return SaResult.data(notificationMgmtService.updateNotificationConfig(config));
  }

  @PostMapping("/test/email")
  public SaResult testNotificationEmail(@RequestBody NotificationConfig config) {
    notificationMgmtService.testNotificationEmail(config);
    return SaResult.ok();
  }

  @PostMapping("/test/webhook")
  public SaResult testNotificationWebhook(@RequestBody NotificationConfig config) {
    notificationMgmtService.testNotificationWebhook(config);
    return SaResult.ok();
  }
}
