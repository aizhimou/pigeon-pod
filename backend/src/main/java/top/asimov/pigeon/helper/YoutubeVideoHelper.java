package top.asimov.pigeon.helper;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.ThumbnailDetails;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Episode.EpisodeBuilder;
import top.asimov.pigeon.service.AccountService;

@Log4j2
@Component
public class YoutubeVideoHelper {

  private static final String APPLICATION_NAME = "My YouTube App";
  private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  private final AccountService accountService;
  private final YouTube youtubeService;

  public YoutubeVideoHelper(AccountService accountService) {
    this.accountService = accountService;

    try {
      this.youtubeService = new YouTube.Builder(
          GoogleNetHttpTransport.newTrustedTransport(),
          JSON_FACTORY,
          null // No need for HttpRequestInitializer for API key access
      ).setApplicationName(APPLICATION_NAME).build();
    } catch (GeneralSecurityException | IOException e) {
      log.error("Failed to initialize YouTube service", e);
      throw new RuntimeException("Failed to initialize YouTube service", e);
    }
  }

  /**
   * 从指定的播放列表获取视频
   *
   * @param playlistId    播放列表 ID
   * @param config        视频获取配置
   * @param stopCondition 停止抓取的条件
   * @param skipCondition 跳过当前视频的条件
   * @return 视频列表
   * @throws IOException 如果发生 I/O 错误
   */
  public List<Episode> fetchVideosFromPlaylist(String playlistId, VideoFetchConfig config,
      Predicate<PlaylistItem> stopCondition, Predicate<PlaylistItem> skipCondition) throws IOException {
    if (config.fetchFromTail()) {
      return fetchVideosFromPlaylistTail(playlistId, config, stopCondition, skipCondition);
    }

    String youtubeApiKey = accountService.getYoutubeApiKey();
    List<Episode> resultEpisodes = new ArrayList<>();
    String nextPageToken = "";
    int currentPage = 0;
    boolean shouldStop = false;

    while (resultEpisodes.size() < config.fetchNum() && currentPage < config.maxPagesToCheck()) {

      long pageSize = config.pageSizeCalculator().apply((long) config.fetchNum());

      PlaylistItemListResponse response = fetchPlaylistPage(
          playlistId, pageSize, nextPageToken, youtubeApiKey);

      List<PlaylistItem> pageItems = response.getItems();
      if (pageItems == null || pageItems.isEmpty()) {
        log.info("没有更多视频数据，停止抓取");
        break;
      }

      currentPage++;
      if (config.maxPagesToCheck() < Integer.MAX_VALUE) {
        log.info("处理第 {} 页，获取到 {} 个视频项", currentPage, pageItems.size());
      }

      // Step 1: Pre-filter and collect video IDs
      List<PlaylistItem> itemsToProcess = new ArrayList<>();
      List<String> videoIdsToFetch = new ArrayList<>();
      for (PlaylistItem item : pageItems) {
        if (stopCondition.test(item)) {
          shouldStop = true;
          break;
        }
        if (skipCondition.test(item)) {
          continue;
        }
        itemsToProcess.add(item);
        videoIdsToFetch.add(item.getSnippet().getResourceId().getVideoId());
      }

      if (shouldStop && itemsToProcess.isEmpty()) {
        break;
      }

      // Step 2: Bulk fetch video details
      Map<String, Video> videoDetailsMap = fetchVideoDetailsInBulk(videoIdsToFetch, youtubeApiKey);

      // Step 3: Final filtering and processing
      for (PlaylistItem item : itemsToProcess) {
        String videoId = item.getSnippet().getResourceId().getVideoId();
        Video video = videoDetailsMap.get(videoId);

        Optional<Episode> episodeOptional = buildEpisodeIfMatches(item, video, config);
        if (episodeOptional.isPresent()) {
          resultEpisodes.add(episodeOptional.get());
          if (config.maxPagesToCheck() < Integer.MAX_VALUE) {
            log.info("添加符合条件的视频: {} (发布于: {})", episodeOptional.get().getTitle(),
                episodeOptional.get().getPublishedAt());
          }
        }

        if (resultEpisodes.size() >= config.fetchNum()) {
          shouldStop = true;
          break;
        }
      }

      if (shouldStop) {
        break;
      }

      nextPageToken = response.getNextPageToken();
      if (nextPageToken == null) {
        if (config.maxPagesToCheck() < Integer.MAX_VALUE) {
          log.info("已到达播放列表末尾");
        }
        break;
      }
    }

    if (currentPage >= config.maxPagesToCheck()
        && config.maxPagesToCheck() < Integer.MAX_VALUE) {
      log.warn("已检查 {} 页视频，停止继续搜索", config.maxPagesToCheck());
    }

    return processResultList(resultEpisodes, config.fetchNum());
  }

