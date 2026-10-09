package top.asimov.pigeon.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.dto.EpisodeFeedReference;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.service.EpisodeService;
import top.asimov.pigeon.service.NotificationConfigService;
import top.asimov.pigeon.service.SystemConfigService;

class FailedDownloadNotifyServiceTest {

  private NotificationConfigService notificationConfigService;
  private SystemConfigService systemConfigService;
  private EpisodeService episodeService;
  private EpisodeMapper episodeMapper;
  private FailedDownloadNotifyService notifyService;

  @BeforeEach
  void setUp() {
    notificationConfigService = mock(NotificationConfigService.class);
    systemConfigService = mock(SystemConfigService.class);
    episodeService = mock(EpisodeService.class);
    episodeMapper = mock(EpisodeMapper.class);

    notifyService = new FailedDownloadNotifyService(
        notificationConfigService,
        systemConfigService,
        episodeService,
        episodeMapper,
        Collections.emptyList()
    );
  }

  @Test
  void buildTestMessage_createsObjectRootPayloadWithCompatibleKeys() {
    NotificationMessage message = notifyService.buildTestMessage("https://example.com", "WEBHOOK");

    assertNotNull(message);
    assertInstanceOf(Map.class, message.webhookPayload());

    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) message.webhookPayload();

    assertEquals("[PigeonPod] Notification test", payload.get("title"));
    assertNotNull(payload.get("content"));
    assertEquals(payload.get("content"), payload.get("text"));
    assertEquals("https://example.com", payload.get("baseUrl"));
    assertEquals(1, payload.get("total"));

    assertInstanceOf(List.class, payload.get("items"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
    assertEquals(1, items.size());
    assertEquals("[PigeonPod] Notification test", items.get(0).get("title"));
  }

  @Test
  void buildFailedDigestMessage_createsObjectRootPayloadWithCompatibleKeys() {
    Episode episode = new Episode();
    episode.setId("ep-1");
    episode.setTitle("Test Episode");
    episode.setRetryNumber(3);
    episode.setErrorLog("ERROR: HTTP Error 403: Forbidden");
    episode.setPublishedAt(LocalDateTime.of(2026, 3, 10, 8, 0));

    EpisodeFeedReference feedRef = new EpisodeFeedReference();
    feedRef.setFeedId("feed-1");
    feedRef.setFeedName("Sample Podcast");
    feedRef.setFeedType("CHANNEL");

    when(episodeMapper.getFeedReferenceByEpisodeId(anyString())).thenReturn(feedRef);

    NotificationMessage message = notifyService.buildFailedDigestMessage(
        List.of(episode), "https://example.com", LocalDateTime.of(2026, 3, 10, 9, 0));

    assertNotNull(message);
    assertInstanceOf(Map.class, message.webhookPayload());

    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) message.webhookPayload();

    assertTrue(payload.get("title").toString().contains("Failed downloads"));
    assertNotNull(payload.get("content"));
    assertEquals(payload.get("content"), payload.get("text"));
    assertEquals("https://example.com", payload.get("baseUrl"));
    assertEquals(1, payload.get("total"));

    assertInstanceOf(List.class, payload.get("items"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
    assertEquals(1, items.size());
    assertEquals("Test Episode", items.get(0).get("title"));
    assertEquals("Sample Podcast", items.get(0).get("feedName"));
    assertEquals("https://example.com/channel/feed-1", items.get(0).get("feedURL"));
  }
}
