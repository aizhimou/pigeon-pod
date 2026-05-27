package top.asimov.pigeon.listener;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.asimov.pigeon.event.DownloadTaskEvent;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadAction;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadTargetType;
import top.asimov.pigeon.event.EpisodesCreatedEvent;
import top.asimov.pigeon.helper.DownloadTaskHelper;
import top.asimov.pigeon.service.ChannelService;
import top.asimov.pigeon.service.PlaylistService;

@Slf4j
@Component
public class EpisodeEventListener {

  private final DownloadTaskHelper downloadTaskHelper;
  private final ChannelService channelService;
  private final PlaylistService playlistService;

  public EpisodeEventListener(DownloadTaskHelper downloadTaskHelper,
      ChannelService channelService, PlaylistService playlistService) {
    this.downloadTaskHelper = downloadTaskHelper;
    this.channelService = channelService;
    this.playlistService = playlistService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleEpisodesCreated(EpisodesCreatedEvent event) {
    log.info(
        "[download] episodes created event received: context={} count={} episodeIds={}",
        event.getContext(),
        event.getEpisodeIds().size(),
        event.getEpisodeIds());
    List<String> episodeIds = event.getEpisodeIds();
    int submittedCount = 0;
    int deferredCount = 0;
    int failedCount = 0;
    List<String> submittedIds = new java.util.ArrayList<>();
    List<String> deferredIds = new java.util.ArrayList<>();
    List<String> failedIds = new java.util.ArrayList<>();

    for (String episodeId : episodeIds) {
      try {
        boolean submitted = downloadTaskHelper.submitDownloadTask(episodeId);
        if (submitted) {
          submittedCount++;
          submittedIds.add(episodeId);
        } else {
          deferredCount++;
          deferredIds.add(episodeId);
        }
      } catch (Exception e) {
        failedCount++;
        failedIds.add(episodeId);
        log.warn("[download] immediate submit failed, keep for scheduler: episodeId={}",
            episodeId, e);
      }
    }

    if (submittedCount > 0 || deferredCount > 0 || failedCount > 0) {
      log.info(
          "[download] episodes created event processed: context={} count={} submitted={} deferred={} failed={} submittedIds={} deferredIds={} failedIds={}",
          event.getContext(),
          episodeIds.size(),
          submittedCount,
          deferredCount,
          failedCount,
          submittedIds,
          deferredIds,
          failedIds);
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleDownloadTask(DownloadTaskEvent event) {
    if (event.getTargetType() == DownloadTargetType.CHANNEL) {
      handleChannelTask(event);
      return;
    }
    if (event.getTargetType() == DownloadTargetType.PLAYLIST) {
      handlePlaylistTask(event);
    }
  }

  private void handleChannelTask(DownloadTaskEvent event) {
    log.info("[feed-sync] download task event received: targetType={} channelId={} action={}",
        event.getTargetType(), event.getTargetId(), event.getAction());
    if (event.getAction() == DownloadAction.INIT) {
      channelService.processChannelInitializationAsync(
          event.getTargetId(),
          event.getDownloadNumber(),
          event.getTitleContainKeywords(),
          event.getTitleExcludeKeywords(),
          event.getMinimumDuration(),
          event.getMaximumDuration());
    }
  }

  private void handlePlaylistTask(DownloadTaskEvent event) {
    log.info("[feed-sync] download task event received: targetType={} playlistId={} action={}",
        event.getTargetType(), event.getTargetId(), event.getAction());
    if (event.getAction() == DownloadAction.INIT) {
      playlistService.processPlaylistInitializationAsync(
          event.getTargetId(),
          event.getDownloadNumber(),
          event.getTitleContainKeywords(),
          event.getTitleExcludeKeywords(),
          event.getDescriptionContainKeywords(),
          event.getDescriptionExcludeKeywords(),
          event.getMinimumDuration(),
          event.getMaximumDuration());
    }
  }

}
