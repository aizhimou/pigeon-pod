package top.asimov.pigeon.helper;

import com.google.api.services.youtube.model.PlaylistItem;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
   * 获取指定 YouTube 频道的最新视频
   *
   * @param channelId 频道 ID
   * @param fetchNum  要获取的视频数量
   * @return 视频列表
   */
  public List<Episode> fetchYoutubeChannelVideos(String channelId, int fetchNum) {
    return fetchYoutubeChannelVideos(channelId, fetchNum, null, null, null, null, null, null);
  }

  /**
   * 获取指定 YouTube 频道的视频，直到指定的最后一个已同步视频
   *
   * @param channelId         频道 ID
   * @param fetchNum          要获取的视频数量
   * @param lastSyncedVideoId 最后一个已同步的视频 ID，抓取将在此视频处停止
   * @param containKeywords   标题必须包含的关键词
   * @param excludeKeywords   标题必须排除的关键词
   * @param descriptionContainKeywords 描述必须包含的关键词
   * @param descriptionExcludeKeywords 描述必须排除的关键词
   * @param minimalDuration   最小视频时长（分钟）
   * @return 视频列表
   */
  public List<Episode> fetchYoutubeChannelVideos(String channelId, int fetchNum,
      String lastSyncedVideoId, String containKeywords, String excludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords, Integer minimalDuration) {
    VideoFetchConfig config = new VideoFetchConfig(
        channelId, null, fetchNum, containKeywords, excludeKeywords,
        descriptionContainKeywords, descriptionExcludeKeywords, minimalDuration,
        (fetchNumLong) -> 50L, // API 单页最大 50
        Integer.MAX_VALUE, // 不限制页数
        false
    );

    Predicate<PlaylistItem> stopCondition = item -> {
      String currentVideoId = item.getSnippet().getResourceId().getVideoId();
      return currentVideoId.equals(lastSyncedVideoId);
    };

    Predicate<PlaylistItem> skipCondition = item -> false; // 不跳过任何视频

    return fetchVideosWithConditions(config, stopCondition, skipCondition);
  }

  /**
   * 获取指定 YouTube 频道在特定日期之前发布的视频
   *
   * @param channelId       频道 ID
   * @param fetchNum        要获取的视频数量
   * @param publishedBefore 最晚发布日期
   * @param titleContainKeywords 标题必须包含的关键词
   * @param titleExcludeKeywords 标题必须排除的关键词
   * @param descriptionContainKeywords 描述必须包含的关键词
   * @param descriptionExcludeKeywords 描述必须排除的关键词
   * @param minimalDuration 最小视频时长（分钟）
   * @return 视频列表
   */
  public List<Episode> fetchYoutubeChannelVideosBeforeDate(String channelId, int fetchNum,
      LocalDateTime publishedBefore, String titleContainKeywords, String titleExcludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords, Integer minimalDuration) {
    VideoFetchConfig config = new VideoFetchConfig(
        channelId, null, fetchNum, titleContainKeywords, titleExcludeKeywords,
        descriptionContainKeywords, descriptionExcludeKeywords, minimalDuration,
        (fetchNumLong) -> 50L, // API 单页最大 50，获取更多数据以便过滤
        20, // 限制最大检查页数，避免无限循环
        false
    );

    Predicate<PlaylistItem> stopCondition = item -> false; // 不因特定视频而停止

    Predicate<PlaylistItem> skipCondition = item -> {
      LocalDateTime videoPublishedAt = LocalDateTime.ofInstant(
          Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue()),
          ZoneId.systemDefault());
      return videoPublishedAt.isAfter(publishedBefore); // 跳过太新的视频
    };

    log.info("开始获取频道 {} 在 {} 之前的视频，目标数量: {}", channelId, publishedBefore, fetchNum);
    List<Episode> result = fetchVideosWithConditions(config, stopCondition, skipCondition);
    log.info("最终获取到 {} 个符合条件的视频", result.size());
    return result;
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
