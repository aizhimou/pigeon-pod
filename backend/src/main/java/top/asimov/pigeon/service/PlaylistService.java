package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.api.services.youtube.model.Video;
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
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.config.AppBaseUrlResolver;
import top.asimov.pigeon.config.YoutubeApiKeyHolder;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadTargetType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.helper.BilibiliPlaylistHelper;
import top.asimov.pigeon.helper.BilibiliResolverHelper;
import top.asimov.pigeon.helper.YoutubeHelper;
import top.asimov.pigeon.helper.YoutubePlaylistHelper;
import top.asimov.pigeon.helper.YoutubeVideoHelper;
import top.asimov.pigeon.mapper.PlaylistEpisodeDetailRetryMapper;
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.dto.PlaylistSnapshotEntry;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.entity.PlaylistEpisode;
import top.asimov.pigeon.model.entity.PlaylistEpisodeDetailRetry;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.enums.FeedSource;
import top.asimov.pigeon.model.response.FeedConfigUpdateResult;
import top.asimov.pigeon.model.response.FeedPack;
import top.asimov.pigeon.model.response.FeedRefreshResult;
import top.asimov.pigeon.model.response.FeedSaveResult;
import top.asimov.pigeon.util.FeedEpisodeVisibilityHelper;
import top.asimov.pigeon.util.FeedSourceUrlBuilder;

@Log4j2
@Service
public class PlaylistService extends AbstractFeedService<Playlist> {

  private static final int VIDEO_DETAILS_BATCH_SIZE = 50;
  private static final int EPISODE_LOOKUP_BATCH_SIZE = 500;
  private static final int EPISODE_SAVE_BATCH_SIZE = 200;
  private static final int DETAIL_RETRY_BATCH_SIZE = 100;
  private static final int DETAIL_RETRY_MAX_ATTEMPTS = 8;
  private static final String CURSOR_TYPE_YOUTUBE_PAGE_TOKEN = "YOUTUBE_PAGE_TOKEN";
  private static final String CURSOR_TYPE_BILIBILI_PAGE_NUM = "BILIBILI_PAGE_NUM";
  private static final Comparator<Episode> AUTO_DOWNLOAD_NEWEST_FIRST =
      Comparator.comparing(Episode::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Episode::getId, Comparator.nullsLast(String::compareTo));
  private static final Comparator<Episode> AUTO_DOWNLOAD_WORST_FIRST =
      AUTO_DOWNLOAD_NEWEST_FIRST.reversed();

  private final PlaylistMapper playlistMapper;
  private final PlaylistEpisodeMapper playlistEpisodeMapper;
  private final PlaylistEpisodeDetailRetryMapper playlistEpisodeDetailRetryMapper;
  private final YoutubeHelper youtubeHelper;
  private final YoutubePlaylistHelper youtubePlaylistHelper;
  private final YoutubeVideoHelper youtubeVideoHelper;
  private final BilibiliResolverHelper bilibiliResolverHelper;
  private final BilibiliPlaylistHelper bilibiliPlaylistHelper;
  private final YtDlpPlaylistSnapshotService ytDlpPlaylistSnapshotService;
  private final AccountService accountService;
  private final MessageSource messageSource;
  private final Executor channelSyncTaskExecutor;
  private final AppBaseUrlResolver appBaseUrlResolver;

