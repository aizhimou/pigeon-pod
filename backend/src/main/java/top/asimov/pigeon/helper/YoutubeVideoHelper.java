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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.config.YoutubeApiKeyHolder;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.enums.YoutubeApiMethod;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Episode.EpisodeBuilder;

@Log4j2
@Component
public class YoutubeVideoHelper {

  private static final String APPLICATION_NAME = "My YouTube App";
  private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  private final YouTube youtubeService;
  private final MessageSource messageSource;
  private final YoutubeApiExecutor youtubeApiExecutor;

  public YoutubeVideoHelper(MessageSource messageSource, YoutubeApiExecutor youtubeApiExecutor) {
    this.messageSource = messageSource;
    this.youtubeApiExecutor = youtubeApiExecutor;
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
    String youtubeApiKey = YoutubeApiKeyHolder.requireYoutubeApiKey(messageSource);
    List<Episode> resultEpisodes = new ArrayList<>();
    String nextPageToken = "";
    int currentPage = 0;
    boolean shouldStop = false;

    while (currentPage < config.maxPagesToCheck()) {

      long pageSize = 50L; // 固定每页50，简化调用与分页逻辑

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
        episodeOptional.ifPresent(resultEpisodes::add);

        // 不再依据 fetchNum 终止，由调用方决定是否截断返回数量
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
    ChannelListResponse channelResponse = youtubeApiExecutor.execute(
        YoutubeApiMethod.CHANNELS_LIST,
        channelRequest::execute);
    return channelResponse.getItems().get(0).getContentDetails().getRelatedPlaylists().getUploads();
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

    if (notMatchesDurationFilter(duration, config.minimalDuration(), config.maximumDuration())) {
      return Optional.empty();
    }

    String channelId = config.channelId() != null ? config.channelId()
        : video.getSnippet().getChannelId();
    Episode episode = buildEpisodeFromVideo(item, video, channelId, duration);
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
    return youtubeApiExecutor.execute(YoutubeApiMethod.PLAYLIST_ITEMS_LIST, request::execute);
  }

  /**
   * 从 Video 对象构建 Episode 对象
   *
   * @param video     YouTube 视频对象
   * @param channelId 频道 ID
   * @param duration  视频时长
   * @return 构建的 Episode 对象
   */
  public Episode buildEpisodeFromVideo(PlaylistItem item, Video video, String channelId, String duration) {
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
        .position(item.getSnippet().getPosition())
        .downloadStatus(EpisodeStatus.READY.name())
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
    String normalizedTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);

    // 处理 containKeywords，仅支持逗号分割的多个关键词，包含任意一个即可（大小写不敏感）
    List<String> containKeywordList = parseCommaSeparatedKeywords(containKeywords);
    if (!containKeywordList.isEmpty()) {
      boolean containsAny = false;
      for (String keyword : containKeywordList) {
        if (normalizedTitle.contains(keyword)) {
          containsAny = true;
          break;
        }
      }
      if (!containsAny) {
        return true;
      }
    }

    // 处理 excludeKeywords，仅支持逗号分割的多个关键词，包含任意一个就排除（大小写不敏感）
    List<String> excludeKeywordList = parseCommaSeparatedKeywords(excludeKeywords);
    for (String keyword : excludeKeywordList) {
      if (normalizedTitle.contains(keyword)) {
        return true; // 包含排除关键词，不匹配
      }
    }

    return false;
  }

  private List<String> parseCommaSeparatedKeywords(String keywords) {
    if (!StringUtils.hasText(keywords)) {
      return Collections.emptyList();
    }

    return Arrays.stream(keywords.split(","))
        .map(String::trim)
        .filter(StringUtils::hasText)
        .map(keyword -> keyword.toLowerCase(Locale.ROOT))
        .toList();
  }

  /**
   * 检查视频时长是否不符合时长过滤器
   *
   * @param duration        视频时长 (ISO 8601 格式)
   * @param minimalDuration 最小时长（分钟）
   * @param maximumDuration 最长时长（分钟）
   * @return 如果不匹配则返回 true，否则返回 false
   */
  public boolean notMatchesDurationFilter(String duration, Integer minimalDuration,
      Integer maximumDuration) {
    if (!StringUtils.hasText(duration)) {
      return true; // 没有时长信息
    }

    try {
      long minutes = Duration.parse(duration).toMinutes();

      if (minimalDuration != null && minutes < minimalDuration) {
        return true;
      }

      if (maximumDuration != null && minutes > maximumDuration) {
        return true;
      }

      return false;
    } catch (Exception e) {
      log.warn("解析视频时长失败: {}", duration);
      return true;
    }
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
    VideoListResponse videoResponse = youtubeApiExecutor.execute(
        YoutubeApiMethod.VIDEOS_LIST,
        () -> youtubeService.videos()
            .list("contentDetails,snippet,liveStreamingDetails")
            .setId(String.join(",", videoIds))
            .setKey(apiKey)
            .execute());

    if (CollectionUtils.isEmpty(videoResponse.getItems())) {
        return Collections.emptyMap();
    }

    return videoResponse.getItems().stream()
            .collect(Collectors.toMap(Video::getId,
                Function.identity(),
                (existing, replacement) -> existing
            ));
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
   * @param titleContainKeywords   标题必须包含的关键词
   * @param titleExcludeKeywords   标题必须排除的关键词
   * @param descriptionContainKeywords 描述必须包含的关键词
   * @param descriptionExcludeKeywords 描述必须排除的关键词
   * @param minimalDuration   最小视频时长（分钟）
   * @param maximumDuration   最长视频时长（分钟）
   * @param maxPagesToCheck   最大检查页数
   */
  public record VideoFetchConfig(String channelId, String playlistId,
                                  String titleContainKeywords, String titleExcludeKeywords,
                                  String descriptionContainKeywords, String descriptionExcludeKeywords,
                                  Integer minimalDuration, Integer maximumDuration,
                                  int maxPagesToCheck) {

  }
}
