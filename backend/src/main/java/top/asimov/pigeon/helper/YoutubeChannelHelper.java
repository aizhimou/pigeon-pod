package top.asimov.pigeon.helper;

import com.google.api.services.youtube.model.PlaylistItem;
import java.util.List;
import java.util.function.Predicate;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.config.YoutubeApiKeyHolder;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.exception.YoutubeAutoSyncBlockedException;
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
    return fetchYoutubeChannelVideos(channelId, maxPagesToCheck, null, null, null, null, null, null,
        null);
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
   * @param minimalDuration            最小视频时长（秒）
   * @param maximumDuration            最长视频时长（分钟）
   * @return 视频列表
   */
  public List<Episode> fetchYoutubeChannelVideos(String channelId, int maxPagesToCheck,
      String lastSyncedVideoId, String containKeywords, String excludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords, Integer minimalDuration,
      Integer maximumDuration) {
    VideoFetchConfig config = new VideoFetchConfig(
        channelId, null,
        containKeywords, excludeKeywords,
        descriptionContainKeywords, descriptionExcludeKeywords, minimalDuration, maximumDuration,
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
   * 按 YouTube 返回的 page token 获取指定频道的一页历史视频。
   *
   * @param channelId 频道 ID
   * @param pageToken 下一页 token；传 null 表示从头开始
   */
  public PageHistoryResult fetchChannelHistoryPage(String channelId, String pageToken) {
    try {
      String youtubeApiKey = YoutubeApiKeyHolder.requireYoutubeApiKey(messageSource);
      String playlistId = videoHelper.getUploadsPlaylistId(channelId, youtubeApiKey);
      YoutubeVideoHelper.PlaylistPageFetchResult result =
          videoHelper.fetchPlaylistEpisodesPage(playlistId, pageToken, channelId, youtubeApiKey);
      return new PageHistoryResult(result.episodes(), result.nextPageToken(), result.exhausted());
    } catch (Exception e) {
      if (e instanceof YoutubeAutoSyncBlockedException autoSyncBlockedException) {
        throw autoSyncBlockedException;
      }
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
      if (e instanceof YoutubeAutoSyncBlockedException autoSyncBlockedException) {
        throw autoSyncBlockedException;
      }
      throw new BusinessException(
          messageSource.getMessage("youtube.fetch.videos.error", new Object[]{e.getMessage()},
              LocaleContextHolder.getLocale()));
    }
  }

  public record PageHistoryResult(List<Episode> episodes, String nextPageToken, boolean exhausted) {

  }
}
