package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.Video;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.mapper.YoutubePlaylistItemMapper;
import top.asimov.pigeon.model.dto.YoutubePlaylistRemoteItem;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.entity.PlaylistEpisode;
import top.asimov.pigeon.model.entity.YoutubePlaylistItem;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.enums.FeedSource;
import top.asimov.pigeon.model.enums.YoutubePlaylistAutoDispatchStatus;
import top.asimov.pigeon.model.enums.YoutubePlaylistMaterializationStatus;
import top.asimov.pigeon.model.enums.YoutubePlaylistPresenceStatus;
import top.asimov.pigeon.model.response.FeedConfigUpdateResult;
import top.asimov.pigeon.model.response.FeedPack;
import top.asimov.pigeon.model.response.FeedRefreshResult;
import top.asimov.pigeon.model.response.FeedSaveResult;
import top.asimov.pigeon.util.FeedEpisodeVisibilityHelper;
import top.asimov.pigeon.util.FeedSourceUrlBuilder;
import top.asimov.pigeon.util.IndividualVideoPlaylistSupport;

@Log4j2
@Service
public class PlaylistService extends AbstractFeedService<Playlist> {

  private static final int VIDEO_DETAILS_BATCH_SIZE = 50;
  private static final int EPISODE_LOOKUP_BATCH_SIZE = 500;
  private static final int EPISODE_SAVE_BATCH_SIZE = 200;
  private static final int DEFAULT_SYNC_INTERVAL_HOURS = 3;
  private static final String CURSOR_TYPE_YOUTUBE_PAGE_TOKEN = "YOUTUBE_PAGE_TOKEN";
  private static final String CURSOR_TYPE_BILIBILI_PAGE_NUM = "BILIBILI_PAGE_NUM";
  private static final Comparator<Episode> AUTO_DOWNLOAD_PLAYLIST_ORDER =
      Comparator.comparing(Episode::getPosition, Comparator.nullsLast(Long::compareTo))
          .thenComparing(Episode::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Episode::getId, Comparator.nullsLast(String::compareTo));

  private final PlaylistMapper playlistMapper;
  private final PlaylistEpisodeMapper playlistEpisodeMapper;
  private final YoutubePlaylistItemMapper youtubePlaylistItemMapper;
  private final YoutubeHelper youtubeHelper;
  private final YoutubePlaylistHelper youtubePlaylistHelper;
  private final YoutubeVideoHelper youtubeVideoHelper;
  private final BilibiliResolverHelper bilibiliResolverHelper;
  private final BilibiliPlaylistHelper bilibiliPlaylistHelper;
  private final AccountService accountService;
  private final MessageSource messageSource;
  private final Executor channelSyncTaskExecutor;
  private final AppBaseUrlResolver appBaseUrlResolver;

