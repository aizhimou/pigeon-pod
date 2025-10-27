package top.asimov.pigeon.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.event.DownloadTaskEvent;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadAction;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadTargetType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.model.response.FeedConfigUpdateResult;
import top.asimov.pigeon.model.response.FeedPack;
import top.asimov.pigeon.model.response.FeedSaveResult;
import top.asimov.pigeon.handler.FeedEpisodeHelper;

public abstract class AbstractFeedService<F extends Feed> {

  protected static final int DEFAULT_FETCH_NUM = 3;
  protected static final int MAX_FETCH_NUM = 5;
  protected static final int ASYNC_FETCH_NUM = 10;

  private static final String FEED_NOT_FOUND_MESSAGE_CODE = "feed.not.found";
  private static final String FEED_CONFIG_UPDATE_FAILED_MESSAGE_CODE =
      "feed.config.update.failed";
  private static final String FEED_ASYNC_PROCESSING_MESSAGE_CODE = "feed.async.processing";
  private static final String FEED_SYNC_COMPLETED_MESSAGE_CODE = "feed.sync.completed";

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
        .orElseThrow(() -> new BusinessException(
            messageSource().getMessage(FEED_NOT_FOUND_MESSAGE_CODE, new Object[]{feedId},
                LocaleContextHolder.getLocale())));

    int oldInitialEpisodes = ObjectUtils.isEmpty(existingFeed.getInitialEpisodes())
        ? DEFAULT_FETCH_NUM
        : existingFeed.getInitialEpisodes();
    Integer newInitialEpisodes = configuration.getInitialEpisodes();

    applyMutableFields(existingFeed, configuration);

    int updated = updateFeed(existingFeed);
    if (updated <= 0) {
      throw new BusinessException(
          messageSource().getMessage(FEED_CONFIG_UPDATE_FAILED_MESSAGE_CODE, null,
              LocaleContextHolder.getLocale()));
    }

    boolean downloadHistory = newInitialEpisodes != null && newInitialEpisodes > oldInitialEpisodes;
    int downloadNumber = downloadHistory ? newInitialEpisodes - oldInitialEpisodes : 0;
    if (downloadHistory) {
      publishDownloadTask(DownloadAction.HISTORY, feedId, downloadNumber, existingFeed);
    }

