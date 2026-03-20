package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import top.asimov.pigeon.config.StorageProperties;
import top.asimov.pigeon.event.EpisodesCreatedEvent;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.service.storage.S3StorageService;

class EpisodeServiceTest {

  private EpisodeMapper episodeMapper;
  private ApplicationEventPublisher eventPublisher;
  private MessageSource messageSource;
  private EpisodeService episodeService;

  @BeforeEach
  void setUp() {
    episodeMapper = mock(EpisodeMapper.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    messageSource = mock(MessageSource.class);

    episodeService = new EpisodeService(
        episodeMapper,
        eventPublisher,
        messageSource,
        mock(ChannelMapper.class),
        mock(PlaylistEpisodeMapper.class),
        mock(PlaylistMapper.class),
        mock(StorageProperties.class),
        mock(S3StorageService.class));
  }

  @Test
  void shouldMarkEpisodePendingBeforePublishingManualDownloadEvent() {
    Episode episode = Episode.builder()
        .id("episode-1")
        .downloadStatus(EpisodeStatus.READY.name())
        .build();
    when(episodeMapper.selectById("episode-1")).thenReturn(episode);

    episodeService.manualDownloadEpisode("episode-1");

    InOrder inOrder = inOrder(episodeMapper, eventPublisher);
    inOrder.verify(episodeMapper).selectById("episode-1");
    inOrder.verify(episodeMapper).updateDownloadStatusAndClearSchedulingFields(
        "episode-1", EpisodeStatus.PENDING.name());
    inOrder.verify(eventPublisher).publishEvent(any(EpisodesCreatedEvent.class));
  }

  @Test
  void shouldRejectManualDownloadWhenEpisodeIsNotReady() {
    Episode episode = Episode.builder()
        .id("episode-1")
        .downloadStatus(EpisodeStatus.PENDING.name())
        .build();
    when(episodeMapper.selectById("episode-1")).thenReturn(episode);
    when(messageSource.getMessage(eq("episode.download.invalid.status"), any(), any()))
        .thenReturn("invalid status");

    assertThrows(BusinessException.class, () -> episodeService.manualDownloadEpisode("episode-1"));

    verify(episodeMapper, never()).updateDownloadStatusAndClearSchedulingFields(
        anyString(), eq(EpisodeStatus.PENDING.name()));
    verify(eventPublisher, never()).publishEvent(any(EpisodesCreatedEvent.class));
  }
}