  public PlaylistService(PlaylistMapper playlistMapper,
      PlaylistEpisodeMapper playlistEpisodeMapper,
      YoutubePlaylistItemMapper youtubePlaylistItemMapper,
      EpisodeService episodeService, ApplicationEventPublisher eventPublisher,
      YoutubeHelper youtubeHelper, YoutubePlaylistHelper youtubePlaylistHelper,
      YoutubeVideoHelper youtubeVideoHelper,
      BilibiliResolverHelper bilibiliResolverHelper,
      BilibiliPlaylistHelper bilibiliPlaylistHelper,
      AccountService accountService, MessageSource messageSource,
      FeedDefaultsService feedDefaultsService,
      @Qualifier("channelSyncTaskExecutor") Executor channelSyncTaskExecutor,
      AppBaseUrlResolver appBaseUrlResolver) {
    super(episodeService, eventPublisher, messageSource, feedDefaultsService);
    this.playlistMapper = playlistMapper;
    this.playlistEpisodeMapper = playlistEpisodeMapper;
    this.youtubePlaylistItemMapper = youtubePlaylistItemMapper;
    this.youtubeHelper = youtubeHelper;
    this.youtubePlaylistHelper = youtubePlaylistHelper;
    this.youtubeVideoHelper = youtubeVideoHelper;
    this.bilibiliResolverHelper = bilibiliResolverHelper;
    this.bilibiliPlaylistHelper = bilibiliPlaylistHelper;
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
    if (IndividualVideoPlaylistSupport.isSingleVideoPlaylist(playlist)) {
      playlist.setOriginalUrl(null);
    } else {
      playlist.setOriginalUrl(
          FeedSourceUrlBuilder.buildPlaylistUrl(playlist.getSource(), playlist.getId(), playlist.getOwnerId()));
    }
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

  @Override
  protected void applyAdditionalMutableFields(Playlist existingFeed, Playlist configuration) {
    if (configuration == null) {
      return;
    }
    Integer syncIntervalHours = configuration.getSyncIntervalHours();
    existingFeed.setSyncIntervalHours(
        syncIntervalHours == null || syncIntervalHours <= 0
            ? DEFAULT_SYNC_INTERVAL_HOURS
            : syncIntervalHours);
  }

  @Transactional
  public FeedConfigUpdateResult updatePlaylistConfig(String playlistId, Playlist configuration) {
    FeedConfigUpdateResult result = updateFeedConfig(playlistId, configuration);
    youtubePlaylistItemMapper.resetSkippedMaterialization(playlistId, LocalDateTime.now());
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

    if (youtubeHelper.isYoutubeVideoInput(playlistUrl) && !looksLikeYoutubePlaylistInput(playlistUrl)) {
      return fetchSingleVideoPlaylist(playlistUrl);
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
          .syncIntervalHours(DEFAULT_SYNC_INTERVAL_HOURS)
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
        .syncIntervalHours(DEFAULT_SYNC_INTERVAL_HOURS)
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
    if (IndividualVideoPlaylistSupport.isSingleVideoPlaylist(playlist)) {
      return saveSingleVideoPlaylist(playlist);
    }
    feedDefaultsService().applyDefaultsIfMissing(playlist);
    normalizeSyncInterval(playlist);
    return saveFeed(playlist);
  }

  public List<Playlist> findDueForSync(LocalDateTime checkTime) {
    List<Playlist> playlists = playlistMapper.selectList(new LambdaQueryWrapper<>());
    return playlists.stream()
        .filter(playlist -> !IndividualVideoPlaylistSupport.isSingleVideoPlaylist(playlist))
        .filter(p -> isDueForSync(p, checkTime))
        .collect(Collectors.toList());
  }

  private boolean isDueForSync(Playlist playlist, LocalDateTime checkTime) {
    if (playlist == null) {
      return false;
    }
    LocalDateTime lastSyncTimestamp = playlist.getLastSyncTimestamp();
    if (lastSyncTimestamp == null) {
      return true;
    }
    int intervalHours = resolveSyncIntervalHours(playlist);
    return !lastSyncTimestamp.plusHours(intervalHours).isAfter(checkTime);
  }

  private void normalizeSyncInterval(Playlist playlist) {
    if (playlist == null) {
      return;
    }
    playlist.setSyncIntervalHours(resolveSyncIntervalHours(playlist));
  }

  private int resolveSyncIntervalHours(Playlist playlist) {
    Integer syncIntervalHours = playlist == null ? null : playlist.getSyncIntervalHours();
    if (syncIntervalHours == null || syncIntervalHours <= 0) {
      return DEFAULT_SYNC_INTERVAL_HOURS;
    }
    return syncIntervalHours;
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
    youtubePlaylistItemMapper.deleteByPlaylistId(playlistId);

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
    if (IndividualVideoPlaylistSupport.isSingleVideoPlaylist(playlist)) {
      return FeedRefreshResult.builder()
          .hasNewEpisodes(false)
          .newEpisodeCount(0)
          .message(messageSource.getMessage("playlist.single.video.refresh.unsupported", null,
              LocaleContextHolder.getLocale()))
          .build();
    }
    if (isBilibiliPlaylist(playlist)) {
      return refreshFeed(playlist);
    }
    return syncYoutubePlaylistWithOfficialApi(playlist, "MANUAL_FULL");
  }

  @Transactional
  public void refreshPlaylist(Playlist playlist) {
    if (IndividualVideoPlaylistSupport.isSingleVideoPlaylist(playlist)) {
      return;
    }
    if (isBilibiliPlaylist(playlist)) {
      refreshFeed(playlist);
      return;
    }
    syncYoutubePlaylistWithOfficialApi(playlist, "INCREMENTAL");
  }

  private FeedRefreshResult syncYoutubePlaylistWithOfficialApi(Playlist playlist, String mode) {
    log.info("开始以官方 API 同步播放列表: {} ({}) mode={}", playlist.getTitle(), playlist.getId(), mode);

    LocalDateTime now = LocalDateTime.now();
    try {
      List<PlaylistItem> remoteItems = youtubePlaylistHelper.fetchAllPlaylistItemsOfficial(playlist.getId());
      OfficialItemDiffResult diffResult = applyOfficialItemDiff(playlist, remoteItems, now);
      int materializedCount = materializeOfficialPlaylistItems(playlist, now);
      DerivePlaylistEpisodeResult deriveResult = derivePlaylistEpisodesFromOfficialItems(playlist);
      int dispatchedCount = 0;
      boolean bootstrap = playlist.getBootstrapCompletedAt() == null;
      if (!bootstrap) {
        dispatchedCount = dispatchOfficialPlaylistAutoDownloads(playlist, now);
      }

      if (bootstrap) {
        playlist.setBootstrapCompletedAt(now);
      }
      playlist.setLastFullScanAt(now);
      playlist.setLastFullScanSize(remoteItems.size());
      playlist.setLastFullScanPages((int) Math.ceil(remoteItems.size() / 50.0));
      playlist.setLastSyncInsertedItemCount(diffResult.insertedCount());
      playlist.setLastSyncRemovedItemCount(diffResult.removedCount());
      playlist.setLastSyncMovedItemCount(diffResult.movedCount());
      playlist.setLastSyncMaterializedCount(materializedCount);
      playlist.setLastSyncDispatchedItemCount(dispatchedCount);
      playlist.setLastSyncTimestamp(now);
      playlist.setSyncError(null);
      playlist.setSyncErrorAt(null);
      updateCoverFromDerivedEpisodes(playlist);
      playlistMapper.updateById(playlist);

      log.info(
          "播放列表 {} 官方 API 同步完成，items={}, inserted={}, removed={}, moved={}, materialized={}, derived={}, dispatched={}, bootstrap={}",
          playlist.getId(), remoteItems.size(), diffResult.insertedCount(), diffResult.removedCount(),
          diffResult.movedCount(), materializedCount, deriveResult.derivedCount(), dispatchedCount, bootstrap);

      int newEpisodeCount = bootstrap ? 0 : materializedCount;
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
      log.error("播放列表 {} 官方 API 同步失败(mode={}): {}", playlist.getId(), mode, e.getMessage(), e);
      return FeedRefreshResult.builder()
          .hasNewEpisodes(false)
          .newEpisodeCount(0)
          .message("playlist sync failed: " + error)
          .build();
    }
  }

  private OfficialItemDiffResult applyOfficialItemDiff(Playlist playlist, List<PlaylistItem> remoteItems,
      LocalDateTime now) {
    List<YoutubePlaylistRemoteItem> remoteEntries = parseOfficialRemoteItems(remoteItems);
    Map<String, YoutubePlaylistRemoteItem> remoteByItemId = new LinkedHashMap<>();
    for (YoutubePlaylistRemoteItem remoteEntry : remoteEntries) {
      remoteByItemId.putIfAbsent(remoteEntry.playlistItemId(), remoteEntry);
    }

    List<YoutubePlaylistItem> localItems = youtubePlaylistItemMapper.selectByPlaylistId(playlist.getId());
    Map<String, YoutubePlaylistItem> localByItemId = new HashMap<>();
    for (YoutubePlaylistItem localItem : localItems) {
      localByItemId.put(localItem.getPlaylistItemId(), localItem);
    }

    boolean bootstrap = playlist.getBootstrapCompletedAt() == null;
    int insertedCount = 0;
    int movedCount = 0;
    for (YoutubePlaylistRemoteItem remoteEntry : remoteEntries) {
      YoutubePlaylistItem localItem = localByItemId.get(remoteEntry.playlistItemId());
      if (localItem == null) {
        YoutubePlaylistItem item = YoutubePlaylistItem.builder()
            .playlistId(playlist.getId())
            .playlistItemId(remoteEntry.playlistItemId())
            .videoId(remoteEntry.videoId())
            .itemAddedAt(remoteEntry.itemAddedAt())
            .videoPublishedAt(remoteEntry.videoPublishedAt())
            .position(remoteEntry.position())
            .itemPrivacyStatus(remoteEntry.itemPrivacyStatus())
            .sourceChannelId(remoteEntry.sourceChannelId())
            .sourceChannelName(remoteEntry.sourceChannelName())
            .sourceChannelUrl(remoteEntry.sourceChannelUrl())
            .presenceStatus(YoutubePlaylistPresenceStatus.ACTIVE.name())
            .materializationStatus(YoutubePlaylistMaterializationStatus.PENDING.name())
            .autoDispatchStatus(bootstrap
                ? YoutubePlaylistAutoDispatchStatus.SUPPRESSED_BOOTSTRAP.name()
                : YoutubePlaylistAutoDispatchStatus.PENDING.name())
            .firstSeenAt(now)
            .lastSeenAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
        youtubePlaylistItemMapper.insertItem(item);
        insertedCount++;
        continue;
      }
      if (!isSamePosition(localItem.getPosition(), remoteEntry.position())) {
        movedCount++;
      }
      boolean videoChanged = !Objects.equals(localItem.getVideoId(), remoteEntry.videoId());
      localItem.setVideoId(remoteEntry.videoId());
      localItem.setItemAddedAt(remoteEntry.itemAddedAt());
      localItem.setVideoPublishedAt(remoteEntry.videoPublishedAt());
      localItem.setPosition(remoteEntry.position());
      localItem.setItemPrivacyStatus(remoteEntry.itemPrivacyStatus());
      localItem.setSourceChannelId(remoteEntry.sourceChannelId());
      localItem.setSourceChannelName(remoteEntry.sourceChannelName());
      localItem.setSourceChannelUrl(remoteEntry.sourceChannelUrl());
      localItem.setLastSeenAt(now);
      localItem.setUpdatedAt(now);
      youtubePlaylistItemMapper.markActive(localItem);
      if (videoChanged) {
        youtubePlaylistItemMapper.updateMaterialization(
            localItem.getId(),
            null,
            YoutubePlaylistMaterializationStatus.PENDING.name(),
            null,
            now);
        youtubePlaylistItemMapper.updateAutoDispatchStatus(
            localItem.getId(),
            bootstrap
                ? YoutubePlaylistAutoDispatchStatus.SUPPRESSED_BOOTSTRAP.name()
                : YoutubePlaylistAutoDispatchStatus.PENDING.name(),
            now);
      }
    }

    int removedCount = 0;
    for (YoutubePlaylistItem localItem : localItems) {
      if (!YoutubePlaylistPresenceStatus.ACTIVE.name().equals(localItem.getPresenceStatus())) {
        continue;
      }
      if (remoteByItemId.containsKey(localItem.getPlaylistItemId())) {
        continue;
      }
      youtubePlaylistItemMapper.markRemoved(localItem.getId(), now, now);
      removedCount++;
    }
    return new OfficialItemDiffResult(insertedCount, removedCount, movedCount);
  }

  private List<YoutubePlaylistRemoteItem> parseOfficialRemoteItems(List<PlaylistItem> remoteItems) {
    if (remoteItems == null || remoteItems.isEmpty()) {
      return List.of();
    }
    List<YoutubePlaylistRemoteItem> result = new ArrayList<>();
    for (PlaylistItem item : remoteItems) {
      if (item == null || !StringUtils.hasText(item.getId())) {
        continue;
      }
      String videoId = item.getContentDetails() == null ? null : item.getContentDetails().getVideoId();
      if (!StringUtils.hasText(videoId) && item.getSnippet() != null
          && item.getSnippet().getResourceId() != null) {
        videoId = item.getSnippet().getResourceId().getVideoId();
      }
      if (!StringUtils.hasText(videoId)) {
        log.warn("跳过缺少 videoId 的 playlist item: {}", item.getId());
        continue;
      }
      Long position = item.getSnippet() == null || item.getSnippet().getPosition() == null
          ? null
          : item.getSnippet().getPosition().longValue();
      LocalDateTime itemAddedAt = item.getSnippet() == null
          ? null
          : toLocalDateTime(item.getSnippet().getPublishedAt());
      LocalDateTime videoPublishedAt = item.getContentDetails() == null
          ? null
          : toLocalDateTime(item.getContentDetails().getVideoPublishedAt());
      String privacyStatus = item.getStatus() == null ? null : item.getStatus().getPrivacyStatus();
      String sourceChannelId = item.getSnippet() == null ? null
          : normalizeGenericString(item.getSnippet().get("videoOwnerChannelId"));
      String sourceChannelName = item.getSnippet() == null ? null
          : normalizeGenericString(item.getSnippet().get("videoOwnerChannelTitle"));
      String sourceChannelUrl = StringUtils.hasText(sourceChannelId)
          ? "https://www.youtube.com/channel/" + sourceChannelId
          : null;
      result.add(new YoutubePlaylistRemoteItem(item.getId(), videoId, itemAddedAt, videoPublishedAt,
          position, privacyStatus, sourceChannelId, sourceChannelName, sourceChannelUrl));
    }
    result.sort(Comparator.comparing(YoutubePlaylistRemoteItem::position, Comparator.nullsLast(Long::compareTo))
        .thenComparing(YoutubePlaylistRemoteItem::playlistItemId));
    return result;
  }

  private LocalDateTime toLocalDateTime(DateTime dateTime) {
    if (dateTime == null) {
      return null;
    }
    return LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(dateTime.getValue()),
        java.time.ZoneId.systemDefault());
  }

  private String normalizeGenericString(Object value) {
    if (value == null) {
      return null;
    }
    String normalized = value.toString();
    return StringUtils.hasText(normalized) ? normalized : null;
  }

  private int materializeOfficialPlaylistItems(Playlist playlist, LocalDateTime now) {
    List<YoutubePlaylistItem> pendingItems =
        youtubePlaylistItemMapper.selectPendingMaterialization(playlist.getId());
    if (pendingItems.isEmpty()) {
      return 0;
    }

    Map<String, YoutubePlaylistItem> itemByVideoId = new LinkedHashMap<>();
    for (YoutubePlaylistItem item : pendingItems) {
      itemByVideoId.putIfAbsent(item.getVideoId(), item);
    }
    List<String> videoIds = new ArrayList<>(itemByVideoId.keySet());
    Map<String, Episode> existingEpisodeMap = loadEpisodesByIdsInBatches(videoIds);
    int linkedCount = 0;

    for (YoutubePlaylistItem item : pendingItems) {
      Episode existingEpisode = existingEpisodeMap.get(item.getVideoId());
      if (existingEpisode == null) {
        continue;
      }
      if (!FeedEpisodeVisibilityHelper.matchesFeedFilter(playlist, existingEpisode)) {
        youtubePlaylistItemMapper.updateMaterialization(
            item.getId(),
            null,
            YoutubePlaylistMaterializationStatus.SKIPPED.name(),
            "filtered by playlist configuration",
            now);
        continue;
      }
      youtubePlaylistItemMapper.updateMaterialization(
          item.getId(),
          existingEpisode.getId(),
          YoutubePlaylistMaterializationStatus.LINKED.name(),
          null,
          now);
      linkedCount++;
    }

    List<String> missingVideoIds = videoIds.stream()
        .filter(videoId -> !existingEpisodeMap.containsKey(videoId))
        .toList();
    if (missingVideoIds.isEmpty()) {
      return linkedCount;
    }

    String apiKey;
    try {
      apiKey = YoutubeApiKeyHolder.requireYoutubeApiKey(messageSource);
    } catch (Exception ex) {
      markMaterializationFailed(pendingItems, missingVideoIds, ex.getMessage(), now);
      return linkedCount;
    }

    for (int start = 0; start < missingVideoIds.size(); start += VIDEO_DETAILS_BATCH_SIZE) {
      int end = Math.min(start + VIDEO_DETAILS_BATCH_SIZE, missingVideoIds.size());
      List<String> batchIds = missingVideoIds.subList(start, end);
      Map<String, Video> details;
      try {
        details = youtubeVideoHelper.fetchVideoDetailsInBulk(batchIds, apiKey);
      } catch (Exception ex) {
        markMaterializationFailed(pendingItems, batchIds, ex.getMessage(), now);
        continue;
      }

      List<Episode> episodesToSave = new ArrayList<>();
      Map<String, Episode> builtByVideoId = new HashMap<>();
      for (String videoId : batchIds) {
        YoutubePlaylistItem item = itemByVideoId.get(videoId);
        Video video = details.get(videoId);
        if (item == null) {
          continue;
        }
        if (video == null) {
          youtubePlaylistItemMapper.updateMaterialization(
              item.getId(),
              null,
              YoutubePlaylistMaterializationStatus.FAILED.name(),
              "missing video detail from YouTube API",
              now);
          continue;
        }
        Optional<Episode> maybeEpisode = buildEpisodeFromOfficialPlaylistItem(playlist, item, video);
        if (maybeEpisode.isEmpty()) {
          youtubePlaylistItemMapper.updateMaterialization(
              item.getId(),
              null,
              YoutubePlaylistMaterializationStatus.SKIPPED.name(),
              "video is not syncable or filtered by playlist configuration",
              now);
          continue;
        }
        Episode episode = maybeEpisode.get();
        episodesToSave.add(episode);
        builtByVideoId.put(videoId, episode);
      }

      if (!episodesToSave.isEmpty()) {
        saveEpisodesInBatches(episodesToSave, EPISODE_SAVE_BATCH_SIZE);
      }
      for (String videoId : batchIds) {
        Episode episode = builtByVideoId.get(videoId);
        YoutubePlaylistItem item = itemByVideoId.get(videoId);
        if (episode == null || item == null) {
          continue;
        }
        youtubePlaylistItemMapper.updateMaterialization(
            item.getId(),
            episode.getId(),
            YoutubePlaylistMaterializationStatus.LINKED.name(),
            null,
            now);
        linkedCount++;
      }
    }

    return linkedCount;
  }

  private void markMaterializationFailed(List<YoutubePlaylistItem> items, List<String> videoIds,
      String errorMessage, LocalDateTime now) {
    if (items == null || items.isEmpty() || videoIds == null || videoIds.isEmpty()) {
      return;
    }
    Set<String> failedIds = new HashSet<>(videoIds);
    for (YoutubePlaylistItem item : items) {
      if (!failedIds.contains(item.getVideoId())) {
        continue;
      }
      youtubePlaylistItemMapper.updateMaterialization(
          item.getId(),
          null,
          YoutubePlaylistMaterializationStatus.FAILED.name(),
          abbreviateError(errorMessage),
          now);
    }
  }

  private Optional<Episode> buildEpisodeFromOfficialPlaylistItem(Playlist playlist,
      YoutubePlaylistItem item, Video video) {
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

    LocalDateTime publishedAt = video.getSnippet().getPublishedAt() == null
        ? item.getVideoPublishedAt()
        : toLocalDateTime(video.getSnippet().getPublishedAt());
    if (publishedAt == null) {
      publishedAt = item.getItemAddedAt() == null ? LocalDateTime.now() : item.getItemAddedAt();
    }

    String sourceChannelId = StringUtils.hasText(item.getSourceChannelId())
        ? item.getSourceChannelId()
        : video.getSnippet().getChannelId();
    String sourceChannelName = StringUtils.hasText(item.getSourceChannelName())
        ? item.getSourceChannelName()
        : video.getSnippet().getChannelTitle();
    String sourceChannelUrl = StringUtils.hasText(item.getSourceChannelUrl())
        ? item.getSourceChannelUrl()
        : StringUtils.hasText(sourceChannelId) ? "https://www.youtube.com/channel/" + sourceChannelId : null;

    Episode.EpisodeBuilder builder = Episode.builder()
        .id(video.getId())
        .channelId(null)
        .title(video.getSnippet().getTitle())
        .description(video.getSnippet().getDescription())
        .publishedAt(publishedAt)
        .duration(duration)
        .durationSeconds(top.asimov.pigeon.util.EpisodeDurationHelper.parseDurationSeconds(duration))
        .liveVod(youtubeVideoHelper.isArchivedLiveVodPro(video))
        .position(item.getPosition())
        .sourceChannelId(sourceChannelId)
        .sourceChannelName(sourceChannelName)
        .sourceChannelUrl(sourceChannelUrl)
        .downloadStatus(EpisodeStatus.READY.name())
        .createdAt(LocalDateTime.now());
    youtubeVideoHelper.applyThumbnails(builder, video.getSnippet().getThumbnails());
    Episode episode = builder.build();
    if (!FeedEpisodeVisibilityHelper.matchesFeedFilter(playlist, episode)) {
      return Optional.empty();
    }
    return Optional.of(episode);
  }

  private Map<String, Episode> loadEpisodesByIdsInBatches(List<String> episodeIds) {
    if (episodeIds == null || episodeIds.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Episode> result = new HashMap<>();
    for (int start = 0; start < episodeIds.size(); start += EPISODE_LOOKUP_BATCH_SIZE) {
      int end = Math.min(start + EPISODE_LOOKUP_BATCH_SIZE, episodeIds.size());
      List<Episode> batchEpisodes = episodeService().getEpisodesByIds(episodeIds.subList(start, end));
      for (Episode episode : batchEpisodes) {
        result.putIfAbsent(episode.getId(), episode);
      }
    }
    return result;
  }

  private DerivePlaylistEpisodeResult derivePlaylistEpisodesFromOfficialItems(Playlist playlist) {
    List<YoutubePlaylistItem> linkedItems = youtubePlaylistItemMapper.selectActiveLinked(playlist.getId());
    Map<String, YoutubePlaylistItem> representativeByEpisodeId = new LinkedHashMap<>();
    for (YoutubePlaylistItem item : linkedItems) {
      if (!StringUtils.hasText(item.getEpisodeId())) {
        continue;
      }
      YoutubePlaylistItem current = representativeByEpisodeId.get(item.getEpisodeId());
      if (current == null || compareOfficialItemPosition(item, current) < 0) {
        representativeByEpisodeId.put(item.getEpisodeId(), item);
      }
    }

    Map<String, PlaylistEpisode> localMappingMap = buildLocalMappingMap(playlist.getId());
    List<String> staleEpisodeIds = new ArrayList<>();
    for (String localEpisodeId : localMappingMap.keySet()) {
      if (!representativeByEpisodeId.containsKey(localEpisodeId)) {
        staleEpisodeIds.add(localEpisodeId);
      }
    }

    if (!staleEpisodeIds.isEmpty()) {
      playlistEpisodeMapper.delete(new LambdaQueryWrapper<PlaylistEpisode>()
          .eq(PlaylistEpisode::getPlaylistId, playlist.getId())
          .in(PlaylistEpisode::getEpisodeId, staleEpisodeIds));
      removeOrphanEpisodesByIds(staleEpisodeIds);
    }

    for (YoutubePlaylistItem item : representativeByEpisodeId.values()) {
      LocalDateTime publishedAt = item.getItemAddedAt() == null
          ? item.getVideoPublishedAt()
          : item.getItemAddedAt();
      upsertPlaylistEpisodeMapping(
          playlist.getId(),
          item.getEpisodeId(),
          item.getPosition(),
          publishedAt,
          item.getSourceChannelId(),
          item.getSourceChannelName(),
          item.getSourceChannelUrl());
    }

    return new DerivePlaylistEpisodeResult(representativeByEpisodeId.size());
  }

  private int compareOfficialItemPosition(YoutubePlaylistItem left, YoutubePlaylistItem right) {
    int positionCompare = Comparator.nullsLast(Long::compareTo).compare(left.getPosition(), right.getPosition());
    if (positionCompare != 0) {
      return positionCompare;
    }
    return Comparator.nullsLast(Long::compareTo).compare(left.getId(), right.getId());
  }

  private int dispatchOfficialPlaylistAutoDownloads(Playlist playlist, LocalDateTime now) {
    List<YoutubePlaylistItem> pendingItems = youtubePlaylistItemMapper.selectPendingDispatch(playlist.getId());
    if (pendingItems.isEmpty()) {
      return 0;
    }
    if (!Boolean.TRUE.equals(playlist.getAutoDownloadEnabled())) {
      for (YoutubePlaylistItem item : pendingItems) {
        youtubePlaylistItemMapper.updateAutoDispatchStatus(
            item.getId(), YoutubePlaylistAutoDispatchStatus.SKIPPED.name(), now);
      }
      return 0;
    }

    Map<String, YoutubePlaylistItem> itemByEpisodeId = new LinkedHashMap<>();
    for (YoutubePlaylistItem item : pendingItems) {
      if (!StringUtils.hasText(item.getEpisodeId())) {
        continue;
      }
      YoutubePlaylistItem current = itemByEpisodeId.get(item.getEpisodeId());
      if (current == null || compareOfficialItemPosition(item, current) < 0) {
        itemByEpisodeId.put(item.getEpisodeId(), item);
      }
    }
    List<String> episodeIds = new ArrayList<>(itemByEpisodeId.keySet());
    Map<String, Episode> episodeMap = loadEpisodesByIdsInBatches(episodeIds);
    List<Episode> candidates = new ArrayList<>();
    for (String episodeId : episodeIds) {
      Episode episode = episodeMap.get(episodeId);
      if (episode == null) {
        continue;
      }
      YoutubePlaylistItem item = itemByEpisodeId.get(episodeId);
      episode.setPosition(item.getPosition());
      candidates.add(episode);
    }
    candidates.sort(AUTO_DOWNLOAD_PLAYLIST_ORDER);
    List<Episode> episodesToDownload = selectEpisodesForAutoRefresh(playlist, candidates);
    Set<String> dispatchedEpisodeIds = episodesToDownload.stream()
        .map(Episode::getId)
        .collect(Collectors.toSet());
    markAndPublishAutoDownloadEpisodes(
        playlist,
        episodesToDownload,
        buildEpisodesCreatedContext("playlist_official_api_sync", playlist));

    int dispatchedCount = 0;
    for (YoutubePlaylistItem item : pendingItems) {
      String status = dispatchedEpisodeIds.contains(item.getEpisodeId())
          ? YoutubePlaylistAutoDispatchStatus.DISPATCHED.name()
          : YoutubePlaylistAutoDispatchStatus.SKIPPED.name();
      youtubePlaylistItemMapper.updateAutoDispatchStatus(item.getId(), status, now);
      if (YoutubePlaylistAutoDispatchStatus.DISPATCHED.name().equals(status)) {
        dispatchedCount++;
      }
    }
    return dispatchedCount;
  }

  private void updateCoverFromDerivedEpisodes(Playlist playlist) {
    List<YoutubePlaylistItem> linkedItems = youtubePlaylistItemMapper.selectActiveLinked(playlist.getId());
    if (linkedItems.isEmpty()) {
      return;
    }
    YoutubePlaylistItem first = linkedItems.get(0);
    if (!StringUtils.hasText(first.getEpisodeId())) {
      return;
    }
    List<Episode> episodes = episodeService().getEpisodesByIds(List.of(first.getEpisodeId()));
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

  private record OfficialItemDiffResult(int insertedCount, int removedCount, int movedCount) {

  }

  private record DerivePlaylistEpisodeResult(int derivedCount) {

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
    if (IndividualVideoPlaylistSupport.isSingleVideoPlaylist(playlist)) {
      return Collections.emptyList();
    }
    if (playlistEpisodeMapper.countByPlaylistId(playlistId) == 0) {
      log.warn("播放列表 {} 尚未初始化节目，跳过历史节目信息抓取", playlistId);
      return Collections.emptyList();
    }
    if (Boolean.TRUE.equals(playlist.getHistoryCursorExhausted())) {
      log.info("播放列表 {} 历史 cursor 已耗尽，无需继续拉取", playlistId);
      return Collections.emptyList();
    }
    if (!isBilibiliPlaylist(playlist)) {
      if (playlist.getBootstrapCompletedAt() == null) {
        syncYoutubePlaylistWithOfficialApi(playlist, "MANUAL_FULL");
      }
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
    if (IndividualVideoPlaylistSupport.isSingleVideoPlaylist(playlist)) {
      log.info("静态单视频播放列表无需初始化同步，playlistId={}", playlistId);
      return;
    }

    FeedRefreshResult result = isBilibiliPlaylist(playlist)
        ? refreshFeed(playlist)
        : syncYoutubePlaylistWithOfficialApi(playlist, "INIT");
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
    if (IndividualVideoPlaylistSupport.isSingleVideoPlaylist(feed)) {
      return fetchSingleVideoPreviewEpisodes(feed);
    }
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
    if (IndividualVideoPlaylistSupport.isSingleVideoPlaylist(feed)) {
      return List.of();
    }
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
      if (IndividualVideoPlaylistSupport.isSingleVideoPlaylist(feed)) {
        attachCustomCoverUrl(feed);
        return;
      }
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

  private FeedPack<Playlist> fetchSingleVideoPlaylist(String videoUrl) {
    Playlist existing = playlistMapper.selectById(IndividualVideoPlaylistSupport.PLAYLIST_ID);
    Playlist fetchedPlaylist = existing == null ? buildNewSingleVideoPlaylist(videoUrl) : copySingleVideoPlaylist(existing, videoUrl);
    Episode episode = resolveSingleVideoEpisode(videoUrl, fetchedPlaylist);
    String coverUrl = episode.getMaxCoverUrl() != null ? episode.getMaxCoverUrl() : episode.getDefaultCoverUrl();
    if (!StringUtils.hasText(fetchedPlaylist.getCoverUrl()) && StringUtils.hasText(coverUrl)) {
      fetchedPlaylist.setCoverUrl(coverUrl);
    }
    attachCustomCoverUrl(fetchedPlaylist);
    return FeedPack.<Playlist>builder()
        .feed(fetchedPlaylist)
        .episodes(List.of(episode))
        .build();
  }

  private Playlist buildNewSingleVideoPlaylist(String videoUrl) {
    Playlist playlist = Playlist.builder()
        .id(IndividualVideoPlaylistSupport.PLAYLIST_ID)
        .title(IndividualVideoPlaylistSupport.DEFAULT_TITLE)
        .coverUrl("")
        .description(IndividualVideoPlaylistSupport.DEFAULT_DESCRIPTION)
        .source(FeedSource.YOUTUBE.name())
        .feedMode(IndividualVideoPlaylistSupport.FEED_MODE)
        .subscribedAt(LocalDateTime.now())
        .originalUrl(videoUrl)
        .autoDownloadEnabled(Boolean.TRUE)
        .syncIntervalHours(DEFAULT_SYNC_INTERVAL_HOURS)
        .build();
    feedDefaultsService().applyDefaultsIfMissing(playlist);
    return playlist;
  }

  private Playlist copySingleVideoPlaylist(Playlist existing, String videoUrl) {
    Playlist playlist = Playlist.builder()
        .id(existing.getId())
        .title(existing.getTitle())
        .customTitle(existing.getCustomTitle())
        .ownerId(existing.getOwnerId())
        .coverUrl(existing.getCoverUrl())
        .description(existing.getDescription())
        .source(existing.getSource())
        .feedMode(existing.getFeedMode())
        .titleContainKeywords(existing.getTitleContainKeywords())
        .titleExcludeKeywords(existing.getTitleExcludeKeywords())
        .descriptionContainKeywords(existing.getDescriptionContainKeywords())
        .descriptionExcludeKeywords(existing.getDescriptionExcludeKeywords())
        .minimumDuration(existing.getMinimumDuration())
        .maximumDuration(existing.getMaximumDuration())
        .excludeLiveVod(existing.getExcludeLiveVod())
        .autoDownloadLimit(existing.getAutoDownloadLimit())
        .autoDownloadDelayMinutes(existing.getAutoDownloadDelayMinutes())
        .maximumEpisodes(existing.getMaximumEpisodes())
        .audioQuality(existing.getAudioQuality())
        .downloadType(existing.getDownloadType())
        .videoQuality(existing.getVideoQuality())
        .videoEncoding(existing.getVideoEncoding())
        .subtitleLanguages(existing.getSubtitleLanguages())
        .subtitleFormat(existing.getSubtitleFormat())
        .autoDownloadEnabled(existing.getAutoDownloadEnabled())
        .syncIntervalHours(resolveSyncIntervalHours(existing))
        .customCoverExt(existing.getCustomCoverExt())
        .subscribedAt(existing.getSubscribedAt())
        .lastUpdatedAt(existing.getLastUpdatedAt())
        .originalUrl(videoUrl)
        .build();
    feedDefaultsService().applyDefaultsIfMissing(playlist);
    return playlist;
  }

  @Transactional
  protected FeedSaveResult<Playlist> saveSingleVideoPlaylist(Playlist incoming) {
    if (!StringUtils.hasText(incoming.getOriginalUrl())) {
      throw new BusinessException(messageSource.getMessage("feed.source.url.missing", null,
          LocaleContextHolder.getLocale()));
    }

    Playlist candidate = buildNewSingleVideoPlaylist(incoming.getOriginalUrl());
    applySingleVideoMutableConfig(candidate, incoming);
    Episode episode = resolveSingleVideoEpisode(incoming.getOriginalUrl(), candidate);
    String coverUrl = episode.getMaxCoverUrl() != null ? episode.getMaxCoverUrl() : episode.getDefaultCoverUrl();
    if (StringUtils.hasText(coverUrl)) {
      candidate.setCoverUrl(coverUrl);
    }

    Playlist playlist = playlistMapper.selectById(IndividualVideoPlaylistSupport.PLAYLIST_ID);
    if (playlist == null) {
      playlist = candidate;
      playlistMapper.insert(playlist);
    } else {
      if (!StringUtils.hasText(playlist.getCoverUrl())
          && !StringUtils.hasText(playlist.getCustomCoverExt())
          && StringUtils.hasText(candidate.getCoverUrl())) {
        playlist.setCoverUrl(candidate.getCoverUrl());
      }
      playlist.setOriginalUrl(candidate.getOriginalUrl());
      applySingleVideoMutableConfig(playlist, incoming);
      playlistMapper.updateById(playlist);
    }
    List<Episode> episodesToPersist = prepareEpisodesForPersistence(List.of(episode));
    episodeService().saveEpisodes(episodesToPersist);

    Episode persistedEpisode = episodesToPersist.get(0);
    if (persistedEpisode.getPosition() == null) {
      persistedEpisode.setPosition(-LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
    }

    boolean alreadyMapped = playlistEpisodeMapper.countByPlaylistAndEpisode(playlist.getId(), persistedEpisode.getId()) > 0;
    upsertPlaylistEpisodes(playlist.getId(), List.of(persistedEpisode));
    afterEpisodesPersisted(playlist, List.of(persistedEpisode));

    if (!alreadyMapped) {
      List<Episode> episodesToDownload = selectEpisodesForAutoRefresh(playlist, List.of(persistedEpisode));
      markAndPublishAutoDownloadEpisodes(
          playlist,
          episodesToDownload,
          buildEpisodesCreatedContext("single_video_add", playlist));
    }

    playlist.setOriginalUrl(null);
    attachCustomCoverUrl(playlist);
    return FeedSaveResult.<Playlist>builder()
        .feed(playlist)
        .async(false)
        .message(messageSource.getMessage(
            alreadyMapped ? "playlist.single.video.duplicate" : "playlist.single.video.added",
            null,
            LocaleContextHolder.getLocale()))
        .build();
  }

  private void applySingleVideoMutableConfig(Playlist target, Playlist source) {
    if (target == null || source == null) {
      return;
    }
    if (!StringUtils.hasText(target.getTitle())) {
      target.setTitle(IndividualVideoPlaylistSupport.DEFAULT_TITLE);
    }
    if (!StringUtils.hasText(target.getDescription())) {
      target.setDescription(IndividualVideoPlaylistSupport.DEFAULT_DESCRIPTION);
    }
    target.setSource(FeedSource.YOUTUBE.name());
    target.setFeedMode(IndividualVideoPlaylistSupport.FEED_MODE);
    target.setCustomTitle(source.getCustomTitle());
    target.setTitleContainKeywords(source.getTitleContainKeywords());
    target.setTitleExcludeKeywords(source.getTitleExcludeKeywords());
    target.setDescriptionContainKeywords(source.getDescriptionContainKeywords());
    target.setDescriptionExcludeKeywords(source.getDescriptionExcludeKeywords());
    target.setMinimumDuration(source.getMinimumDuration());
    target.setMaximumDuration(source.getMaximumDuration());
    target.setExcludeLiveVod(source.getExcludeLiveVod());
    target.setAutoDownloadEnabled(source.getAutoDownloadEnabled());
    target.setAutoDownloadLimit(source.getAutoDownloadLimit());
    target.setAutoDownloadDelayMinutes(source.getAutoDownloadDelayMinutes());
    target.setMaximumEpisodes(source.getMaximumEpisodes());
    target.setAudioQuality(source.getAudioQuality());
    target.setDownloadType(source.getDownloadType());
    target.setVideoQuality(source.getVideoQuality());
    target.setVideoEncoding(source.getVideoEncoding());
    target.setSubtitleLanguages(source.getSubtitleLanguages());
    target.setSubtitleFormat(source.getSubtitleFormat());
  }

  private Episode resolveSingleVideoEpisode(String videoUrl, Playlist playlist) {
    String videoId = youtubeHelper.extractYoutubeVideoId(videoUrl);
    if (!StringUtils.hasText(videoId)) {
      throw new BusinessException(messageSource.getMessage("youtube.invalid.video.url", null,
          LocaleContextHolder.getLocale()));
    }

    try {
      String apiKey = YoutubeApiKeyHolder.requireYoutubeApiKey(messageSource);
      Map<String, Video> detailMap = youtubeVideoHelper.fetchVideoDetailsInBulk(List.of(videoId), apiKey);
      Video video = detailMap.get(videoId);
      if (video == null) {
        throw new BusinessException(messageSource.getMessage("youtube.video.not.found", null,
            LocaleContextHolder.getLocale()));
      }
      YoutubeVideoHelper.VideoFetchConfig config = new YoutubeVideoHelper.VideoFetchConfig(
          null,
          playlist.getId(),
          playlist.getTitleContainKeywords(),
          playlist.getTitleExcludeKeywords(),
          playlist.getDescriptionContainKeywords(),
          playlist.getDescriptionExcludeKeywords(),
          playlist.getMinimumDuration(),
          playlist.getMaximumDuration(),
          1);
      return youtubeVideoHelper.buildSingleVideoEpisodeIfSyncable(video, config)
          .orElseThrow(() -> new BusinessException(messageSource.getMessage("youtube.video.not.found", null,
              LocaleContextHolder.getLocale())));
    } catch (IOException exception) {
      throw new BusinessException(messageSource.getMessage("youtube.fetch.playlist.failed",
          new Object[]{exception.getMessage()}, LocaleContextHolder.getLocale()));
    }
  }

  private List<Episode> fetchSingleVideoPreviewEpisodes(Playlist feed) {
    if (StringUtils.hasText(feed.getOriginalUrl())) {
      return List.of(resolveSingleVideoEpisode(feed.getOriginalUrl(), feed));
    }
    List<Episode> episodes = episodeService().getEpisodesByPlaylistId(feed.getId());
    episodes = FeedEpisodeVisibilityHelper.filterVisibleEpisodes(feed, episodes);
    if (episodes.size() > AbstractFeedService.DEFAULT_PREVIEW_NUM) {
      return episodes.subList(0, AbstractFeedService.DEFAULT_PREVIEW_NUM);
    }
    return episodes;
  }

  private void attachCustomCoverUrl(Playlist playlist) {
    if (playlist == null || !StringUtils.hasText(playlist.getCustomCoverExt())) {
      return;
    }
    String coverUrl = "/media/feed/" + playlist.getId() + "/cover";
    if (playlist.getLastUpdatedAt() != null) {
      coverUrl += "?v=" + playlist.getLastUpdatedAt().toEpochSecond(ZoneOffset.UTC);
    }
    playlist.setCustomCoverUrl(coverUrl);
  }

  private boolean looksLikeYoutubePlaylistInput(String input) {
    if (!StringUtils.hasText(input)) {
      return false;
    }
    String normalized = input.trim().toLowerCase();
    return normalized.contains("list=") || normalized.contains("playlist");
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