    return FeedConfigUpdateResult.builder()
        .downloadHistory(downloadHistory)
        .downloadNumber(downloadNumber)
        .build();
  }

  private void applyMutableFields(F existingFeed, F configuration) {
    existingFeed.setTitleContainKeywords(configuration.getTitleContainKeywords());
    existingFeed.setTitleExcludeKeywords(configuration.getTitleExcludeKeywords());
    existingFeed.setDescriptionContainKeywords(configuration.getDescriptionContainKeywords());
    existingFeed.setDescriptionExcludeKeywords(configuration.getDescriptionExcludeKeywords());
    existingFeed.setMinimumDuration(configuration.getMinimumDuration());
    existingFeed.setMaximumEpisodes(configuration.getMaximumEpisodes());
    existingFeed.setInitialEpisodes(configuration.getInitialEpisodes());
    existingFeed.setAudioQuality(configuration.getAudioQuality());
    existingFeed.setCustomTitle(configuration.getCustomTitle());
    existingFeed.setCustomCoverExt(configuration.getCustomCoverExt());
    existingFeed.setDownloadType(configuration.getDownloadType());
    existingFeed.setVideoQuality(configuration.getVideoQuality());
    existingFeed.setVideoEncoding(configuration.getVideoEncoding());
    applyAdditionalMutableFields(existingFeed, configuration);
  }

  @Transactional
  public FeedSaveResult<F> saveFeed(F feed) {
    int initialEpisodes = normalizeInitialEpisodes(feed);
    if (initialEpisodes > ASYNC_FETCH_NUM) {
      return saveFeedAsync(feed, initialEpisodes);
    }
    return saveFeedSync(feed, initialEpisodes);
  }

  private int normalizeInitialEpisodes(F feed) {
    Integer initialEpisodes = feed.getInitialEpisodes();
    if (initialEpisodes == null || initialEpisodes <= 0) {
      feed.setInitialEpisodes(DEFAULT_FETCH_NUM);
      return DEFAULT_FETCH_NUM;
    }
    return initialEpisodes;
  }

  private FeedSaveResult<F> saveFeedAsync(F feed, int initialEpisodes) {
    insertFeed(feed);
    publishDownloadTask(DownloadAction.INIT, feed.getId(), initialEpisodes, feed);
    String message = messageSource().getMessage(FEED_ASYNC_PROCESSING_MESSAGE_CODE,
        new Object[]{initialEpisodes}, LocaleContextHolder.getLocale());
    return FeedSaveResult.<F>builder()
        .feed(feed)
        .async(true)
        .message(message)
        .build();
  }

  private FeedSaveResult<F> saveFeedSync(F feed, int initialEpisodes) {
    List<Episode> episodes = fetchEpisodes(feed, initialEpisodes);

    FeedEpisodeHelper.findLatestEpisode(episodes).ifPresent(latest -> {
      feed.setLastSyncVideoId(latest.getId());
      feed.setLastSyncTimestamp(LocalDateTime.now());
    });

    insertFeed(feed);

    if (!episodes.isEmpty()) {
      persistEpisodesAndPublish(feed, episodes);
    }

    String message = messageSource().getMessage(FEED_SYNC_COMPLETED_MESSAGE_CODE,
        new Object[]{initialEpisodes}, LocaleContextHolder.getLocale());
    return FeedSaveResult.<F>builder()
        .feed(feed)
        .async(false)
        .message(message)
        .build();
  }

  protected void persistEpisodesAndPublish(F feed, List<Episode> episodes) {
    episodeService().saveEpisodes(prepareEpisodesForPersistence(episodes));
    afterEpisodesPersisted(feed, episodes);
    FeedEpisodeHelper.publishEpisodesCreated(eventPublisher(), this, episodes);
  }

  protected List<Episode> prepareEpisodesForPersistence(List<Episode> episodes) {
    return episodes;
  }

  protected void afterEpisodesPersisted(F feed, List<Episode> episodes) {
    // default no-op, subclasses may override
  }

  protected void applyAdditionalMutableFields(F existingFeed, F configuration) {
    // default no-op, subclasses may override
  }

  protected void publishDownloadTask(DownloadAction action, String feedId, int number, F feed) {
    DownloadTaskEvent event = new DownloadTaskEvent(
        this,
        downloadTargetType(),
        action,
        feedId,
        number,
        feed.getTitleContainKeywords(),
        feed.getTitleExcludeKeywords(),
        feed.getDescriptionContainKeywords(),
        feed.getDescriptionExcludeKeywords(),
        feed.getMinimumDuration());
    eventPublisher().publishEvent(event);
    logger().info("已发布{} {} 下载事件，目标: {}, 数量: {}", action, downloadTargetType(), feedId,
        number);
  }

  public FeedPack<F> previewFeed(F feed) {
    int fetchNum = DEFAULT_FETCH_NUM;
    if (StringUtils.hasText(feed.getTitleContainKeywords()) || StringUtils.hasText(
        feed.getTitleExcludeKeywords())) {
      fetchNum = MAX_FETCH_NUM;
    }
    List<Episode> episodes = fetchEpisodes(feed, fetchNum);
    return FeedPack.<F>builder().feed(feed).episodes(episodes).build();
  }

  @Transactional
  public void refreshFeed(F feed) {
    List<Episode> newEpisodes = fetchIncrementalEpisodes(feed);
    if (newEpisodes.isEmpty()) {
      feed.setLastSyncTimestamp(LocalDateTime.now());
      updateFeed(feed);
      logger().info("{} 没有新内容。", feed.getTitle());
      return;
    }

    logger().info("{} 发现 {} 个新节目。", feed.getTitle(), newEpisodes.size());

    persistEpisodesAndPublish(feed, newEpisodes);

    FeedEpisodeHelper.findLatestEpisode(newEpisodes).ifPresent(latest -> {
      feed.setLastSyncVideoId(latest.getId());
      feed.setLastSyncTimestamp(LocalDateTime.now());
    });
    updateFeed(feed);
  }

  protected abstract Optional<F> findFeedById(String feedId);

  protected abstract int updateFeed(F feed);

  protected abstract void insertFeed(F feed);

  protected abstract DownloadTargetType downloadTargetType();

  protected abstract List<Episode> fetchEpisodes(F feed, int fetchNum);

  protected abstract List<Episode> fetchIncrementalEpisodes(F feed);

  protected abstract Logger logger();
}
