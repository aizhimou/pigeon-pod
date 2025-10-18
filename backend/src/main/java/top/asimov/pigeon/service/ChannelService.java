package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.constant.FeedSource;
import top.asimov.pigeon.constant.Youtube;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadTargetType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.model.FeedConfigUpdateResult;
import top.asimov.pigeon.model.FeedPack;
import top.asimov.pigeon.model.FeedSaveResult;
import top.asimov.pigeon.util.FeedEpisodeUtils;
import top.asimov.pigeon.util.YoutubeHelper;
import top.asimov.pigeon.util.YoutubeChannelHelper;

@Log4j2
@Service
public class ChannelService extends AbstractFeedService<Channel> {

  @Value("${pigeon.base-url}")
  private String appBaseUrl;

  private final ChannelMapper channelMapper;
  private final YoutubeHelper youtubeHelper;
  private final YoutubeChannelHelper youtubeChannelHelper;
  private final AccountService accountService;
  private final MessageSource messageSource;

  public ChannelService(ChannelMapper channelMapper, EpisodeService episodeService,
      ApplicationEventPublisher eventPublisher, YoutubeHelper youtubeHelper,
      YoutubeChannelHelper youtubeChannelHelper, AccountService accountService,
      MessageSource messageSource) {
    super(episodeService, eventPublisher, messageSource);
    this.channelMapper = channelMapper;
    this.youtubeHelper = youtubeHelper;
    this.youtubeChannelHelper = youtubeChannelHelper;
    this.accountService = accountService;
    this.messageSource = messageSource;
  }

  @PostConstruct
  private void init() {
    // 在依赖注入完成后，处理 appBaseUrl 值
    if (appBaseUrl != null && appBaseUrl.endsWith("/")) {
      appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
    }
  }

  /**
   * 获取所有频道列表，包含最后上传时间
   *
   * @return 频道列表
   */
  public List<Channel> selectChannelList() {
    return channelMapper.selectChannelsByLastUploadedAt();
  }

  /**
   * 获取频道详情
   *
   * @param id 频道ID
   * @return 频道对象
   */
  public Channel channelDetail(String id) {
    Channel channel = channelMapper.selectById(id);
    if (channel == null) {
      throw new BusinessException(
          messageSource.getMessage("channel.not.found", new Object[]{id},
              LocaleContextHolder.getLocale()));
    }
    channel.setOriginalUrl(Youtube.CHANNEL_URL + channel.getId());
    return channel;
  }

