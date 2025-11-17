package top.asimov.pigeon.helper;

import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.Video;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.config.YoutubeApiKeyHolder;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.helper.YoutubeVideoHelper.VideoFetchConfig;
import top.asimov.pigeon.model.entity.Episode;

@Log4j2
@Component
public class YoutubeChannelHelper {

  private final MessageSource messageSource;
  private final YoutubeVideoHelper videoHelper;

  public YoutubeChannelHelper(MessageSource messageSource, YoutubeVideoHelper videoHelper) {
    this.messageSource = messageSource;
    this.videoHelper = videoHelper;
  }

  /**
   * 获取指定 YouTube 频道的最新视频（按页遍历，固定每页50）
   *
   * @param channelId       频道 ID
   * @param maxPagesToCheck 最大检查页数（预览场景可传 1）
   * @return 视频列表（调用方可自行截断数量）
   */
  public List<Episode> fetchYoutubeChannelVideos(String channelId, int maxPagesToCheck) {
    return fetchYoutubeChannelVideos(channelId, maxPagesToCheck, null, null, null, null, null, null);
  }

  /**
   * 获取指定 YouTube 频道的视频，直到指定的最后一个已同步视频
   *
   * @param channelId                  频道 ID
   * @param maxPagesToCheck            最大检查页数
   * @param lastSyncedVideoId          最后一个已同步的视频 ID，抓取将在此视频处停止
   * @param containKeywords            标题必须包含的关键词
   * @param excludeKeywords            标题必须排除的关键词
   * @param descriptionContainKeywords 描述必须包含的关键词
   * @param descriptionExcludeKeywords 描述必须排除的关键词
   * @param minimalDuration            最小视频时长（分钟）
   * @return 视频列表
   */
  public List<Episode> fetchYoutubeChannelVideos(String channelId, int maxPagesToCheck,
      String lastSyncedVideoId, String containKeywords, String excludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords, Integer minimalDuration) {
    VideoFetchConfig config = new VideoFetchConfig(
        channelId, null,
        containKeywords, excludeKeywords,
        descriptionContainKeywords, descriptionExcludeKeywords, minimalDuration,
        maxPagesToCheck
    );

    Predicate<PlaylistItem> stopCondition = item -> {
      String currentVideoId = item.getSnippet().getResourceId().getVideoId();
      return currentVideoId.equals(lastSyncedVideoId);
    };

    Predicate<PlaylistItem> skipCondition = item -> false; // 不跳过任何视频

    return fetchVideosWithConditions(config, stopCondition, skipCondition);
  }

  /**
   * 按页获取指定 YouTube 频道的历史视频（仅返回指定页的数据）。
   *
   * <p>调用方通常会根据「当前已入库节目数量 / 每页大小」计算出目标页码，例如：
   * totalCount=40, pageSize=50 => 当前最早节目在第1页，则目标历史页为第2页。</p>
   *
   * @param channelId 频道 ID
   * @param pageIndex 目标页码（从 1 开始）
   */
  public List<Episode> fetchChannelHistoryPage(String channelId, int pageIndex,
      String titleContainKeywords, String titleExcludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords,
      Integer minimalDuration) {
    if (pageIndex <= 0) {
      return Collections.emptyList();
    }
    try {
      String youtubeApiKey = YoutubeApiKeyHolder.requireYoutubeApiKey(messageSource);
      String playlistId = videoHelper.getUploadsPlaylistId(channelId, youtubeApiKey);

      // 仅用于过滤配置，maxPagesToCheck 在这里不会用于分页控制
      VideoFetchConfig config = new VideoFetchConfig(
          channelId, null,
          titleContainKeywords, titleExcludeKeywords,
          descriptionContainKeywords, descriptionExcludeKeywords, minimalDuration,
          1
      );

      String nextPageToken = null;
      PlaylistItemListResponse response = null;
      long pageSize = 50L;

      for (int currentPage = 1; currentPage <= pageIndex; currentPage++) {
        response =
            videoHelper.fetchPlaylistPage(playlistId, pageSize, nextPageToken, youtubeApiKey);
        List<PlaylistItem> items = response.getItems();
        if (items == null || items.isEmpty()) {
          return Collections.emptyList();
        }
        if (currentPage < pageIndex) {
          nextPageToken = response.getNextPageToken();
          if (nextPageToken == null) {
            return Collections.emptyList();
          }
        }
      }

      if (response.getItems().isEmpty()) {
        return Collections.emptyList();
      }

      List<PlaylistItem> pageItems = response.getItems();
      List<String> videoIds = new ArrayList<>();
      for (PlaylistItem item : pageItems) {
        videoIds.add(item.getSnippet().getResourceId().getVideoId());
      }

      Map<String, Video> videoDetails =
          videoHelper.fetchVideoDetailsInBulk(videoIds, youtubeApiKey);

      List<Episode> result = new ArrayList<>();
      for (PlaylistItem item : pageItems) {
        String videoId = item.getSnippet().getResourceId().getVideoId();
        Video video = videoDetails.get(videoId);
        Optional<Episode> maybeEpisode = videoHelper.buildEpisodeIfMatches(item, video, config);
        maybeEpisode.ifPresent(result::add);
      }
      log.info("频道 {} 历史页 {} 拉取到 {} 个符合条件的视频", channelId, pageIndex, result.size());
      return result;
    } catch (Exception e) {
      throw new BusinessException(
          messageSource.getMessage("youtube.fetch.videos.error", new Object[]{e.getMessage()},
              LocaleContextHolder.getLocale()));
    }
  }

  /**
   * 根据给定的配置和条件（停止和跳过）获取视频
   *
   * @param config        视频获取配置
   * @param stopCondition 停止抓取的条件
   * @param skipCondition 跳过当前视频的条件
   * @return 视频列表
   */
  private List<Episode> fetchVideosWithConditions(VideoFetchConfig config,
      Predicate<PlaylistItem> stopCondition, Predicate<PlaylistItem> skipCondition) {
    try {
      String youtubeApiKey = YoutubeApiKeyHolder.requireYoutubeApiKey(messageSource);

      String playlistId = videoHelper.getUploadsPlaylistId(config.channelId(), youtubeApiKey);

      return videoHelper.fetchVideosFromPlaylist(playlistId, config, stopCondition, skipCondition);
    } catch (Exception e) {
      throw new BusinessException(
          messageSource.getMessage("youtube.fetch.videos.error", new Object[]{e.getMessage()},
              LocaleContextHolder.getLocale()));
    }
  }
}