  /**
   * 从播放列表的尾部（旧视频）开始获取视频
   *
   * @param playlistId    播放列表 ID
   * @param config        视频获取配置
   * @param stopCondition 停止抓取的条件
   * @param skipCondition 跳过当前视频的条件
   * @return 视频列表
   * @throws IOException 如果发生 I/O 错误
   */
  public List<Episode> fetchVideosFromPlaylistTail(String playlistId, VideoFetchConfig config,
      Predicate<PlaylistItem> stopCondition,
      Predicate<PlaylistItem> skipCondition) throws IOException {
    String youtubeApiKey = accountService.getYoutubeApiKey();
    Deque<PlaylistItem> tailItems = new ArrayDeque<>();
    String nextPageToken = "";
    int currentPage = 0;
    long pageSize = 50L; // Always use a full page size to build the buffer
    int bufferSize = Math.max(config.fetchNum() * 6, config.fetchNum() + 50);

    while (currentPage < config.maxPagesToCheck()) {
      PlaylistItemListResponse response = fetchPlaylistPage(
          playlistId, pageSize, nextPageToken, youtubeApiKey);

      List<PlaylistItem> pageItems = response.getItems();
      if (CollectionUtils.isEmpty(pageItems)) {
        log.info("没有更多视频数据，停止抓取");
        break;
      }

      currentPage++;

      for (PlaylistItem item : pageItems) {
        if (stopCondition.test(item)) {
          tailItems.clear();
          continue;
        }

        if (skipCondition.test(item)) {
          continue;
        }

        tailItems.addLast(item);
        if (tailItems.size() > bufferSize) {
          tailItems.removeFirst();
        }
      }

      nextPageToken = response.getNextPageToken();
      if (nextPageToken == null) {
        break;
      }
    }

    List<Episode> resultEpisodes = new ArrayList<>();
    if (tailItems.isEmpty()) {
      return resultEpisodes;
    }

    List<PlaylistItem> candidateItems = new ArrayList<>(tailItems);
    for (int i = candidateItems.size() - 1;
         i >= 0 && resultEpisodes.size() < config.fetchNum();
         i--) {
      PlaylistItem item = candidateItems.get(i);
      if (stopCondition.test(item)) {
        break;
      }

      // Revert to calling the old buildEpisodeIfMatches with apiKey
      Optional<Episode> episodeOptional = buildEpisodeIfMatches(item, config, youtubeApiKey);
      episodeOptional.ifPresent(resultEpisodes::add);
    }

    return resultEpisodes;
  }

  /**
   * 获取频道的上传播放列表 ID
   *
   * @param channelId     频道 ID
   * @param youtubeApiKey YouTube API 密钥
   * @return 上传播放列表 ID
   * @throws IOException 如果发生 I/O 错误
   */
  public String getUploadsPlaylistId(String channelId, String youtubeApiKey) throws IOException {
    YouTube.Channels.List channelRequest = youtubeService.channels().list("contentDetails");
    channelRequest.setId(channelId).setKey(youtubeApiKey);
    log.info("[YouTube API] channels.list(contentDetails) channelId={}", channelId);
    ChannelListResponse channelResponse = channelRequest.execute();
    return channelResponse.getItems().get(0).getContentDetails().getRelatedPlaylists().getUploads();
  }

