package top.asimov.pigeon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import java.net.URI;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.NotificationConfigMapper;
import top.asimov.pigeon.model.entity.NotificationConfig;
import top.asimov.pigeon.service.notification.NotificationTemplateHelper;

@Service
public class NotificationConfigService {

  private final NotificationConfigMapper notificationConfigMapper;
  private final ObjectMapper objectMapper;

  public NotificationConfigService(NotificationConfigMapper notificationConfigMapper,
      ObjectMapper objectMapper) {
    this.notificationConfigMapper = notificationConfigMapper;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public NotificationConfig getCurrentConfig() {
    NotificationConfig config = notificationConfigMapper.selectById(NotificationConfig.SINGLETON_ID);
    if (config == null) {
      return createDefaultConfig();
    }
    normalizeDefaults(config);
    return config;
  }

  @Transactional
  public NotificationConfig ensureExists() {
    NotificationConfig config = notificationConfigMapper.selectById(NotificationConfig.SINGLETON_ID);
    if (config != null) {
      normalizeDefaults(config);
      return config;
    }
    NotificationConfig created = createDefaultConfig();
    notificationConfigMapper.insert(created);
    return created;
  }

  @Transactional
  public NotificationConfig updateNotificationConfig(NotificationConfig incoming) {
    NotificationConfig candidate = buildCandidate(incoming);
    candidate.setUpdatedAt(LocalDateTime.now());
    notificationConfigMapper.updateById(candidate);
    return getCurrentConfig();
  }

  @Transactional
  public NotificationConfig buildCandidate(NotificationConfig incoming) {
    if (incoming == null) {
      throw new BusinessException("notification config is required");
    }

    NotificationConfig existing = ensureExists();
    NotificationConfig candidate = cloneConfig(existing);
    mergeConfig(candidate, incoming);
    validate(candidate);
    return candidate;
  }

  public void normalizeDefaults(NotificationConfig config) {
    if (config == null) {
      return;
    }

    if (config.getId() == null) {
      config.setId(NotificationConfig.SINGLETON_ID);
    }
    if (config.getEmailEnabled() == null) {
      config.setEmailEnabled(false);
    }
    if (config.getEmailStarttlsEnabled() == null) {
      config.setEmailStarttlsEnabled(true);
    }
    if (config.getEmailSslEnabled() == null) {
      config.setEmailSslEnabled(false);
    }
    if (config.getWebhookEnabled() == null) {
      config.setWebhookEnabled(false);
    }

    config.setEmailHost(normalizeOptionalText(config.getEmailHost()));
    config.setEmailUsername(normalizeOptionalText(config.getEmailUsername()));
    config.setEmailFrom(normalizeOptionalText(config.getEmailFrom()));
    config.setEmailTo(normalizeOptionalText(config.getEmailTo()));
    config.setWebhookUrl(normalizeOptionalText(config.getWebhookUrl()));
    config.setWebhookCustomHeaders(normalizeOptionalTextarea(config.getWebhookCustomHeaders()));
    config.setWebhookJsonBody(normalizeOptionalTextarea(config.getWebhookJsonBody()));
    config.setHasEmailPassword(StringUtils.hasText(config.getEmailPassword()));
  }

  private void mergeConfig(NotificationConfig existing, NotificationConfig incoming) {
    existing.setEmailEnabled(Boolean.TRUE.equals(incoming.getEmailEnabled()));
    existing.setEmailHost(normalizeOptionalText(incoming.getEmailHost()));
    existing.setEmailPort(incoming.getEmailPort());
    existing.setEmailUsername(normalizeOptionalText(incoming.getEmailUsername()));
    if (incoming.getEmailPassword() != null) {
      if (StringUtils.hasText(incoming.getEmailPassword())) {
        existing.setEmailPassword(incoming.getEmailPassword());
      } else if (!Boolean.TRUE.equals(incoming.getHasEmailPassword())) {
        existing.setEmailPassword(null);
      }
    }
    existing.setEmailFrom(normalizeOptionalText(incoming.getEmailFrom()));
    existing.setEmailTo(normalizeOptionalText(incoming.getEmailTo()));
    existing.setEmailStarttlsEnabled(Boolean.TRUE.equals(incoming.getEmailStarttlsEnabled()));
    existing.setEmailSslEnabled(Boolean.TRUE.equals(incoming.getEmailSslEnabled()));
    existing.setWebhookEnabled(Boolean.TRUE.equals(incoming.getWebhookEnabled()));
    existing.setWebhookUrl(normalizeOptionalText(incoming.getWebhookUrl()));
    existing.setWebhookCustomHeaders(normalizeOptionalTextarea(incoming.getWebhookCustomHeaders()));
    existing.setWebhookJsonBody(normalizeOptionalTextarea(incoming.getWebhookJsonBody()));
    normalizeDefaults(existing);
  }

  private void validate(NotificationConfig config) {
    if (Boolean.TRUE.equals(config.getEmailEnabled())) {
      validateNonBlank(config.getEmailHost(), "notification email host is required");
      validateRange(config.getEmailPort());
      validateNonBlank(config.getEmailFrom(), "notification email from is required");
      validateNonBlank(config.getEmailTo(), "notification email to is required");
      validateEmailAddress(config.getEmailFrom(), "notification email from is invalid");
      validateEmailAddress(config.getEmailTo(), "notification email to is invalid");
      if (StringUtils.hasText(config.getEmailPassword())
          && !StringUtils.hasText(config.getEmailUsername())) {
        throw new BusinessException("notification email username is required when password is set");
      }
      if (Boolean.TRUE.equals(config.getEmailStarttlsEnabled())
          && Boolean.TRUE.equals(config.getEmailSslEnabled())) {
        throw new BusinessException("notification email starttls and ssl cannot both be enabled");
      }
    }

    if (Boolean.TRUE.equals(config.getWebhookEnabled())) {
      validateNonBlank(config.getWebhookUrl(), "notification webhook url is required");
      validateHttpUrl(config.getWebhookUrl());
      NotificationTemplateHelper.parseHeaders(config.getWebhookCustomHeaders(), null);
      validateWebhookJsonBody(config.getWebhookJsonBody());
    }
  }

  private void validateWebhookJsonBody(String template) {
    if (!StringUtils.hasText(template)) {
      return;
    }
    try {
      JsonNode parsed = objectMapper.readTree(template);
      if (parsed == null || (!parsed.isObject() && !parsed.isArray())) {
        throw new BusinessException("notification webhook json body must be a json object or array");
      }
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BusinessException("notification webhook json body is invalid");
    }
  }

  private void validateRange(Integer value) {
    if (value == null || value < 1 || value > 65535) {
      throw new BusinessException("notification email port out of range");
    }
  }

  private void validateNonBlank(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new BusinessException(message);
    }
  }

