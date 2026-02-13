package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.api.services.youtube.model.Video;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadTargetType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.config.YoutubeApiKeyHolder;
import top.asimov.pigeon.helper.YoutubeVideoHelper;
import top.asimov.pigeon.helper.YoutubeHelper;
import top.asimov.pigeon.helper.YoutubePlaylistHelper;
import top.asimov.pigeon.mapper.PlaylistEpisodeDetailRetryMapper;
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.constant.Youtube;
import top.asimov.pigeon.model.dto.PlaylistSnapshotEntry;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.entity.PlaylistEpisode;
import top.asimov.pigeon.model.entity.PlaylistEpisodeDetailRetry;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.enums.FeedSource;
import top.asimov.pigeon.model.response.FeedConfigUpdateResult;
import top.asimov.pigeon.model.response.FeedPack;
import top.asimov.pigeon.model.response.FeedSaveResult;
import top.asimov.pigeon.model.response.FeedRefreshResult;

@Log4j2
@Service
public class PlaylistService extends AbstractFeedService<Playlist> {

  private static final int VIDEO_DETAILS_BATCH_SIZE = 50;
  private static final int EPISODE_LOOKUP_BATCH_SIZE = 500;
  private static final int EPISODE_SAVE_BATCH_SIZE = 200;
  private static final int DETAIL_RETRY_BATCH_SIZE = 100;
  private static final int DETAIL_RETRY_MAX_ATTEMPTS = 8;
  private static final Comparator<Episode> AUTO_DOWNLOAD_NEWEST_FIRST =
      Comparator.comparing(Episode::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Episode::getId, Comparator.nullsLast(String::compareTo));
  private static final Comparator<Episode> AUTO_DOWNLOAD_WORST_FIRST =
      AUTO_DOWNLOAD_NEWEST_FIRST.reversed();

  @Value("${pigeon.base-url}")
  private String appBaseUrl;

  private final PlaylistMapper playlistMapper;
  private final PlaylistEpisodeMapper playlistEpisodeMapper;
  private final PlaylistEpisodeDetailRetryMapper playlistEpisodeDetailRetryMapper;
  private final YoutubeHelper youtubeHelper;
  private final YoutubePlaylistHelper youtubePlaylistHelper;
  private final YoutubeVideoHelper youtubeVideoHelper;
  private final YtDlpPlaylistSnapshotService ytDlpPlaylistSnapshotService;
  private final AccountService accountService;
  private final MessageSource messageSource;
  private final Executor channelSyncTaskExecutor;

  public PlaylistService(PlaylistMapper playlistMapper,
      PlaylistEpisodeMapper playlistEpisodeMapper,
      PlaylistEpisodeDetailRetryMapper playlistEpisodeDetailRetryMapper,
      EpisodeService episodeService, ApplicationEventPublisher eventPublisher,
      YoutubeHelper youtubeHelper, YoutubePlaylistHelper youtubePlaylistHelper,
      YoutubeVideoHelper youtubeVideoHelper,
      YtDlpPlaylistSnapshotService ytDlpPlaylistSnapshotService,
      AccountService accountService, MessageSource messageSource,
      FeedDefaultsService feedDefaultsService,
      @Qualifier("channelSyncTaskExecutor") Executor channelSyncTaskExecutor) {
    super(episodeService, eventPublisher, messageSource, feedDefaultsService);
    this.playlistMapper = playlistMapper;
    this.playlistEpisodeMapper = playlistEpisodeMapper;
    this.playlistEpisodeDetailRetryMapper = playlistEpisodeDetailRetryMapper;
    this.youtubeHelper = youtubeHelper;
    this.youtubePlaylistHelper = youtubePlaylistHelper;
    this.youtubeVideoHelper = youtubeVideoHelper;
    this.ytDlpPlaylistSnapshotService = ytDlpPlaylistSnapshotService;
    this.accountService = accountService;
    this.messageSource = messageSource;
    this.channelSyncTaskExecutor = channelSyncTaskExecutor;
  }

  @PostConstruct
  private void init() {
    if (appBaseUrl != null && appBaseUrl.endsWith("/")) {
      appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
      log.info("已移除 appBaseUrl 末尾的斜杠，处理后的值为: {}", appBaseUrl);
    }
  }

  public List<Playlist> selectPlaylistList() {
    return playlistMapper.selectPlaylistsByLastPublishedAt();
  }

