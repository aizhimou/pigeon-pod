package top.asimov.pigeon.service;

import com.rometools.modules.itunes.EntryInformation;
import com.rometools.modules.itunes.EntryInformationImpl;
import com.rometools.modules.itunes.FeedInformation;
import com.rometools.modules.itunes.FeedInformationImpl;
import com.rometools.modules.itunes.types.Duration;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEnclosureImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedOutput;
import jakarta.annotation.PostConstruct;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.constant.Youtube;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.service.MediaService.SubtitleInfo;

@Log4j2
@Service
public class RssService {

  private final ChannelService channelService;
  private final EpisodeService episodeService;
  private final PlaylistService playlistService;
  private final MediaService mediaService;
  private final MessageSource messageSource;

  // Podcasting 2.0 namespace
  private static final Namespace PODCAST_NAMESPACE = Namespace.getNamespace("podcast", "https://podcastindex.org/namespace/1.0");

  // 从 application.properties 读取应用基础 URL
  @Value("${pigeon.base-url}")
  private String appBaseUrl;

  public RssService(ChannelService channelService, EpisodeService episodeService,
      PlaylistService playlistService, MediaService mediaService, MessageSource messageSource) {
    this.channelService = channelService;
    this.episodeService = episodeService;
    this.playlistService = playlistService;
    this.mediaService = mediaService;
    this.messageSource = messageSource;
  }

  @PostConstruct
  private void init() {
    // 在依赖注入完成后，处理 appBaseUrl 值
    if (appBaseUrl != null && appBaseUrl.endsWith("/")) {
      appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
      log.info("已移除 appBaseUrl 末尾的斜杠，处理后的值为: {}", appBaseUrl);
    }
  }

  public String generateRssFeed(String channelIdentification) throws MalformedURLException {
    // 1. 获取频道信息
    Channel channel = channelService.findChannelByIdentification(channelIdentification);
    if (ObjectUtils.isEmpty(channel)) {
      throw new BusinessException(
          messageSource.getMessage("channel.not.found.handler", new Object[]{channelIdentification},
              LocaleContextHolder.getLocale()));
    }

    List<Episode> episodes = episodeService.getEpisodeOrderByPublishDateDesc(channel.getId());
    SyndFeed feed = createFeed(StringUtils.hasText(channel.getCustomTitle()) ?
            channel.getCustomTitle() : channel.getTitle(),
        Youtube.CHANNEL_URL + channel.getId(), channel.getDescription(), getCoverUrl(channel));
    feed.setEntries(buildEntries(episodes));
    return writeFeed(feed);
  }