  private void validateEmailAddress(String value, String message) {
    try {
      InternetAddress address = new InternetAddress(value);
      address.validate();
    } catch (Exception ex) {
      throw new BusinessException(message);
    }
  }

  private void validateHttpUrl(String value) {
    try {
      URI uri = URI.create(value);
      String scheme = uri.getScheme();
      if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
        throw new BusinessException("notification webhook url is invalid");
      }
      if (!StringUtils.hasText(uri.getHost())) {
        throw new BusinessException("notification webhook url is invalid");
      }
    } catch (Exception ex) {
      if (ex instanceof BusinessException businessException) {
        throw businessException;
      }
      throw new BusinessException("notification webhook url is invalid");
    }
  }

  private NotificationConfig createDefaultConfig() {
    NotificationConfig config = NotificationConfig.builder()
        .id(NotificationConfig.SINGLETON_ID)
        .emailEnabled(false)
        .emailStarttlsEnabled(true)
        .emailSslEnabled(false)
        .webhookEnabled(false)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
    normalizeDefaults(config);
    return config;
  }

  private NotificationConfig cloneConfig(NotificationConfig source) {
    if (source == null) {
      return null;
    }
    return NotificationConfig.builder()
        .id(source.getId())
        .emailEnabled(source.getEmailEnabled())
        .emailHost(source.getEmailHost())
        .emailPort(source.getEmailPort())
        .emailUsername(source.getEmailUsername())
        .emailPassword(source.getEmailPassword())
        .emailFrom(source.getEmailFrom())
        .emailTo(source.getEmailTo())
        .emailStarttlsEnabled(source.getEmailStarttlsEnabled())
        .emailSslEnabled(source.getEmailSslEnabled())
        .webhookEnabled(source.getWebhookEnabled())
        .webhookUrl(source.getWebhookUrl())
        .webhookCustomHeaders(source.getWebhookCustomHeaders())
        .webhookJsonBody(source.getWebhookJsonBody())
        .createdAt(source.getCreatedAt())
        .updatedAt(source.getUpdatedAt())
        .hasEmailPassword(source.getHasEmailPassword())
        .build();
  }

  private String normalizeOptionalText(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private String normalizeOptionalTextarea(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String normalized = value.replace("\r\n", "\n").trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
