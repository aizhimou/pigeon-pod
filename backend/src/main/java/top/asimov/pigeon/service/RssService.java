package top.asimov.pigeon.service;

import com.rometools.modules.itunes.EntryInformation;
import com.rometools.modules.itunes.EntryInformationImpl;
import com.rometools.modules.itunes.FeedInformation;
import com.rometools.modules.itunes.FeedInformationImpl;
import com.rometools.modules.itunes.ITunes;
import com.rometools.modules.itunes.types.Duration;
import com.rometools.modules.itunes.types.Category;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEnclosureImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedOutput;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.config.AppBaseUrlResolver;
import top.asimov.pigeon.model.constant.Youtube;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.dto.SubtitleInfo;

@Log4j2
@Service
public class RssService {

  private final ChannelService channelService;
  private final EpisodeService episodeService;
  private final PlaylistService playlistService;
  private final MediaService mediaService;
  private final MessageSource messageSource;
  private final AppBaseUrlResolver appBaseUrlResolver;

  private static final Namespace PODCAST_NS = Namespace.getNamespace("podcast", "https://podcastindex.org/namespace/1.0");
  private static final Namespace ITUNES_NS = Namespace.getNamespace("itunes", ITunes.URI);
  private static final String ITUNES_CATEGORY_TEXT = "PigeonPod";
  private static final String ITUNES_EXPLICIT_TEXT = "clean";

  public RssService(ChannelService channelService, EpisodeService episodeService,
      PlaylistService playlistService, MediaService mediaService, MessageSource messageSource,
      AppBaseUrlResolver appBaseUrlResolver) {
    this.channelService = channelService;
    this.episodeService = episodeService;
    this.playlistService = playlistService;
    this.mediaService = mediaService;
    this.messageSource = messageSource;
    this.appBaseUrlResolver = appBaseUrlResolver;
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
    String appBaseUrl = appBaseUrlResolver.requireBaseUrl();
    SyndFeed feed = createFeed(StringUtils.hasText(channel.getCustomTitle()) ?
            channel.getCustomTitle() : channel.getTitle(),
        Youtube.CHANNEL_URL + channel.getId(), channel.getDescription(), getCoverUrl(channel, appBaseUrl));
    feed.setEntries(buildEntries(episodes, appBaseUrl));
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
    String appBaseUrl = appBaseUrlResolver.requireBaseUrl();
    SyndFeed feed = createFeed(StringUtils.hasText(playlist.getCustomTitle()) ?
            playlist.getCustomTitle() : playlist.getTitle(),
        Youtube.PLAYLIST_URL + playlist.getId(), playlist.getDescription(), getCoverUrl(playlist, appBaseUrl));
    feed.setEntries(buildEntries(episodes, appBaseUrl));
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

    FeedInformation feedInfo = new FeedInformationImpl();
    feedInfo.setAuthor(title);
    feedInfo.setSummary(description);
    if (coverUrl != null) {
      feedInfo.setImage(new URL(coverUrl));
    }
    feedInfo.setCategories(Collections.singletonList(new Category(ITUNES_CATEGORY_TEXT)));
    feed.getModules().add(feedInfo);
    return feed;
  }

