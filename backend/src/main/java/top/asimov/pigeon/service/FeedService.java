package top.asimov.pigeon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.helper.BilibiliResolverHelper;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.enums.FeedType;
import top.asimov.pigeon.model.response.FeedConfigUpdateResult;
import top.asimov.pigeon.model.response.FeedPack;
import top.asimov.pigeon.model.response.FeedSaveResult;
import top.asimov.pigeon.model.response.FeedRefreshResult;

@Log4j2
@Service
public class FeedService {

  private final ChannelService channelService;
  private final PlaylistService playlistService;
  private final MessageSource messageSource;
  private final MediaService mediaService;
  private final ObjectMapper objectMapper;
  private final BilibiliResolverHelper bilibiliResolverHelper;

  public FeedService(ChannelService channelService, PlaylistService playlistService,
      MessageSource messageSource, MediaService mediaService, ObjectMapper objectMapper,
      BilibiliResolverHelper bilibiliResolverHelper) {
    this.channelService = channelService;
    this.playlistService = playlistService;
    this.messageSource = messageSource;
    this.mediaService = mediaService;
    this.objectMapper = objectMapper;
    this.bilibiliResolverHelper = bilibiliResolverHelper;
  }

  public FeedType resolveType(String rawType) {
    if (!StringUtils.hasText(rawType)) {
      throw new BusinessException(messageSource
          .getMessage("feed.type.invalid", new Object[]{rawType},
              LocaleContextHolder.getLocale()));
    }
    try {
      return FeedType.valueOf(rawType.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(messageSource
          .getMessage("feed.type.invalid", new Object[]{rawType},
              LocaleContextHolder.getLocale()));
    }
  }

  public List<Feed> listAll() {
    List<Feed> result = new ArrayList<>();
    for (Feed feed : channelService.selectChannelList()) {
      fillCustomCoverUrl(feed);
      result.add(feed);
    }
    for (Feed feed : playlistService.selectPlaylistList()) {
      fillCustomCoverUrl(feed);
      result.add(feed);
    }
    return result;
  }

  public Feed detail(FeedType type, String id) {
    Feed feed = switch (type) {
      case CHANNEL -> channelService.channelDetail(id);
      case PLAYLIST -> playlistService.playlistDetail(id);
    };
    fillCustomCoverUrl(feed);
    return feed;
  }

  public String getSubscribeUrl(FeedType type, String id) {
    return switch (type) {
      case CHANNEL -> channelService.getChannelRssFeedUrl(id);
      case PLAYLIST -> playlistService.getPlaylistRssFeedUrl(id);
    };
  }

  public List<Episode> fetchHistory(FeedType type, String id) {
    return switch (type) {
      case CHANNEL -> channelService.fetchChannelHistory(id);
      case PLAYLIST -> playlistService.fetchPlaylistHistory(id);
    };
  }

  public FeedConfigUpdateResult updateConfig(FeedType type, String id,
      Map<String, Object> payload) {
    Map<String, Object> safePayload = payload == null ? Map.of() : payload;
    return switch (type) {
      case CHANNEL -> channelService.updateChannelConfig(id, objectMapper.convertValue(safePayload, Channel.class));
      case PLAYLIST -> playlistService.updatePlaylistConfig(id, objectMapper.convertValue(safePayload, Playlist.class));
    };
  }

  public void updateCustomCover(FeedType type, String id, MultipartFile file) throws IOException {
    Feed feed = detail(type, id);
    if (feed == null) {
      throw new BusinessException("Feed not found");
    }
    mediaService.deleteFeedCover(id, feed.getCustomCoverExt());
    String newExtension = mediaService.saveFeedCover(id, file);
    Map<String, Object> payload = new HashMap<>();
    payload.put("customCoverExt", newExtension);
    updateConfig(type, id, payload);
  }

  public void clearCustomCover(FeedType type, String id) throws IOException {
    Feed feed = detail(type, id);
    if (feed == null) {
      throw new BusinessException("Feed not found");
    }
    if (StringUtils.hasText(feed.getCustomCoverExt())) {
      mediaService.deleteFeedCover(id, feed.getCustomCoverExt());
      Map<String, Object> payload = new HashMap<>();
      payload.put("customCoverExt", "");
      updateConfig(type, id, payload);
    }
  }

  public FeedPack<? extends Feed> fetch(Map<String, String> payload) {
    String source = resolveSourceUrl(payload, "source");
    FeedType feedType = guessFeedType(source);
    return switch (feedType) {
      case CHANNEL -> channelService.fetchChannel(source);
      case PLAYLIST -> playlistService.fetchPlaylist(source);
    };
  }

  public FeedPack<? extends Feed> preview(FeedType type, Map<String, Object> payload) {
    Map<String, Object> safePayload = payload == null ? Map.of() : payload;
    return switch (type) {
      case CHANNEL -> channelService.previewChannel(objectMapper.convertValue(safePayload, Channel.class));
      case PLAYLIST -> playlistService.previewPlaylist(objectMapper.convertValue(safePayload, Playlist.class));
    };
  }

  private FeedType guessFeedType(String source) {
    if (bilibiliResolverHelper.isBilibiliInput(source)) {
      return bilibiliResolverHelper.isBilibiliPlaylistInput(source)
          ? FeedType.PLAYLIST
          : FeedType.CHANNEL;
    }
    String normalized = source == null ? "" : source.trim().toLowerCase();
    if (normalized.contains("list=") || normalized.contains("playlist")
        || normalized.startsWith("pl") || normalized.startsWith("uu")
        || normalized.startsWith("ol") || normalized.startsWith("ll")) {
      return FeedType.PLAYLIST;
    }
    return FeedType.CHANNEL;
  }

  public FeedSaveResult<? extends Feed> add(FeedType type, Map<String, Object> payload) {
    Map<String, Object> safePayload = payload == null ? Map.of() : payload;
    return switch (type) {
      case CHANNEL -> channelService.saveChannel(objectMapper.convertValue(safePayload, Channel.class));
      case PLAYLIST -> playlistService.savePlaylist(objectMapper.convertValue(safePayload, Playlist.class));
    };
  }

  public void delete(FeedType type, String id) {
    Feed feed = detail(type, id);
    if (feed != null && StringUtils.hasText(feed.getCustomCoverExt())) {
      try {
        mediaService.deleteFeedCover(id, feed.getCustomCoverExt());
      } catch (IOException e) {
        log.error("Failed to delete custom cover for feed " + id, e);
      }
    }
    switch (type) {
      case CHANNEL -> channelService.deleteChannel(id);
      case PLAYLIST -> playlistService.deletePlaylist(id);
    }
  }

  public FeedRefreshResult refresh(FeedType type, String id) {
    return switch (type) {
      case CHANNEL -> channelService.refreshChannelById(id);
      case PLAYLIST -> playlistService.refreshPlaylistById(id);
    };
  }

  private String resolveSourceUrl(Map<String, ?> request, String fallbackKey) {
    String sourceUrl = asText(request == null ? null : request.get("originalUrl"));
    if (!StringUtils.hasText(sourceUrl)) {
      sourceUrl = asText(request == null ? null : request.get("sourceUrl"));
    }
    if (!StringUtils.hasText(sourceUrl)) {
      sourceUrl = asText(request == null ? null : request.get(fallbackKey));
    }
    if (!StringUtils.hasText(sourceUrl)) {
      throw new BusinessException(messageSource
          .getMessage("feed.source.url.missing", null,
              LocaleContextHolder.getLocale()));
    }
    return sourceUrl;
  }

  private String asText(Object value) {
    return value instanceof String ? (String) value : null;
  }

  private void fillCustomCoverUrl(Feed feed) {
    if (!StringUtils.hasText(feed.getCustomCoverExt())) {
      return;
    }
    String coverUrl = "/media/feed/" + feed.getId() + "/cover";
    if (feed.getLastUpdatedAt() != null) {
      coverUrl += "?v=" + feed.getLastUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC);
    }
    feed.setCustomCoverUrl(coverUrl);
  }
}
