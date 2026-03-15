package top.asimov.pigeon.service.notification;

import top.asimov.pigeon.model.entity.NotificationConfig;

public interface NotificationSender {

  String channel();

  boolean isEnabled(NotificationConfig config);

  void send(NotificationMessage message, NotificationConfig config);
}
