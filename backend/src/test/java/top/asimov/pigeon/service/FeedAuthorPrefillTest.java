package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.youtube.model.ChannelSnippet;
import com.google.api.services.youtube.model.PlaylistSnippet;
import com.google.api.services.youtube.model.Thumbnail;
import com.google.api.services.youtube.model.ThumbnailDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import top.asimov.pigeon.config.AppBaseUrlResolver;
import top.asimov.pigeon.helper.BilibiliChannelHelper;
import top.asimov.pigeon.helper.BilibiliPlaylistHelper;
import top.asimov.pigeon.helper.BilibiliResolverHelper;
import top.asimov.pigeon.helper.YoutubeChannelHelper;
import top.asimov.pigeon.helper.YoutubeHelper;
import top.asimov.pigeon.helper.YoutubePlaylistHelper;
import top.asimov.pigeon.helper.YoutubeVideoHelper;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.response.FeedPack;

@ExtendWith(MockitoExtension.class)
class FeedAuthorPrefillTest {

  @Mock
  private PlaylistMapper playlistMapper;
  @Mock
  private PlaylistEpisodeMapper playlistEpisodeMapper;
  @Mock
  private ChannelMapper channelMapper;
  @Mock
  private EpisodeService episodeService;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private YoutubeHelper youtubeHelper;
  @Mock
  private YoutubePlaylistHelper youtubePlaylistHelper;
  @Mock
  private YoutubeChannelHelper youtubeChannelHelper;
  @Mock
  private YoutubeVideoHelper youtubeVideoHelper;
  @Mock
  private BilibiliResolverHelper bilibiliResolverHelper;
  @Mock
  private BilibiliPlaylistHelper bilibiliPlaylistHelper;
  @Mock
  private BilibiliChannelHelper bilibiliChannelHelper;
  @Mock
  private AccountService accountService;
  @Mock
  private MessageSource messageSource;
  @Mock
  private FeedDefaultsService feedDefaultsService;
  @Mock
  private AppBaseUrlResolver appBaseUrlResolver;

  @Test
  void fetchYoutubePlaylistPrefillsAuthorWithOwnerChannelName() {
    String playlistUrl = "https://www.youtube.com/playlist?list=PLtestplaylist0001";
    com.google.api.services.youtube.model.Playlist ytPlaylist =
        new com.google.api.services.youtube.model.Playlist()
            .setId("PLtestplaylist0001")
            .setSnippet(new PlaylistSnippet()
                .setTitle("Weekly Tech Talks")
                .setChannelId("UCowner")
                .setChannelTitle("Owner Channel")
                .setDescription(""));
    when(youtubeHelper.fetchYoutubePlaylist(playlistUrl)).thenReturn(ytPlaylist);

    PlaylistService playlistService = new PlaylistService(
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
        appBaseUrlResolver,
        new ObjectMapper());

    FeedPack<Playlist> result = playlistService.fetchPlaylist(playlistUrl);

    assertEquals("Weekly Tech Talks", result.getFeed().getTitle());
    assertEquals("Owner Channel", result.getFeed().getAuthor());
  }

  @Test
  void fetchYoutubeChannelPrefillsAuthorWithChannelName() {
    String channelUrl = "https://www.youtube.com/@owner";
    com.google.api.services.youtube.model.Channel ytChannel =
        new com.google.api.services.youtube.model.Channel()
            .setId("UCowner")
            .setSnippet(new ChannelSnippet()
                .setTitle("Owner Channel")
                .setDescription("About the channel")
                .setThumbnails(new ThumbnailDetails()
                    .setHigh(new Thumbnail().setUrl("https://yt3.example.com/avatar.jpg"))));
    when(youtubeHelper.fetchYoutubeChannel(channelUrl)).thenReturn(ytChannel);

    ChannelService channelService = new ChannelService(
        channelMapper,
        episodeService,
        eventPublisher,
        youtubeHelper,
        youtubeChannelHelper,
        bilibiliResolverHelper,
        bilibiliChannelHelper,
        accountService,
        messageSource,
        feedDefaultsService,
        appBaseUrlResolver,
        new ObjectMapper());

    FeedPack<Channel> result = channelService.fetchChannel(channelUrl);

    assertEquals("Owner Channel", result.getFeed().getAuthor());
  }
}
