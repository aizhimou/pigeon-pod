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

  @Mock
  private FeedDefaultsService feedDefaultsService;

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

  @Test
  void testEpisodeCoverUsesLocalUrlWhenResolved() throws Exception {
    Channel channel = new Channel();
    channel.setId("ytchannel");
    channel.setTitle("Test Channel");
    channel.setSource("YOUTUBE");
    channel.setDescription("Test Channel Description");

    Episode episode = Episode.builder()
        .id("video_001")
        .channelId("ytchannel")
        .title("Episode With Local Cover")
        .publishedAt(LocalDateTime.of(2026, 10, 1, 12, 0, 0))
        .mediaFilePath("/data/audio/video_001.mp3")
        .mediaType("audio/mpeg")
        .maxCoverUrl("https://i.ytimg.com/vi/video_001/maxresdefault.jpg")
        .build();

    when(channelService.findChannelByIdentification("ytchannel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel))
        .thenReturn(List.of(episode));
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");
    when(mediaService.resolveMediaUrlForRss(eq("https://pigeon.example.com"), eq(episode)))
        .thenReturn("https://pigeon.example.com/media/video_001.mp3");
    when(mediaService.resolveMediaLengthForRss(eq(episode))).thenReturn(1000L);
    when(mediaService.getAvailableSubtitles(any())).thenReturn(Collections.emptyList());
    when(mediaService.resolveEpisodeCoverUrlForRss(eq("https://pigeon.example.com"), eq(episode)))
        .thenReturn("https://pigeon.example.com/media/video_001/cover");

    String rssXml = rssService.generateRssFeed("ytchannel");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("https://pigeon.example.com/media/video_001/cover"),
        "RSS item must contain the resolved local cover url");
    assertFalse(rssXml.contains("https://i.ytimg.com/vi/video_001/maxresdefault.jpg"),
        "RSS item must not use remote YouTube cover when local cover was resolved");
  }

  @Test
  void testEpisodeCoverFallsBackToRemoteUrlWhenNotLocallyAvailable() throws Exception {
    Channel channel = new Channel();
    channel.setId("ytchannel");
    channel.setTitle("Test Channel");
    channel.setSource("YOUTUBE");
    channel.setDescription("Test Channel Description");

    Episode episode = Episode.builder()
        .id("video_002")
        .channelId("ytchannel")
        .title("Episode With Fallback Cover")
        .publishedAt(LocalDateTime.of(2026, 10, 1, 12, 0, 0))
        .mediaFilePath("/data/audio/video_002.mp3")
        .mediaType("audio/mpeg")
        .maxCoverUrl("https://i.ytimg.com/vi/video_002/maxresdefault.jpg")
        .build();

    when(channelService.findChannelByIdentification("ytchannel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel))
        .thenReturn(List.of(episode));
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");
    when(mediaService.resolveMediaUrlForRss(eq("https://pigeon.example.com"), eq(episode)))
        .thenReturn("https://pigeon.example.com/media/video_002.mp3");
    when(mediaService.resolveMediaLengthForRss(eq(episode))).thenReturn(1000L);
    when(mediaService.getAvailableSubtitles(any())).thenReturn(Collections.emptyList());
    when(mediaService.resolveEpisodeCoverUrlForRss(eq("https://pigeon.example.com"), eq(episode)))
        .thenReturn("https://i.ytimg.com/vi/video_002/maxresdefault.jpg");

    String rssXml = rssService.generateRssFeed("ytchannel");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("https://i.ytimg.com/vi/video_002/maxresdefault.jpg"),
        "RSS item must fall back to remote cover URL when local cover is not available");
  }

  @Test
  void testEpisodeCoverWhenBaseUrlHasNoProtocol() throws Exception {
    Channel channel = new Channel();
    channel.setId("ytchannel");
    channel.setTitle("Test Channel");
    channel.setSource("YOUTUBE");
    channel.setDescription("Test Channel Description");

    Episode episode = Episode.builder()
        .id("video_003")
        .channelId("ytchannel")
        .title("Episode With No Protocol Base URL")
        .publishedAt(LocalDateTime.of(2026, 10, 1, 12, 0, 0))
        .mediaFilePath("/data/audio/video_003.mp3")
        .mediaType("audio/mpeg")
        .maxCoverUrl("https://i.ytimg.com/vi/video_003/maxresdefault.jpg")
        .build();

    when(channelService.findChannelByIdentification("ytchannel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel))
        .thenReturn(List.of(episode));
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("localhost:8080");
    when(mediaService.resolveMediaUrlForRss(eq("localhost:8080"), eq(episode)))
        .thenReturn("localhost:8080/media/video_003.mp3");
    when(mediaService.resolveMediaLengthForRss(eq(episode))).thenReturn(1000L);
    when(mediaService.getAvailableSubtitles(any())).thenReturn(Collections.emptyList());
    when(mediaService.resolveEpisodeCoverUrlForRss(eq("localhost:8080"), eq(episode)))
        .thenReturn("localhost:8080/media/video_003/cover");

    String rssXml = rssService.generateRssFeed("ytchannel");

    assertNotNull(rssXml);
    // Should automatically normalize to http://localhost:8080/media/video_003/cover and render itunes:image
    assertTrue(rssXml.contains("<itunes:image href=\"http://localhost:8080/media/video_003/cover\" />"),
        "RSS item must contain normalized itunes:image when baseUrl has no protocol");
  }

  @Test
  void testRssFeedContainsCustomLanguageWhenSetOnChannel() throws Exception {
    Channel channel = new Channel();
    channel.setId("lang_channel");
    channel.setTitle("Language Test Channel");
    channel.setSource("YOUTUBE");
    channel.setDescription("Test Description");
    channel.setLanguage("zh-cn");

    when(channelService.findChannelByIdentification("lang_channel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel)).thenReturn(Collections.emptyList());
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");

    String rssXml = rssService.generateRssFeed("lang_channel");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<language>zh-cn</language>"),
        "RSS channel must contain the custom language specified on the channel");
  }

  @Test
  void testRssFeedFallsBackToFeedDefaultsWhenChannelLanguageNull() throws Exception {
    Channel channel = new Channel();
    channel.setId("default_lang_channel");
    channel.setTitle("Default Language Channel");
    channel.setSource("YOUTUBE");
    channel.setDescription("Test Description");
    channel.setLanguage(null);

    top.asimov.pigeon.model.entity.FeedDefaults defaults = top.asimov.pigeon.model.entity.FeedDefaults.builder()
        .language("ja")
        .build();

    when(channelService.findChannelByIdentification("default_lang_channel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel)).thenReturn(Collections.emptyList());
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");
    when(feedDefaultsService.getEffectiveFeedDefaults()).thenReturn(defaults);

    String rssXml = rssService.generateRssFeed("default_lang_channel");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<language>ja</language>"),
        "RSS channel must fallback to feedDefaults language when channel language is null");
  }

  @Test
  void testRssFeedFallsBackToBuiltinDefaultWhenAllEmpty() throws Exception {
    Channel channel = new Channel();
    channel.setId("fallback_lang_channel");
    channel.setTitle("Fallback Language Channel");
    channel.setSource("YOUTUBE");
    channel.setDescription("Test Description");
    channel.setLanguage(null);

    when(channelService.findChannelByIdentification("fallback_lang_channel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel)).thenReturn(Collections.emptyList());
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");
    when(feedDefaultsService.getEffectiveFeedDefaults()).thenReturn(null);

    String rssXml = rssService.generateRssFeed("fallback_lang_channel");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<language>en</language>"),
        "RSS channel must fallback to builtin default 'en' when all language configs are empty");
  }
}
