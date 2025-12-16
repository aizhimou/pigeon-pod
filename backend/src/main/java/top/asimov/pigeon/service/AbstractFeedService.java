package top.asimov.pigeon.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.annotation.Transactional;
import top.asimov.pigeon.event.DownloadTaskEvent;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadAction;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadTargetType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.model.response.FeedConfigUpdateResult;
import top.asimov.pigeon.model.response.FeedPack;
import top.asimov.pigeon.model.response.FeedSaveResult;
import top.asimov.pigeon.model.response.FeedRefreshResult;
import top.asimov.pigeon.handler.FeedEpisodeHelper;

public abstract class AbstractFeedService<F extends Feed> {

  protected static final int DEFAULT_DOWNLOAD_NUM = 3;
  protected static final int DEFAULT_PREVIEW_NUM = 5;

  private final EpisodeService episodeService;
  private final ApplicationEventPublisher eventPublisher;
  private final MessageSource messageSource;

  protected AbstractFeedService(EpisodeService episodeService,
      ApplicationEventPublisher eventPublisher,
      MessageSource messageSource) {
    this.episodeService = episodeService;
    this.eventPublisher = eventPublisher;
    this.messageSource = messageSource;
  }

  protected EpisodeService episodeService() {
    return episodeService;
  }

  protected ApplicationEventPublisher eventPublisher() {
    return eventPublisher;
  }

  protected MessageSource messageSource() {
    return messageSource;
  }

  @Transactional
  public FeedConfigUpdateResult updateFeedConfig(String feedId, F configuration) {
    F existingFeed = findFeedById(feedId)
        .orElseThrow(() -> new BusinessException(messageSource()
            .getMessage("feed.not.found", new Object[]{feedId}, LocaleContextHolder.getLocale())));

    applyMutableFields(existingFeed, configuration);

    int updated = updateFeed(existingFeed);
    if (updated <= 0) {
      throw new BusinessException(messageSource()
          .getMessage("feed.config.update.failed", null, LocaleContextHolder.getLocale()));
    }

    return FeedConfigUpdateResult.builder()
        .downloadHistory(false)
        .downloadNumber(0)
        .build();
  }

  private void applyMutableFields(F existingFeed, F configuration) {
    existingFeed.setTitleContainKeywords(configuration.getTitleContainKeywords());
    existingFeed.setTitleExcludeKeywords(configuration.getTitleExcludeKeywords());
    existingFeed.setDescriptionContainKeywords(configuration.getDescriptionContainKeywords());
    existingFeed.setDescriptionExcludeKeywords(configuration.getDescriptionExcludeKeywords());
    existingFeed.setMinimumDuration(configuration.getMinimumDuration());
    existingFeed.setMaximumDuration(configuration.getMaximumDuration());
    existingFeed.setMaximumEpisodes(configuration.getMaximumEpisodes());
    existingFeed.setInitialEpisodes(configuration.getInitialEpisodes());
    existingFeed.setAudioQuality(configuration.getAudioQuality());
    existingFeed.setCustomTitle(configuration.getCustomTitle());
    existingFeed.setCustomCoverExt(configuration.getCustomCoverExt());
    existingFeed.setDownloadType(configuration.getDownloadType());
    existingFeed.setVideoQuality(configuration.getVideoQuality());
    existingFeed.setVideoEncoding(configuration.getVideoEncoding());
    existingFeed.setSyncState(configuration.getSyncState());
    existingFeed.setSubtitleFormat(configuration.getSubtitleFormat());
    existingFeed.setSubtitleLanguages(configuration.getSubtitleLanguages());
  }

  @Transactional
  public FeedSaveResult<F> saveFeed(F feed) {
    if (feed.getSyncState() == null) {
      feed.setSyncState(Boolean.TRUE);
    }
    int initialEpisodes = normalizeInitialEpisodes(feed);
    return saveFeedAsync(feed, initialEpisodes);
  }

  private int normalizeInitialEpisodes(F feed) {
    Integer initialEpisodes = feed.getInitialEpisodes();
    if (initialEpisodes == null || initialEpisodes <= 0) {
      feed.setInitialEpisodes(DEFAULT_DOWNLOAD_NUM);
      return DEFAULT_DOWNLOAD_NUM;
    }
    return initialEpisodes;
  }

  private FeedSaveResult<F> saveFeedAsync(F feed, int initialEpisodes) {
    insertFeed(feed);
    publishDownloadTask(feed.getId(), initialEpisodes, feed);
    String message = messageSource().getMessage("feed.async.processing",
        new Object[]{initialEpisodes}, LocaleContextHolder.getLocale());
    return FeedSaveResult.<F>builder()
        .feed(feed)
        .async(true)
        .message(message)
        .build();
  }

  protected void persistEpisodesAndPublish(F feed, List<Episode> episodes) {
    episodeService().saveEpisodes(prepareEpisodesForPersistence(episodes));
    afterEpisodesPersisted(feed, episodes);
    List<Episode> episodesToDownload = selectEpisodesForAutoDownload(feed, episodes);
    FeedEpisodeHelper.publishEpisodesCreated(eventPublisher(), this, episodesToDownload);
  }

  protected List<Episode> prepareEpisodesForPersistence(List<Episode> episodes) {
    return episodes;
  }

  protected void afterEpisodesPersisted(F feed, List<Episode> episodes) {
    // default no-op, subclasses may override
  }