  /**
   * 获取单个视频的详细信息
   *
   * @param youtubeService YouTube 服务实例
   * @param apiKey         YouTube API 密钥
   * @param videoId        视频 ID
   * @return 视频详细信息
   * @throws IOException 如果发生 I/O 错误
   */
  public Video fetchVideoDetails(YouTube youtubeService, String apiKey, String videoId)
      throws IOException {
    log.info("[YouTube API] videos.list(contentDetails,snippet,liveStreamingDetails) videoId={}",
        videoId);
    VideoListResponse videoResponse = youtubeService.videos()
        .list("contentDetails,snippet,liveStreamingDetails")
        .setId(videoId)
        .setKey(apiKey)
        .execute();

    List<Video> videos = videoResponse.getItems();
    if (CollectionUtils.isEmpty(videos)) {
      return null;
    }

    return videos.get(0);
  }

  /**
   * 如果播放列表项符合条件，则构建 Episode 对象（内部获取视频详情）
   *
   * @param item          播放列表项
   * @param config        视频获取配置
   * @param youtubeApiKey YouTube API 密钥
   * @return 如果符合条件，则返回包含 Episode 的 Optional，否则返回空 Optional
   * @throws IOException 如果发生 I/O 错误
   */
  public Optional<Episode> buildEpisodeIfMatches(PlaylistItem item, VideoFetchConfig config,
      String youtubeApiKey) throws IOException {
    String videoId = item.getSnippet().getResourceId().getVideoId();
    Video video = fetchVideoDetails(youtubeService, youtubeApiKey, videoId);
    return buildEpisodeIfMatches(item, video, config);
  }

  /**
   * 如果播放列表项和视频详细信息符合条件，则构建 Episode 对象
   *
   * @param item   播放列表项
   * @param video  视频详细信息
   * @param config 视频获取配置
   * @return 如果符合条件，则返回包含 Episode 的 Optional，否则返回空 Optional
   */
  public Optional<Episode> buildEpisodeIfMatches(PlaylistItem item, Video video, VideoFetchConfig config) {
    String title = item.getSnippet().getTitle();

    if (notMatchesKeywordFilter(title, config.titleContainKeywords(), config.titleExcludeKeywords())) {
      return Optional.empty();
    }

    if (video == null || video.getSnippet() == null) {
      return Optional.empty();
    }

    String description = video.getSnippet().getDescription();
    if (notMatchesKeywordFilter(description, config.descriptionContainKeywords(),
        config.descriptionExcludeKeywords())) {
      return Optional.empty();
    }

    if (shouldSkipLiveContent(video)) {
      return Optional.empty();
    }

    String duration = (video.getContentDetails() != null)
        ? video.getContentDetails().getDuration()
        : null;
    if (!StringUtils.hasText(duration)) {
      log.warn("无法读取视频时长: {} - {}", video.getId(), video.getSnippet().getTitle());
      return Optional.empty();
    }

    if (notMatchesDurationFilter(duration, config.minimalDuration())) {
      return Optional.empty();
    }

    String channelId = config.channelId() != null ? config.channelId()
        : video.getSnippet().getChannelId();
    Episode episode = buildEpisodeFromVideo(video, channelId, duration);
    return Optional.of(episode);
  }

  /**
   * 获取播放列表的单个页面
   *
   * @param playlistId    播放列表 ID
   * @param pageSize      每页大小
   * @param nextPageToken 下一页的令牌
   * @param youtubeApiKey YouTube API 密钥
   * @return 播放列表项列表响应
   * @throws IOException 如果发生 I/O 错误
   */
  public PlaylistItemListResponse fetchPlaylistPage(String playlistId, long pageSize,
      String nextPageToken, String youtubeApiKey) throws IOException {
    YouTube.PlaylistItems.List request = youtubeService.playlistItems()
        .list("snippet")
        .setPlaylistId(playlistId)
        .setMaxResults(pageSize)
        .setPageToken(nextPageToken)
        .setKey(youtubeApiKey);
    log.info("[YouTube API] playlistItems.list(snippet) playlistId={} maxResults={} pageToken={}",
        playlistId, pageSize, nextPageToken == null ? "<none>" : nextPageToken);
    return request.execute();
  }