  public PlaylistService(PlaylistMapper playlistMapper,
      PlaylistEpisodeMapper playlistEpisodeMapper,
      PlaylistEpisodeDetailRetryMapper playlistEpisodeDetailRetryMapper,
      EpisodeService episodeService, ApplicationEventPublisher eventPublisher,
      YoutubeHelper youtubeHelper, YoutubePlaylistHelper youtubePlaylistHelper,
      YoutubeVideoHelper youtubeVideoHelper,
      BilibiliResolverHelper bilibiliResolverHelper,
      BilibiliPlaylistHelper bilibiliPlaylistHelper,
      YtDlpPlaylistSnapshotService ytDlpPlaylistSnapshotService,
      AccountService accountService, MessageSource messageSource,
      FeedDefaultsService feedDefaultsService,
      @Qualifier("channelSyncTaskExecutor") Executor channelSyncTaskExecutor,
      AppBaseUrlResolver appBaseUrlResolver) {
    super(episodeService, eventPublisher, messageSource, feedDefaultsService);
    this.playlistMapper = playlistMapper;
    this.playlistEpisodeMapper = playlistEpisodeMapper;
    this.playlistEpisodeDetailRetryMapper = playlistEpisodeDetailRetryMapper;
    this.youtubeHelper = youtubeHelper;
    this.youtubePlaylistHelper = youtubePlaylistHelper;
    this.youtubeVideoHelper = youtubeVideoHelper;
    this.bilibiliResolverHelper = bilibiliResolverHelper;
    this.bilibiliPlaylistHelper = bilibiliPlaylistHelper;
    this.ytDlpPlaylistSnapshotService = ytDlpPlaylistSnapshotService;
    this.accountService = accountService;
    this.messageSource = messageSource;
    this.channelSyncTaskExecutor = channelSyncTaskExecutor;
    this.appBaseUrlResolver = appBaseUrlResolver;
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
    playlist.setOriginalUrl(
        FeedSourceUrlBuilder.buildPlaylistUrl(playlist.getSource(), playlist.getId(), playlist.getOwnerId()));
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
    return appBaseUrlResolver.requireBaseUrl() + "/api/rss/playlist/" + playlistId + ".xml?apikey=" + apiKey;
  }

  @Transactional
  public FeedConfigUpdateResult updatePlaylistConfig(String playlistId, Playlist configuration) {
    FeedConfigUpdateResult result = updateFeedConfig(playlistId, configuration);
    log.info("播放列表 {} 配置更新成功", playlistId);
    return result;
  }