  public Playlist playlistDetail(String id) {
    Playlist playlist = playlistMapper.selectById(id);
    if (playlist == null) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{id},
              LocaleContextHolder.getLocale()));
    }
    playlist.setOriginalUrl(Youtube.PLAYLIST_URL + playlist.getId());
    return playlist;
  }

  public String getPlaylistRssFeedUrl(String playlistId) {
    Playlist playlist = playlistMapper.selectById(playlistId);
    if (ObjectUtils.isEmpty(playlist)) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{playlistId},
              LocaleContextHolder.getLocale()));
    }
    String apiKey = accountService.getApiKey();
    if (ObjectUtils.isEmpty(apiKey)) {
      throw new BusinessException(
          messageSource.getMessage("playlist.api.key.failed", null,
              LocaleContextHolder.getLocale()));
    }
    return appBaseUrl + "/api/rss/playlist/" + playlistId + ".xml?apikey=" + apiKey;
  }

  @Transactional
  public FeedConfigUpdateResult updatePlaylistConfig(String playlistId, Playlist configuration) {
    FeedConfigUpdateResult result = updateFeedConfig(playlistId, configuration);
    log.info("播放列表 {} 配置更新成功", playlistId);
    return result;
  }

  public FeedPack<Playlist> fetchPlaylist(String playlistUrl) {
    if (ObjectUtils.isEmpty(playlistUrl)) {
      throw new BusinessException(
          messageSource.getMessage("playlist.source.empty", null,
              LocaleContextHolder.getLocale()));
    }

    com.google.api.services.youtube.model.Playlist ytPlaylist;

    ytPlaylist = youtubeHelper.fetchYoutubePlaylist(playlistUrl);

    String ytPlaylistId = ytPlaylist.getId();
    // 先抓取一页预览视频（固定每页50），再截断到5条
    List<Episode> episodes = youtubePlaylistHelper.fetchPlaylistVideos(ytPlaylistId, 1);
    if (episodes.size() > DEFAULT_PREVIEW_NUM) {
      episodes = episodes.subList(0, DEFAULT_PREVIEW_NUM);
    }

    String playlistFallbackCover = ytPlaylist.getSnippet() != null
        && ytPlaylist.getSnippet().getThumbnails() != null
        && ytPlaylist.getSnippet().getThumbnails().getHigh() != null
        ? ytPlaylist.getSnippet().getThumbnails().getHigh().getUrl()
        : null;

    String episodeCover = !episodes.isEmpty()
        ? episodes.get(0).getMaxCoverUrl() != null
        ? episodes.get(0).getMaxCoverUrl()
        : episodes.get(0).getDefaultCoverUrl()
        : null;

    Playlist fetchedPlaylist = Playlist.builder()
        .id(ytPlaylistId)
        .title(ytPlaylist.getSnippet().getTitle())
        .ownerId(ytPlaylist.getSnippet().getChannelId())
        // 使用首个视频的大图作为封面，避免 Playlist 默认缩略图的黑边；否则回退到 playlist 自带缩略图
        .coverUrl(episodeCover != null ? episodeCover : playlistFallbackCover)
        .description(ytPlaylist.getSnippet().getDescription())
        .subscribedAt(LocalDateTime.now())
        .source(FeedSource.YOUTUBE.name())
        .originalUrl(playlistUrl)
        .autoDownloadEnabled(Boolean.TRUE)
        .build();
    feedDefaultsService().applyDefaultsIfMissing(fetchedPlaylist);

    return FeedPack.<Playlist>builder().feed(fetchedPlaylist).episodes(episodes).build();
  }

  public FeedPack<Playlist> previewPlaylist(Playlist playlist) {
    return previewFeed(playlist);
  }

  @Transactional
  public FeedSaveResult<Playlist> savePlaylist(Playlist playlist) {
    feedDefaultsService().applyDefaultsIfMissing(playlist);
    return saveFeed(playlist);
  }

  public List<Playlist> findDueForSync(LocalDateTime checkTime) {
    List<Playlist> playlists = playlistMapper.selectList(new LambdaQueryWrapper<>());
    return playlists.stream()
        .filter(p -> p.getLastSyncTimestamp() == null ||
            p.getLastSyncTimestamp().isBefore(checkTime))
        .collect(Collectors.toList());
  }

  @Transactional
  public void deletePlaylist(String playlistId) {
    log.info("开始删除播放列表: {}", playlistId);

    Playlist playlist = playlistMapper.selectById(playlistId);
    if (playlist == null) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{playlistId},
              LocaleContextHolder.getLocale()));
    }

    List<Episode> playlistEpisodes = episodeService().getEpisodesByPlaylistId(playlistId);
    LinkedHashMap<String, Episode> uniqueEpisodes = new LinkedHashMap<>();
    for (Episode episode : playlistEpisodes) {
      if (episode != null && StringUtils.hasText(episode.getId())) {
        uniqueEpisodes.putIfAbsent(episode.getId(), episode);
      }
    }

    playlistEpisodeMapper.delete(new LambdaQueryWrapper<PlaylistEpisode>().eq(PlaylistEpisode::getPlaylistId, playlistId));
    playlistEpisodeDetailRetryMapper.delete(new LambdaQueryWrapper<PlaylistEpisodeDetailRetry>().
        eq(PlaylistEpisodeDetailRetry::getPlaylistId, playlistId));

    int result = playlistMapper.deleteById(playlistId);
    if (result > 0) {
      scheduleOrphanCleanupAfterCommit(playlistId, uniqueEpisodes.values());
      log.info("播放列表 {} 删除成功，孤立节目清理任务已提交", playlist.getTitle());
    } else {
      log.error("播放列表 {} 删除失败", playlist.getTitle());
      throw new BusinessException(
          messageSource.getMessage("playlist.delete.failed", null,
              LocaleContextHolder.getLocale()));
    }
  }

  private void scheduleOrphanCleanupAfterCommit(String playlistId, Collection<Episode> episodes) {
    if (episodes == null || episodes.isEmpty()) {
      return;
    }
    List<Episode> cleanupTargets = new ArrayList<>(episodes);
    Runnable cleanupTask = () -> channelSyncTaskExecutor.execute(() -> {
      try {
        removeOrphanEpisodes(cleanupTargets);
        log.info("播放列表 {} 的孤立节目清理完成，候选数量={}", playlistId, cleanupTargets.size());
      } catch (Exception ex) {
        log.error("播放列表 {} 的孤立节目异步清理失败: {}", playlistId, ex.getMessage(), ex);
      }
    });

    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      cleanupTask.run();
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        cleanupTask.run();
      }
    });
  }

  @Transactional
  public FeedRefreshResult refreshPlaylistById(String playlistId) {
    Playlist playlist = playlistMapper.selectById(playlistId);
    if (playlist == null) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{playlistId},
              LocaleContextHolder.getLocale()));
    }
    return syncPlaylistWithSnapshot(playlist, "MANUAL_FULL");
  }

  @Transactional
  public void refreshPlaylist(Playlist playlist) {
    syncPlaylistWithSnapshot(playlist, "INCREMENTAL");
  }

  private FeedRefreshResult syncPlaylistWithSnapshot(Playlist playlist, String mode) {
    log.info("开始以 {} 模式同步播放列表: {} ({})", mode, playlist.getTitle(), playlist.getId());

    LocalDateTime now = LocalDateTime.now();
    try {
      List<PlaylistSnapshotEntry> snapshotEntries =
          ytDlpPlaylistSnapshotService.fetchPlaylistSnapshot(playlist.getId());
      Map<String, PlaylistSnapshotEntry> remoteEntryMap = buildRemoteEntryMap(snapshotEntries);
      Map<String, PlaylistEpisode> localMappingMap = buildLocalMappingMap(playlist.getId());

      List<String> addedIds = new ArrayList<>();
      List<String> removedIds = new ArrayList<>();
      List<PlaylistSnapshotEntry> movedEntries = new ArrayList<>();

      for (String localEpisodeId : localMappingMap.keySet()) {
        if (!remoteEntryMap.containsKey(localEpisodeId)) {
          removedIds.add(localEpisodeId);
        }
      }

      for (PlaylistSnapshotEntry remoteEntry : snapshotEntries) {
        PlaylistEpisode localMapping = localMappingMap.get(remoteEntry.videoId());
        if (localMapping == null) {
          addedIds.add(remoteEntry.videoId());
          continue;
        }
        if (!isSamePosition(localMapping.getPosition(), remoteEntry.position())) {
          movedEntries.add(remoteEntry);
        }
      }

      if (!removedIds.isEmpty()) {
        playlistEpisodeMapper.delete(new LambdaQueryWrapper<PlaylistEpisode>()
            .eq(PlaylistEpisode::getPlaylistId, playlist.getId())
            .in(PlaylistEpisode::getEpisodeId, removedIds));
        playlistEpisodeMapper.delete(new LambdaQueryWrapper<PlaylistEpisode>().eq(PlaylistEpisode::getPlaylistId, removedIds));
        playlistEpisodeDetailRetryMapper.delete(new LambdaQueryWrapper<PlaylistEpisodeDetailRetry>()
            .eq(PlaylistEpisodeDetailRetry::getPlaylistId, playlist.getId())
            .in(PlaylistEpisodeDetailRetry::getEpisodeId, removedIds));
        removeOrphanEpisodesByIds(removedIds);
      }

      for (PlaylistSnapshotEntry movedEntry : movedEntries) {
        PlaylistEpisode localMapping = localMappingMap.get(movedEntry.videoId());
        LocalDateTime publishedAt = localMapping != null && localMapping.getPublishedAt() != null
            ? localMapping.getPublishedAt()
            : movedEntry.approximatePublishedAt();
        upsertPlaylistEpisodeMapping(playlist.getId(), movedEntry.videoId(), movedEntry.position(),
            publishedAt);
      }

      AddedBackfillResult backfillResult = processAddedEntries(playlist, addedIds, remoteEntryMap);
      if (!backfillResult.autoDownloadCandidates().isEmpty()) {
        markAndPublishAutoDownloadEpisodes(playlist, backfillResult.autoDownloadCandidates());
      }

      if (!addedIds.isEmpty()) {
        playlist.setLastSyncVideoId(addedIds.get(0));
      }
      playlist.setLastSnapshotAt(now);
      playlist.setLastSnapshotSize(snapshotEntries.size());
      playlist.setLastSyncAddedCount(backfillResult.mappedAddedCount());
      playlist.setLastSyncRemovedCount(removedIds.size());
      playlist.setLastSyncMovedCount(movedEntries.size());
      playlist.setLastSyncTimestamp(now);
      playlist.setSyncError(null);
      playlist.setSyncErrorAt(null);
      updateCoverFromSnapshot(playlist, snapshotEntries);
      playlistMapper.updateById(playlist);

      log.info("播放列表 {} 同步完成(mode={})，snapshot={}, added={}, removed={}, moved={}, queuedRetry={}",
          playlist.getId(), mode, snapshotEntries.size(), backfillResult.mappedAddedCount(),
          removedIds.size(), movedEntries.size(), backfillResult.queuedRetryCount());

      int newEpisodeCount = backfillResult.newEpisodeCount();
      return FeedRefreshResult.builder()
          .hasNewEpisodes(newEpisodeCount > 0)
          .newEpisodeCount(newEpisodeCount)
          .message(messageSource.getMessage(
              newEpisodeCount == 0 ? "feed.refresh.no.new" : "feed.refresh.new.episodes",
              newEpisodeCount == 0
                  ? new Object[]{playlist.getTitle()}
                  : new Object[]{newEpisodeCount, playlist.getTitle()},
              LocaleContextHolder.getLocale()))
          .build();
    } catch (Exception e) {
      String error = abbreviateError(e.getMessage());
      playlist.setSyncError(error);
      playlist.setSyncErrorAt(now);
      playlist.setLastSyncTimestamp(now);
      playlistMapper.updateById(playlist);
      log.error("播放列表 {} 同步失败(mode={}): {}", playlist.getId(), mode, e.getMessage(), e);
      return FeedRefreshResult.builder()
          .hasNewEpisodes(false)
          .newEpisodeCount(0)
          .message("playlist sync failed: " + error)
          .build();
    }
  }

  @Transactional
  public int processPlaylistDetailRetryQueue(int limit) {
    int effectiveLimit = limit > 0 ? limit : DETAIL_RETRY_BATCH_SIZE;
    List<PlaylistEpisodeDetailRetry> dueRetries =
        playlistEpisodeDetailRetryMapper.selectDue(LocalDateTime.now(), effectiveLimit);
    if (dueRetries.isEmpty()) {
      return 0;
    }

    Map<String, List<PlaylistEpisodeDetailRetry>> grouped = dueRetries.stream()
        .collect(Collectors.groupingBy(PlaylistEpisodeDetailRetry::getPlaylistId));

    int recovered = 0;
    for (Map.Entry<String, List<PlaylistEpisodeDetailRetry>> group : grouped.entrySet()) {
      String playlistId = group.getKey();
      Playlist playlist = playlistMapper.selectById(playlistId);
      if (playlist == null) {
        for (PlaylistEpisodeDetailRetry retry : group.getValue()) {
          playlistEpisodeDetailRetryMapper.deleteById(retry.getId());
        }
        continue;
      }

      String apiKey;
      try {
        apiKey = YoutubeApiKeyHolder.requireYoutubeApiKey(messageSource);
      } catch (Exception ex) {
        for (PlaylistEpisodeDetailRetry retry : group.getValue()) {
          handleRetryFailure(retry, ex.getMessage());
        }
        continue;
      }

      Map<String, PlaylistEpisodeDetailRetry> retryByEpisodeId = new HashMap<>();
      List<String> episodeIds = new ArrayList<>();
      for (PlaylistEpisodeDetailRetry retry : group.getValue()) {
        retryByEpisodeId.put(retry.getEpisodeId(), retry);
        episodeIds.add(retry.getEpisodeId());
      }

      List<Episode> existing = episodeService().getEpisodeStatusByIds(episodeIds);
      Set<String> existingIds = existing.stream().map(Episode::getId).collect(Collectors.toSet());
      int recoveredInGroup = 0;
      TopEpisodeCollector autoDownloadCollector =
          new TopEpisodeCollector(resolveDownloadLimit(playlist));

      for (int start = 0; start < episodeIds.size(); start += VIDEO_DETAILS_BATCH_SIZE) {
        int end = Math.min(start + VIDEO_DETAILS_BATCH_SIZE, episodeIds.size());
        List<String> batch = episodeIds.subList(start, end);
        Map<String, Video> details;
        try {
          details = youtubeVideoHelper.fetchVideoDetailsInBulk(batch, apiKey);
        } catch (Exception ex) {
          for (String episodeId : batch) {
            PlaylistEpisodeDetailRetry retry = retryByEpisodeId.get(episodeId);
            if (retry != null) {
              handleRetryFailure(retry, ex.getMessage());
            }
          }
          continue;
        }

        for (String episodeId : batch) {
          PlaylistEpisodeDetailRetry retry = retryByEpisodeId.get(episodeId);
          if (retry == null) {
            continue;
          }
          Video video = details.get(episodeId);
          if (video == null) {
            handleRetryFailure(retry, "missing video detail from YouTube API");
            continue;
          }

          PlaylistSnapshotEntry snapshotEntry = new PlaylistSnapshotEntry(
              retry.getEpisodeId(),
              retry.getPosition(),
              null,
              retry.getApproximatePublishedAt()
          );
          Optional<Episode> maybeEpisode = buildEpisodeFromVideo(playlist, video, snapshotEntry);
          if (maybeEpisode.isEmpty()) {
            playlistEpisodeDetailRetryMapper.deleteById(retry.getId());
            continue;
          }

          Episode episode = maybeEpisode.get();
          episodeService().saveEpisodes(List.of(episode));
          upsertPlaylistEpisodeMapping(playlistId, episode.getId(), snapshotEntry.position(),
              episode.getPublishedAt());
          playlistEpisodeDetailRetryMapper.deleteById(retry.getId());
          recoveredInGroup++;

          if (!existingIds.contains(episode.getId())) {
            autoDownloadCollector.offer(episode);
          }
        }
      }

      List<Episode> autoDownloadCandidates = autoDownloadCollector.toSortedList();
      if (!autoDownloadCandidates.isEmpty()) {
        markAndPublishAutoDownloadEpisodes(playlist, autoDownloadCandidates);
      }
      recovered += recoveredInGroup;
    }
    return recovered;
  }

  private AddedBackfillResult processAddedEntries(Playlist playlist, List<String> addedIds,
      Map<String, PlaylistSnapshotEntry> remoteEntryMap) {
    if (addedIds.isEmpty()) {
      return new AddedBackfillResult(0, 0, 0, List.of());
    }

    String playlistId = playlist.getId();
    int mappedAddedCount = 0;
    int queuedRetryCount = 0;
    int newEpisodeCount = 0;
    TopEpisodeCollector autoDownloadCollector =
        new TopEpisodeCollector(resolveDownloadLimit(playlist));

    String apiKey = null;
    try {
      apiKey = YoutubeApiKeyHolder.requireYoutubeApiKey(messageSource);
    } catch (Exception ex) {
      log.warn("播放列表 {} 获取 YouTube API Key 失败，新增详情将进入重试队列: {}", playlistId, ex.getMessage());
    }

    for (int chunkStart = 0; chunkStart < addedIds.size(); chunkStart += EPISODE_LOOKUP_BATCH_SIZE) {
      int chunkEnd = Math.min(chunkStart + EPISODE_LOOKUP_BATCH_SIZE, addedIds.size());
      List<String> addedChunk = addedIds.subList(chunkStart, chunkEnd);
      Map<String, Episode> existingEpisodeMap = loadBasicEpisodesByIdsInBatches(addedChunk);
      List<String> detailRequiredIds = new ArrayList<>();

      for (String episodeId : addedChunk) {
        PlaylistSnapshotEntry snapshotEntry = remoteEntryMap.get(episodeId);
        if (snapshotEntry == null) {
          continue;
        }
        Episode existing = existingEpisodeMap.get(episodeId);
        if (existing == null) {
          detailRequiredIds.add(episodeId);
          continue;
        }
        if (!matchesPlaylistFilters(playlist, existing, null)) {
          continue;
        }
        upsertPlaylistEpisodeMapping(playlistId, episodeId, snapshotEntry.position(),
            existing.getPublishedAt() != null ? existing.getPublishedAt()
                : snapshotEntry.approximatePublishedAt());
        mappedAddedCount++;
      }

      if (detailRequiredIds.isEmpty()) {
        continue;
      }

      if (!StringUtils.hasText(apiKey)) {
        queuedRetryCount += queueMissingDetails(playlistId, detailRequiredIds, remoteEntryMap,
            "youtube api key unavailable");
        continue;
      }

      for (int start = 0; start < detailRequiredIds.size(); start += VIDEO_DETAILS_BATCH_SIZE) {
        int end = Math.min(start + VIDEO_DETAILS_BATCH_SIZE, detailRequiredIds.size());
        List<String> detailBatchIds = detailRequiredIds.subList(start, end);
        Map<String, Video> detailMap;
        try {
          detailMap = youtubeVideoHelper.fetchVideoDetailsInBulk(detailBatchIds, apiKey);
        } catch (Exception ex) {
          queuedRetryCount += queueMissingDetails(playlistId, detailBatchIds, remoteEntryMap, ex.getMessage());
          continue;
        }

        List<Episode> batchEpisodes = new ArrayList<>();
        for (String episodeId : detailBatchIds) {
          PlaylistSnapshotEntry snapshotEntry = remoteEntryMap.get(episodeId);
          if (snapshotEntry == null) {
            continue;
          }
          Video video = detailMap.get(episodeId);
          if (video == null) {
            queuedRetryCount += queueMissingDetails(playlistId, List.of(episodeId), remoteEntryMap,
                "missing video detail from YouTube API");
            continue;
          }

          Optional<Episode> maybeEpisode = buildEpisodeFromVideo(playlist, video, snapshotEntry);
          maybeEpisode.ifPresent(batchEpisodes::add);
        }

        if (batchEpisodes.isEmpty()) {
          continue;
        }

        saveEpisodesInBatches(batchEpisodes, EPISODE_SAVE_BATCH_SIZE);
        upsertPlaylistEpisodes(playlistId, batchEpisodes);
        mappedAddedCount += batchEpisodes.size();
        newEpisodeCount += batchEpisodes.size();
        autoDownloadCollector.offerAll(batchEpisodes);
      }
    }

    return new AddedBackfillResult(
        mappedAddedCount,
        queuedRetryCount,
        newEpisodeCount,
        autoDownloadCollector.toSortedList());
  }

  private Optional<Episode> buildEpisodeFromVideo(Playlist playlist, Video video,
      PlaylistSnapshotEntry snapshotEntry) {
    if (video == null || video.getSnippet() == null || !StringUtils.hasText(video.getId())) {
      return Optional.empty();
    }
    if (youtubeVideoHelper.shouldSkipLiveContent(video)) {
      return Optional.empty();
    }

    String duration = video.getContentDetails() == null ? null : video.getContentDetails().getDuration();
    if (!StringUtils.hasText(duration)) {
      return Optional.empty();
    }

    LocalDateTime publishedAt = snapshotEntry.approximatePublishedAt();
    if (video.getSnippet().getPublishedAt() != null) {
      publishedAt = LocalDateTime.ofInstant(
          java.time.Instant.ofEpochMilli(video.getSnippet().getPublishedAt().getValue()),
          java.time.ZoneId.systemDefault());
    }
    if (publishedAt == null) {
      publishedAt = LocalDateTime.now();
    }

    Episode.EpisodeBuilder builder = Episode.builder()
        .id(video.getId())
        // 播放列表新增节目不直接归属频道，避免污染频道视图。
        .channelId(null)
        .title(video.getSnippet().getTitle())
        .description(video.getSnippet().getDescription())
        .publishedAt(publishedAt)
        .duration(duration)
        .position(snapshotEntry.position())
        .downloadStatus(EpisodeStatus.READY.name())
        .createdAt(LocalDateTime.now());
    youtubeVideoHelper.applyThumbnails(builder, video.getSnippet().getThumbnails());
    Episode candidate = builder.build();
    if (!matchesPlaylistFilters(playlist, candidate, video)) {
      return Optional.empty();
    }
    return Optional.of(candidate);
  }

  private boolean matchesPlaylistFilters(Playlist playlist, Episode episode, Video video) {
    if (episode == null) {
      return false;
    }
    if (youtubeVideoHelper.notMatchesKeywordFilter(episode.getTitle(), playlist.getTitleContainKeywords(),
        playlist.getTitleExcludeKeywords())) {
      return false;
    }
    if (youtubeVideoHelper.notMatchesKeywordFilter(episode.getDescription(),
        playlist.getDescriptionContainKeywords(), playlist.getDescriptionExcludeKeywords())) {
      return false;
    }
    if (youtubeVideoHelper.notMatchesDurationFilter(episode.getDuration(), playlist.getMinimumDuration(),
        playlist.getMaximumDuration())) {
      return false;
    }
    return video == null || !youtubeVideoHelper.shouldSkipLiveContent(video);
  }

  private int queueMissingDetails(String playlistId, List<String> episodeIds,
      Map<String, PlaylistSnapshotEntry> remoteEntryMap, String errorMessage) {
    if (episodeIds == null || episodeIds.isEmpty()) {
      return 0;
    }

    LocalDateTime now = LocalDateTime.now();
    int queued = 0;
    for (String episodeId : episodeIds) {
      PlaylistSnapshotEntry entry = remoteEntryMap.get(episodeId);
      if (entry == null) {
        continue;
      }
      PlaylistEpisodeDetailRetry retry = PlaylistEpisodeDetailRetry.builder()
          .playlistId(playlistId)
          .episodeId(episodeId)
          .position(entry.position())
          .approximatePublishedAt(entry.approximatePublishedAt())
          .retryCount(0)
          .nextRetryAt(now.plusMinutes(10))
          .lastError(abbreviateError(errorMessage))
          .createdAt(now)
          .updatedAt(now)
          .build();
      playlistEpisodeDetailRetryMapper.upsert(retry);
      queued++;
    }
    return queued;
  }

  private void handleRetryFailure(PlaylistEpisodeDetailRetry retry, String errorMessage) {
    if (retry == null || retry.getId() == null) {
      return;
    }
    int nextRetryCount = retry.getRetryCount() == null ? 1 : retry.getRetryCount() + 1;
    if (nextRetryCount >= DETAIL_RETRY_MAX_ATTEMPTS) {
      playlistEpisodeDetailRetryMapper.deleteById(retry.getId());
      return;
    }

    long delayMinutes = Math.min(12 * 60L, (long) (5 * Math.pow(2, nextRetryCount)));
    LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
    playlistEpisodeDetailRetryMapper.updateRetryMeta(
        retry.getId(),
        nextRetryCount,
        nextRetryAt,
        abbreviateError(errorMessage),
        LocalDateTime.now());
  }

  private void updateCoverFromSnapshot(Playlist playlist, List<PlaylistSnapshotEntry> snapshotEntries) {
    if (snapshotEntries == null || snapshotEntries.isEmpty()) {
      return;
    }
    PlaylistSnapshotEntry first = snapshotEntries.get(0);
    List<Episode> episodes = episodeService().getEpisodesByIds(List.of(first.videoId()));
    if (episodes.isEmpty()) {
      return;
    }
    Episode latest = episodes.get(0);
    String candidateCover = latest.getMaxCoverUrl() != null ? latest.getMaxCoverUrl()
        : latest.getDefaultCoverUrl();
    if (StringUtils.hasText(candidateCover)) {
      playlist.setCoverUrl(candidateCover);
    }
  }

  private Map<String, PlaylistSnapshotEntry> buildRemoteEntryMap(List<PlaylistSnapshotEntry> snapshotEntries) {
    Map<String, PlaylistSnapshotEntry> remote = new LinkedHashMap<>();
    for (PlaylistSnapshotEntry entry : snapshotEntries) {
      if (!StringUtils.hasText(entry.videoId())) {
        continue;
      }
      remote.putIfAbsent(entry.videoId(), entry);
    }
    return remote;
  }

  private Map<String, PlaylistEpisode> buildLocalMappingMap(String playlistId) {
    List<PlaylistEpisode> mappings = playlistEpisodeMapper.selectMappingsByPlaylistId(playlistId);
    Map<String, PlaylistEpisode> result = new HashMap<>();
    for (PlaylistEpisode mapping : mappings) {
      if (mapping == null || !StringUtils.hasText(mapping.getEpisodeId())) {
        continue;
      }
      result.putIfAbsent(mapping.getEpisodeId(), mapping);
    }
    return result;
  }

  private boolean isSamePosition(Long left, Long right) {
    if (left == null && right == null) {
      return true;
    }
    if (left == null || right == null) {
      return false;
    }
    return left.equals(right);
  }

  private void upsertPlaylistEpisodeMapping(String playlistId, String episodeId, Long position,
      LocalDateTime publishedAt) {
    int count = playlistEpisodeMapper.countByPlaylistAndEpisode(playlistId, episodeId);
    if (count > 0) {
      playlistEpisodeMapper.updateMapping(playlistId, episodeId, position, publishedAt);
      return;
    }
    playlistEpisodeMapper.insertMapping(playlistId, episodeId, position, publishedAt);
  }

  private void removeOrphanEpisodesByIds(List<String> episodeIds) {
    if (episodeIds == null || episodeIds.isEmpty()) {
      return;
    }
    for (int start = 0; start < episodeIds.size(); start += EPISODE_LOOKUP_BATCH_SIZE) {
      int end = Math.min(start + EPISODE_LOOKUP_BATCH_SIZE, episodeIds.size());
      List<String> batchIds = episodeIds.subList(start, end);
      List<Episode> episodes = episodeService().getEpisodesByIds(batchIds);
      removeOrphanEpisodes(episodes);
    }
  }

  private Map<String, Episode> loadBasicEpisodesByIdsInBatches(List<String> episodeIds) {
    if (episodeIds == null || episodeIds.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Episode> existing = new HashMap<>();
    for (int start = 0; start < episodeIds.size(); start += EPISODE_LOOKUP_BATCH_SIZE) {
      int end = Math.min(start + EPISODE_LOOKUP_BATCH_SIZE, episodeIds.size());
      List<String> batch = episodeIds.subList(start, end);
      List<Episode> batchEpisodes = episodeService().getEpisodesBasicByIds(batch);
      for (Episode episode : batchEpisodes) {
        if (episode == null || !StringUtils.hasText(episode.getId())) {
          continue;
        }
        existing.putIfAbsent(episode.getId(), episode);
      }
    }
    return existing;
  }

  private void saveEpisodesInBatches(List<Episode> episodes, int batchSize) {
    if (episodes == null || episodes.isEmpty()) {
      return;
    }
    int effectiveBatchSize = batchSize > 0 ? batchSize : EPISODE_SAVE_BATCH_SIZE;
    for (int start = 0; start < episodes.size(); start += effectiveBatchSize) {
      int end = Math.min(start + effectiveBatchSize, episodes.size());
      List<Episode> batch = episodes.subList(start, end);
      episodeService().saveEpisodes(batch);
    }
  }

  private String abbreviateError(String message) {
    if (!StringUtils.hasText(message)) {
      return "unknown";
    }
    String trimmed = message.trim();
    if (trimmed.length() <= 400) {
      return trimmed;
    }
    return trimmed.substring(0, 400);
  }

  private record AddedBackfillResult(int mappedAddedCount, int queuedRetryCount,
                                     int newEpisodeCount, List<Episode> autoDownloadCandidates) {

  }

  private static final class TopEpisodeCollector {

    private final int limit;
    private final PriorityQueue<Episode> queue;

    private TopEpisodeCollector(int limit) {
      this.limit = Math.max(limit, 0);
      this.queue = new PriorityQueue<>(Math.max(1, this.limit), AUTO_DOWNLOAD_WORST_FIRST);
    }

    private void offerAll(List<Episode> episodes) {
      if (episodes == null || episodes.isEmpty()) {
        return;
      }
      for (Episode episode : episodes) {
        offer(episode);
      }
    }

    private void offer(Episode episode) {
      if (limit <= 0 || episode == null) {
        return;
      }
      if (queue.size() < limit) {
        queue.offer(episode);
        return;
      }
      Episode worst = queue.peek();
      if (worst == null) {
        queue.offer(episode);
        return;
      }
      if (AUTO_DOWNLOAD_NEWEST_FIRST.compare(episode, worst) < 0) {
        queue.poll();
        queue.offer(episode);
      }
    }

    private List<Episode> toSortedList() {
      if (queue.isEmpty()) {
        return List.of();
      }
      List<Episode> result = new ArrayList<>(queue);
      result.sort(AUTO_DOWNLOAD_NEWEST_FIRST);
      return result;
    }

  }

  /**
   * 拉取播放列表历史节目信息：基于当前已入库节目数量，计算 YouTube Data API 的下一页，
   * 抓取该页节目并按当前配置过滤后仅入库节目信息，不触发内容下载。
   *
   * @param playlistId 播放列表 ID
   * @return 新增的节目信息列表（已去重）
   */
  @Transactional
  public List<Episode> fetchPlaylistHistory(String playlistId) {
    Playlist playlist = playlistMapper.selectById(playlistId);
    if (playlist == null) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{playlistId},
              LocaleContextHolder.getLocale()));
    }

    long totalCount = playlistEpisodeMapper.countByPlaylistId(playlistId);
    if (totalCount <= 0) {
      log.warn("播放列表 {} 尚未初始化节目，跳过历史节目信息抓取", playlistId);
      return Collections.emptyList();
    }

    int pageSize = 50;
    int currentPage = (int) ((totalCount + pageSize - 1) / pageSize);
    int targetPage = currentPage + 1;

    log.info("准备为播放列表 {} 拉取历史节目信息：totalCount={}, currentPage={}, targetPage={}",
        playlistId, totalCount, currentPage, targetPage);

    List<Episode> episodes = youtubePlaylistHelper.fetchPlaylistHistoryPage(
        playlistId,
        targetPage,
        playlist.getTitleContainKeywords(),
        playlist.getTitleExcludeKeywords(),
        playlist.getDescriptionContainKeywords(),
        playlist.getDescriptionExcludeKeywords(),
        playlist.getMinimumDuration(),
        playlist.getMaximumDuration());

    if (episodes.isEmpty()) {
      log.info("播放列表 {} 在历史页 {} 未找到任何符合条件的节目", playlistId, targetPage);
      return Collections.emptyList();
    }

    List<Episode> episodesToPersist = prepareEpisodesForPersistence(episodes);
    episodeService().saveEpisodes(episodesToPersist);
    upsertPlaylistEpisodes(playlistId, episodesToPersist);

    log.info("播放列表 {} 历史节目信息入库完成，本次新增 {} 条记录（请求页: {}）",
        playlistId, episodesToPersist.size(), targetPage);

    return episodesToPersist;
  }

  @Transactional
  public void processPlaylistInitializationAsync(String playlistId, Integer autoDownloadLimit,
      String titleContainKeywords, String titleExcludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords,
      Integer minimumDuration, Integer maximumDuration) {
    log.info(
        "开始异步处理播放列表初始化，播放列表ID: {}, autoDownloadLimit={}, titleContainKeywords={}, titleExcludeKeywords={}, descriptionContainKeywords={}, descriptionExcludeKeywords={}, minimumDuration={}, maximumDuration={}",
        playlistId, autoDownloadLimit, titleContainKeywords, titleExcludeKeywords,
        descriptionContainKeywords, descriptionExcludeKeywords, minimumDuration, maximumDuration);

    Playlist playlist = playlistMapper.selectById(playlistId);
    if (playlist == null) {
      log.warn("播放列表初始化跳过：播放列表不存在，playlistId={}", playlistId);
      return;
    }

    FeedRefreshResult result = syncPlaylistWithSnapshot(playlist, "INIT");
    log.info("播放列表 {} 初始化同步完成: {}", playlistId, result);
  }

  private void upsertPlaylistEpisodes(String playlistId, List<Episode> episodes) {
    for (Episode episode : episodes) {
      int count = playlistEpisodeMapper.countByPlaylistAndEpisode(playlistId, episode.getId());
      int affected;
      if (count > 0) {
        affected = playlistEpisodeMapper.updateMapping(playlistId, episode.getId(), episode.getPosition(),
            episode.getPublishedAt());
      } else {
        affected = playlistEpisodeMapper.insertMapping(playlistId, episode.getId(), episode.getPosition(),
            episode.getPublishedAt());
      }
      if (affected <= 0) {
        log.warn("更新播放列表 {} 与节目 {} 的关联失败", playlistId, episode.getId());
      }
    }
  }

  /**
   * 删除播放列表孤立节目：即仅被当前播放列表引用的节目。
   *
   * @param episodes 播放列表关联的所有节目
   */
  private void removeOrphanEpisodes(Collection<Episode> episodes) {
    if (episodes == null || episodes.isEmpty()) {
      return;
    }

    boolean s3Mode = episodeService().isS3Mode();
    Set<String> candidateDirectories = new HashSet<>();
    for (Episode episode : episodes) {
      long orhanEpisode = playlistEpisodeMapper.isOrhanEpisode(episode.getId());
      if (orhanEpisode == 0) {
        continue;
      }

      String mediaFilePath = episode.getMediaFilePath();

      try {
        int deleteResult = episodeService().deleteEpisodeCompletelyById(episode.getId());
        if (!s3Mode && deleteResult > 0 && StringUtils.hasText(mediaFilePath)) {
          File audioFile = new File(mediaFilePath);
          File parentDir = audioFile.getParentFile();
          if (parentDir != null) {
            candidateDirectories.add(parentDir.getAbsolutePath());
          }
        }
      } catch (Exception ex) {
        log.error("删除播放列表孤立节目 {} 失败: {}", episode.getId(), ex.getMessage(), ex);
      }
    }

    if (!s3Mode) {
      cleanupEmptyDirectories(candidateDirectories);
    }
  }

  private void cleanupEmptyDirectories(Set<String> directories) {
    if (directories == null || directories.isEmpty()) {
      return;
    }
    for (String directoryPath : directories) {
      if (!StringUtils.hasText(directoryPath)) {
        continue;
      }
      try {
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
          File[] files = directory.listFiles();
          if (files != null && files.length == 0) {
            boolean deleted = directory.delete();
            if (deleted) {
              log.info("空的播放列表音频文件夹删除成功: {}", directoryPath);
            } else {
              log.warn("空的播放列表音频文件夹删除失败: {}", directoryPath);
            }
          }
        }
      } catch (Exception ex) {
        log.error("检查或删除播放列表音频文件夹时出错: {}", directoryPath, ex);
      }
    }
  }

  @Override
  protected Optional<Playlist> findFeedById(String feedId) {
    return Optional.ofNullable(playlistMapper.selectById(feedId));
  }

  @Override
  protected int updateFeed(Playlist feed) {
    return playlistMapper.updateById(feed);
  }

  @Override
  protected void insertFeed(Playlist feed) {
    playlistMapper.insert(feed);
  }

  @Override
  protected DownloadTargetType downloadTargetType() {
    return DownloadTargetType.PLAYLIST;
  }

  @Override
  protected List<Episode> fetchEpisodes(Playlist feed) {
    int pages = Math.max(1, (int) Math.ceil((double) Math.max(1, AbstractFeedService.DEFAULT_PREVIEW_NUM) / 50.0));
    List<Episode> episodes = youtubePlaylistHelper.fetchPlaylistVideos(
        feed.getId(), pages, null,
        feed.getTitleContainKeywords(), feed.getTitleExcludeKeywords(),
        feed.getDescriptionContainKeywords(), feed.getDescriptionExcludeKeywords(),
        feed.getMinimumDuration(),
        feed.getMaximumDuration());
    if (episodes.size() > AbstractFeedService.DEFAULT_PREVIEW_NUM) {
      return episodes.subList(0, AbstractFeedService.DEFAULT_PREVIEW_NUM);
    }
    return episodes;
  }

  @Override
  protected List<Episode> fetchIncrementalEpisodes(Playlist feed) {
    // 为了应对播放列表顺序调整、插入旧视频等情况，每次刷新时对整个播放列表做全量扫描，
    // 然后根据 Episode ID 与数据库中的现有记录做差值，确定真正新增的节目。
    List<Episode> episodes = youtubePlaylistHelper.fetchPlaylistVideos(
        feed.getId(),
        Integer.MAX_VALUE,
        null,
        feed.getTitleContainKeywords(),
        feed.getTitleExcludeKeywords(),
        feed.getDescriptionContainKeywords(),
        feed.getDescriptionExcludeKeywords(),
        feed.getMinimumDuration(),
        feed.getMaximumDuration());

    return filterNewEpisodes(episodes);
  }

  @Override
  protected List<Episode> prepareEpisodesForPersistence(List<Episode> episodes) {
    if (episodes == null || episodes.isEmpty()) {
      return List.of();
    }
    List<Episode> normalized = new ArrayList<>(episodes.size());
    for (Episode episode : episodes) {
      if (episode == null) {
        continue;
      }
      // 兜底清空 channelId，覆盖来自通用 helper/历史抓取路径的值。
      episode.setChannelId(null);
      normalized.add(episode);
    }
    return normalized;
  }

  @Override
  protected void afterEpisodesPersisted(Playlist feed, List<Episode> episodes) {
    if (feed != null) {
      upsertPlaylistEpisodes(feed.getId(), episodes);
      // 使用最新一期节目的大图更新播放列表封面，避免播放列表默认缩略图的黑边
      if (!ObjectUtils.isEmpty(episodes)) {
        Episode latest = episodes.get(0);
        String candidateCover = latest.getMaxCoverUrl() != null
            ? latest.getMaxCoverUrl()
            : latest.getDefaultCoverUrl();
        if (StringUtils.hasText(candidateCover) && !candidateCover.equals(feed.getCoverUrl())) {
          feed.setCoverUrl(candidateCover);
          updateFeed(feed);
        }
      }
    }
  }

  @Override
  protected org.apache.logging.log4j.Logger logger() {
    return log;
  }
}
