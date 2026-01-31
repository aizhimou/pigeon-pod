package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.event.EpisodesCreatedEvent;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.PlaylistEpisode;
import top.asimov.pigeon.model.enums.EpisodeBatchAction;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.response.EpisodeStatisticsResponse;

@Log4j2
@Service
public class EpisodeService {

  private final EpisodeMapper episodeMapper;
  private final ApplicationEventPublisher eventPublisher;
  private final MessageSource messageSource;
  private final ChannelMapper channelMapper;
  private final PlaylistEpisodeMapper playlistEpisodeMapper;

  public EpisodeService(EpisodeMapper episodeMapper, ApplicationEventPublisher eventPublisher,
      MessageSource messageSource, ChannelMapper channelMapper,
      PlaylistEpisodeMapper playlistEpisodeMapper) {
    this.episodeMapper = episodeMapper;
    this.eventPublisher = eventPublisher;
    this.messageSource = messageSource;
    this.channelMapper = channelMapper;
    this.playlistEpisodeMapper = playlistEpisodeMapper;
  }

  public Page<Episode> episodePage(String feedId, Page<Episode> page) {
    return episodePage(feedId, page, null, "newest", "all");
  }

  public Page<Episode> episodePage(String feedId, Page<Episode> page, String search, String sort,
      String filter) {
    Channel channel = channelMapper.selectById(feedId);
    if (channel != null) {
      LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
      queryWrapper.eq(Episode::getChannelId, feedId);
      if (StringUtils.hasText(search)) {
        queryWrapper.like(Episode::getTitle, search.trim());
      }
      if ("downloaded".equalsIgnoreCase(filter)) {
        queryWrapper.eq(Episode::getDownloadStatus, EpisodeStatus.COMPLETED);
      }
      boolean oldestFirst = "oldest".equalsIgnoreCase(sort);
      queryWrapper.orderBy(true, oldestFirst, Episode::getPublishedAt);
      return episodeMapper.selectPage(page, queryWrapper);
    }

    boolean downloadedOnly = "downloaded".equalsIgnoreCase(filter);
    String sortOrder = "oldest".equalsIgnoreCase(sort) ? "oldest" : "newest";
    long total = playlistEpisodeMapper.countByPlaylistIdWithFilters(feedId,
        StringUtils.hasText(search) ? search.trim() : null, downloadedOnly);
    page.setTotal(total);
    if (total == 0) {
      page.setRecords(Collections.emptyList());
      return page;
    }

    long current = page.getCurrent() > 0 ? page.getCurrent() : 1;
    long size = page.getSize() > 0 ? page.getSize() : 10;
    long offset = (current - 1) * size;

    List<Episode> episodes = playlistEpisodeMapper.selectEpisodePageByPlaylistIdWithFilters(feedId,
        offset, size, StringUtils.hasText(search) ? search.trim() : null, downloadedOnly, sortOrder);
    page.setRecords(episodes);
    return page;
  }

