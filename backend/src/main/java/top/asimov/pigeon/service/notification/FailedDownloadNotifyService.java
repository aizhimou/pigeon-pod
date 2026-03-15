package top.asimov.pigeon.service.notification;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.dto.EpisodeFeedReference;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.NotificationConfig;
import top.asimov.pigeon.service.EpisodeService;
import top.asimov.pigeon.service.NotificationConfigService;
import top.asimov.pigeon.service.SystemConfigService;
import top.asimov.pigeon.util.EpisodeRetryPolicy;

@Log4j2
@Service
public class FailedDownloadNotifyService {

  private static final int FAILED_NOTIFICATION_BATCH_SIZE = 100;

  private final NotificationConfigService notificationConfigService;
  private final SystemConfigService systemConfigService;
  private final EpisodeService episodeService;
  private final EpisodeMapper episodeMapper;
  private final List<NotificationSender> notificationSenders;

  public FailedDownloadNotifyService(NotificationConfigService notificationConfigService,
      SystemConfigService systemConfigService, EpisodeService episodeService,
      EpisodeMapper episodeMapper, List<NotificationSender> notificationSenders) {
    this.notificationConfigService = notificationConfigService;
    this.systemConfigService = systemConfigService;
    this.episodeService = episodeService;
    this.episodeMapper = episodeMapper;
    this.notificationSenders = notificationSenders;
  }

  public int notifyFailedDownloadsIfNeeded() {
    NotificationConfig config = notificationConfigService.getCurrentConfig();
    List<NotificationSender> enabledSenders = getEnabledSenders(config);
    if (enabledSenders.isEmpty()) {
      return 0;
    }

    List<Episode> candidates = episodeService.getFailedNotificationCandidates(
        EpisodeRetryPolicy.MAX_AUTO_RETRY_ATTEMPTS, FAILED_NOTIFICATION_BATCH_SIZE);
    if (candidates.isEmpty()) {
      return 0;
    }

    String baseUrl = systemConfigService.getCurrentConfig().getBaseUrl();
    LocalDateTime now = LocalDateTime.now();
    NotificationMessage message = buildFailedDigestMessage(candidates, baseUrl, now);
    boolean delivered = false;
    List<String> failedChannels = new ArrayList<>();

    for (NotificationSender sender : enabledSenders) {
      try {
        sender.send(message, config);
        delivered = true;
      } catch (Exception exception) {
        failedChannels.add(sender.channel());
        log.warn("Failed to deliver failed-download digest via {}", sender.channel(), exception);
      }
    }

    if (delivered) {
      episodeService.markFailureNotificationSent(
          candidates.stream().map(Episode::getId).toList(), now);
    }
    if (!failedChannels.isEmpty()) {
      log.warn("Failed-download digest not delivered on some channels: {}", failedChannels);
    }
    return delivered ? candidates.size() : 0;
  }

  public void sendTestEmail(NotificationConfig incoming) {
    NotificationConfig candidate = notificationConfigService.buildCandidate(incoming);
    NotificationSender sender = requireSender("EMAIL", candidate);
    String baseUrl = systemConfigService.getCurrentConfig().getBaseUrl();
    NotificationMessage message = buildTestMessage(baseUrl, "EMAIL");
    sender.send(message, candidate);
  }

  public void sendTestWebhook(NotificationConfig incoming) {
    NotificationConfig candidate = notificationConfigService.buildCandidate(incoming);
    NotificationSender sender = requireSender("WEBHOOK", candidate);
    String baseUrl = systemConfigService.getCurrentConfig().getBaseUrl();
    NotificationMessage message = buildTestMessage(baseUrl, "WEBHOOK");
    sender.send(message, candidate);
  }

  private NotificationSender requireSender(String channel, NotificationConfig config) {
    return notificationSenders.stream()
        .filter(sender -> channel.equals(sender.channel()))
        .filter(sender -> sender.isEnabled(config))
        .findFirst()
        .orElseThrow(() -> new BusinessException("notification " + channel.toLowerCase() + " is not enabled"));
  }

  private List<NotificationSender> getEnabledSenders(NotificationConfig config) {
    return notificationSenders.stream()
        .filter(sender -> sender.isEnabled(config))
        .toList();
  }

  private NotificationMessage buildTestMessage(String baseUrl, String channel) {
    LocalDateTime now = LocalDateTime.now();
    String timestamp = toIsoUtc(now);
    String subject = "[PigeonPod] Notification test";
    StringBuilder text = new StringBuilder();
    text.append("This is a test notification from PigeonPod.\n\n");
    text.append("Channel: ").append(channel).append('\n');
    text.append("Generated at: ").append(timestamp).append('\n');
    if (StringUtils.hasText(baseUrl)) {
      text.append("Base URL: ").append(baseUrl).append('\n');
    }

    Map<String, String> variables = buildTemplateVariables(subject, text.toString(), timestamp, baseUrl, 1);
    List<Map<String, Object>> payload = new ArrayList<>();
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("title", subject);
    item.put("content", text.toString());
    item.put("generatedAt", timestamp);
    item.put("baseUrl", baseUrl);
    item.put("total", 1);
    payload.add(item);
    String html = buildTestHtml(subject, channel, timestamp, baseUrl);
    return new NotificationMessage(subject, text.toString(), html, variables, payload);
  }

