package top.asimov.pigeon.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import top.asimov.pigeon.model.enums.FeedType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.model.response.FeedConfigUpdateResult;
import top.asimov.pigeon.model.response.FeedPack;
import top.asimov.pigeon.model.response.FeedSaveResult;

@Log4j2
@Service
public class FeedService {

  private final Map<FeedType, FeedHandler<? extends Feed>> handlerRegistry;
  private final MessageSource messageSource;
  private final MediaService mediaService;

  public FeedService(List<FeedHandler<? extends Feed>> feedHandlers,
      MessageSource messageSource, MediaService mediaService) {
    Map<FeedType, FeedHandler<? extends Feed>> registry = new EnumMap<>(FeedType.class);
    feedHandlers.forEach(handler -> registry.put(handler.getType(), handler));
    this.handlerRegistry = Collections.unmodifiableMap(registry);
    this.messageSource = messageSource;
    this.mediaService = mediaService;
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
    for (FeedType type : FeedType.values()) {
      FeedHandler<? extends Feed> handler = handlerRegistry.get(type);
      if (handler != null) {
        List<? extends Feed> list = handler.list();
        for (Feed feed : list) {
          if (StringUtils.hasText(feed.getCustomCoverExt())) {
            String coverUrl = "/media/feed/" + feed.getId() + "/cover";
            if (feed.getLastUpdatedAt() != null) {
              coverUrl += "?v=" + feed.getLastUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC);
            }
            feed.setCustomCoverUrl(coverUrl);
          }
          result.add(feed);
        }
      }
    }
    return result;
  }

  public Feed detail(FeedType type, String id) {
    Feed feed = resolveHandler(type).detail(id);
    if (StringUtils.hasText(feed.getCustomCoverExt())) {
      String coverUrl = "/media/feed/" + feed.getId() + "/cover";
      if (feed.getLastUpdatedAt() != null) {
        coverUrl += "?v=" + feed.getLastUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC);
      }
      feed.setCustomCoverUrl(coverUrl);
    }
    return feed;
  }

  public String getSubscribeUrl(FeedType type, String id) {
    return resolveHandler(type).getSubscribeUrl(id);
  }

  public FeedConfigUpdateResult updateConfig(FeedType type, String id,
      Map<String, Object> payload) {
    return resolveHandler(type).updateConfig(id, payload);
  }

  public void updateCustomCover(FeedType type, String id, MultipartFile file) throws IOException {
    Feed feed = resolveHandler(type).detail(id);
    if (feed == null) {
      throw new BusinessException("Feed not found");
    }
    mediaService.deleteFeedCover(id, feed.getCustomCoverExt());
    String newExtension = mediaService.saveFeedCover(id, file);
    Map<String, Object> payload = new HashMap<>();
    payload.put("customCoverExt", newExtension);
    resolveHandler(type).updateConfig(id, payload);
  }

  public void clearCustomCover(FeedType type, String id) throws IOException {
    Feed feed = resolveHandler(type).detail(id);
    if (feed == null) {
      throw new BusinessException("Feed not found");
    }
    if (StringUtils.hasText(feed.getCustomCoverExt())) {
      mediaService.deleteFeedCover(id, feed.getCustomCoverExt());
      Map<String, Object> payload = new HashMap<>();
      payload.put("customCoverExt", "");
      resolveHandler(type).updateConfig(id, payload);
    }
  }

  public FeedPack<? extends Feed> fetch(Map<String, String> payload) {
    String source = payload == null ? null : payload.getOrDefault("source", null);
    FeedType feedType = guessFeedType(source);
    FeedHandler<? extends Feed> handler = handlerRegistry.get(feedType);
    if (handler == null) {
      throw new BusinessException("error.feed.type.unsupported");
    }
    Map<String, Object> handlerPayload = buildFetchPayload(feedType, source);
    return handler.fetch(handlerPayload);
  }

  public FeedPack<? extends Feed> preview(FeedType type, Map<String, Object> payload) {
    return resolveHandler(type).preview(payload);
  }

  private FeedType guessFeedType(String source) {
    String normalized = source == null ? "" : source.trim().toLowerCase();
    if (normalized.contains("list=") || normalized.contains("playlist")
        || normalized.startsWith("pl") || normalized.startsWith("uu")
        || normalized.startsWith("ol") || normalized.startsWith("ll")) {
      return FeedType.PLAYLIST;
    }
    return FeedType.CHANNEL;
  }

  private Map<String, Object> buildFetchPayload(FeedType type, String source) {
    Map<String, Object> handlerPayload = new HashMap<>();
    if (type == FeedType.PLAYLIST) {
      handlerPayload.put("playlistUrl", source);
    } else if (type == FeedType.CHANNEL) {
      handlerPayload.put("channelUrl", source);
    } else {
      handlerPayload.put("source", source);
    }
    return handlerPayload;
  }

  public FeedSaveResult<? extends Feed> add(FeedType type, Map<String, Object> payload) {
    return resolveHandler(type).add(payload);
  }

  public void delete(FeedType type, String id) {
    Feed feed = resolveHandler(type).detail(id);
    if (feed != null && StringUtils.hasText(feed.getCustomCoverExt())) {
      try {
        mediaService.deleteFeedCover(id, feed.getCustomCoverExt());
      } catch (IOException e) {
        log.error("Failed to delete custom cover for feed " + id, e);
      }
    }
    resolveHandler(type).delete(id);
  }

  public void refresh(FeedType type, String id) {
    resolveHandler(type).refresh(id);
  }

  private <T extends Feed> FeedHandler<T> resolveHandler(FeedType type) {
    FeedHandler<? extends Feed> handler = handlerRegistry.get(type);
    if (handler == null) {
      throw new BusinessException(messageSource
          .getMessage("feed.type.invalid", new Object[]{type.name()},
              LocaleContextHolder.getLocale()));
    }
    @SuppressWarnings("unchecked")
    FeedHandler<T> typedHandler = (FeedHandler<T>) handler;
    return typedHandler;
  }
}