  public String generatePlaylistRssFeed(String playlistId) throws MalformedURLException {
    Playlist playlist = playlistService.playlistDetail(playlistId);
    if (ObjectUtils.isEmpty(playlist)) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{playlistId},
              LocaleContextHolder.getLocale()));
    }

    List<Episode> episodes = episodeService.getEpisodesByPlaylistId(playlistId);
    SyndFeed feed = createFeed(StringUtils.hasText(playlist.getCustomTitle()) ?
            playlist.getCustomTitle() : playlist.getTitle(),
        Youtube.PLAYLIST_URL + playlist.getId(), playlist.getDescription(), getCoverUrl(playlist));
    feed.setEntries(buildEntries(episodes));
    return writeFeed(feed);
  }

  private SyndFeed createFeed(String title, String link, String description, String coverUrl)
      throws MalformedURLException {
    SyndFeed feed = new SyndFeedImpl();
    feed.setFeedType("rss_2.0");
    feed.setTitle(title);
    feed.setLink(link);
    feed.setDescription(description);
    feed.setPublishedDate(new Date());

    // 添加 Podcasting 2.0 namespace 声明
    Element podcastNamespaceElement = new Element("dummy", PODCAST_NAMESPACE);
    List<Element> foreignMarkup = new ArrayList<>();
    foreignMarkup.add(podcastNamespaceElement);
    feed.setForeignMarkup(foreignMarkup);

    FeedInformation feedInfo = new FeedInformationImpl();
    feedInfo.setAuthor(title);
    feedInfo.setSummary(description);
    if (coverUrl != null) {
      feedInfo.setImage(new URL(coverUrl));
    }
    feed.getModules().add(feedInfo);
    return feed;
  }

  private List<SyndEntry> buildEntries(List<Episode> episodes) {
    List<SyndEntry> entries = new ArrayList<>();
    for (Episode episode : episodes) {
      if (episode == null || episode.getPublishedAt() == null) {
        continue;
      }

      SyndEntry entry = new SyndEntryImpl();
      entry.setTitle(episode.getTitle());
      entry.setLink("https://www.youtube.com/watch?v=" + episode.getId());
      entry.setPublishedDate(
          Date.from(episode.getPublishedAt().toInstant(java.time.ZoneOffset.UTC)));

      SyndContent description = new SyndContentImpl();
      description.setType("text/html");
      String episodeDescription = episode.getDescription();
      description.setValue(episodeDescription == null ? ""
          : episodeDescription.replaceAll("\n", "<br/>"));
      entry.setDescription(description);

      try {
        String mediaFilePath = episode.getMediaFilePath();
        if (mediaFilePath == null) {
          continue;
        }

        SyndEnclosure enclosure = new SyndEnclosureImpl();
        String suffix = getSuffix(mediaFilePath);
        String audioUrl = appBaseUrl + "/media/" + episode.getId() + "." + suffix;
        enclosure.setUrl(audioUrl);
        String enclosureType = StringUtils.hasText(episode.getMediaType()) ?
            episode.getMediaType() : "audio/mpeg";
        enclosure.setType(enclosureType);
        long fileSize = Files.size(Paths.get(mediaFilePath));
        enclosure.setLength(fileSize);
        entry.setEnclosures(Collections.singletonList(enclosure));
      } catch (Exception e) {
        log.error("无法为 episode {} 创建 enclosure: {}", episode.getId(), e.getMessage());
        continue;
      }

      EntryInformation entryInfo = new EntryInformationImpl();
      entryInfo.setSummary(episode.getDescription());
      entryInfo.setDuration(convertToRomeDuration(episode.getDuration()));
      if (episode.getMaxCoverUrl() != null) {
        try {
          entryInfo.setImage(new URL(episode.getMaxCoverUrl()));
        } catch (MalformedURLException e) {
          log.warn("Episode {} cover url is invalid: {}", episode.getId(), e.getMessage());
        }
      }
      entry.getModules().add(entryInfo);

      // 添加 Podcasting 2.0 字幕标签
      addSubtitleElements(entry, episode);

      entries.add(entry);
    }
    return entries;
  }

  /**
   * 为 RSS entry 添加 Podcasting 2.0 字幕标签
   * 
   * @param entry RSS entry
   * @param episode 节目信息
   */
  private void addSubtitleElements(SyndEntry entry, Episode episode) {
    try {
      List<SubtitleInfo> subtitles = mediaService.getAvailableSubtitles(episode);
      if (subtitles.isEmpty()) {
        return;
      }

      List<Element> foreignMarkup = entry.getForeignMarkup();
      if (foreignMarkup == null) {
        foreignMarkup = new ArrayList<>();
        entry.setForeignMarkup(foreignMarkup);
      }

      for (SubtitleInfo subtitle : subtitles) {
        Element transcriptElement = new Element("transcript", PODCAST_NAMESPACE);
        
        // 构建字幕文件 URL
        String subtitleUrl = appBaseUrl + "/media/" + episode.getId() + "/subtitle/" + subtitle.getLanguage();
        transcriptElement.setAttribute("url", subtitleUrl);
        
        // 设置 MIME 类型
        String mimeType = subtitle.getFormat().equals("vtt") ? "text/vtt" : "application/x-subrip";
        transcriptElement.setAttribute("type", mimeType);
        
        // 设置语言代码
        transcriptElement.setAttribute("language", subtitle.getLanguage());
        
        foreignMarkup.add(transcriptElement);
        log.debug("为 episode {} 添加字幕标签: language={}, format={}", 
            episode.getId(), subtitle.getLanguage(), subtitle.getFormat());
      }
    } catch (Exception e) {
      log.warn("为 episode {} 添加字幕标签时出错: {}", episode.getId(), e.getMessage());
    }
  }

  private String writeFeed(SyndFeed feed) {
    try (StringWriter writer = new StringWriter()) {
      SyndFeedOutput output = new SyndFeedOutput();
      output.output(feed, writer);
      return writer.toString();
    } catch (Exception e) {
      throw new RuntimeException(messageSource.getMessage("system.generate.rss.failed",
          null, LocaleContextHolder.getLocale()), e);
    }
  }

  private Duration convertToRomeDuration(String isoDuration) {
    if (isoDuration == null || isoDuration.isBlank()) {
      return new Duration();
    }
    try {
      // 1. 解析为 Java Duration
      java.time.Duration javaDuration = java.time.Duration.parse(isoDuration);
      // 2. 获取总毫秒数
      long millis = javaDuration.toMillis();
      // 3. 创建 ROME Duration
      return new Duration(millis);
    } catch (Exception e) {
      // 如果解析失败，返回一个0时长的对象并记录日志
      log.warn("无法解析时长字符串: '{}', 将返回0时长.", isoDuration, e);
      return new Duration();
    }
  }

  private String getCoverUrl(Feed feed) {
    String customCoverExt = feed.getCustomCoverExt();
    if (StringUtils.hasText(customCoverExt)) {
      String coverUrl = appBaseUrl + "/media/feed/" + feed.getId() + "/cover";
      if (feed.getLastUpdatedAt() != null) {
        coverUrl += "?v=" + feed.getLastUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC);
      }
      return coverUrl;
    }
    return feed.getCoverUrl();
  }

  private String getSuffix(String mediaFilePath) {
    String[] strings = mediaFilePath.split("\\.");
    return strings[strings.length -1 ];
  }
}
