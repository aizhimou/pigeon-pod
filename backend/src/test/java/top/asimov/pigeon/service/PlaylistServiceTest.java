package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemContentDetails;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoContentDetails;
import com.google.api.services.youtube.model.VideoSnippet;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import top.asimov.pigeon.config.AppBaseUrlResolver;
import top.asimov.pigeon.config.YoutubeApiKeyHolder;
import top.asimov.pigeon.helper.BilibiliPlaylistHelper;
import top.asimov.pigeon.helper.BilibiliResolverHelper;
import top.asimov.pigeon.helper.YoutubeHelper;
import top.asimov.pigeon.helper.YoutubePlaylistHelper;
import top.asimov.pigeon.helper.YoutubeVideoHelper;
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.entity.PlaylistEpisode;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.enums.FeedSource;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

  @Mock
  private PlaylistMapper playlistMapper;
  @Mock
  private PlaylistEpisodeMapper playlistEpisodeMapper;
  @Mock
  private EpisodeService episodeService;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private YoutubeHelper youtubeHelper;
  @Mock
  private YoutubePlaylistHelper youtubePlaylistHelper;
  @Mock
  private YoutubeVideoHelper youtubeVideoHelper;
  @Mock
  private BilibiliResolverHelper bilibiliResolverHelper;
  @Mock
  private BilibiliPlaylistHelper bilibiliPlaylistHelper;
  @Mock
  private AccountService accountService;
  @Mock
  private MessageSource messageSource;
  @Mock
  private FeedDefaultsService feedDefaultsService;
  @Mock
  private AppBaseUrlResolver appBaseUrlResolver;

  private PlaylistService playlistService;

  @BeforeEach
  void setUp() {
    YoutubeApiKeyHolder.updateYoutubeApiKey("test-key");
    playlistService = new PlaylistService(
        playlistMapper,
        playlistEpisodeMapper,
        episodeService,
        eventPublisher,
        youtubeHelper,
        youtubePlaylistHelper,
        youtubeVideoHelper,
        bilibiliResolverHelper,
        bilibiliPlaylistHelper,
        accountService,
        messageSource,
        feedDefaultsService,
        Runnable::run,
        appBaseUrlResolver);
    when(messageSource.getMessage(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(playlistMapper.updateById(any(Playlist.class))).thenReturn(1);
    when(playlistEpisodeMapper.selectMappingsByPlaylistId("pl")).thenReturn(Collections.emptyList());
    lenient().when(playlistEpisodeMapper.insertMapping(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    lenient().when(playlistEpisodeMapper.updateMapping(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    lenient().doAnswer(invocation -> null).when(youtubeVideoHelper).applyThumbnails(any(), any());
  }

  @AfterEach
  void tearDown() {
    YoutubeApiKeyHolder.updateYoutubeApiKey(null);
  }

  @Test
  void retriesLivePlaylistItemWhenItBecomesVodWithoutPersistingSkippedState() throws Exception {
    Playlist playlist = youtubePlaylist();
    PlaylistItem item = playlistItem("item-1", "v1", 0);
    Video liveVideo = video("v1", "Match episode", "PT30M");
    Video vodVideo = video("v1", "Match episode", "PT30M");

    when(playlistMapper.selectById("pl")).thenReturn(playlist);
    when(youtubePlaylistHelper.fetchAllPlaylistItemsOfficial("pl")).thenReturn(List.of(item));
    when(episodeService.getEpisodesByIds(anyList())).thenReturn(Collections.emptyList());
    when(youtubeVideoHelper.fetchVideoDetailsInBulk(anyList(), eq("test-key")))
        .thenReturn(Map.of("v1", liveVideo), Map.of("v1", vodVideo));
    when(youtubeVideoHelper.shouldSkipLiveContent(any())).thenReturn(true, false);
    when(youtubeVideoHelper.isArchivedLiveVodPro(any())).thenReturn(false);

    playlistService.refreshPlaylistById("pl");

    verify(playlistEpisodeMapper, never()).insertMapping(any(), any(), any(), any(), any(), any(), any());

    playlistService.refreshPlaylistById("pl");

    verify(playlistEpisodeMapper).insertMapping(eq("pl"), eq("v1"), eq(0L), any(), eq("owner"), eq("Owner"),
        eq("https://www.youtube.com/channel/owner"));
    verify(episodeService).saveEpisodes(anyList());
    verify(episodeService).markEpisodesPending(anyList());
  }

  @Test
  void reEvaluatesFilterOnEachFullScanWithoutSkippedState() throws Exception {
    Playlist playlist = youtubePlaylist();
    playlist.setTitleContainKeywords("match");
    PlaylistItem item = playlistItem("item-1", "v1", 0);
    Video video = video("v1", "Other episode", "PT30M");

    when(playlistMapper.selectById("pl")).thenReturn(playlist);
    when(youtubePlaylistHelper.fetchAllPlaylistItemsOfficial("pl")).thenReturn(List.of(item));
    when(episodeService.getEpisodesByIds(anyList())).thenReturn(Collections.emptyList());
    when(youtubeVideoHelper.fetchVideoDetailsInBulk(anyList(), eq("test-key"))).thenReturn(Map.of("v1", video));
    when(youtubeVideoHelper.shouldSkipLiveContent(video)).thenReturn(false);
    when(youtubeVideoHelper.isArchivedLiveVodPro(video)).thenReturn(false);

    playlistService.refreshPlaylistById("pl");

    verify(playlistEpisodeMapper, never()).insertMapping(any(), any(), any(), any(), any(), any(), any());

    playlist.setTitleContainKeywords(null);
    playlistService.refreshPlaylistById("pl");

    verify(playlistEpisodeMapper).insertMapping(eq("pl"), eq("v1"), eq(0L), any(), any(), any(), any());
  }

  @Test
  void removesStaleMappingsAfterSuccessfulFullScan() {
    Playlist playlist = youtubePlaylist();
    PlaylistEpisode staleMapping = PlaylistEpisode.builder()
        .playlistId("pl")
        .episodeId("old")
        .position(0L)
        .build();
    Episode staleEpisode = episode("old", EpisodeStatus.READY.name());

    when(playlistMapper.selectById("pl")).thenReturn(playlist);
    when(youtubePlaylistHelper.fetchAllPlaylistItemsOfficial("pl")).thenReturn(Collections.emptyList());
    when(playlistEpisodeMapper.selectMappingsByPlaylistId("pl")).thenReturn(List.of(staleMapping));
    when(episodeService.getEpisodesByIds(List.of("old"))).thenReturn(List.of(staleEpisode));
    when(playlistEpisodeMapper.isOrhanEpisode("old")).thenReturn(0L);

    playlistService.refreshPlaylistById("pl");

    verify(playlistEpisodeMapper).delete(any());
    verify(episodeService).getEpisodesByIds(List.of("old"));
  }

  @Test
  void limitsInitialPlaylistAutoDownloadToConfiguredLimit() throws Exception {
    Playlist playlist = youtubePlaylist();
    playlist.setAutoDownloadLimit(1);
    PlaylistItem first = playlistItem("item-1", "v1", 0);
    PlaylistItem second = playlistItem("item-2", "v2", 1);
    Video firstVideo = video("v1", "First", "PT30M");
    Video secondVideo = video("v2", "Second", "PT30M");

    when(playlistMapper.selectById("pl")).thenReturn(playlist);
    when(youtubePlaylistHelper.fetchAllPlaylistItemsOfficial("pl")).thenReturn(List.of(first, second));
    when(episodeService.getEpisodesByIds(anyList())).thenReturn(Collections.emptyList());
    when(youtubeVideoHelper.fetchVideoDetailsInBulk(anyList(), eq("test-key")))
        .thenReturn(Map.of("v1", firstVideo, "v2", secondVideo));
    when(youtubeVideoHelper.shouldSkipLiveContent(any())).thenReturn(false);
    when(youtubeVideoHelper.isArchivedLiveVodPro(any())).thenReturn(false);

    playlistService.refreshPlaylistById("pl");

    ArgumentCaptor<List<Episode>> pendingCaptor = ArgumentCaptor.forClass(List.class);
    verify(episodeService).markEpisodesPending(pendingCaptor.capture());
    assertEquals(1, pendingCaptor.getValue().size());
    assertEquals("v1", pendingCaptor.getValue().get(0).getId());
  }

  @Test
  void doesNotRequeueExistingCompletedEpisodeWhenItIsNewlyMapped() {
    Playlist playlist = youtubePlaylist();
    playlist.setBootstrapCompletedAt(LocalDateTime.now().minusHours(1));
    PlaylistItem item = playlistItem("item-1", "v1", 0);
    Episode completed = episode("v1", EpisodeStatus.COMPLETED.name());

    when(playlistMapper.selectById("pl")).thenReturn(playlist);
    when(youtubePlaylistHelper.fetchAllPlaylistItemsOfficial("pl")).thenReturn(List.of(item));
    when(episodeService.getEpisodesByIds(anyList())).thenReturn(List.of(completed));

    playlistService.refreshPlaylistById("pl");

    verify(playlistEpisodeMapper).insertMapping(eq("pl"), eq("v1"), eq(0L), any(), eq("owner"), eq("Owner"),
        eq("https://www.youtube.com/channel/owner"));
    verify(episodeService, never()).markEpisodesPending(anyList());
  }

  @Test
  void keepsOnlyFirstPlaylistOccurrenceForDuplicateVideoIds() {
    Playlist playlist = youtubePlaylist();
    PlaylistItem first = playlistItem("item-1", "v1", 0);
    PlaylistItem duplicate = playlistItem("item-2", "v1", 1);
    Episode existing = episode("v1", EpisodeStatus.READY.name());

    when(playlistMapper.selectById("pl")).thenReturn(playlist);
    when(youtubePlaylistHelper.fetchAllPlaylistItemsOfficial("pl")).thenReturn(List.of(duplicate, first));
    when(episodeService.getEpisodesByIds(anyList())).thenReturn(List.of(existing));

    playlistService.refreshPlaylistById("pl");

    verify(playlistEpisodeMapper).insertMapping(eq("pl"), eq("v1"), eq(0L), any(), any(), any(), any());
  }

  private Playlist youtubePlaylist() {
    return Playlist.builder()
        .id("pl")
        .title("Playlist")
        .source(FeedSource.YOUTUBE.name())
        .autoDownloadEnabled(Boolean.TRUE)
        .autoDownloadLimit(10)
        .autoDownloadDelayMinutes(0)
        .build();
  }

  private PlaylistItem playlistItem(String playlistItemId, String videoId, long position) {
    ResourceId resourceId = new ResourceId();
    resourceId.setVideoId(videoId);
    PlaylistItemSnippet snippet = new PlaylistItemSnippet();
    snippet.setResourceId(resourceId);
    snippet.setPosition(position);
    snippet.setPublishedAt(new DateTime("2026-01-01T00:00:00Z"));
    snippet.set("videoOwnerChannelId", "owner");
    snippet.set("videoOwnerChannelTitle", "Owner");
    PlaylistItemContentDetails contentDetails = new PlaylistItemContentDetails();
    contentDetails.setVideoId(videoId);
    contentDetails.setVideoPublishedAt(new DateTime("2026-01-01T00:00:00Z"));
    PlaylistItem item = new PlaylistItem();
    item.setId(playlistItemId);
    item.setSnippet(snippet);
    item.setContentDetails(contentDetails);
    return item;
  }

  private Video video(String videoId, String title, String duration) {
    VideoSnippet snippet = new VideoSnippet();
    snippet.setTitle(title);
    snippet.setDescription("description");
    snippet.setChannelId("owner");
    snippet.setChannelTitle("Owner");
    snippet.setPublishedAt(new DateTime("2026-01-01T00:00:00Z"));
    VideoContentDetails contentDetails = new VideoContentDetails();
    contentDetails.setDuration(duration);
    Video video = new Video();
    video.setId(videoId);
    video.setSnippet(snippet);
    video.setContentDetails(contentDetails);
    return video;
  }

  private Episode episode(String episodeId, String downloadStatus) {
    return Episode.builder()
        .id(episodeId)
        .title("Episode " + episodeId)
        .description("description")
        .duration("PT30M")
        .durationSeconds(1800)
        .downloadStatus(downloadStatus)
        .liveVod(Boolean.FALSE)
        .build();
  }
}
