package top.asimov.pigeon.scheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.service.EpisodeService;

@Log4j2
@Component
public class EpisodeCleaner {

  private final EpisodeMapper episodeMapper;
  private final ChannelMapper channelMapper;
  private final PlaylistMapper playlistMapper;
  private final EpisodeService episodeService;

  public EpisodeCleaner(EpisodeMapper episodeMapper, ChannelMapper channelMapper,
      PlaylistMapper playlistMapper, EpisodeService episodeService) {
    this.episodeMapper = episodeMapper;
    this.channelMapper = channelMapper;
    this.playlistMapper = playlistMapper;
    this.episodeService = episodeService;
  }

  /**
   * 每2小时执行一次，按每个订阅（频道/播放列表）的 maximumEpisodes
   * 对已下载完成（COMPLETED）的节目做自动清理：
   * - 只统计 download_status = COMPLETED 的节目数量
   * - 若超过 maximumEpisodes，则从最旧的开始删除文件并将状态重置为 READY
   * - 保留数据库中的 Episode 记录，方便继续展示节目元数据
   * 为提高效率，使用 SQL 分组统计找出“超限”的频道/播放列表，
   * 然后仅针对这些 feed 精确查询需要清理的节目。
   */
  @Scheduled(fixedRate = 2, timeUnit = TimeUnit.HOURS)
  public void cleanupEpisodesOverMaximum() {
    log.info("开始执行 Episode 自动清理任务...");
    int channelCleaned = cleanupChannelEpisodes();
    int playlistCleaned = cleanupPlaylistEpisodes();
    log.info("Episode 自动清理任务执行完毕。频道清理 {} 条，播放列表清理 {} 条。", channelCleaned,
        playlistCleaned);
  }

  private int cleanupChannelEpisodes() {
    List<Map<String, Object>> stats = channelMapper.selectChannelCompletedOverLimit();
    if (stats == null || stats.isEmpty()) {
      return 0;
    }

    int cleanedCount = 0;
    for (Map<String, Object> row : stats) {
      String channelId = (String) row.get("channel_id");

      long[] numberToCleanup = calculateNumberToCleanup(row);
      if (ObjectUtils.isEmpty(numberToCleanup)) {
        continue;
      }

      long maximumEpisodes = numberToCleanup[0];
      long toCleanup = numberToCleanup[1];
      List<Episode> episodesToCleanup =
          episodeMapper.selectCompletedEpisodesByChannelWithOffset(channelId, maximumEpisodes, toCleanup);
      if (episodesToCleanup == null || episodesToCleanup.isEmpty()) {
        continue;
      }

      String title = (String) row.get("channel_title");
      log.info("频道 {} (id={}) 超出限制 {} 条，准备清理。", title, channelId, episodesToCleanup.size());

      for (Episode episode : episodesToCleanup) {
        episodeService.cleanupCompletedEpisode(episode);
        cleanedCount++;
      }
    }
    return cleanedCount;
  }

  private int cleanupPlaylistEpisodes() {
    List<Map<String, Object>> stats = playlistMapper.selectPlaylistCompletedOverLimit();
    if (stats == null || stats.isEmpty()) {
      return 0;
    }

    int cleanedCount = 0;
    for (Map<String, Object> row : stats) {
      String playlistId = (String) row.get("playlist_id");
      if (playlistId == null) {
        continue;
      }

      long[] numberToCleanup = calculateNumberToCleanup(row);
      if (ObjectUtils.isEmpty(numberToCleanup)) {
        continue;
      }

      long maximumEpisodes = numberToCleanup[0];
      long toCleanup = numberToCleanup[1];
      List<Episode> episodesToCleanup =
          episodeMapper.selectCompletedEpisodesByPlaylistWithOffset(playlistId, maximumEpisodes, toCleanup);
      if (episodesToCleanup == null || episodesToCleanup.isEmpty()) {
        continue;
      }

      String title = (String) row.get("playlist_title");
      log.info("播放列表 {} (id={}) 超出限制 {} 条，准备清理。", title, playlistId, episodesToCleanup.size());

      for (Episode episode : episodesToCleanup) {
        episodeService.cleanupCompletedEpisode(episode);
        cleanedCount++;
      }
    }
    return cleanedCount;
  }

  private long[] calculateNumberToCleanup (Map<String, Object> row) {
    Number completedCountNum = (Number) row.get("completed_count");
    Number maximumEpisodesNum = (Number) row.get("maximum_episodes");
    if (completedCountNum == null || maximumEpisodesNum == null) {
      return null;
    }

    long completedCount = completedCountNum.longValue();
    long maximumEpisodes = maximumEpisodesNum.longValue();
    long toCleanup = completedCount - maximumEpisodes;
    if (toCleanup <= 0) {
      return null;
    }
    return new long[] { maximumEpisodes, toCleanup };
  }
}
