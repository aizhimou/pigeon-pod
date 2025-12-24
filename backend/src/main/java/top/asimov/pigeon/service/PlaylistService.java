package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadTargetType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.handler.FeedEpisodeHelper;
import top.asimov.pigeon.helper.YoutubeHelper;
import top.asimov.pigeon.helper.YoutubePlaylistHelper;
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.constant.Youtube;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.enums.FeedSource;
import top.asimov.pigeon.model.response.FeedConfigUpdateResult;
import top.asimov.pigeon.model.response.FeedPack;
import top.asimov.pigeon.model.response.FeedSaveResult;
import top.asimov.pigeon.model.response.FeedRefreshResult;

@Log4j2
@Service
public class PlaylistService extends AbstractFeedService<Playlist> {

  @Value("${pigeon.base-url}")
  private String appBaseUrl;

  private final PlaylistMapper playlistMapper;
  private final PlaylistEpisodeMapper playlistEpisodeMapper;
  private final YoutubeHelper youtubeHelper;
  private final YoutubePlaylistHelper youtubePlaylistHelper;
  private final AccountService accountService;
  private final MessageSource messageSource;

  public PlaylistService(PlaylistMapper playlistMapper, PlaylistEpisodeMapper playlistEpisodeMapper,
      EpisodeService episodeService, ApplicationEventPublisher eventPublisher,
      YoutubeHelper youtubeHelper, YoutubePlaylistHelper youtubePlaylistHelper,
      AccountService accountService, MessageSource messageSource) {
    super(episodeService, eventPublisher, messageSource);
    this.playlistMapper = playlistMapper;
    this.playlistEpisodeMapper = playlistEpisodeMapper;
    this.youtubeHelper = youtubeHelper;
    this.youtubePlaylistHelper = youtubePlaylistHelper;
    this.accountService = accountService;
    this.messageSource = messageSource;
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
        .syncState(Boolean.TRUE)
        .build();

    return FeedPack.<Playlist>builder().feed(fetchedPlaylist).episodes(episodes).build();
  }

  public FeedPack<Playlist> previewPlaylist(Playlist playlist) {
    return previewFeed(playlist);
  }

  @Transactional
  public FeedSaveResult<Playlist> savePlaylist(Playlist playlist) {
    return saveFeed(playlist);
  }

  public List<Playlist> findDueForSync(LocalDateTime checkTime) {
    List<Playlist> playlists = playlistMapper.selectList(new LambdaQueryWrapper<>());
    return playlists.stream()
        .filter(p -> Boolean.TRUE.equals(p.getSyncState()))
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

    playlistEpisodeMapper.deleteByPlaylistId(playlistId);

    int result = playlistMapper.deleteById(playlistId);
    if (result > 0) {
      log.info("播放列表 {} 删除成功", playlist.getTitle());
      removeOrphanEpisodes(uniqueEpisodes.values());
    } else {
      log.error("播放列表 {} 删除失败", playlist.getTitle());
      throw new BusinessException(
          messageSource.getMessage("playlist.delete.failed", null,
              LocaleContextHolder.getLocale()));
    }
  }

