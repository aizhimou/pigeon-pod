package top.asimov.pigeon.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;
import top.asimov.pigeon.config.MediaPathProperties;
import top.asimov.pigeon.config.OutboundProxyHolder;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.dto.EpisodeCoverStyle;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.model.enums.FeedType;
import top.asimov.pigeon.util.EpisodeCoverRenderer;

/**
 * Serves episode artwork re-drawn according to a feed's {@link EpisodeCoverStyle}.
 *
 * <p>The original thumbnail is never modified. Rendered covers are cached on disk under
 * {@code <cover dir>/episode-styles/<feedId>/<episodeId>-<fingerprint>.jpg}; the fingerprint
 * changes with the style, so podcast apps and the cache both pick up new settings.</p>
 */
@Slf4j
@Service
public class EpisodeCoverService {

  static final String CACHE_DIRECTORY = "episode-styles";
  static final int MAX_COVER_SIDE = 1600;
  static final int PREVIEW_MAX_SIDE = 480;
  /** Size of the small covers shown in PigeonPod's own episode lists. */
  static final int SMALL_COVER_SIDE = 400;
  private static final String SMALL_SUFFIX = "-s";
  private static final int MAX_CONCURRENT_RENDERS = 2;
  private static final Duration RENDER_WAIT = Duration.ofSeconds(30);
  private static final int PREVIEW_SOURCE_CACHE_SIZE = 24;
  private static final Duration REMOTE_CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REMOTE_READ_TIMEOUT = Duration.ofSeconds(20);

  private final ChannelMapper channelMapper;
  private final PlaylistMapper playlistMapper;
  private final EpisodeMapper episodeMapper;
  private final MediaService mediaService;
  private final MediaPathProperties mediaPathProperties;
  private final OutboundProxyHolder outboundProxyHolder;

  private final Semaphore renderPermits = new Semaphore(MAX_CONCURRENT_RENDERS);