  /**
   * 根据 Episode ID 与数据库中的现有记录进行比对，返回真正新增的节目列表。
   *
   * <p>注意：以 Episode.id 作为全局唯一键进行判断，与具体所属频道/播放列表无关。</p>
   *
   * @param episodes 新抓取到的节目列表
   * @return 仅包含数据库中尚不存在的节目列表
   */
  protected List<Episode> filterNewEpisodes(List<Episode> episodes) {
    if (episodes == null || episodes.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> ids = episodes.stream()
        .map(Episode::getId)
        .toList();
    if (ids.isEmpty()) {
      return Collections.emptyList();
    }

    List<Episode> existingEpisodes = episodeService().getEpisodeStatusByIds(ids);
    if (existingEpisodes.isEmpty()) {
      return episodes;
    }

    Set<String> existingIds = existingEpisodes.stream()
        .map(Episode::getId)
        .collect(Collectors.toSet());

    return episodes.stream()
        .filter(episode -> !existingIds.contains(episode.getId()))
        .collect(Collectors.toList());
  }

  /**
   * 解析当前订阅的自动下载数量上限。
   *
   * <p>如果用户未显式配置 initialEpisodes 或配置为非正数，则使用默认值
   * {@link #DEFAULT_DOWNLOAD_NUM}。</p>
   *
   * @param feed 当前订阅
   * @return 每次刷新自动触发下载的节目数量上限
   */
  protected int resolveDownloadLimit(F feed) {
    Integer initialEpisodes = feed.getInitialEpisodes();
    if (initialEpisodes == null || initialEpisodes <= 0) {
      return DEFAULT_DOWNLOAD_NUM;
    }
    return initialEpisodes;
  }

  /**
   * 根据订阅配置，从本次新增的节目中筛选出需要自动下载的子集。
   *
   * <p>所有节目都会被入库为 READY，仅有前 N 条（由 initialEpisodes 或默认值决定）
   * 会被发布下载事件，其余节目保留为仅元数据状态，由用户按需手动下载。</p>
   *
   * @param feed        当前订阅
   * @param newEpisodes 本次新增的节目列表
   * @return 需要自动下载的节目的子列表
   */
  protected List<Episode> selectEpisodesForAutoDownload(F feed, List<Episode> newEpisodes) {
    if (newEpisodes == null || newEpisodes.isEmpty()) {
      return Collections.emptyList();
    }
    int limit = resolveDownloadLimit(feed);
    if (limit <= 0 || newEpisodes.size() <= limit) {
      return newEpisodes;
    }
    return newEpisodes.subList(0, limit);
  }

  protected void publishDownloadTask(String feedId, int number, F feed) {
    DownloadTaskEvent event = new DownloadTaskEvent(
        this,
        downloadTargetType(),
        DownloadAction.INIT,
        feedId,
        number,
        feed.getTitleContainKeywords(),
        feed.getTitleExcludeKeywords(),
        feed.getDescriptionContainKeywords(),
        feed.getDescriptionExcludeKeywords(),
        feed.getMinimumDuration(),
        feed.getMaximumDuration());
    eventPublisher().publishEvent(event);
    logger().info("已发布{} {} 下载事件，目标: {}, 数量: {}", DownloadAction.INIT, downloadTargetType(), feedId,
        number);
  }

  public FeedPack<F> previewFeed(F feed) {
    List<Episode> episodes = fetchEpisodes(feed);
    if (episodes.size() > DEFAULT_PREVIEW_NUM) {
      episodes = episodes.subList(0, DEFAULT_PREVIEW_NUM);
    }
    return FeedPack.<F>builder().feed(feed).episodes(episodes).build();
  }

  @Transactional
  public FeedRefreshResult refreshFeed(F feed) {
    List<Episode> newEpisodes = fetchIncrementalEpisodes(feed);
    if (newEpisodes.isEmpty()) {
      feed.setLastSyncTimestamp(LocalDateTime.now());
      updateFeed(feed);
      logger().info("{} 没有新内容。", feed.getTitle());
      return FeedRefreshResult.builder()
          .hasNewEpisodes(false)
          .newEpisodeCount(0)
          .message(messageSource().getMessage("feed.refresh.no.new",
              new Object[]{feed.getTitle()}, LocaleContextHolder.getLocale()))
          .build();
    }

    logger().info("{} 发现 {} 个新节目。", feed.getTitle(), newEpisodes.size());

    persistEpisodesAndPublish(feed, newEpisodes);

    FeedEpisodeHelper.findLatestEpisode(newEpisodes).ifPresent(latest -> {
      feed.setLastSyncVideoId(latest.getId());
      feed.setLastSyncTimestamp(LocalDateTime.now());
    });
    updateFeed(feed);

    return FeedRefreshResult.builder()
        .hasNewEpisodes(true)
        .newEpisodeCount(newEpisodes.size())
        .message(messageSource().getMessage("feed.refresh.new.episodes",
            new Object[]{newEpisodes.size(), feed.getTitle()},
            LocaleContextHolder.getLocale()))
        .build();
  }

  protected abstract Optional<F> findFeedById(String feedId);

  protected abstract int updateFeed(F feed);

  protected abstract void insertFeed(F feed);

  protected abstract DownloadTargetType downloadTargetType();

  protected abstract List<Episode> fetchEpisodes(F feed);

  protected abstract List<Episode> fetchIncrementalEpisodes(F feed);

  protected abstract Logger logger();
}