  public List<Episode> findByChannelId(String channelId) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getChannelId, channelId);
    return episodeMapper.selectList(queryWrapper);
  }

  public long countByChannelId(String channelId) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getChannelId, channelId);
    return episodeMapper.selectCount(queryWrapper);
  }

  public List<Episode> getEpisodeOrderByPublishDateDesc(String channelId) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getChannelId, channelId)
        .eq(Episode::getDownloadStatus, EpisodeStatus.COMPLETED)
        .orderByDesc(Episode::getPublishedAt);
    return episodeMapper.selectList(queryWrapper);
  }

  public List<Episode> getEpisodesByPlaylistId(String playlistId) {
    return episodeMapper.selectEpisodesByPlaylistId(playlistId);
  }

  @Transactional
  public void saveEpisodes(List<Episode> episodes) {
    // 1. 列表内部去重：防止传入的 list 中包含重复的 ID
    // 使用 Map 以 ID 为键，保留第一个出现的对象
    Collection<Episode> distinctEpisodes = episodes.stream()
        .collect(Collectors.toMap(
            Episode::getId,
            e -> e,
            (existing, replacement) -> existing
        ))
        .values();

    // 2. 检查数据库中已存在的记录
    QueryWrapper<Episode> queryWrapper = new QueryWrapper<>();
    queryWrapper.in("id", distinctEpisodes.stream().map(Episode::getId).toList());
    List<Episode> existingEpisodes = episodeMapper.selectList(queryWrapper);

    // 3. 排除数据库已有的 ID
    List<Episode> finalEpisodesToSave = new ArrayList<>(distinctEpisodes);
    if (!existingEpisodes.isEmpty()) {
      Set<String> existingIds = existingEpisodes.stream()
          .map(Episode::getId)
          .collect(Collectors.toSet()); // 使用 Set 提高查询效率
      finalEpisodesToSave.removeIf(episode -> existingIds.contains(episode.getId()));
    }
    // 4. 入库
    finalEpisodesToSave.forEach(episodeMapper::insert);
  }

  /**
   * 将指定节目批量标记为 PENDING，用于自动下载队列。
   */
  @Transactional
  public void markEpisodesPending(List<Episode> episodes) {
    if (episodes == null || episodes.isEmpty()) {
      return;
    }
    for (Episode episode : episodes) {
      if (episode == null || episode.getId() == null) {
        continue;
      }
      episodeMapper.updateDownloadStatus(episode.getId(), EpisodeStatus.PENDING.name());
      episode.setDownloadStatus(EpisodeStatus.PENDING.name());
    }
  }

  @Transactional
  public int deleteEpisodeById(String id) {
    Episode episode = episodeMapper.selectById(id);
    if (episode == null) {
      log.error("Episode not found with id: {}", id);
      throw new BusinessException(messageSource.getMessage("episode.not.found",
          new Object[]{id}, LocaleContextHolder.getLocale()));
    }

    String audioFilePath = episode.getMediaFilePath();

    // 删除同名字幕文件（safeTitle.lang.ext），支持 vtt/srt
    deleteSubtitleFiles(audioFilePath);

    // 删除同名封面文件（safeTitle.ext），当前为 jpg
    deleteThumbnailFiles(audioFilePath);

    if (StringUtils.hasText(audioFilePath)) {
      try {
        Files.deleteIfExists(Paths.get(audioFilePath));
      } catch (Exception e) {
        log.error("Failed to delete audio file: " + audioFilePath, e);
        throw new BusinessException(
            messageSource.getMessage("episode.delete.audio.failed", new Object[] { audioFilePath },
                LocaleContextHolder.getLocale()));
      }
    }

    // 删除 Episode 记录
    int result = episodeMapper.deleteById(id);

    // 如果属于播放列表，同步删除 playlist_episode 记录
    LambdaQueryWrapper<PlaylistEpisode> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(PlaylistEpisode::getEpisodeId, id);
    playlistEpisodeMapper.delete(wrapper);

    return result;
  }

  void deleteSubtitleFiles(String mediaFilePath) {
    if (!StringUtils.hasText(mediaFilePath)) {
      return;
    }
    try {
      Path mediaPath = Paths.get(mediaFilePath);
      Path parent = mediaPath.getParent();
      if (parent == null) {
        return;
      }

      String fileName = mediaPath.getFileName().toString();
      String baseName;
      int dotIndex = fileName.lastIndexOf('.');
      if (dotIndex > 0) {
        baseName = fileName.substring(0, dotIndex);
      } else {
        baseName = fileName;
      }

      try (Stream<Path> pathStream = Files.list(parent)) {
        List<Path> subtitleFiles = pathStream
            .filter(path -> {
              String name = path.getFileName().toString();
              boolean subtitleExt = name.endsWith(".vtt") || name.endsWith(".srt");
              boolean samePrefix = name.startsWith(baseName + ".");
              boolean isMediaFile = name.equals(fileName);
              return subtitleExt && samePrefix && !isMediaFile;
            }).toList();

        for (Path subtitlePath : subtitleFiles) {
          try {
            Files.deleteIfExists(subtitlePath);
          } catch (Exception e) {
            log.error("Failed to delete subtitle file: {}", subtitlePath, e);
            throw new BusinessException("Failed to delete subtitle file: " + subtitlePath);
          }
        }
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to delete subtitle files for media: {}", mediaFilePath, e);
      throw new BusinessException("Failed to delete subtitle files for media: " + mediaFilePath);
    }
  }

  /**
   * 删除与媒体文件同名的封面文件（缩略图）。
   * <p>
   * 目前 yt-dlp 通过 {@code --write-thumbnail --convert-thumbnails jpg}
   * 在与媒体文件同一目录下生成 {@code safeTitle.jpg} 等文件。
   * 本方法会根据媒体文件名（不含扩展名）删除所有同前缀的 JPG/PNG/WEBP 文件。
   * </p>
   *
   * @param mediaFilePath 媒体文件完整路径
   */
  void deleteThumbnailFiles(String mediaFilePath) {
    if (!StringUtils.hasText(mediaFilePath)) {
      return;
    }
    try {
      Path mediaPath = Paths.get(mediaFilePath);
      Path parent = mediaPath.getParent();
      if (parent == null) {
        return;
      }

      String fileName = mediaPath.getFileName().toString();
      String baseName;
      int dotIndex = fileName.lastIndexOf('.');
      if (dotIndex > 0) {
        baseName = fileName.substring(0, dotIndex);
      } else {
        baseName = fileName;
      }

      try (Stream<Path> pathStream = Files.list(parent)) {
        List<Path> thumbnailFiles = pathStream
            .filter(path -> {
              String name = path.getFileName().toString();
              boolean imageExt =
                  name.endsWith(".jpg") || name.endsWith(".jpeg")
                      || name.endsWith(".png") || name.endsWith(".webp");
              boolean samePrefix = name.startsWith(baseName + ".");
              boolean isMediaFile = name.equals(fileName);
              return imageExt && samePrefix && !isMediaFile;
            }).toList();

        for (Path thumbnailPath : thumbnailFiles) {
          try {
            Files.deleteIfExists(thumbnailPath);
          } catch (Exception e) {
            log.error("Failed to delete thumbnail file: {}", thumbnailPath, e);
            throw new BusinessException("Failed to delete thumbnail file: " + thumbnailPath);
          }
        }
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to delete thumbnail files for media: {}", mediaFilePath, e);
      throw new BusinessException(
          "Failed to delete thumbnail files for media: " + mediaFilePath);
    }
  }

  public int deleteEpisodesByChannelId(String channelId) {
    LambdaQueryWrapper<Episode> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Episode::getChannelId, channelId);
    return episodeMapper.delete(wrapper);
  }

  /**
   * 清理已完成下载的节目：
   * - 删除对应的媒体文件及字幕文件
   * - 保留数据库记录，将 download_status 重置为 READY
   * - 清空 mediaFilePath 和 errorLog，表示当前本地没有已下载文件
   *
   * 该方法主要用于 EpisodeCleaner 定时任务。
   */
  @Transactional
  public void cleanupCompletedEpisode(Episode episode) {
    if (episode == null || episode.getId() == null) {
      return;
    }

    Episode persisted = episodeMapper.selectById(episode.getId());
    if (persisted == null) {
      log.warn("清理 Episode 时发现记录不存在，id={}", episode.getId());
      return;
    }

    if (!EpisodeStatus.COMPLETED.name().equals(persisted.getDownloadStatus())) {
      // 状态已被其他流程修改（例如正在重试/手动删除），跳过清理
      return;
    }

    String mediaFilePath = persisted.getMediaFilePath();
    if (StringUtils.hasText(mediaFilePath)) {
      try {
        deleteSubtitleFiles(mediaFilePath);
        deleteThumbnailFiles(mediaFilePath);

        boolean deleted = Files.deleteIfExists(Paths.get(mediaFilePath));
        if (deleted) {
          log.info("清理 Episode {} 媒体文件成功: {}", persisted.getId(), mediaFilePath);
        } else {
          log.info("清理 Episode {} 媒体文件时，文件不存在: {}", persisted.getId(), mediaFilePath);
        }
      } catch (Exception e) {
        log.error("清理 Episode {} 文件时失败: {}", persisted.getId(), mediaFilePath, e);
        if (e instanceof BusinessException) {
          throw (BusinessException) e;
        }
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
          message = "Failed to delete episode files: " + mediaFilePath;
        }
        throw new BusinessException(message);
      }
    }

    persisted.setMediaFilePath(null);
    persisted.setDownloadStatus(EpisodeStatus.READY.name());
    persisted.setErrorLog(null);

    episodeMapper.updateById(persisted);
  }

  /**
   * 根据节目ID列表获取节目状态
   *
   * @param episodeIds 节目ID列表
   * @return 节目状态列表（只包含状态相关字段）
   */
  public List<Episode> getEpisodeStatusByIds(List<String> episodeIds) {
    if (episodeIds == null || episodeIds.isEmpty()) {
      return Collections.emptyList();
    }
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.in(Episode::getId, episodeIds);
    // 只选择状态相关的字段，减少网络传输
    queryWrapper.select(Episode::getId, Episode::getDownloadStatus, Episode::getErrorLog, Episode::getMediaType);
    return episodeMapper.selectList(queryWrapper);
  }

  /**
   * 重试下载episode音频文件
   *
   * @param episodeId episode id
   */
  @Transactional
  public void retryEpisode(String episodeId) {
    log.info("Starting retry for episode: {}", episodeId);

    // 1. 根据episode id查询出当前的episode
    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      log.error("Episode not found with id: {}", episodeId);
      throw new BusinessException(
          messageSource.getMessage("episode.not.found", new Object[] { episodeId },
              LocaleContextHolder.getLocale()));
    }

    // 状态校验：只允许重试 FAILED 状态的 Episode
    if (!EpisodeStatus.FAILED.name().equals(episode.getDownloadStatus())) {
      log.error("Cannot retry episode with status: {}", episode.getDownloadStatus());
      throw new BusinessException(
          messageSource.getMessage("episode.retry.invalid.status",
              new Object[] { episode.getDownloadStatus() },
              LocaleContextHolder.getLocale()));
    }

    // 2. 删除当前episode的audio file，可能有，也可能没有，需要做好错误处理
    String audioFilePath = episode.getMediaFilePath();
    if (StringUtils.hasText(audioFilePath)) {
      try {
        boolean deleted = Files.deleteIfExists(Paths.get(audioFilePath));
        if (deleted) {
          log.info("Successfully deleted existing audio file: {}", audioFilePath);
        } else {
          log.info("Audio file does not exist: {}", audioFilePath);
        }
        // 清空数据库中的音频文件路径
        episode.setMediaFilePath(null);
        episodeMapper.updateById(episode);
      } catch (Exception e) {
        log.warn("Failed to delete audio file: {} - {}", audioFilePath, e.getMessage());
        // 不抛出异常，继续执行下载流程
      }
    } else {
      log.info("No audio file path found for episode: {}, continue to download.", episodeId);
    }

    // 3. 调用事件发布机制，触发异步下载
    log.info("Publishing retry event for episode: {}", episodeId);
    eventPublisher.publishEvent(
        new EpisodesCreatedEvent(this, Collections.singletonList(episodeId)));
  }

  /**
   * 手动触发下载某个仅保存元数据但尚未下载内容的单集
   *
   * @param episodeId episode id
   */
  @Transactional
  public void manualDownloadEpisode(String episodeId) {
    log.info("Manually trigger download for episode: {}", episodeId);

    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      log.error("Episode not found with id: {}", episodeId);
      throw new BusinessException(
          messageSource.getMessage("episode.not.found", new Object[] { episodeId },
              LocaleContextHolder.getLocale()));
    }

    String status = episode.getDownloadStatus();
    // 只允许对 READY 状态的单集进行手动下载
    if (!EpisodeStatus.READY.name().equals(status)) {
      log.error("Cannot manually download episode with status: {}", status);
      throw new BusinessException(
          messageSource.getMessage("episode.download.invalid.status",
              new Object[] { status },
              LocaleContextHolder.getLocale()));
    }

    // 通过发布事件，复用统一的下载异步流程
    eventPublisher.publishEvent(
        new EpisodesCreatedEvent(this, Collections.singletonList(episodeId)));
  }

  /**
   * 获取各状态的Episode统计数量
   */
  public EpisodeStatisticsResponse getStatistics() {
    // 使用 GROUP BY 一次查询获取所有状态的统计
    List<Map<String, Object>> statusCounts = episodeMapper.countGroupByStatus();

    // 初始化所有计数为0
    long pendingCount = 0L;
    long downloadingCount = 0L;
    long completedCount = 0L;
    long failedCount = 0L;

    // 遍历结果，填充对应状态的计数
    for (Map<String, Object> row : statusCounts) {
      String status = (String) row.get("status");
      long count = ((Number) row.get("count")).longValue();

      if (EpisodeStatus.PENDING.name().equals(status)) {
        pendingCount = count;
      } else if (EpisodeStatus.DOWNLOADING.name().equals(status)) {
        downloadingCount = count;
      } else if (EpisodeStatus.COMPLETED.name().equals(status)) {
        completedCount = count;
      } else if (EpisodeStatus.FAILED.name().equals(status)) {
        failedCount = count;
      }
    }

    return EpisodeStatisticsResponse.builder()
        .pendingCount(pendingCount)
        .downloadingCount(downloadingCount)
        .completedCount(completedCount)
        .failedCount(failedCount)
        .build();
  }

  /**
   * 分页查询指定状态的Episode列表
   */
  public Page<Episode> getEpisodesByStatus(EpisodeStatus status, Page<Episode> page) {
    return episodeMapper.selectEpisodesByStatusWithFeedInfo(page, status.name());
  }

  /**
   * 取消PENDING状态的任务
   */
  @Transactional
  public void cancelPendingEpisode(String episodeId) {
    log.info("Cancelling pending episode: {}", episodeId);

    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      log.error("Episode not found with id: {}", episodeId);
      throw new BusinessException(
          messageSource.getMessage("episode.not.found", new Object[] { episodeId },
              LocaleContextHolder.getLocale()));
    }

    // 状态校验：只允许取消 PENDING 状态的 Episode
    if (!EpisodeStatus.PENDING.name().equals(episode.getDownloadStatus())) {
      log.error("Cannot cancel episode with status: {}", episode.getDownloadStatus());
      throw new BusinessException(
          messageSource.getMessage("episode.cancel.invalid.status",
              new Object[] { episode.getDownloadStatus() },
              LocaleContextHolder.getLocale()));
    }

    // 更新状态为 READY
    episodeMapper.updateDownloadStatus(episodeId, EpisodeStatus.READY.name());
  }

  @Transactional
  public void batchProcessEpisodes(EpisodeBatchAction action, EpisodeStatus status,
      List<String> episodeIds) {
    EpisodeStatus targetStatus = getTargetStatus(action, status);

    List<String> targetIds = new ArrayList<>();
    if (episodeIds != null && !episodeIds.isEmpty()) {
      targetIds.addAll(episodeIds);
    } else {
      LambdaQueryWrapper<Episode> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(Episode::getDownloadStatus, targetStatus.name());
      List<Episode> episodes = episodeMapper.selectList(wrapper);
      if (episodes != null && !episodes.isEmpty()) {
        targetIds = episodes.stream().map(Episode::getId).toList();
      }
    }

    if (targetIds.isEmpty()) {
      return;
    }

    for (String episodeId : targetIds) {
      switch (action) {
        case CANCEL -> cancelPendingEpisode(episodeId);
        case DELETE -> deleteEpisodeById(episodeId);
        case RETRY -> retryEpisode(episodeId);
      }
    }
  }

  private static EpisodeStatus getTargetStatus(EpisodeBatchAction action, EpisodeStatus status) {
    if (action == null) {
      throw new BusinessException("Invalid batch action");
    }

    EpisodeStatus targetStatus = getEpisodeStatus(action, status);

    if (action == EpisodeBatchAction.RETRY && targetStatus != EpisodeStatus.FAILED) {
      throw new BusinessException("Retry operation only supports failed episodes");
    }

    if (action == EpisodeBatchAction.DELETE && targetStatus != EpisodeStatus.COMPLETED) {
      throw new BusinessException("Delete operation only supports completed episodes");
    }

    if (action == EpisodeBatchAction.CANCEL && targetStatus != EpisodeStatus.PENDING) {
      throw new BusinessException("Cancel operation only supports pending episodes");
    }
    return targetStatus;
  }

  private static EpisodeStatus getEpisodeStatus(EpisodeBatchAction action, EpisodeStatus status) {
    EpisodeStatus expectedStatus = switch (action) {
      case CANCEL -> EpisodeStatus.PENDING;
      case DELETE -> EpisodeStatus.COMPLETED;
      case RETRY -> EpisodeStatus.FAILED;
    };

    return status != null ? status : expectedStatus;
  }
}