  /**
   * 从 Video 对象构建 Episode 对象
   *
   * @param video     YouTube 视频对象
   * @param channelId 频道 ID
   * @param duration  视频时长
   * @return 构建的 Episode 对象
   */
  public Episode buildEpisodeFromVideo(Video video, String channelId, String duration) {
    LocalDateTime publishedAt = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(video.getSnippet().getPublishedAt().getValue()),
        ZoneId.systemDefault());

    EpisodeBuilder builder = Episode.builder()
        .id(video.getId())
        .channelId(channelId)
        .title(video.getSnippet().getTitle())
        .description(video.getSnippet().getDescription())
        .publishedAt(publishedAt)
        .duration(duration)
        .downloadStatus(EpisodeStatus.PENDING.name())
        .createdAt(LocalDateTime.now());

    applyThumbnails(builder, video.getSnippet().getThumbnails());
    return builder.build();
  }

  /**
   * 检查标题是否不符合关键词过滤器
   *
   * @param title           视频标题
   * @param containKeywords 必须包含的关键词
   * @param excludeKeywords 必须排除的关键词
   * @return 如果不匹配则返回 true，否则返回 false
   */
  public boolean notMatchesKeywordFilter(String title, String containKeywords,
      String excludeKeywords) {
    // 处理 containKeywords，支持空格分割的多个关键词，包含任意一个就行
    if (StringUtils.hasLength(containKeywords)) {
      String[] keywords = containKeywords.trim().split("\\s+");
      boolean containsAny = false;
      for (String keyword : keywords) {
        if (title.toLowerCase().contains(keyword.toLowerCase())) {
          containsAny = true;
          break;
        }
      }
      if (!containsAny) {
        return true;
      }
    }

    // 处理 excludeKeywords，支持空格分割的多个关键词，包含任意一个就排除
    if (StringUtils.hasLength(excludeKeywords)) {
      String[] keywords = excludeKeywords.trim().split("\\s+");
      for (String keyword : keywords) {
        if (title.toLowerCase().contains(keyword.toLowerCase())) {
          return true; // 包含排除关键词，不匹配
        }
      }
    }

    return false;
  }

  /**
   * 检查视频时长是否不符合时长过滤器
   *
   * @param duration        视频时长 (ISO 8601 格式)
   * @param minimalDuration 最小时长（分钟）
   * @return 如果不匹配则返回 true，否则返回 false
   */
  public boolean notMatchesDurationFilter(String duration, Integer minimalDuration) {
    if (minimalDuration == null) {
      return false; // 没有时长限制
    }

    if (!StringUtils.hasText(duration)) {
      return true; // 没有时长信息
    }

    try {
      long minutes = Duration.parse(duration).toMinutes();
      return minutes < minimalDuration;
    } catch (Exception e) {
      log.warn("解析视频时长失败: {}", duration);
      return true;
    }
  }

  /**
   * 处理结果列表，截断到指定的数量
   *
   * @param episodes 视频列表
   * @param fetchNum 要获取的数量
   * @return 处理后的视频列表
   */
  public List<Episode> processResultList(List<Episode> episodes, int fetchNum) {
    if (episodes.isEmpty()) {
      return Collections.emptyList();
    }

    // 截断到精确数量
    if (episodes.size() > fetchNum) {
      return episodes.subList(0, fetchNum);
    }

    return episodes;
  }

  /**
   * 批量获取视频详细信息
   *
   * @param videoIds 视频 ID 列表
   * @param apiKey   YouTube API 密钥
   * @return 视频 ID 到视频详细信息的映射
   * @throws IOException 如果发生 I/O 错误
   */
  public Map<String, Video> fetchVideoDetailsInBulk(List<String> videoIds, String apiKey) throws IOException {
    if (CollectionUtils.isEmpty(videoIds)) {
        return Collections.emptyMap();
    }
    log.info("[YouTube API] videos.list(contentDetails,snippet,liveStreamingDetails) videoIds=[...](count: {})", videoIds.size());
    VideoListResponse videoResponse = youtubeService.videos()
            .list("contentDetails,snippet,liveStreamingDetails")
            .setId(String.join(",", videoIds))
            .setKey(apiKey)
            .execute();

    if (CollectionUtils.isEmpty(videoResponse.getItems())) {
        return Collections.emptyMap();
    }

    return videoResponse.getItems().stream()
            .collect(Collectors.toMap(Video::getId, Function.identity()));
  }

  /**
   * 检查是否应跳过直播内容
   *
   * @param video 视频对象
   * @return 如果是直播内容则返回 true，否则返回 false
   */
  public boolean shouldSkipLiveContent(Video video) {
    String title = video.getSnippet().getTitle();
    String videoId = video.getId();
    String liveBroadcastContent = video.getSnippet().getLiveBroadcastContent();

    if ("live".equals(liveBroadcastContent) || "upcoming".equals(liveBroadcastContent)) {
      log.info("跳过 live 节目: {} - {}", videoId, title);
      return true;
    }

    if (video.getLiveStreamingDetails() != null &&
        video.getLiveStreamingDetails().getScheduledStartTime() != null &&
        video.getLiveStreamingDetails().getActualEndTime() == null) {
      log.info("跳过即将开始的 live 节目: {} - {}", videoId, title);
      return true;
    }

    return false;
  }

  /**
   * 将缩略图 URL 应用于 Episode 构建器
   *
   * @param builder    Episode 构建器
   * @param thumbnails 缩略图详细信息
   */
  public void applyThumbnails(EpisodeBuilder builder, ThumbnailDetails thumbnails) {
    if (thumbnails == null) {
      return;
    }

    if (thumbnails.getDefault() != null) {
      builder.defaultCoverUrl(thumbnails.getDefault().getUrl());
    }

    String maxCoverUrl = null;
    if (thumbnails.getMaxres() != null) {
      maxCoverUrl = thumbnails.getMaxres().getUrl();
    } else if (thumbnails.getStandard() != null) {
      maxCoverUrl = thumbnails.getStandard().getUrl();
    } else if (thumbnails.getHigh() != null) {
      maxCoverUrl = thumbnails.getHigh().getUrl();
    } else if (thumbnails.getMedium() != null) {
      maxCoverUrl = thumbnails.getMedium().getUrl();
    } else if (thumbnails.getDefault() != null) {
      maxCoverUrl = thumbnails.getDefault().getUrl();
    }

    builder.maxCoverUrl(maxCoverUrl);
  }

  /**
   * 视频获取配置
   *
   * @param channelId         频道 ID
   * @param playlistId        播放列表 ID
   * @param fetchNum          要获取的视频数量
   * @param titleContainKeywords   标题必须包含的关键词
   * @param titleExcludeKeywords   标题必须排除的关键词
   * @param descriptionContainKeywords 描述必须包含的关键词
   * @param descriptionExcludeKeywords 描述必须排除的关键词
   * @param minimalDuration   最小视频时长（分钟）
   * @param pageSizeCalculator 计算页面大小的函数
   * @param maxPagesToCheck   最大检查页数
   * @param fetchFromTail     是否从尾部获取
   */
  public record VideoFetchConfig(String channelId, String playlistId, int fetchNum,
                                  String titleContainKeywords, String titleExcludeKeywords,
                                  String descriptionContainKeywords, String descriptionExcludeKeywords,
                                  Integer minimalDuration, Function<Long, Long> pageSizeCalculator,
                                  int maxPagesToCheck, boolean fetchFromTail) {

  }
}