  private List<SyndEntry> buildEntries(List<Episode> episodes, String appBaseUrl) {
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
        if (!StringUtils.hasText(mediaFilePath)) {
          continue;
        }

        SyndEnclosure enclosure = new SyndEnclosureImpl();
        String audioUrl = mediaService.resolveMediaUrlForRss(appBaseUrl, episode);
        if (!StringUtils.hasText(audioUrl)) {
          continue;
        }
        enclosure.setUrl(audioUrl);
        String enclosureType = StringUtils.hasText(episode.getMediaType()) ?
            episode.getMediaType() : "audio/mpeg";
        enclosure.setType(enclosureType);
        long fileSize = mediaService.resolveMediaLengthForRss(episode);
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
      addSubtitleElements(entry, episode, appBaseUrl);
      addChaptersElement(entry, episode, appBaseUrl);

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
  private void addSubtitleElements(SyndEntry entry, Episode episode, String appBaseUrl) {
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
        Element transcriptElement = new Element("transcript", PODCAST_NS);

        String subtitleUrl = mediaService.resolveSubtitleUrlForRss(appBaseUrl, episode, subtitle);
        if (!StringUtils.hasText(subtitleUrl)) {
          continue;
        }
        transcriptElement.setAttribute("url", subtitleUrl);

        String mimeType = subtitle.getFormat().equals("vtt") ? "text/vtt" : "application/x-subrip";
        transcriptElement.setAttribute("type", mimeType);
        transcriptElement.setAttribute("language", subtitle.getLanguage());
        transcriptElement.setAttribute("rel", "captions");

        foreignMarkup.add(transcriptElement);
        log.debug("为 episode {} 添加字幕标签: language={}, format={}",
            episode.getId(), subtitle.getLanguage(), subtitle.getFormat());
      }
    } catch (Exception e) {
      log.warn("为 episode {} 添加字幕标签时出错: {}", episode.getId(), e.getMessage());
    }
  }

  /**
   * 为 RSS entry 添加 Podcasting 2.0 章节标签。
   *
   * @param entry RSS entry
   * @param episode 节目信息
   */
  private void addChaptersElement(SyndEntry entry, Episode episode, String appBaseUrl) {
    try {
      String chapterUrl = mediaService.resolveChaptersUrlForRss(appBaseUrl, episode);
      if (!StringUtils.hasText(chapterUrl)) {
        return;
      }

      List<Element> foreignMarkup = entry.getForeignMarkup();
      if (foreignMarkup == null) {
        foreignMarkup = new ArrayList<>();
        entry.setForeignMarkup(foreignMarkup);
      }

      Element chaptersElement = new Element("chapters", PODCAST_NS);
      chaptersElement.setAttribute("url", chapterUrl);
      chaptersElement.setAttribute("type", "application/json");
      foreignMarkup.add(chaptersElement);
      log.debug("为 episode {} 添加章节标签", episode.getId());
    } catch (Exception e) {
      log.warn("为 episode {} 添加章节标签时出错: {}", episode.getId(), e.getMessage());
    }
  }

  private String writeFeed(SyndFeed feed) {
    try (StringWriter writer = new StringWriter()) {
      SyndFeedOutput output = new SyndFeedOutput();

      // 1. 不要直接 output 到 writer，而是先生成 JDOM Document 对象
      Document document = output.outputJDom(feed);

      // 2. 获取根节点 (<rss>)
      Element root = document.getRootElement();

      // 3. 【关键步骤】手动向根节点添加 Podcasting 2.0 的 Namespace 声明
      // 只要根节点有了这个声明，JDOM 在输出时就会自动移除子节点中多余的重复声明
      root.addNamespaceDeclaration(PODCAST_NS);

      // 4. 使用 JDOM 的输出工具将修改后的 Document 写出
      applyGlobalItunesTags(document);
      XMLOutputter xmlOutputter = new XMLOutputter(Format.getPrettyFormat());
      xmlOutputter.output(document, writer);

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

  private String getCoverUrl(Feed feed, String appBaseUrl) {
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

  private void applyGlobalItunesTags(Document document) {
    Element root = document.getRootElement();
    Element channel = root.getChild("channel");
    if (channel == null) {
      return;
    }

    root.addNamespaceDeclaration(ITUNES_NS);
    upsertItunesExplicit(channel);
    ensureItunesCategory(channel);
  }

  private void upsertItunesExplicit(Element channel) {
    Element explicitElement = channel.getChild("explicit", ITUNES_NS);
    if (explicitElement == null) {
      explicitElement = new Element("explicit", ITUNES_NS);
      channel.addContent(explicitElement);
    }
    explicitElement.setText(ITUNES_EXPLICIT_TEXT);
  }

  private void ensureItunesCategory(Element channel) {
    List<Element> categoryElements = channel.getChildren("category", ITUNES_NS);
    boolean hasPigeonPodCategory = categoryElements.stream()
        .anyMatch(element -> ITUNES_CATEGORY_TEXT.equals(element.getAttributeValue("text")));
    if (hasPigeonPodCategory) {
      return;
    }

    Element categoryElement = new Element("category", ITUNES_NS);
    categoryElement.setAttribute("text", ITUNES_CATEGORY_TEXT);
    channel.addContent(categoryElement);
  }
}