  private NotificationMessage buildFailedDigestMessage(List<Episode> episodes, String baseUrl,
      LocalDateTime generatedAt) {
    String timestamp = toIsoUtc(generatedAt);
    String subject = "[PigeonPod] Failed downloads require manual attention (" + episodes.size() + ")";
    StringBuilder text = new StringBuilder();
    text.append("PigeonPod detected ")
        .append(episodes.size())
        .append(" download task(s) that exhausted automatic retries and still require manual attention.\n\n");
    text.append("Generated at: ").append(timestamp).append('\n');
    if (StringUtils.hasText(baseUrl)) {
      text.append("Base URL: ").append(baseUrl).append('\n');
    }
    text.append('\n');

    List<Map<String, Object>> payloadItems = new ArrayList<>();
    int index = 1;
    for (Episode episode : episodes) {
      EpisodeFeedReference feedReference = episodeMapper.getFeedReferenceByEpisodeId(episode.getId());
      String feedName = feedReference == null ? null : feedReference.getFeedName();
      String feedUrl = buildFeedConsoleUrl(baseUrl, feedReference);
      String errorSummary = summarizeError(episode.getErrorLog());
      String title = defaultText(episode.getTitle(), episode.getId());

      text.append(index).append(". ");
      if (StringUtils.hasText(feedName)) {
        text.append('[').append(feedName).append("] ");
      }
      text.append(title).append('\n');
      if (StringUtils.hasText(feedUrl)) {
        text.append("   Feed URL: ").append(feedUrl).append('\n');
      }
      text.append("   Retry count: ").append(episode.getRetryNumber()).append('\n');
      if (episode.getPublishedAt() != null) {
        text.append("   Published at: ").append(episode.getPublishedAt()).append('\n');
      }
      if (StringUtils.hasText(errorSummary)) {
        text.append("   Last error: ").append(errorSummary).append('\n');
      }
      text.append('\n');

      Map<String, Object> itemPayload = new LinkedHashMap<>();
      itemPayload.put("title", title);
      itemPayload.put("feedName", feedName);
      itemPayload.put("feedURL", feedUrl);
      itemPayload.put("retryNumber", episode.getRetryNumber());
      itemPayload.put("publishedAt", episode.getPublishedAt() == null ? null : episode.getPublishedAt().toString());
      itemPayload.put("error", errorSummary);
      payloadItems.add(itemPayload);
      index++;
    }

    Map<String, String> variables = buildTemplateVariables(subject, text.toString(), timestamp, baseUrl,
        episodes.size());
    String html = buildDigestHtml(subject, timestamp, baseUrl, payloadItems);
    return new NotificationMessage(subject, text.toString(), html, variables, payloadItems);
  }

  private Map<String, String> buildTemplateVariables(String title, String content, String generatedAt,
      String baseUrl, int total) {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("title", title);
    variables.put("content", content);
    variables.put("generatedAt", generatedAt);
    variables.put("baseUrl", baseUrl == null ? "" : baseUrl);
    variables.put("total", String.valueOf(total));
    return variables;
  }

  private String buildFeedConsoleUrl(String baseUrl, EpisodeFeedReference feedReference) {
    if (!StringUtils.hasText(baseUrl) || feedReference == null) {
      return null;
    }
    if (!StringUtils.hasText(feedReference.getFeedType())
        || !StringUtils.hasText(feedReference.getFeedId())) {
      return null;
    }
    return baseUrl + "/" + feedReference.getFeedType().trim().toLowerCase() + "/"
        + feedReference.getFeedId().trim();
  }

  private String summarizeError(String rawError) {
    if (!StringUtils.hasText(rawError)) {
      return null;
    }
    String normalized = rawError.replace('\r', ' ').replace('\n', ' ').trim();
    if (normalized.length() <= 280) {
      return normalized;
    }
    return normalized.substring(0, 280) + "...";
  }

  private String defaultText(String preferred, String fallback) {
    if (StringUtils.hasText(preferred)) {
      return preferred.trim();
    }
    return fallback;
  }

