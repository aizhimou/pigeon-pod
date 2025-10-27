package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
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
    Channel channel = channelMapper.selectById(feedId);
    if (channel != null) {
      LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
      queryWrapper.eq(Episode::getChannelId, feedId);
      queryWrapper.orderByDesc(Episode::getPublishedAt);
      return episodeMapper.selectPage(page, queryWrapper);
    }

    long total = playlistEpisodeMapper.countByPlaylistId(feedId);
    page.setTotal(total);
    if (total == 0) {
      page.setRecords(Collections.emptyList());
      return page;
    }

    long current = page.getCurrent() > 0 ? page.getCurrent() : 1;
    long size = page.getSize() > 0 ? page.getSize() : 10;
    long offset = (current - 1) * size;

    List<Episode> episodes = playlistEpisodeMapper.selectEpisodePageByPlaylistId(feedId, offset,
        size);
    page.setRecords(episodes);
    return page;
  }

  public List<Episode> findByChannelId(String channelId) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getChannelId, channelId);
    return episodeMapper.selectList(queryWrapper);
  }

  public List<Episode> getEpisodeOrderByPublishDateDesc(String channelId) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getChannelId, channelId).orderByDesc(Episode::getPublishedAt);
    return episodeMapper.selectList(queryWrapper);
  }

  public List<Episode> getEpisodesByPlaylistId(String playlistId) {
    return episodeMapper.selectEpisodesByPlaylistId(playlistId);
  }

  @Transactional
  public void saveEpisodes(List<Episode> episodes) {
    QueryWrapper<Episode> queryWrapper = new QueryWrapper<>();
    queryWrapper.in("id", episodes.stream().map(Episode::getId).toList());
    List<Episode> existingEpisodes = episodeMapper.selectList(queryWrapper);
    if (!existingEpisodes.isEmpty()) {
      List<String> existingIds = existingEpisodes.stream().map(Episode::getId).toList();
      episodes.removeIf(episode -> existingIds.contains(episode.getId()));
    }
    episodes.forEach(episodeMapper::insert);
  }

  @Transactional
  public int deleteEpisodeById(String id) {
    Episode episode = episodeMapper.selectById(id);
    String audioFilePath = episode.getMediaFilePath();
    if (StringUtils.hasText(audioFilePath)) {
      try {
        Files.deleteIfExists(Paths.get(audioFilePath));
      } catch (Exception e) {
        log.error("Failed to delete audio file: " + audioFilePath, e);
        throw new BusinessException(
            messageSource.getMessage("episode.delete.audio.failed", new Object[]{audioFilePath},
                LocaleContextHolder.getLocale()));
      }
    }
    return episodeMapper.deleteById(id);
  }

  public int deleteEpisodesByChannelId(String channelId) {
    LambdaQueryWrapper<Episode> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Episode::getChannelId, channelId);
    return episodeMapper.delete(wrapper);
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
    queryWrapper.select(Episode::getId, Episode::getDownloadStatus, Episode::getErrorLog);
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
          messageSource.getMessage("episode.not.found", new Object[]{episodeId},
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
   * 找到指定频道已保存的最早的视频
   */
  public Episode findEarliestEpisode(String channelId) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getChannelId, channelId);
    queryWrapper.orderByAsc(Episode::getPublishedAt);
    return episodeMapper.selectList(queryWrapper).get(0);
  }
}
