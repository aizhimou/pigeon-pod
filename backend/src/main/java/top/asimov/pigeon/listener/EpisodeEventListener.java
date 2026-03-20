package top.asimov.pigeon.listener;

import java.util.List;
import lombok.extern.log4j.Log4j2;
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

@Log4j2
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
        "监听到事务已提交的 EpisodesCreatedEvent 事件，开始处理下载任务: context={}, total={}, episodeIds={}",
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
        log.warn("即时提交下载任务失败，保留给后续调度补位: episodeId={}", episodeId, e);
      }
    }

    if (submittedCount > 0 || deferredCount > 0 || failedCount > 0) {
      log.info(
          "EpisodesCreatedEvent 处理完成: context={}, total={}, submitted={}, deferred={}, failed={}, submittedIds={}, deferredIds={}, failedIds={}",
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
    log.info("监听到频道下载任务事件，频道ID: {}, 类型: {}", event.getTargetId(), event.getAction());
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
    log.info("监听到播放列表下载任务事件，播放列表ID: {}, 类型: {}", event.getTargetId(),
        event.getAction());
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