  private String toIsoUtc(LocalDateTime value) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value.atOffset(ZoneOffset.UTC));
  }

  private String buildTestHtml(String subject, String channel, String generatedAt, String baseUrl) {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f5f7fb;\">")
        .append("<div style=\"max-width:680px;margin:0 auto;padding:24px 16px;\">")
        .append("<div style=\"background:#ffffff;border:1px solid #e5e7eb;border-radius:16px;overflow:hidden;\">")
        .append("<div style=\"padding:28px 28px 20px;border-bottom:1px solid #e5e7eb;\">")
        .append("<div style=\"font:600 13px/1.4 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;")
        .append("letter-spacing:.08em;text-transform:uppercase;color:#2563eb;\">PigeonPod</div>")
        .append("<div style=\"margin-top:10px;font:700 28px/1.2 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;")
        .append("color:#111827;\">").append(escapeHtml(subject)).append("</div>")
        .append("</div>")
        .append("<div style=\"padding:28px;\">")
        .append("<div style=\"font:400 18px/1.7 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#111827;\">")
        .append("This is a test notification from PigeonPod.</div>")
        .append("<div style=\"margin-top:20px;padding:18px 20px;background:#f8fafc;border-radius:12px;\">")
        .append(metaRowHtml("Channel", channel))
        .append(metaRowHtml("Generated at", generatedAt));
    if (StringUtils.hasText(baseUrl)) {
      html.append(metaLinkRowHtml("Base URL", baseUrl, baseUrl));
    }
    html.append("</div></div></div></div></body></html>");
    return html.toString();
  }

  private String buildDigestHtml(String subject, String generatedAt, String baseUrl,
      List<Map<String, Object>> payloadItems) {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f5f7fb;\">")
        .append("<div style=\"max-width:680px;margin:0 auto;padding:24px 16px;\">")
        .append("<div style=\"background:#ffffff;border:1px solid #e5e7eb;border-radius:16px;overflow:hidden;\">")
        .append("<div style=\"padding:28px 28px 20px;border-bottom:1px solid #e5e7eb;\">")
        .append("<div style=\"font:600 13px/1.4 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;")
        .append("letter-spacing:.08em;text-transform:uppercase;color:#2563eb;\">PigeonPod</div>")
        .append("<div style=\"margin-top:10px;font:700 28px/1.2 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;")
        .append("color:#111827;\">").append(escapeHtml(subject)).append("</div>")
        .append("</div>")
        .append("<div style=\"padding:28px;\">")
        .append("<div style=\"font:400 18px/1.7 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#111827;\">")
        .append("PigeonPod detected ").append(payloadItems.size())
        .append(" download task(s) that exhausted automatic retries and still require manual attention.</div>")
        .append("<div style=\"margin-top:20px;padding:18px 20px;background:#f8fafc;border-radius:12px;\">")
        .append(metaRowHtml("Generated at", generatedAt));
    if (StringUtils.hasText(baseUrl)) {
      html.append(metaLinkRowHtml("Base URL", baseUrl, baseUrl));
    }
    html.append("</div>")
        .append("<div style=\"margin-top:20px;\">");

    for (Map<String, Object> item : payloadItems) {
      String title = asString(item.get("title"));
      String feedName = asString(item.get("feedName"));
      String feedUrl = asString(item.get("feedURL"));
      String retryNumber = asString(item.get("retryNumber"));
      String publishedAt = asString(item.get("publishedAt"));
      String error = asString(item.get("error"));

      html.append("<div style=\"margin-top:16px;padding:20px;border:1px solid #e5e7eb;border-radius:14px;background:#ffffff;\">");
      if (StringUtils.hasText(feedName)) {
        html.append("<div style=\"font:600 12px/1.4 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;")
            .append("letter-spacing:.04em;text-transform:uppercase;color:#6b7280;\">")
            .append(escapeHtml(feedName)).append("</div>");
      }
      html.append("<div style=\"margin-top:6px;font:700 20px/1.35 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;")
          .append("color:#111827;\">").append(escapeHtml(title)).append("</div>")
          .append("<div style=\"margin-top:14px;\">");
      if (StringUtils.hasText(feedUrl)) {
        html.append(metaLinkRowHtml("Feed URL", feedUrl, feedUrl));
      }
      if (StringUtils.hasText(retryNumber)) {
        html.append(metaRowHtml("Retry count", retryNumber));
      }
      if (StringUtils.hasText(publishedAt)) {
        html.append(metaRowHtml("Published at", publishedAt));
      }
      if (StringUtils.hasText(error)) {
        html.append(metaRowHtml("Last error", error));
      }
      html.append("</div></div>");
    }

    html.append("</div></div></div></div></body></html>");
    return html.toString();
  }

  private String metaRowHtml(String label, String value) {
    return "<div style=\"margin-top:8px;font:400 15px/1.6 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
        + "color:#111827;\"><span style=\"font-weight:600;color:#4b5563;\">"
        + escapeHtml(label) + ":</span> " + escapeHtml(value) + "</div>";
  }

  private String metaLinkRowHtml(String label, String url, String text) {
    return "<div style=\"margin-top:8px;font:400 15px/1.6 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
        + "color:#111827;\"><span style=\"font-weight:600;color:#4b5563;\">"
        + escapeHtml(label) + ":</span> <a href=\"" + escapeHtmlAttribute(url)
        + "\" style=\"color:#2563eb;text-decoration:underline;\">" + escapeHtml(text) + "</a></div>";
  }

  private String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private String escapeHtmlAttribute(String value) {
    return escapeHtml(value);
  }
}