  @Transactional
  public void updatePlaylistCustomCoverExt(String playlistId, String customCoverExt) {
    Playlist playlist = playlistMapper.selectById(playlistId);
    if (playlist == null) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{playlistId},
              LocaleContextHolder.getLocale()));
    }
    playlist.setCustomCoverExt(customCoverExt);
    int updated = playlistMapper.updateById(playlist);
    if (updated <= 0) {
      throw new BusinessException(messageSource.getMessage("feed.config.update.failed", null,
          LocaleContextHolder.getLocale()));
    }
  }

  public FeedPack<Playlist> fetchPlaylist(String playlistUrl) {
    if (ObjectUtils.isEmpty(playlistUrl)) {
      throw new BusinessException(
          messageSource.getMessage("playlist.source.empty", null,
              LocaleContextHolder.getLocale()));
    }

    if (bilibiliResolverHelper.isBilibiliInput(playlistUrl)
        && bilibiliResolverHelper.isBilibiliPlaylistInput(playlistUrl)) {
      BilibiliPlaylistHelper.PlaylistFetchResult bilibiliPlaylist =
          bilibiliPlaylistHelper.fetchPlaylistByInput(playlistUrl);
      List<Episode> episodes = bilibiliPlaylist.previewEpisodes();
      if (episodes.size() > DEFAULT_PREVIEW_NUM) {
        episodes = episodes.subList(0, DEFAULT_PREVIEW_NUM);
      }
      Playlist fetchedPlaylist = Playlist.builder()
          .id(bilibiliPlaylist.playlistId())
          .title(
              StringUtils.hasText(bilibiliPlaylist.title()) ? bilibiliPlaylist.title() : bilibiliPlaylist.playlistId())
          .ownerId(bilibiliPlaylist.ownerMid())
          .coverUrl(StringUtils.hasText(bilibiliPlaylist.coverUrl()) ? bilibiliPlaylist.coverUrl() : "")
          .description(StringUtils.hasText(bilibiliPlaylist.description()) ? bilibiliPlaylist.description() : "")
          .subscribedAt(LocalDateTime.now())
          .source(FeedSource.BILIBILI.name())
          .originalUrl(playlistUrl)
          .autoDownloadEnabled(Boolean.TRUE)
          .build();
      feedDefaultsService().applyDefaultsIfMissing(fetchedPlaylist);
      episodes = bilibiliPlaylistHelper.fetchPlaylistVideos(
          fetchedPlaylist.getId(),
          fetchedPlaylist.getOwnerId(),
          1,
          fetchedPlaylist.getTitleContainKeywords(),
          fetchedPlaylist.getTitleExcludeKeywords(),
          fetchedPlaylist.getDescriptionContainKeywords(),
          fetchedPlaylist.getDescriptionExcludeKeywords(),
          fetchedPlaylist.getMinimumDuration(),
          fetchedPlaylist.getMaximumDuration());
      episodes = FeedEpisodeVisibilityHelper.filterVisibleEpisodes(fetchedPlaylist, episodes);
      if (episodes.size() > DEFAULT_PREVIEW_NUM) {
        episodes = episodes.subList(0, DEFAULT_PREVIEW_NUM);
      }
      return FeedPack.<Playlist>builder().feed(fetchedPlaylist).episodes(episodes).build();
    }

    com.google.api.services.youtube.model.Playlist ytPlaylist;

    ytPlaylist = youtubeHelper.fetchYoutubePlaylist(playlistUrl);

    String ytPlaylistId = ytPlaylist.getId();

    String playlistFallbackCover = ytPlaylist.getSnippet() != null
        && ytPlaylist.getSnippet().getThumbnails() != null
        && ytPlaylist.getSnippet().getThumbnails().getHigh() != null
        ? ytPlaylist.getSnippet().getThumbnails().getHigh().getUrl()
        : null;

    Playlist fetchedPlaylist = Playlist.builder()
        .id(ytPlaylistId)
        .title(ytPlaylist.getSnippet().getTitle())
        .ownerId(ytPlaylist.getSnippet().getChannelId())
        .coverUrl(playlistFallbackCover)
        .description(ytPlaylist.getSnippet().getDescription())
        .subscribedAt(LocalDateTime.now())
        .source(FeedSource.YOUTUBE.name())
        .originalUrl(playlistUrl)
        .autoDownloadEnabled(Boolean.TRUE)
        .build();
    feedDefaultsService().applyDefaultsIfMissing(fetchedPlaylist);
    List<Episode> episodes = youtubePlaylistHelper.fetchPlaylistVideos(
        ytPlaylistId,
        1,
        null,
        fetchedPlaylist.getTitleContainKeywords(),
        fetchedPlaylist.getTitleExcludeKeywords(),
        fetchedPlaylist.getDescriptionContainKeywords(),
        fetchedPlaylist.getDescriptionExcludeKeywords(),
        fetchedPlaylist.getMinimumDuration(),
        fetchedPlaylist.getMaximumDuration());
    episodes = FeedEpisodeVisibilityHelper.filterVisibleEpisodes(fetchedPlaylist, episodes);
    if (episodes.size() > DEFAULT_PREVIEW_NUM) {
      episodes = episodes.subList(0, DEFAULT_PREVIEW_NUM);
    }
    String episodeCover = !episodes.isEmpty()
        ? episodes.get(0).getMaxCoverUrl() != null
        ? episodes.get(0).getMaxCoverUrl()
        : episodes.get(0).getDefaultCoverUrl()
        : null;
    if (StringUtils.hasText(episodeCover)) {
      // 优先使用首个符合过滤条件节目的封面，避免 playlist 默认缩略图黑边
      fetchedPlaylist.setCoverUrl(episodeCover);
    }

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

    playlistEpisodeMapper.delete(
        new LambdaQueryWrapper<PlaylistEpisode>().eq(PlaylistEpisode::getPlaylistId, playlistId));
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
    if (isBilibiliPlaylist(playlist)) {
      return refreshFeed(playlist);
    }
    return syncPlaylistWithSnapshot(playlist, "MANUAL_FULL");
  }

  @Transactional
  public void refreshPlaylist(Playlist playlist) {
    if (isBilibiliPlaylist(playlist)) {
      refreshFeed(playlist);
      return;
    }
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
      List<PlaylistSnapshotEntry> mappingRefreshEntries = new ArrayList<>();

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
          mappingRefreshEntries.add(remoteEntry);
          continue;
        }
        if (needsSourceChannelRefresh(localMapping, remoteEntry)) {
          mappingRefreshEntries.add(remoteEntry);
        }
      }

      if (!removedIds.isEmpty()) {
        playlistEpisodeMapper.delete(new LambdaQueryWrapper<PlaylistEpisode>()
            .eq(PlaylistEpisode::getPlaylistId, playlist.getId())
            .in(PlaylistEpisode::getEpisodeId, removedIds));
        playlistEpisodeDetailRetryMapper.delete(new LambdaQueryWrapper<PlaylistEpisodeDetailRetry>()
            .eq(PlaylistEpisodeDetailRetry::getPlaylistId, playlist.getId())
            .in(PlaylistEpisodeDetailRetry::getEpisodeId, removedIds));
        removeOrphanEpisodesByIds(removedIds);
      }

      for (PlaylistSnapshotEntry entryToRefresh : mappingRefreshEntries) {
        PlaylistEpisode localMapping = localMappingMap.get(entryToRefresh.videoId());
        LocalDateTime publishedAt = localMapping != null && localMapping.getPublishedAt() != null
            ? localMapping.getPublishedAt()
            : entryToRefresh.approximatePublishedAt();
        upsertPlaylistEpisodeMapping(playlist.getId(), entryToRefresh.videoId(),
            entryToRefresh.position(), publishedAt,
            resolveSourceChannelId(entryToRefresh, localMapping),
            resolveSourceChannelName(entryToRefresh, localMapping),
            resolveSourceChannelUrl(entryToRefresh, localMapping));
      }

      boolean limitAutoDownloadToInitialSelection = "INIT".equals(mode);
      AddedBackfillResult backfillResult = processAddedEntries(
          playlist,
          addedIds,
          remoteEntryMap,
          limitAutoDownloadToInitialSelection);
      if (!backfillResult.autoDownloadCandidates().isEmpty()) {
        markAndPublishAutoDownloadEpisodes(
            playlist,
            backfillResult.autoDownloadCandidates(),
            buildEpisodesCreatedContext("playlist_sync_" + mode.toLowerCase(), playlist));
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
      List<Episode> autoDownloadCandidates = new ArrayList<>();

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
              retry.getApproximatePublishedAt(),
              null,
              null,
              null
          );
          Optional<Episode> maybeEpisode = buildEpisodeFromVideo(playlist, video, snapshotEntry);
          if (maybeEpisode.isEmpty()) {
            playlistEpisodeDetailRetryMapper.deleteById(retry.getId());
            continue;
          }

          Episode episode = maybeEpisode.get();
          episodeService().saveEpisodes(List.of(episode));
          upsertPlaylistEpisodeMapping(playlistId, episode.getId(), snapshotEntry.position(),
              episode.getPublishedAt(), episode.getSourceChannelId(),
              episode.getSourceChannelName(), episode.getSourceChannelUrl());
          playlistEpisodeDetailRetryMapper.deleteById(retry.getId());
          recoveredInGroup++;

          if (!existingIds.contains(episode.getId())
              && FeedEpisodeVisibilityHelper.matchesFeedFilter(playlist, episode)) {
            autoDownloadCandidates.add(episode);
          }
        }
      }

      List<Episode> sortedAutoDownloadCandidates = sortAutoDownloadCandidates(autoDownloadCandidates);
      if (!sortedAutoDownloadCandidates.isEmpty()) {
        markAndPublishAutoDownloadEpisodes(
            playlist,
            sortedAutoDownloadCandidates,
            buildEpisodesCreatedContext("playlist_detail_retry", playlist));
      }
      recovered += recoveredInGroup;
    }
    return recovered;
  }

  private AddedBackfillResult processAddedEntries(Playlist playlist, List<String> addedIds,
      Map<String, PlaylistSnapshotEntry> remoteEntryMap, boolean limitAutoDownloadCandidates) {
    if (addedIds.isEmpty()) {
      return new AddedBackfillResult(0, 0, 0, List.of());
    }

    String playlistId = playlist.getId();
    int mappedAddedCount = 0;
    int queuedRetryCount = 0;
    int newEpisodeCount = 0;
    List<Episode> autoDownloadCandidates = new ArrayList<>();

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
        upsertPlaylistEpisodeMapping(playlistId, episodeId, snapshotEntry.position(),
            existing.getPublishedAt() != null ? existing.getPublishedAt()
                : snapshotEntry.approximatePublishedAt(),
            snapshotEntry.sourceChannelId(), snapshotEntry.sourceChannelName(),
            snapshotEntry.sourceChannelUrl());
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
        autoDownloadCandidates.addAll(
            FeedEpisodeVisibilityHelper.filterVisibleEpisodes(playlist, batchEpisodes));
      }
    }

    return new AddedBackfillResult(
        mappedAddedCount,
        queuedRetryCount,
        newEpisodeCount,
        finalizeAutoDownloadCandidates(playlist, autoDownloadCandidates, limitAutoDownloadCandidates));
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
        .durationSeconds(top.asimov.pigeon.util.EpisodeDurationHelper.parseDurationSeconds(duration))
        .position(snapshotEntry.position())
        .downloadStatus(EpisodeStatus.READY.name())
        .createdAt(LocalDateTime.now());
    String sourceChannelId = StringUtils.hasText(snapshotEntry.sourceChannelId())
        ? snapshotEntry.sourceChannelId() : video.getSnippet().getChannelId();
    String sourceChannelName = StringUtils.hasText(snapshotEntry.sourceChannelName())
        ? snapshotEntry.sourceChannelName() : video.getSnippet().getChannelTitle();
    String sourceChannelUrl = snapshotEntry.sourceChannelUrl();
    if (!StringUtils.hasText(sourceChannelUrl) && StringUtils.hasText(sourceChannelId)) {
      sourceChannelUrl = "https://www.youtube.com/channel/" + sourceChannelId;
    }
    builder.sourceChannelId(sourceChannelId)
        .sourceChannelName(sourceChannelName)
        .sourceChannelUrl(sourceChannelUrl);
    youtubeVideoHelper.applyThumbnails(builder, video.getSnippet().getThumbnails());
    return Optional.of(builder.build());
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
      LocalDateTime publishedAt, String sourceChannelId, String sourceChannelName,
      String sourceChannelUrl) {
    int count = playlistEpisodeMapper.countByPlaylistAndEpisode(playlistId, episodeId);
    if (count > 0) {
      playlistEpisodeMapper.updateMapping(playlistId, episodeId, position, publishedAt,
          sourceChannelId, sourceChannelName, sourceChannelUrl);
      return;
    }
    playlistEpisodeMapper.insertMapping(playlistId, episodeId, position, publishedAt,
        sourceChannelId, sourceChannelName, sourceChannelUrl);
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

  private List<Episode> finalizeAutoDownloadCandidates(Playlist playlist, List<Episode> candidates,
      boolean limitToConfiguredCount) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    if (!Boolean.TRUE.equals(playlist.getAutoDownloadEnabled())) {
      return List.of();
    }
    if (!limitToConfiguredCount) {
      return sortAutoDownloadCandidates(candidates);
    }
    TopEpisodeCollector collector = new TopEpisodeCollector(resolveDownloadLimit(playlist));
    collector.offerAll(candidates);
    return collector.toSortedList();
  }

  private List<Episode> sortAutoDownloadCandidates(List<Episode> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    List<Episode> sorted = new ArrayList<>(candidates);
    sorted.sort(AUTO_DOWNLOAD_NEWEST_FIRST);
    return sorted;
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
   * 拉取播放列表历史节目信息：统一按持久化 cursor 顺序推进，不再用本地数量推导远端分页。
   *
   * <p>playlist 不再套用 channel 的锚点式 bootstrap。对于没有 cursor 的旧订阅，首次 history
   * 请求会从远端头部开始按独立 cursor 模型推进，通过去重补齐缺失元数据。</p>
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
    if (playlistEpisodeMapper.countByPlaylistId(playlistId) == 0) {
      log.warn("播放列表 {} 尚未初始化节目，跳过历史节目信息抓取", playlistId);
      return Collections.emptyList();
    }
    if (Boolean.TRUE.equals(playlist.getHistoryCursorExhausted())) {
      log.info("播放列表 {} 历史 cursor 已耗尽，无需继续拉取", playlistId);
      return Collections.emptyList();
    }
    return fetchPlaylistHistoryByCursor(playlist);
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

    FeedRefreshResult result = isBilibiliPlaylist(playlist)
        ? refreshFeed(playlist)
        : syncPlaylistWithSnapshot(playlist, "INIT");
    log.info("播放列表 {} 初始化同步完成: {}", playlistId, result);
  }

  private void upsertPlaylistEpisodes(String playlistId, List<Episode> episodes) {
    for (Episode episode : episodes) {
      int count = playlistEpisodeMapper.countByPlaylistAndEpisode(playlistId, episode.getId());
      int affected;
      if (count > 0) {
        affected = playlistEpisodeMapper.updateMapping(playlistId, episode.getId(), episode.getPosition(),
            episode.getPublishedAt(), episode.getSourceChannelId(), episode.getSourceChannelName(),
            episode.getSourceChannelUrl());
      } else {
        affected = playlistEpisodeMapper.insertMapping(playlistId, episode.getId(), episode.getPosition(),
            episode.getPublishedAt(), episode.getSourceChannelId(), episode.getSourceChannelName(),
            episode.getSourceChannelUrl());
      }
      if (affected <= 0) {
        log.warn("更新播放列表 {} 与节目 {} 的关联失败", playlistId, episode.getId());
      }
    }
  }

  private boolean needsSourceChannelRefresh(PlaylistEpisode localMapping, PlaylistSnapshotEntry remoteEntry) {
    if (localMapping == null || remoteEntry == null) {
      return false;
    }
    if (StringUtils.hasText(remoteEntry.sourceChannelId()) && !Objects.equals(
        remoteEntry.sourceChannelId(), localMapping.getSourceChannelId())) {
      return true;
    }
    if (StringUtils.hasText(remoteEntry.sourceChannelName()) && !Objects.equals(
        remoteEntry.sourceChannelName(), localMapping.getSourceChannelName())) {
      return true;
    }
    if (StringUtils.hasText(remoteEntry.sourceChannelUrl()) && !Objects.equals(
        remoteEntry.sourceChannelUrl(), localMapping.getSourceChannelUrl())) {
      return true;
    }
    return false;
  }

  private String resolveSourceChannelId(PlaylistSnapshotEntry remoteEntry, PlaylistEpisode localMapping) {
    if (remoteEntry != null && StringUtils.hasText(remoteEntry.sourceChannelId())) {
      return remoteEntry.sourceChannelId();
    }
    return localMapping == null ? null : localMapping.getSourceChannelId();
  }

  private String resolveSourceChannelName(PlaylistSnapshotEntry remoteEntry, PlaylistEpisode localMapping) {
    if (remoteEntry != null && StringUtils.hasText(remoteEntry.sourceChannelName())) {
      return remoteEntry.sourceChannelName();
    }
    return localMapping == null ? null : localMapping.getSourceChannelName();
  }

  private String resolveSourceChannelUrl(PlaylistSnapshotEntry remoteEntry, PlaylistEpisode localMapping) {
    if (remoteEntry != null && StringUtils.hasText(remoteEntry.sourceChannelUrl())) {
      return remoteEntry.sourceChannelUrl();
    }
    return localMapping == null ? null : localMapping.getSourceChannelUrl();
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
    List<Episode> episodes;
    if (isBilibiliPlaylist(feed)) {
      episodes = bilibiliPlaylistHelper.fetchPlaylistVideos(
          feed.getId(),
          feed.getOwnerId(),
          pages,
          feed.getTitleContainKeywords(),
          feed.getTitleExcludeKeywords(),
          feed.getDescriptionContainKeywords(),
          feed.getDescriptionExcludeKeywords(),
          feed.getMinimumDuration(),
          feed.getMaximumDuration());
    } else {
      episodes = youtubePlaylistHelper.fetchPlaylistVideos(
          feed.getId(), pages, null,
          feed.getTitleContainKeywords(), feed.getTitleExcludeKeywords(),
          feed.getDescriptionContainKeywords(), feed.getDescriptionExcludeKeywords(),
          feed.getMinimumDuration(),
          feed.getMaximumDuration());
    }
    episodes = FeedEpisodeVisibilityHelper.filterVisibleEpisodes(feed, episodes);
    if (episodes.size() > AbstractFeedService.DEFAULT_PREVIEW_NUM) {
      return episodes.subList(0, AbstractFeedService.DEFAULT_PREVIEW_NUM);
    }
    return episodes;
  }

  @Override
  protected List<Episode> fetchIncrementalEpisodes(Playlist feed) {
    List<Episode> episodes;
    if (isBilibiliPlaylist(feed)) {
      episodes = bilibiliPlaylistHelper.fetchPlaylistVideos(
          feed.getId(),
          feed.getOwnerId(),
          1,
          feed.getTitleContainKeywords(),
          feed.getTitleExcludeKeywords(),
          feed.getDescriptionContainKeywords(),
          feed.getDescriptionExcludeKeywords(),
          feed.getMinimumDuration(),
          feed.getMaximumDuration());
    } else {
      // 为了应对播放列表顺序调整、插入旧视频等情况，每次刷新时对整个播放列表做全量扫描，
      // 然后根据 Episode ID 与数据库中的现有记录做差值，确定真正新增的节目。
      episodes = youtubePlaylistHelper.fetchPlaylistVideos(
          feed.getId(),
          Integer.MAX_VALUE,
          null,
          feed.getTitleContainKeywords(),
          feed.getTitleExcludeKeywords(),
          feed.getDescriptionContainKeywords(),
          feed.getDescriptionExcludeKeywords(),
          feed.getMinimumDuration(),
          feed.getMaximumDuration());
    }

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

  private List<Episode> fetchPlaylistHistoryByCursor(Playlist playlist) {
    if (isBilibiliPlaylist(playlist)) {
      return fetchBilibiliPlaylistHistoryByCursor(playlist);
    }
    return fetchYoutubePlaylistHistoryByCursor(playlist);
  }

  private List<Episode> fetchYoutubePlaylistHistoryByCursor(Playlist playlist) {
    YoutubePlaylistHelper.PageHistoryResult page =
        youtubePlaylistHelper.fetchPlaylistHistoryPage(playlist.getId(), playlist.getHistoryCursorValue());
    return persistPlaylistHistoryPage(playlist, page.episodes(), page.nextPageToken(), page.exhausted(),
        CURSOR_TYPE_YOUTUBE_PAGE_TOKEN, nextHistoryPageNumber(playlist) + 1);
  }

  private List<Episode> fetchBilibiliPlaylistHistoryByCursor(Playlist playlist) {
    int pageNumber = nextHistoryPageNumber(playlist);
    List<Episode> episodes = bilibiliPlaylistHelper.fetchPlaylistHistoryPage(
        playlist.getId(), playlist.getOwnerId(), pageNumber, null, null, null, null, null, null);
    boolean exhausted = episodes.isEmpty();
    return persistPlaylistHistoryPage(playlist, episodes, null, exhausted, CURSOR_TYPE_BILIBILI_PAGE_NUM,
        pageNumber + 1);
  }

  private List<Episode> persistPlaylistHistoryPage(Playlist playlist, List<Episode> episodes, String nextCursorValue,
      boolean exhausted, String cursorType, int nextPageNumber) {
    if (episodes == null || episodes.isEmpty()) {
      if (exhausted) {
        markHistoryExhausted(playlist);
      }
      return List.of();
    }
    List<Episode> newEpisodes = filterNewEpisodes(episodes);
    List<Episode> episodesToPersist = prepareEpisodesForPersistence(newEpisodes);
    if (!episodesToPersist.isEmpty()) {
      episodeService().saveEpisodes(episodesToPersist);
      upsertPlaylistEpisodes(playlist.getId(), episodesToPersist);
    }
    updateHistoryCursor(playlist, cursorType, nextCursorValue, nextPageNumber, exhausted);
    return FeedEpisodeVisibilityHelper.filterVisibleEpisodes(playlist, episodesToPersist);
  }

  private int nextHistoryPageNumber(Playlist playlist) {
    return playlist != null && playlist.getHistoryCursorPage() != null ? playlist.getHistoryCursorPage() : 1;
  }

  private void updateHistoryCursor(Playlist playlist, String type, String value, int nextPageNumber,
      boolean exhausted) {
    playlist.setHistoryCursorType(type);
    playlist.setHistoryCursorValue(value);
    playlist.setHistoryCursorPage(nextPageNumber);
    playlist.setHistoryCursorExhausted(exhausted);
    playlist.setHistoryCursorUpdatedAt(LocalDateTime.now());
    playlistMapper.updateById(playlist);
  }

  private void markHistoryExhausted(Playlist playlist) {
    updateHistoryCursor(playlist, playlist.getHistoryCursorType(), null, playlist.getHistoryCursorPage(), true);
  }

  @Override
  protected org.apache.logging.log4j.Logger logger() {
    return log;
  }

  private boolean isBilibiliPlaylist(Playlist playlist) {
    return playlist != null && FeedSource.BILIBILI.name().equalsIgnoreCase(playlist.getSource());
  }
}
