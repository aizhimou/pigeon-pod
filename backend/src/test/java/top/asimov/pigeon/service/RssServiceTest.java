package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import top.asimov.pigeon.config.AppBaseUrlResolver;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;

@ExtendWith(MockitoExtension.class)
class RssServiceTest {

  @Mock
  private ChannelService channelService;

  @Mock
  private EpisodeService episodeService;

  @Mock
  private PlaylistService playlistService;

  @Mock
  private MediaService mediaService;

  @Mock
  private MessageSource messageSource;

  @Mock
  private AppBaseUrlResolver appBaseUrlResolver;

  @InjectMocks
  private RssService rssService;

  private TimeZone originalTimeZone;

  @BeforeEach
  void setUp() {
    originalTimeZone = TimeZone.getDefault();
  }

  @AfterEach
  void tearDown() {
    TimeZone.setDefault(originalTimeZone);
  }

  @Test
  void testPubDatePreservesExactUtcInstantWhenServerInAsiaShanghai() throws Exception {
    // 模拟服务端位于东八区 (Asia/Shanghai, UTC+8)
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

    Channel channel = new Channel();
    channel.setId("bilichannel");
    channel.setTitle("Test Channel");
    channel.setSource("BILIBILI");
    channel.setDescription("Channel Description");

    // 假设平台在本地时间 2026-09-30 20:00:00 发布（对应实际绝对时间 12:00:00Z）
    LocalDateTime publishedAt = LocalDateTime.of(2026, 9, 30, 20, 0, 0);

    Episode episode = Episode.builder()
        .id("BV1test12345")
        .channelId("bilichannel")
        .title("Test Episode 1")
        .description("Test Description")
        .publishedAt(publishedAt)
        .mediaFilePath("/data/audio/BV1test12345.mp3")
        .mediaType("audio/mpeg")
        .duration("00:30:00")
        .durationSeconds(1800)
        .build();

    when(channelService.findChannelByIdentification("bilichannel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel))
        .thenReturn(List.of(episode));
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");
    when(mediaService.resolveMediaUrlForRss(eq("https://pigeon.example.com"), eq(episode)))
        .thenReturn("https://pigeon.example.com/media/audio/BV1test12345.mp3");
    when(mediaService.resolveMediaLengthForRss(eq(episode))).thenReturn(1024000L);
    when(mediaService.getAvailableSubtitles(any())).thenReturn(Collections.emptyList());

    String rssXml = rssService.generateRssFeed("bilichannel");

    assertNotNull(rssXml);
    // 验证 RSS 中的 pubDate 为正确的 UTC 绝对时间：12:00:00 GMT
    assertTrue(rssXml.contains("<pubDate>Wed, 30 Sep 2026 12:00:00 GMT</pubDate>"),
        "pubDate must be converted to UTC instant from system default timezone");
    // 验证绝不包含将本地墙上时钟当成 UTC 的错误 pubDate (20:00:00 GMT)
    assertFalse(rssXml.contains("<pubDate>Wed, 30 Sep 2026 20:00:00 GMT</pubDate>"),
        "pubDate must not treat local time as UTC");
  }

  @Test
  void testPubDatePreservesExactUtcInstantWhenServerInNewYork() throws Exception {
    // 模拟服务端位于美东时间 (America/New_York, 9月为 EDT 夏令时 UTC-4)
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

    Channel channel = new Channel();
    channel.setId("ytchannel");
    channel.setTitle("NY Channel");
    channel.setSource("YOUTUBE");
    channel.setDescription("NY Channel Description");

    // 假设当地时间 2026-09-30 10:00:00 EDT (对应 UTC 14:00:00Z)
    LocalDateTime publishedAt = LocalDateTime.of(2026, 9, 30, 10, 0, 0);

    Episode episode = Episode.builder()
        .id("video_ny_01")
        .channelId("ytchannel")
        .title("NY Video")
        .description("NY Video Description")
        .publishedAt(publishedAt)
        .mediaFilePath("/data/audio/video_ny_01.mp3")
        .mediaType("audio/mpeg")
        .duration("00:15:00")
        .durationSeconds(900)
        .build();

    when(channelService.findChannelByIdentification("ytchannel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel))
        .thenReturn(List.of(episode));
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");
    when(mediaService.resolveMediaUrlForRss(eq("https://pigeon.example.com"), eq(episode)))
        .thenReturn("https://pigeon.example.com/media/audio/video_ny_01.mp3");
    when(mediaService.resolveMediaLengthForRss(eq(episode))).thenReturn(512000L);
    when(mediaService.getAvailableSubtitles(any())).thenReturn(Collections.emptyList());

    String rssXml = rssService.generateRssFeed("ytchannel");

    assertNotNull(rssXml);
    // 验证 RSS 中的 pubDate 为正确的 UTC 时间：14:00:00 GMT
    assertTrue(rssXml.contains("<pubDate>Wed, 30 Sep 2026 14:00:00 GMT</pubDate>"),
        "pubDate must be converted to UTC instant from New York EDT timezone");
  }
}
