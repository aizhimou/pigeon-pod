package top.asimov.pigeon.service.notification;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import lombok.extern.log4j.Log4j2;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.entity.NotificationConfig;

@Log4j2
@Service
public class SmtpNotificationSender implements NotificationSender {

  @Override
  public String channel() {
    return "EMAIL";
  }

  @Override
  public boolean isEnabled(NotificationConfig config) {
    return config != null && Boolean.TRUE.equals(config.getEmailEnabled());
  }

  @Override
  public void send(NotificationMessage message, NotificationConfig config) {
    JavaMailSenderImpl sender = buildSender(config);
    MimeMessage mail = sender.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(mail, false, StandardCharsets.UTF_8.name());
      helper.setFrom(config.getEmailFrom());
      helper.setTo(config.getEmailTo());
      helper.setSubject(message.subject());
      helper.setText(message.htmlBody(), true);
      sender.send(mail);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to send HTML email notification", exception);
    }
    log.info("Notification email sent to {}", config.getEmailTo());
  }

  private JavaMailSenderImpl buildSender(NotificationConfig config) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(config.getEmailHost());
    if (config.getEmailPort() != null) {
      sender.setPort(config.getEmailPort());
    }
    sender.setProtocol("smtp");
    sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
    if (StringUtils.hasText(config.getEmailUsername())) {
      sender.setUsername(config.getEmailUsername());
    }
    if (StringUtils.hasText(config.getEmailPassword())) {
      sender.setPassword(config.getEmailPassword());
    }

    Properties properties = sender.getJavaMailProperties();
    properties.put("mail.transport.protocol", "smtp");
    properties.put("mail.smtp.auth",
        String.valueOf(StringUtils.hasText(config.getEmailUsername())));
    properties.put("mail.smtp.starttls.enable",
        String.valueOf(Boolean.TRUE.equals(config.getEmailStarttlsEnabled())));
    properties.put("mail.smtp.ssl.enable",
        String.valueOf(Boolean.TRUE.equals(config.getEmailSslEnabled())));
    properties.put("mail.smtp.connectiontimeout", "5000");
    properties.put("mail.smtp.timeout", "5000");
    properties.put("mail.smtp.writetimeout", "5000");
    return sender;
  }
}
