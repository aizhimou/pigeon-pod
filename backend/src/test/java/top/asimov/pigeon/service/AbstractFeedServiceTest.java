package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadTargetType;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;

class AbstractFeedServiceTest {

  private EpisodeService episodeService;
  private ApplicationEventPublisher eventPublisher;
  private MessageSource messageSource;
  private FeedDefaultsService feedDefaultsService;
  private TestFeedService feedService;

  @BeforeEach
  void setUp() {
    episodeService = mock(EpisodeService.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    messageSource = mock(MessageSource.class);
    feedDefaultsService = mock(FeedDefaultsService.class);
    when(messageSource.getMessage(eq("feed.refresh.new.episodes"), any(Object[].class), any(Locale.class)))
        .thenReturn("refresh ok");

    feedService = new TestFeedService(
        episodeService,
        eventPublisher,
        messageSource,
        feedDefaultsService);
  }

  @Test
  void shouldAutoDownloadAllVisibleEpisodesDuringRefreshEvenWhenInitialLimitIsOne() {
    Channel channel = Channel.builder()
        .id("channel-1")
        .title("Channel")
        .autoDownloadEnabled(Boolean.TRUE)
        .autoDownloadLimit(1)
        .autoDownloadDelayMinutes(0)
        .build();
    feedService.incrementalEpisodes = List.of(
        episode("episode-1", 1),
        episode("episode-2", 2),
        episode("episode-3", 3));

    feedService.refreshFeed(channel);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Episode>> captor = ArgumentCaptor.forClass(List.class);
    verify(episodeService).markEpisodesPending(captor.capture());
    assertEquals(3, captor.getValue().size());
  }

  @Test
  void shouldSkipAutoDownloadDuringRefreshWhenDisabled() {
    Channel channel = Channel.builder()
        .id("channel-1")
        .title("Channel")
        .autoDownloadEnabled(Boolean.FALSE)
        .autoDownloadLimit(1)
        .autoDownloadDelayMinutes(0)
        .build();
    feedService.incrementalEpisodes = List.of(
        episode("episode-1", 1),
        episode("episode-2", 2));

    feedService.refreshFeed(channel);

    verify(episodeService, never()).markEpisodesPending(any());
    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  private static Episode episode(String id, int minutesAgo) {
    return Episode.builder()
        .id(id)
        .title(id)
        .publishedAt(LocalDateTime.now().minusMinutes(minutesAgo))
        .downloadStatus("READY")
        .createdAt(LocalDateTime.now())
        .build();
  }

  private static final class TestFeedService extends AbstractFeedService<Channel> {

    private List<Episode> incrementalEpisodes = List.of();

    private TestFeedService(EpisodeService episodeService,
        ApplicationEventPublisher eventPublisher,
        MessageSource messageSource,
        FeedDefaultsService feedDefaultsService) {
      super(episodeService, eventPublisher, messageSource, feedDefaultsService);
    }

    @Override
    protected Optional<Channel> findFeedById(String feedId) {
      return Optional.empty();
    }

    @Override
    protected int updateFeed(Channel feed) {
      return 1;
    }

    @Override
    protected void insertFeed(Channel feed) {
    }

    @Override
    protected DownloadTargetType downloadTargetType() {
      return DownloadTargetType.CHANNEL;
    }

    @Override
    protected List<Episode> fetchEpisodes(Channel feed) {
      return List.of();
    }

    @Override
    protected List<Episode> fetchIncrementalEpisodes(Channel feed) {
      return incrementalEpisodes;
    }

    @Override
    protected Logger logger() {
      return LogManager.getLogger(TestFeedService.class);
    }
  }
}