  @Transactional
  public FeedRefreshResult refreshPlaylistById(String playlistId) {
    Playlist playlist = playlistMapper.selectById(playlistId);
    if (playlist == null) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{playlistId},
              LocaleContextHolder.getLocale()));
    }
    return refreshPlaylist(playlist);
  }

  @Transactional
  public FeedRefreshResult refreshPlaylist(Playlist playlist) {
    log.info("正在同步播放列表: {}", playlist.getTitle());

    // 1. 全量抓取当前播放列表中的所有节目（按 YouTube 播放列表顺序）
    List<Episode> allEpisodes = youtubePlaylistHelper.fetchPlaylistVideos(
        playlist.getId(),
        Integer.MAX_VALUE,
        null,
        playlist.getTitleContainKeywords(),
        playlist.getTitleExcludeKeywords(),
        playlist.getDescriptionContainKeywords(),
        playlist.getDescriptionExcludeKeywords(),
        playlist.getMinimumDuration(),
        playlist.getMaximumDuration());

    if (allEpisodes.isEmpty()) {
      playlist.setLastSyncTimestamp(LocalDateTime.now());
      playlistMapper.updateById(playlist);
      log.info("播放列表 {} 没有获取到任何节目。", playlist.getTitle());
      return FeedRefreshResult.builder()
          .hasNewEpisodes(false)
          .newEpisodeCount(0)
          .message(messageSource.getMessage("feed.refresh.no.new",
              new Object[]{playlist.getTitle()}, LocaleContextHolder.getLocale()))
          .build();
    }

    // 2. 计算真正新增的节目（基于 Episode ID 与数据库做差值）
    List<Episode> newEpisodes = filterNewEpisodes(allEpisodes);

    if (!newEpisodes.isEmpty()) {
      log.info("播放列表 {} 发现 {} 个新节目。", playlist.getTitle(), newEpisodes.size());

      List<Episode> episodesToPersist = prepareEpisodesForPersistence(newEpisodes);
      episodeService().saveEpisodes(episodesToPersist);

      // 仅对新增节目触发下载任务，数量受 initialEpisodes 限制
      List<Episode> episodesToDownload = selectEpisodesForAutoDownload(playlist, episodesToPersist);
      FeedEpisodeHelper.publishEpisodesCreated(eventPublisher(), this, episodesToDownload);

      // 以本次新增节目中发布时间最晚的一期，更新 lastSync 标记
      FeedEpisodeHelper.findLatestEpisode(newEpisodes).ifPresent(latest -> {
        playlist.setLastSyncVideoId(latest.getId());
        playlist.setLastSyncTimestamp(LocalDateTime.now());
      });
      if (playlist.getLastSyncTimestamp() == null) {
        playlist.setLastSyncTimestamp(LocalDateTime.now());
      }
    } else {
      log.info("播放列表 {} 本次未发现新增节目，仅同步顺序。", playlist.getTitle());
      playlist.setLastSyncTimestamp(LocalDateTime.now());
    }

    // 3. 使用完整列表同步 playlist_episode 中的顺序及封面信息，
    //    确保本地节目顺序始终与 YouTube 播放列表保持一致。
    afterEpisodesPersisted(playlist, allEpisodes);
    playlistMapper.updateById(playlist);

    log.info("播放列表 {} 同步完成，本次新增 {} 个新节目，总节目数 {}。", playlist.getTitle(),
        newEpisodes.size(), allEpisodes.size());

    return FeedRefreshResult.builder()
        .hasNewEpisodes(!newEpisodes.isEmpty())
        .newEpisodeCount(newEpisodes.size())
        .message(messageSource.getMessage(
            newEpisodes.isEmpty() ? "feed.refresh.no.new" : "feed.refresh.new.episodes",
            newEpisodes.isEmpty()
                ? new Object[]{playlist.getTitle()}
                : new Object[]{newEpisodes.size(), playlist.getTitle()},
            LocaleContextHolder.getLocale()))
        .build();
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
  public void processPlaylistInitializationAsync(String playlistId, Integer initialEpisodes,
      String titleContainKeywords, String titleExcludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords,
      Integer minimumDuration, Integer maximumDuration) {
    log.info("开始异步处理播放列表初始化，播放列表ID: {}, 初始视频数量: {}", playlistId, initialEpisodes);

    try {
      Playlist playlist = playlistMapper.selectById(playlistId);
      // 计算实际需要下载的节目数量（仅控制下载，不限制入库数量）
      int downloadLimit =
          initialEpisodes != null && initialEpisodes > 0 ? initialEpisodes : DEFAULT_DOWNLOAD_NUM;

      // 播放列表初始化：抓取整个播放列表（所有页），全部入库
      int pages = Integer.MAX_VALUE;
      List<Episode> episodes = youtubePlaylistHelper.fetchPlaylistVideos(
          playlistId, pages, null,
          titleContainKeywords, titleExcludeKeywords,
          descriptionContainKeywords, descriptionExcludeKeywords, minimumDuration, maximumDuration);

      if (episodes.isEmpty()) {
        log.info("播放列表 {} 没有找到任何视频。", playlistId);
        return;
      }

      // 根据 downloadLimit 标记前 N 个节目为准备下载，其余仅保存元数据
      for (int i = 0; i < episodes.size(); i++) {
        Episode episode = episodes.get(i);
        if (i < downloadLimit) {
          episode.setDownloadStatus(EpisodeStatus.PENDING.name());
        } else {
          episode.setDownloadStatus(EpisodeStatus.READY.name());
        }
      }

      FeedEpisodeHelper.findLatestEpisode(episodes).ifPresent(latest -> {
        if (playlist != null) {
          playlist.setLastSyncVideoId(latest.getId());
          playlist.setLastSyncTimestamp(LocalDateTime.now());
          playlistMapper.updateById(playlist);
        }
      });

      // 入库所有节目（包括仅保存元数据的部分）
      List<Episode> episodesToPersist = prepareEpisodesForPersistence(episodes);
      episodeService().saveEpisodes(episodesToPersist);

      if (playlist != null) {
        afterEpisodesPersisted(playlist, episodesToPersist);
      } else {
        // playlist 记录不存在时，仍需维护播放列表与节目的关联关系
        upsertPlaylistEpisodes(playlistId, episodesToPersist);
      }

      // 仅对前 downloadLimit 个节目触发下载任务
      List<Episode> episodesToDownload = episodes;
      if (episodes.size() > downloadLimit) {
        episodesToDownload = episodes.subList(0, downloadLimit);
      }
      FeedEpisodeHelper.publishEpisodesCreated(eventPublisher(), this, episodesToDownload);

      log.info("播放列表 {} 异步初始化完成，保存了 {} 个视频", playlistId, episodes.size());

    } catch (Exception e) {
      log.error("播放列表 {} 异步初始化失败: {}", playlistId, e.getMessage(), e);
    }
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

    Set<String> candidateDirectories = new HashSet<>();
    for (Episode episode : episodes) {
      long orhanEpisode = playlistEpisodeMapper.isOrhanEpisode(episode.getId());
      if (orhanEpisode == 0) {
        continue;
      }

      String mediaFilePath = episode.getMediaFilePath();

      if (StringUtils.hasText(mediaFilePath)) {
        try {
          episodeService().deleteSubtitleFiles(mediaFilePath);
        } catch (Exception ex) {
          log.error("删除播放列表孤立节目字幕文件 {} 失败: {}", mediaFilePath, ex.getMessage(), ex);
        }

        try {
          episodeService().deleteThumbnailFiles(mediaFilePath);
        } catch (Exception ex) {
          log.error("删除播放列表孤立节目封面文件 {} 失败: {}", mediaFilePath, ex.getMessage(), ex);
        }
      }

      try {
        int deleteResult = episodeService().deleteEpisodeById(episode.getId());
        if (deleteResult > 0 && StringUtils.hasText(mediaFilePath)) {
          File audioFile = new File(mediaFilePath);
          File parentDir = audioFile.getParentFile();
          if (parentDir != null) {
            candidateDirectories.add(parentDir.getAbsolutePath());
          }
        }
      } catch (BusinessException ex) {
        log.error("删除播放列表孤立节目 {} 失败: {}", episode.getId(), ex.getMessage(), ex);
      }
    }

    cleanupEmptyDirectories(candidateDirectories);
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
    return new ArrayList<>(episodes);
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
