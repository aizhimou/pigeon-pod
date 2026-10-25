package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.entity.SystemConfig;

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

  @Mock
  private SystemConfigService systemConfigService;

  @Mock
  private EpisodeCoverService episodeCoverService;

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

  @Test
  void testRssFeedDescriptionFallsBackToTitleWhenChannelDescriptionEmpty() throws Exception {
    Channel channel = new Channel();
    channel.setId("no_desc_channel");
    channel.setTitle("No Description Channel");
    channel.setSource("YOUTUBE");
    channel.setDescription("");

    when(channelService.findChannelByIdentification("no_desc_channel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel)).thenReturn(Collections.emptyList());
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");

    String rssXml = rssService.generateRssFeed("no_desc_channel");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<description>No Description Channel</description>"),
        "RSS channel description must fall back to the feed title when the source has none");
    assertTrue(rssXml.contains("<itunes:summary>No Description Channel</itunes:summary>"),
        "itunes:summary must fall back to the feed title when the source has none");
    assertFalse(rssXml.contains("<description />"), "RSS channel must not contain an empty description");
    assertFalse(rssXml.contains("<itunes:summary />"), "RSS channel must not contain an empty itunes:summary");
  }

  @Test
  void testPlaylistRssFeedDescriptionFallsBackToCustomTitleWhenDescriptionNull() throws Exception {
    Playlist playlist = new Playlist();
    playlist.setId("PLtestplaylist0001");
    playlist.setTitle("Weekly Tech Talks");
    playlist.setCustomTitle("Tech Talks Podcast");
    playlist.setSource("YOUTUBE");
    playlist.setDescription(null);

    when(playlistService.playlistDetail("PLtestplaylist0001")).thenReturn(playlist);
    when(episodeService.getVisibleCompletedEpisodesForPlaylist(playlist)).thenReturn(Collections.emptyList());
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");

    String rssXml = rssService.generatePlaylistRssFeed("PLtestplaylist0001");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<description>Tech Talks Podcast</description>"),
        "RSS channel description must fall back to the displayed feed title");
    assertTrue(rssXml.contains("<itunes:summary>Tech Talks Podcast</itunes:summary>"),
        "itunes:summary must fall back to the displayed feed title");
  }

  @Test
  void testRssFeedUsesCustomDescriptionWhenSet() throws Exception {
    Channel channel = new Channel();
    channel.setId("custom_desc_channel");
    channel.setTitle("Custom Description Channel");
    channel.setSource("YOUTUBE");
    channel.setDescription("Original description");
    channel.setCustomDescription("My own description");

    when(channelService.findChannelByIdentification("custom_desc_channel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel)).thenReturn(Collections.emptyList());
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");

    String rssXml = rssService.generateRssFeed("custom_desc_channel");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<description>My own description</description>"),
        "RSS channel description must prefer the custom description");
    assertTrue(rssXml.contains("<itunes:summary>My own description</itunes:summary>"),
        "itunes:summary must prefer the custom description");
    assertFalse(rssXml.contains("Original description"),
        "RSS channel must not contain the source description when a custom one is set");
  }

  @Test
  void testPlaylistRssFeedUsesCustomDescriptionWhenSourceDescriptionEmpty() throws Exception {
    Playlist playlist = new Playlist();
    playlist.setId("PLcustomdesc");
    playlist.setTitle("Playlist Without Description");
    playlist.setSource("YOUTUBE");
    playlist.setDescription("");
    playlist.setCustomDescription("Weekly conversations about tech");

    when(playlistService.playlistDetail("PLcustomdesc")).thenReturn(playlist);
    when(episodeService.getVisibleCompletedEpisodesForPlaylist(playlist)).thenReturn(Collections.emptyList());
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");

    String rssXml = rssService.generatePlaylistRssFeed("PLcustomdesc");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<description>Weekly conversations about tech</description>"),
        "RSS channel description must use the custom description for playlists");
  }

  @Test
  void testPlaylistRssFeedUsesAuthorForItunesAuthor() throws Exception {
    Playlist playlist = new Playlist();
    playlist.setId("PLauthor");
    playlist.setTitle("Weekly Tech Talks");
    playlist.setAuthor("Owner Channel");
    playlist.setSource("YOUTUBE");
    playlist.setDescription("Playlist description");

    when(playlistService.playlistDetail("PLauthor")).thenReturn(playlist);
    when(episodeService.getVisibleCompletedEpisodesForPlaylist(playlist)).thenReturn(Collections.emptyList());
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");

    String rssXml = rssService.generatePlaylistRssFeed("PLauthor");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<itunes:author>Owner Channel</itunes:author>"),
        "itunes:author must use the feed author when set");
    assertTrue(rssXml.contains("<title>Weekly Tech Talks</title>"),
        "feed title must stay the playlist name");
  }

  @Test
  void testRssFeedFallsBackToTitleForItunesAuthorWhenAuthorEmpty() throws Exception {
    Channel channel = new Channel();
    channel.setId("no_author_channel");
    channel.setTitle("No Author Channel");
    channel.setCustomTitle("Custom Show Name");
    channel.setAuthor("  ");
    channel.setSource("YOUTUBE");
    channel.setDescription("Test Description");

    when(channelService.findChannelByIdentification("no_author_channel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel)).thenReturn(Collections.emptyList());
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");

    String rssXml = rssService.generateRssFeed("no_author_channel");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<itunes:author>Custom Show Name</itunes:author>"),
        "itunes:author must fall back to the displayed feed title when no author is set");
  }

  @Test
  void testRssFeedKeepsSourceDescriptionWhenPresent() throws Exception {
    Channel channel = new Channel();
    channel.setId("desc_channel");
    channel.setTitle("Described Channel");
    channel.setSource("YOUTUBE");
    channel.setDescription("A real description");

    when(channelService.findChannelByIdentification("desc_channel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel)).thenReturn(Collections.emptyList());
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");

    String rssXml = rssService.generateRssFeed("desc_channel");

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<description>A real description</description>"));
    assertTrue(rssXml.contains("<itunes:summary>A real description</itunes:summary>"));
  }

  @Test
  void testGenerateAllRssFeed() throws Exception {
    Episode episode1 = Episode.builder()
        .id("BV1testUnified")
        .title("Unified Episode 1")
        .description("Unified Desc 1")
        .publishedAt(LocalDateTime.of(2026, 9, 30, 20, 0, 0))
        .mediaFilePath("/data/audio/BV1testUnified.mp3")
        .mediaType("audio/mpeg")
        .duration("00:15:00")
        .durationSeconds(900)
        .build();

    when(episodeService.getAllCompletedEpisodesForRss(300)).thenReturn(List.of(episode1));
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");
    when(mediaService.resolveEpisodeCoverUrlForRss(eq("https://pigeon.example.com"), eq(episode1)))
        .thenReturn("https://pigeon.example.com/cover.jpg");
    when(mediaService.resolveMediaUrlForRss(eq("https://pigeon.example.com"), eq(episode1)))
        .thenReturn("https://pigeon.example.com/media/BV1testUnified.mp3");
    when(mediaService.resolveMediaLengthForRss(eq(episode1))).thenReturn(500000L);
    when(feedDefaultsService.getEffectiveFeedDefaults()).thenReturn(null);

    String rssXml = rssService.generateAllRssFeed();

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<title>PigeonPod</title>"));
    assertTrue(rssXml.contains("<title>Unified Episode 1</title>"));
    assertTrue(rssXml.contains("https://www.bilibili.com/video/BV1testUnified"));
    assertTrue(rssXml.contains("<enclosure url=\"https://pigeon.example.com/media/BV1testUnified.mp3\""));
  }

  @Test
  void testGenerateAllRssFeedWithCustomAppearance() throws Exception {
    Episode episode1 = Episode.builder()
        .id("BV1testUnified")
        .title("Unified Episode 1")
        .description("Unified Desc 1")
        .publishedAt(LocalDateTime.of(2026, 9, 30, 20, 0, 0))
        .mediaFilePath("/data/audio/BV1testUnified.mp3")
        .mediaType("audio/mpeg")
        .duration("00:15:00")
        .durationSeconds(900)
        .build();

    SystemConfig config = SystemConfig.builder()
        .unifiedFeedCustomTitle("My Favorite Podcast Archive")
        .unifiedFeedCustomCoverExt("png")
        .unifiedFeedLanguage("zh-CN")
        .unifiedFeedUpdatedAt(LocalDateTime.of(2026, 10, 9, 12, 0, 0))
        .build();

    when(episodeService.getAllCompletedEpisodesForRss(300)).thenReturn(List.of(episode1));
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");
    when(systemConfigService.getCurrentConfig()).thenReturn(config);
    when(mediaService.resolveMediaUrlForRss(eq("https://pigeon.example.com"), eq(episode1)))
        .thenReturn("https://pigeon.example.com/media/BV1testUnified.mp3");
    when(mediaService.resolveMediaLengthForRss(eq(episode1))).thenReturn(500000L);

    String rssXml = rssService.generateAllRssFeed();

    assertNotNull(rssXml);
    assertTrue(rssXml.contains("<title>My Favorite Podcast Archive</title>"));
    assertTrue(rssXml.contains("<language>zh-CN</language>"));
    assertTrue(rssXml.contains("/media/feed/all/cover?v="));
  }

  @Test
  void testEpisodeCoverUsesStyledUrlWhenFeedSquaresArtwork() throws Exception {
    Channel channel = coverStyleChannel("{\"mode\":\"PAD\",\"fill\":\"BLUR\"}");
    Episode episode = coverStyleEpisode();
    stubChannelFeed(channel, episode);
    String styledUrl =
        "https://pigeon.example.com/media/feed/channel/ytchannel/episodes/video_003/cover.jpg?v=abc";
    when(episodeCoverService.hasCoverSource(episode)).thenReturn(true);
    when(episodeCoverService.buildCoverUrl(eq("https://pigeon.example.com"), eq(channel),
        eq(episode), any())).thenReturn(styledUrl);

    String rssXml = rssService.generateRssFeed("ytchannel");

    assertTrue(rssXml.contains(styledUrl), "RSS item must link the squared cover");
    verify(mediaService, never()).resolveEpisodeCoverUrlForRss(any(), any());
  }

  @Test
  void testEpisodeCoverKeepsOriginalWhenSquaredArtworkHasNoSource() throws Exception {
    Channel channel = coverStyleChannel("{\"mode\":\"CROP\",\"position\":30}");
    Episode episode = coverStyleEpisode();
    stubChannelFeed(channel, episode);
    when(episodeCoverService.hasCoverSource(episode)).thenReturn(false);
    when(mediaService.resolveEpisodeCoverUrlForRss(eq("https://pigeon.example.com"), eq(episode)))
        .thenReturn("https://pigeon.example.com/media/video_003/cover");

    String rssXml = rssService.generateRssFeed("ytchannel");

    assertTrue(rssXml.contains("https://pigeon.example.com/media/video_003/cover"));
    assertFalse(rssXml.contains("/episodes/video_003/cover.jpg"));
  }

  @Test
  void testEpisodeCoverUsesFeedCoverWhenFeedCoverModeSelected() throws Exception {
    Channel channel = coverStyleChannel("{\"mode\":\"FEED_COVER\"}");
    channel.setCoverUrl("https://yt3.example.com/channel-avatar.jpg");
    Episode episode = coverStyleEpisode();
    stubChannelFeed(channel, episode);

    String rssXml = rssService.generateRssFeed("ytchannel");

    int occurrences = rssXml.split("https://yt3.example.com/channel-avatar.jpg", -1).length - 1;
    assertEquals(2, occurrences, "Feed cover must be used for the channel and the episode");
    assertFalse(rssXml.contains("maxresdefault.jpg"));
    verify(mediaService, never()).resolveEpisodeCoverUrlForRss(any(), any());
  }

  @Test
  void testEpisodeOverridesReplaceTitleAndDescription() throws Exception {
    Channel channel = coverStyleChannel(null);
    Episode episode = coverStyleEpisode();
    episode.setDescription("Original description");
    episode.setCustomTitle("Edited title");
    episode.setCustomDescription("Edited description");
    stubChannelFeed(channel, episode);

    String rssXml = rssService.generateRssFeed("ytchannel");

    assertTrue(rssXml.contains("<title>Edited title</title>"), rssXml);
    assertFalse(rssXml.contains("Episode With Styled Cover"));
    assertTrue(rssXml.contains("Edited description"));
    assertFalse(rssXml.contains("Original description"));
    assertTrue(rssXml.contains("Source video:"), "default format still adds the source link");
  }

  @Test
  void testDescriptionFormatCanKeepOnlyTheOriginalDescription() throws Exception {
    Channel channel = coverStyleChannel(null);
    channel.setEpisodeDescriptionTemplate("{description}");
    Episode episode = coverStyleEpisode();
    episode.setDescription("Just the original");
    stubChannelFeed(channel, episode);

    String rssXml = rssService.generateRssFeed("ytchannel");

    assertTrue(rssXml.contains("Just the original"));
    assertFalse(rssXml.contains("Source video:"));
  }

  @Test
  void testDescriptionFormatFillsPlaceholders() throws Exception {
    Channel channel = coverStyleChannel(null);
    channel.setEpisodeDescriptionTemplate("{title} | {channel} | {date}\n{source_url}");
    Episode episode = coverStyleEpisode();
    stubChannelFeed(channel, episode);

    String rssXml = rssService.generateRssFeed("ytchannel");

    assertTrue(rssXml.contains(
        "Episode With Styled Cover | Test Channel | 2026-10-01<br/>https://youtu.be/video_003"),
        rssXml);
  }

  @Test
  void testPreviewEpisodeDescriptionUsesTheGivenFormat() {
    Channel channel = coverStyleChannel(null);
    Episode episode = coverStyleEpisode();
    episode.setDescription("Original");

    assertEquals("Original", rssService.previewEpisodeDescription(channel, episode,
        "{description}"));
    assertTrue(rssService.previewEpisodeDescription(channel, episode, "  ")
        .startsWith("Original\n\nSource video: <a href=\"https://youtu.be/video_003\">"));
  }

  @Test
  void testFeedCategoryIsWrittenWithSubcategory() throws Exception {
    Channel channel = coverStyleChannel(null);
    channel.setCategory("Society & Culture|Documentary");
    stubChannelFeed(channel, coverStyleEpisode());

    String rssXml = rssService.generateRssFeed("ytchannel");

    assertTrue(rssXml.contains("<itunes:category text=\"Society &amp; Culture\">"), rssXml);
    assertTrue(rssXml.contains("<itunes:category text=\"Documentary\" />"), rssXml);
    assertFalse(rssXml.contains("Technology"));
  }

  @Test
  void testFeedCategoryDefaultsToTechnology() throws Exception {
    stubChannelFeed(coverStyleChannel(null), coverStyleEpisode());

    String rssXml = rssService.generateRssFeed("ytchannel");

    assertTrue(rssXml.contains("<itunes:category text=\"Technology\" />"), rssXml);
  }

  private Channel coverStyleChannel(String episodeCoverStyle) {
    Channel channel = new Channel();
    channel.setId("ytchannel");
    channel.setTitle("Test Channel");
    channel.setSource("YOUTUBE");
    channel.setDescription("Test Channel Description");
    channel.setEpisodeCoverStyle(episodeCoverStyle);
    return channel;
  }

  private Episode coverStyleEpisode() {
    return Episode.builder()
        .id("video_003")
        .channelId("ytchannel")
        .title("Episode With Styled Cover")
        .publishedAt(LocalDateTime.of(2026, 10, 1, 12, 0, 0))
        .mediaFilePath("/data/audio/video_003.mp3")
        .mediaType("audio/mpeg")
        .maxCoverUrl("https://i.ytimg.com/vi/video_003/maxresdefault.jpg")
        .build();
  }

  private void stubChannelFeed(Channel channel, Episode episode) throws Exception {
    when(channelService.findChannelByIdentification("ytchannel")).thenReturn(channel);
    when(episodeService.getVisibleCompletedEpisodesForChannel(channel))
        .thenReturn(List.of(episode));
    when(appBaseUrlResolver.requireBaseUrl()).thenReturn("https://pigeon.example.com");
    when(mediaService.resolveMediaUrlForRss(eq("https://pigeon.example.com"), eq(episode)))
        .thenReturn("https://pigeon.example.com/media/video_003.mp3");
    when(mediaService.resolveMediaLengthForRss(eq(episode))).thenReturn(1000L);
    when(mediaService.getAvailableSubtitles(any())).thenReturn(Collections.emptyList());
  }
}