  /**
   * 根据频道ID或handler查找频道
   *
   * @param channelIdentification 频道ID或handler
   * @return 频道对象，如果未找到则返回null
   */
  public Channel findChannelByIdentification(String channelIdentification) {
    // 先按ID查询
    Channel channel = channelMapper.selectById(channelIdentification);
    if (channel != null) {
      return channel;
    }
    // 再按handler查询
    LambdaQueryWrapper<Channel> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Channel::getHandler, channelIdentification);
    return channelMapper.selectOne(queryWrapper);
  }

  /**
   * 获取频道的RSS订阅链接
   *
   * @param channelId 频道ID
   * @return RSS订阅链接
   */
  public String getChannelRssFeedUrl(String channelId) {
    Channel channel = channelMapper.selectById(channelId);
    if (ObjectUtils.isEmpty(channel)) {
      throw new BusinessException(
          messageSource.getMessage("channel.not.found", new Object[]{channelId},
              LocaleContextHolder.getLocale()));
    }
    String apiKey = accountService.getApiKey();
    if (ObjectUtils.isEmpty(apiKey)) {
      throw new BusinessException(
          messageSource.getMessage("channel.api.key.failed", null,
              LocaleContextHolder.getLocale()));
    }
    return appBaseUrl + "/api/rss/" + channelId + ".xml?apikey=" + apiKey;
  }

  /**
   * 更新频道的配置项
   *
   * @param channelId     频道ID
   * @param configuration 包含更新配置的Channel对象
   * @return 更新后的频道对象
   */
  @Transactional
  public FeedConfigUpdateResult updateChannelConfig(String channelId, Channel configuration) {
    FeedConfigUpdateResult result = updateFeedConfig(channelId, configuration);
    log.info("频道 {} 配置更新成功", channelId);
    return result;
  }

  /**
   * 根据用户输入的链接，获取频道信息和预览视频列表
   *
   * @param channelUrl 包含用户输入的频道URL或ID等
   * @return 包含频道详情和预览视频列表的FeedPack对象
   */
  public FeedPack<Channel> fetchChannel(String channelUrl) {
    if (ObjectUtils.isEmpty(channelUrl)) {
      throw new BusinessException(
          messageSource.getMessage("channel.source.empty", null, LocaleContextHolder.getLocale()));
    }

    // 获取频道信息
    com.google.api.services.youtube.model.Channel ytChannel;

    ytChannel = youtubeHelper.fetchYoutubeChannel(channelUrl);

    String ytChannelId = ytChannel.getId();
    Channel fetchedChannel = Channel.builder()
        .id(ytChannelId)
        .title(ytChannel.getSnippet().getTitle())
        .coverUrl(ytChannel.getSnippet().getThumbnails().getHigh().getUrl())
        .description(ytChannel.getSnippet().getDescription())
        .subscribedAt(LocalDateTime.now())
        .source(FeedSource.YOUTUBE.name()) // 目前只支持YouTube
        .originalUrl(channelUrl)
        .build();

    // 获取最近3个视频确认是目标频道
    List<Episode> episodes = youtubeChannelHelper.fetchYoutubeChannelVideos(ytChannelId,
        DEFAULT_FETCH_NUM);
    return FeedPack.<Channel>builder().feed(fetchedChannel).episodes(episodes).build();
  }

  /**
   * 预览频道的最新视频
   *
   * @param channel 包含频道ID和筛选条件的Channel对象
   * @return 预览的视频列表
   */
  public FeedPack<Channel> previewChannel(Channel channel) {
    return previewFeed(channel);
  }

  /**
   * 保存频道并初始化下载最新的视频 当initialEpisodes较大时（> ASYNC_FETCH_NUM），使用异步处理模式
   *
   * @param channel 要保存的频道信息
   * @return 包含频道信息和处理状态的FeedSaveResult对象
   */
  @Transactional
  public FeedSaveResult<Channel> saveChannel(Channel channel) {
    return saveFeed(channel);
  }

  /**
   * 查找所有需要同步的频道
   *
   * @param checkTime 检查时间点，所有 lastSyncTimestamp 早于该时间点的频道都需要同步
   * @return 需要同步的频道列表
   */
  public List<Channel> findDueForSync(LocalDateTime checkTime) {
    List<Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<>());
    return channels.stream()
        .filter(c -> c.getLastSyncTimestamp() == null ||
            c.getLastSyncTimestamp().isBefore(checkTime))
        .collect(Collectors.toList());
  }

  /**
   * 删除频道及其所有关联资源
   *
   * @param channelId 要删除的频道ID
   */
  @Transactional
  public void deleteChannel(String channelId) {
    log.info("开始删除频道: {}", channelId);

    // 1. 获取频道信息，确认存在
    Channel channel = channelMapper.selectById(channelId);
    if (channel == null) {
      throw new BusinessException(
          messageSource.getMessage("channel.not.found", new Object[]{channelId},
              LocaleContextHolder.getLocale()));
    }

    // 2. 查询该频道下所有的episodes
    List<Episode> episodes = episodeService().findByChannelId(channelId);
    log.info("频道 {} 下有 {} 个episodes需要删除", channel.getTitle(), episodes.size());

    // 3. 删除所有episodes对应的音频文件
    deleteAudioFiles(episodes);

    // 4. 从数据库中删除所有episodes记录
    deleteEpisodeRecords(channelId);

    // 5. 删除频道记录
    int result = channelMapper.deleteById(channelId);
    if (result > 0) {
      log.info("频道 {} 删除成功", channel.getTitle());
    } else {
      log.error("频道 {} 删除失败", channel.getTitle());
      throw new BusinessException(
          messageSource.getMessage("channel.delete.failed", null, LocaleContextHolder.getLocale()));
    }
  }

  /**
   * 同步频道，检查是否有新视频并处理
   *
   * @param channel 要同步的频道对象
   */
  @Transactional
  public void refreshChannel(Channel channel) {
    log.info("正在同步频道: {}", channel.getTitle());
    refreshFeed(channel);
  }

  /**
   * 获取并保存初始化的视频
   *
   * @param channelId       频道ID
   * @param initialEpisodes 要获取的初始视频数量
   * @param containKeywords 包含关键词
   * @param excludeKeywords 排除关键词
   * @param minimumDuration 最小时长
   */
  @Transactional
  public void processChannelInitializationAsync(String channelId, Integer initialEpisodes,
      String containKeywords, String excludeKeywords, Integer minimumDuration) {
    log.info("开始异步处理频道初始化，频道ID: {}, 初始视频数量: {}", channelId, initialEpisodes);

    try {
      // 获取频道的初始视频
      List<Episode> episodes = youtubeChannelHelper.fetchYoutubeChannelVideos(
          channelId, initialEpisodes, containKeywords, excludeKeywords, minimumDuration);

      if (episodes.isEmpty()) {
        log.info("频道 {} 没有找到任何视频。", channelId);
        return;
      }

      Channel channel = channelMapper.selectById(channelId);
      FeedEpisodeUtils.findLatestEpisode(episodes).ifPresent(latest -> {
        if (channel != null) {
          channel.setLastSyncVideoId(latest.getId());
          channel.setLastSyncTimestamp(LocalDateTime.now());
          channelMapper.updateById(channel);
        }
      });
      if (channel != null) {
        persistEpisodesAndPublish(channel, episodes);
      } else {
        episodeService().saveEpisodes(episodes);
        FeedEpisodeUtils.publishEpisodesCreated(eventPublisher(), this, episodes);
      }

      log.info("频道 {} 异步初始化完成，保存了 {} 个视频", channelId, episodes.size());

    } catch (Exception e) {
      log.error("频道 {} 异步初始化失败: {}", channelId, e.getMessage(), e);
    }
  }

  @Transactional
  public void processChannelDownloadHistoryAsync(String channelId, Integer episodesToDownload,
      String containKeywords, String excludeKeywords, Integer minimumDuration) {
    Episode earliestEpisode = episodeService().findEarliestEpisode(channelId);
    LocalDateTime earliestTime = earliestEpisode.getPublishedAt().minusSeconds(1);
    log.info("频道 {} 开始重新下载历史节目，准备下载 {} 个视频", channelId, episodesToDownload);
    try {
      // 获取频道指定时间之前指定数量的历史视频
      List<Episode> episodes = youtubeChannelHelper.fetchYoutubeChannelVideosBeforeDate(channelId,
          episodesToDownload, earliestTime,
          containKeywords, excludeKeywords, minimumDuration);

      if (episodes.isEmpty()) {
        log.info("频道 {} 没有找到任何历史视频。", channelId);
        return;
      }

      Channel channel = channelMapper.selectById(channelId);
      if (channel != null) {
        persistEpisodesAndPublish(channel, episodes);
      } else {
        episodeService().saveEpisodes(episodes);
        FeedEpisodeUtils.publishEpisodesCreated(eventPublisher(), this, episodes);
      }

      log.info("频道 {} 历史节目处理完成，新增 {} 个视频", channelId, episodes.size());
    } catch (Exception e) {
      log.error("频道 {} 重新下载历史节目失败: {}", channelId, e.getMessage(), e);
    }
  }

  /**
   * 从数据库中删除指定频道的所有episode记录
   */
  private void deleteEpisodeRecords(String channelId) {
    try {
      int count = episodeService().deleteEpisodesByChannelId(channelId);
      log.info("删除了 {} 条episode记录", count);
    } catch (Exception e) {
      log.error("删除episode记录时出错", e);
      throw new BusinessException(messageSource.getMessage("episode.delete.records.failed",
          new Object[]{e.getMessage()}, LocaleContextHolder.getLocale()));
    }
  }

  /**
   * 删除episodes对应的音频文件，并在删除完所有文件后清理空的频道文件夹
   */
  private void deleteAudioFiles(List<Episode> episodes) {
    java.util.Set<String> channelDirectories = new java.util.HashSet<>();

    // 删除所有音频文件，同时收集频道目录路径
    for (Episode episode : episodes) {
      String mediaFilePath = episode.getMediaFilePath();
      if (!ObjectUtils.isEmpty(mediaFilePath)) {
        try {
          java.io.File audioFile = new java.io.File(mediaFilePath);
          if (audioFile.exists()) {
            boolean deleted = audioFile.delete();
            if (deleted) {
              log.info("音频文件删除成功: {}", mediaFilePath);
              // 收集父目录路径（频道文件夹）
              java.io.File parentDir = audioFile.getParentFile();
              if (parentDir != null) {
                channelDirectories.add(parentDir.getAbsolutePath());
              }
            } else {
              log.warn("音频文件删除失败: {}", mediaFilePath);
            }
          } else {
            log.warn("音频文件不存在: {}", mediaFilePath);
          }
        } catch (Exception e) {
          log.error("删除音频文件时出错: {}", mediaFilePath, e);
        }
      }
    }

    // 检查并删除空的频道文件夹
    for (String channelDirPath : channelDirectories) {
      try {
        java.io.File channelDir = new java.io.File(channelDirPath);
        if (channelDir.exists() && channelDir.isDirectory()) {
          // 检查目录是否为空
          java.io.File[] files = channelDir.listFiles();
          if (files != null && files.length == 0) {
            boolean deleted = channelDir.delete();
            if (deleted) {
              log.info("空的频道文件夹删除成功: {}", channelDirPath);
            } else {
              log.warn("空的频道文件夹删除失败: {}", channelDirPath);
            }
          } else {
            log.info("频道文件夹不为空，保留: {} (包含 {} 个文件/子目录)",
                channelDirPath, files != null ? files.length : 0);
          }
        }
      } catch (Exception e) {
        log.error("检查或删除频道文件夹时出错: {}", channelDirPath, e);
      }
    }
  }

  @Override
  protected Optional<Channel> findFeedById(String feedId) {
    return Optional.ofNullable(channelMapper.selectById(feedId));
  }

  @Override
  protected int updateFeed(Channel feed) {
    return channelMapper.updateById(feed);
  }

  @Override
  protected void insertFeed(Channel feed) {
    channelMapper.insert(feed);
  }

  @Override
  protected DownloadTargetType downloadTargetType() {
    return DownloadTargetType.CHANNEL;
  }

  @Override
  protected List<Episode> fetchEpisodes(Channel feed, int fetchNum) {
    return youtubeChannelHelper.fetchYoutubeChannelVideos(feed.getId(), fetchNum,
        feed.getContainKeywords(), feed.getExcludeKeywords(), feed.getMinimumDuration());
  }

  @Override
  protected List<Episode> fetchIncrementalEpisodes(Channel feed) {
    return youtubeChannelHelper.fetchYoutubeChannelVideos(feed.getId(), MAX_FETCH_NUM,
        feed.getLastSyncVideoId(), feed.getContainKeywords(), feed.getExcludeKeywords(),
        feed.getMinimumDuration());
  }

  @Override
  protected org.apache.logging.log4j.Logger logger() {
    return log;
  }
}