  /** Downscaled sources for the settings preview, so dragging a slider doesn't refetch them. */
  private final Map<String, BufferedImage> previewSources = Collections.synchronizedMap(
      new LinkedHashMap<>(PREVIEW_SOURCE_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
          return size() > PREVIEW_SOURCE_CACHE_SIZE;
        }
      });

  public EpisodeCoverService(ChannelMapper channelMapper, PlaylistMapper playlistMapper,
      EpisodeMapper episodeMapper, MediaService mediaService,
      MediaPathProperties mediaPathProperties, OutboundProxyHolder outboundProxyHolder) {
    this.channelMapper = channelMapper;
    this.playlistMapper = playlistMapper;
    this.episodeMapper = episodeMapper;
    this.mediaService = mediaService;
    this.mediaPathProperties = mediaPathProperties;
    this.outboundProxyHolder = outboundProxyHolder;
  }

  /** Whether there is any thumbnail to re-draw for this episode. */
  public boolean hasCoverSource(Episode episode) {
    return StringUtils.hasText(mediaService.getFallbackCoverUrl(episode))
        || mediaService.hasEpisodeCover(episode);
  }

  public String buildCoverUrl(String appBaseUrl, Feed feed, Episode episode,
      EpisodeCoverStyle style) {
    return normalizeBaseUrl(appBaseUrl)
        + "/media/feed/" + feed.getType().name().toLowerCase(Locale.ROOT)
        + "/" + UriUtils.encodePathSegment(feed.getId(), StandardCharsets.UTF_8)
        + "/episodes/" + UriUtils.encodePathSegment(episode.getId(), StandardCharsets.UTF_8)
        + "/cover.jpg?v=" + style.fingerprint()
        + mediaService.customCoverVersionQuery(episode).replace("?v=", "-");
  }

  /** Drops every cached cover of one episode, e.g. after its thumbnail was replaced. */
  public void evictEpisode(String episodeId) {
    previewSources.remove(episodeId);
    Path root = Path.of(mediaPathProperties.getCoverFilePath(), CACHE_DIRECTORY);
    if (!Files.isDirectory(root)) {
      return;
    }
    String prefix = safeFileName(episodeId) + "-";
    try (DirectoryStream<Path> feeds = Files.newDirectoryStream(root, Files::isDirectory)) {
      for (Path feedDirectory : feeds) {
        try (DirectoryStream<Path> covers = Files.newDirectoryStream(feedDirectory,
            entry -> isCachedCoverOf(entry, prefix))) {
          for (Path cover : covers) {
            Files.deleteIfExists(cover);
          }
        }
      }
    } catch (IOException e) {
      log.warn("[media] episode cover cache eviction failed: episodeId={} reason={}", episodeId,
          e.getMessage());
    }
  }

  /** Cache files are {@code <episodeId>-<10 hex fingerprint>.jpg}. */
  private static boolean isCachedCoverOf(Path entry, String prefix) {
    String name = entry.getFileName().toString();
    return name.startsWith(prefix)
        && name.substring(prefix.length()).matches("[0-9a-f]{10}(-s)?\\.jpg");
  }

  public ResponseEntity<?> buildCoverResponse(String type, String feedId, String episodeId) {
    return buildCoverResponse(type, feedId, episodeId, false);
  }

  /** {@code small} serves a reduced copy for PigeonPod's own lists instead of the full cover. */
  public ResponseEntity<?> buildCoverResponse(String type, String feedId, String episodeId,
      boolean small) {
    try {
      Feed feed = findFeed(type, feedId);
      Episode episode = episodeMapper.selectById(episodeId);
      if (feed == null || episode == null) {
        return ResponseEntity.notFound().build();
      }
      EpisodeCoverStyle style = EpisodeCoverStyle.parse(feed.getEpisodeCoverStyle());
      if (!style.requiresRendering()) {
        return mediaService.buildEpisodeCoverResponse(episodeId);
      }
      Path cached = resolveCachedCover(feed, episode, style, small);
      if (cached == null) {
        return mediaService.buildEpisodeCoverResponse(episodeId);
      }
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
      return ResponseEntity.ok()
          .headers(headers)
          .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
          .contentType(MediaType.IMAGE_JPEG)
          .body(new FileSystemResource(cached));
    } catch (Exception e) {
      log.warn("[media] styled episode cover failed, serving original: feedId={} episodeId={} reason={}",
          feedId, episodeId, e.getMessage());
      return mediaService.buildEpisodeCoverResponse(episodeId);
    }
  }

  public byte[] renderPreview(FeedType type, String feedId, String episodeId, String styleJson) {
    if (findFeed(type, feedId) == null) {
      throw new BusinessException("Feed not found");
    }
    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      throw new BusinessException("Episode not found");
    }
    BufferedImage source = previewSources.get(episodeId);
    if (source == null) {
      source = loadSourceImage(episode);
      if (source == null) {
        throw new BusinessException("No artwork available for this episode");
      }
      source = EpisodeCoverRenderer.downscale(source, PREVIEW_MAX_SIDE);
      previewSources.put(episodeId, source);
    }
    try {
      BufferedImage rendered = renderLimited(source, EpisodeCoverStyle.parse(styleJson),
          PREVIEW_MAX_SIDE);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      EpisodeCoverRenderer.writeJpeg(rendered, output);
      return output.toByteArray();
    } catch (IOException e) {
      throw new BusinessException("Failed to render artwork preview");
    }
  }

  /**
   * Renders with at most {@link #MAX_CONCURRENT_RENDERS} at a time: a progressive blur holds
   * several full-size copies of the image, and podcast apps fetch many covers at once.
   */
  private BufferedImage renderLimited(BufferedImage source, EpisodeCoverStyle style, int maxSide)
      throws IOException {
    try {
      if (!renderPermits.tryAcquire(RENDER_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new IOException("Timed out waiting to render episode cover");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting to render episode cover", e);
    }
    try {
      return EpisodeCoverRenderer.render(source, style, maxSide);
    } finally {
      renderPermits.release();
    }
  }

  /** Removes cached covers that no longer match the feed's current style. */
  public void pruneCache(FeedType type, String feedId) {
    Feed feed = findFeed(type, feedId);
    if (feed == null) {
      evictFeed(feedId);
      return;
    }
    EpisodeCoverStyle style = EpisodeCoverStyle.parse(feed.getEpisodeCoverStyle());
    if (!style.requiresRendering()) {
      evictFeed(feedId);
      return;
    }
    String fullSuffix = "-" + style.fingerprint() + ".jpg";
    String smallSuffix = "-" + style.fingerprint() + SMALL_SUFFIX + ".jpg";
    Path directory = feedCacheDirectory(feedId);
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.jpg")) {
      for (Path entry : stream) {
        String name = entry.getFileName().toString();
        if (!name.endsWith(fullSuffix) && !name.endsWith(smallSuffix)) {
          Files.deleteIfExists(entry);
        }
      }
    } catch (IOException e) {
      log.warn("[media] episode cover cache prune failed: feedId={} reason={}", feedId,
          e.getMessage());
    }
  }

  public void evictFeed(String feedId) {
    try {
      FileSystemUtils.deleteRecursively(feedCacheDirectory(feedId));
    } catch (IOException e) {
      log.warn("[media] episode cover cache eviction failed: feedId={} reason={}", feedId,
          e.getMessage());
    }
  }

  private Path resolveCachedCover(Feed feed, Episode episode, EpisodeCoverStyle style,
      boolean small)
      throws IOException {
    Path directory = feedCacheDirectory(feed.getId());
    Path target = directory.resolve(
        safeFileName(episode.getId()) + "-" + style.fingerprint()
            + (small ? SMALL_SUFFIX : "") + ".jpg");
    File localSource = mediaService.isS3ModeEnabled() ? null : findLocalSource(episode);
    if (Files.isRegularFile(target) && (localSource == null
        || localSource.lastModified() <= Files.getLastModifiedTime(target).toMillis())) {
      return target;
    }

    BufferedImage source = loadSourceImage(episode);
    if (source == null) {
      return null;
    }
    BufferedImage rendered = renderLimited(source, style,
        small ? SMALL_COVER_SIDE : MAX_COVER_SIDE);
    Files.createDirectories(directory);
    Path temp = Files.createTempFile(directory, "render-", ".tmp");
    try {
      try (OutputStream output = Files.newOutputStream(temp)) {
        EpisodeCoverRenderer.writeJpeg(rendered, output);
      }
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temp);
    }
    log.info("[media] styled episode cover rendered: feedId={} episodeId={} mode={}",
        feed.getId(), episode.getId(), style.mode());
    return target;
  }

  private BufferedImage loadSourceImage(Episode episode) {
    if (!mediaService.isS3ModeEnabled()) {
      File localSource = findLocalSource(episode);
      if (localSource != null) {
        try {
          BufferedImage image = ImageIO.read(localSource);
          if (image != null) {
            return image;
          }
        } catch (IOException e) {
          log.warn("[media] local episode cover unreadable: episodeId={} reason={}",
              episode.getId(), e.getMessage());
        }
      }
    } else {
      String key = mediaService.findEpisodeThumbnailObjectKey(episode);
      if (StringUtils.hasText(key)) {
        BufferedImage image = readRemote(mediaService.resolveEpisodeCoverUrlForRss(null, episode),
            Proxy.NO_PROXY);
        if (image != null) {
          return image;
        }
      }
    }
    String fallbackUrl = mediaService.getFallbackCoverUrl(episode);
    if (!StringUtils.hasText(fallbackUrl)) {
      return null;
    }
    return readRemote(fallbackUrl, outboundProxyHolder.current().toJavaNetProxy());
  }

  private File findLocalSource(Episode episode) {
    File coverFile = mediaService.resolveLocalCoverFile(episode);
    if (coverFile == null || !coverFile.isFile() || !mediaService.isFilePathAllowed(coverFile)) {
      return null;
    }
    return coverFile;
  }

  private BufferedImage readRemote(String url, Proxy proxy) {
    if (!StringUtils.hasText(url)) {
      return null;
    }
    try {
      URLConnection connection = URI.create(url.trim()).toURL().openConnection(proxy);
      connection.setConnectTimeout((int) REMOTE_CONNECT_TIMEOUT.toMillis());
      connection.setReadTimeout((int) REMOTE_READ_TIMEOUT.toMillis());
      connection.setRequestProperty("User-Agent", "PigeonPod");
      try (InputStream input = connection.getInputStream()) {
        return ImageIO.read(input);
      }
    } catch (IOException | IllegalArgumentException e) {
      log.warn("[media] remote episode cover unreadable: url={} reason={}", url, e.getMessage());
      return null;
    }
  }

  private Feed findFeed(String type, String feedId) {
    FeedType feedType;
    try {
      feedType = FeedType.valueOf(type.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException e) {
      return null;
    }
    return findFeed(feedType, feedId);
  }

  private Feed findFeed(FeedType type, String feedId) {
    return switch (type) {
      case CHANNEL -> channelMapper.selectById(feedId);
      case PLAYLIST -> playlistMapper.selectById(feedId);
    };
  }

  private Path feedCacheDirectory(String feedId) {
    return Path.of(mediaPathProperties.getCoverFilePath(), CACHE_DIRECTORY, safeFileName(feedId));
  }

  private static String safeFileName(String value) {
    return value.replaceAll("[^A-Za-z0-9_-]", "_");
  }

  private static String normalizeBaseUrl(String appBaseUrl) {
    String base = StringUtils.hasText(appBaseUrl) ? appBaseUrl.trim() : "";
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (StringUtils.hasText(base) && !base.startsWith("http://") && !base.startsWith("https://")) {
      base = "http://" + base;
    }
    return base;
  }
}
