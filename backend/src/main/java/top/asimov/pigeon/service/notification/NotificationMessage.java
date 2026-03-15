package top.asimov.pigeon.service.notification;

import java.util.Map;

public record NotificationMessage(
    String subject,
    String textBody,
    String htmlBody,
    Map<String, String> templateVariables,
    Object webhookPayload
) {

}
