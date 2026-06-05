package top.asimov.pigeon.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import top.asimov.pigeon.config.MediaPathProperties;
import top.asimov.pigeon.config.StorageProperties;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.handler.MediaFileResourceHandler;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.dto.SubtitleInfo;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.service.storage.S3StorageService;
import top.asimov.pigeon.util.MediaKeyUtil;

@Slf4j
@Service
public class MediaService {

  private final EpisodeMapper episodeMapper;
  private final MessageSource messageSource;
  private final StorageProperties storageProperties;
  private final S3StorageService s3StorageService;
  private final MediaPathProperties mediaPathProperties;
  private final MediaFileResourceHandler mediaFileResourceHandler;

  public MediaService(EpisodeMapper episodeMapper, MessageSource messageSource, StorageProperties storageProperties,
      S3StorageService s3StorageService, MediaPathProperties mediaPathProperties,
      MediaFileResourceHandler mediaFileResourceHandler) {
    this.episodeMapper = episodeMapper;
    this.messageSource = messageSource;
    this.storageProperties = storageProperties;
    this.s3StorageService = s3StorageService;
    this.mediaPathProperties = mediaPathProperties;
    this.mediaFileResourceHandler = mediaFileResourceHandler;
  }

  public boolean isS3ModeEnabled() {
    return storageProperties.isS3Mode();
  }

  public boolean objectKeyExists(String key) {
    if (!StringUtils.hasText(key) || !isS3ModeEnabled()) {
      return false;
    }
    return s3StorageService.keyExists(key);
  }

  public boolean isFilePathAllowed(File file) {
    if (file == null) {
      return false;
    }
    return !isFileInAllowedDirectory(file);
  }

  public String saveFeedCover(String feedId, MultipartFile file) throws IOException {
    String contentType = file.getContentType();
    if (!Arrays.asList("image/jpeg", "image/png", "image/webp").contains(contentType)) {
      throw new IOException("Invalid file type. Only JPG, JPEG, PNG, and WEBP are allowed.");
    }

    String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
    if (!StringUtils.hasText(extension)) {
      throw new IOException("Invalid file extension");
    }

    if (isS3ModeEnabled()) {
      String objectKey = MediaKeyUtil.buildFeedCoverKey(feedId, extension);
      s3StorageService.uploadBytes(file.getBytes(), objectKey, contentType);
      return extension;
    }

    Path coverPath = Path.of(mediaPathProperties.getCoverFilePath());
    if (!Files.exists(coverPath)) {
      Files.createDirectories(coverPath);
    }

    String filename = feedId + "." + extension;
    Path destinationFile = coverPath.resolve(filename);
    Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
    return extension;
  }

  public void deleteFeedCover(String feedId, String extension) throws IOException {
    if (!StringUtils.hasText(feedId) || !StringUtils.hasText(extension)) {
      return;
    }
    if (isS3ModeEnabled()) {
      s3StorageService.deleteObjectQuietly(MediaKeyUtil.buildFeedCoverKey(feedId, extension));
      return;
    }

    Path coverPath = Path.of(mediaPathProperties.getCoverFilePath());
    if (!Files.exists(coverPath)) {
      return;
    }
    Path fileToDelete = coverPath.resolve(feedId + "." + extension);
    if (Files.exists(fileToDelete)) {
      Files.delete(fileToDelete);
    }
  }

  public ResponseEntity<?> buildFeedCoverResponse(String feedId) {
    try {
      if (isS3ModeEnabled()) {
        String key = findFeedCoverObjectKey(feedId);
        if (!StringUtils.hasText(key)) {
          return ResponseEntity.notFound().build();
        }
        return buildRedirectResponse(s3StorageService.generatePresignedGetUrl(
            key, s3StorageService.getDefaultPresignDuration(), null));
      }

      File coverFile = getFeedCover(feedId);
      if (coverFile == null) {
        return ResponseEntity.notFound().build();
      }
      Resource resource = new FileSystemResource(coverFile);
      MediaType mediaType = getMediaTypeByFileName(coverFile.getName());
      return ResponseEntity.ok().contentType(mediaType).body(resource);
    } catch (Exception e) {
      log.warn("[media] feed cover response failed: feedId={}", feedId, e);
      return ResponseEntity.notFound().build();
    }
  }

  public File getFeedCover(String feedId) throws IOException {
    Path coverPath = Path.of(mediaPathProperties.getCoverFilePath());
    if (!Files.exists(coverPath)) {
      return null;
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(coverPath, feedId + ".*")) {
      for (Path entry : stream) {
        return entry.toFile();
      }
    }
    return null;
  }

  public ResponseEntity<?> buildEpisodeMediaFileResponse(String episodeId) {
    if (isS3ModeEnabled()) {
      try {
        Episode episode = requireEpisode(episodeId, false);
        String mediaKey = episode.getMediaFilePath();
        if (!StringUtils.hasText(mediaKey)) {
          return ResponseEntity.notFound().build();
        }
        String filename = extractFileName(mediaKey);
        String disposition = buildContentDisposition("inline", filename);
        return buildRedirectResponse(s3StorageService.generatePresignedGetUrl(
            mediaKey, s3StorageService.getDefaultPresignDuration(), disposition));
      } catch (BusinessException e) {
        return ResponseEntity.notFound().build();
      }
    }

    try {
      File audioFile = getAudioFile(episodeId);
      Resource resource = new FileSystemResource(audioFile);
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition("inline", audioFile.getName()));
      headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
      MediaType mediaType = getMediaTypeByFileName(audioFile.getName());
      // Omit contentLength() to let Spring handle Range requests properly
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(mediaType)
          .body(resource);
    } catch (BusinessException e) {
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("[media] media file response failed: episodeId={}", episodeId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  public void serveEpisodeMediaFile(String episodeId, HttpServletRequest request,
      HttpServletResponse response) throws ServletException, IOException {
    if (isS3ModeEnabled()) {
      ResponseEntity<?> entity = buildEpisodeMediaFileResponse(episodeId);
      sendResponseEntity(entity, response);
      return;
    }

    try {
      File audioFile = getAudioFile(episodeId);
      request.setAttribute("requestedResource", new FileSystemResource(audioFile));
      response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
          buildContentDisposition("inline", audioFile.getName()));
      response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
      mediaFileResourceHandler.handleRequest(request, response);
    } catch (BusinessException e) {
      if (!response.isCommitted()) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
      }
    } catch (Exception e) {
      if (isClientAbortException(e)) {
        log.debug("[media] client aborted media request: episodeId={}", episodeId);
      } else {
        log.error("[media] optimized media file serving failed: episodeId={}", episodeId, e);
        if (!response.isCommitted()) {
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
      }
    }
  }

  private boolean isClientAbortException(Throwable e) {
    if (e == null) {
      return false;
    }
    String className = e.getClass().getSimpleName();
    if ("ClientAbortException".equals(className) || "BrokenPipeException".equals(className)) {
      return true;
    }
    String message = e.getMessage();
    if (message != null && (message.contains("Broken pipe") || message.contains("connection was aborted") || message.contains("Connection reset by peer"))) {
      return true;
    }
    return isClientAbortException(e.getCause());
  }

  private void sendResponseEntity(ResponseEntity<?> entity, HttpServletResponse response)
      throws IOException {
    response.setStatus(entity.getStatusCode().value());
    entity.getHeaders().forEach((name, values) -> {
      for (String value : values) {
        response.addHeader(name, value);
      }
    });
    if (entity.getBody() != null) {
      // For redirects, body is usually null. If not, this is a simplified handler.
      response.getWriter().write(entity.getBody().toString());
    }
  }

  public ResponseEntity<?> buildSubtitleFileResponse(String episodeId, String languageWithExt) {
    if (!StringUtils.hasText(languageWithExt) || !languageWithExt.contains(".")) {
      return ResponseEntity.notFound().build();
    }

    String language = languageWithExt.substring(0, languageWithExt.lastIndexOf('.'));
    String format = languageWithExt.substring(languageWithExt.lastIndexOf('.') + 1).toLowerCase();

    if (isS3ModeEnabled()) {
      try {
        Episode episode = requireEpisode(episodeId, false);
        String key = buildSubtitleKeyForEpisode(episode, language, format);
        String disposition = buildContentDisposition("inline", extractFileName(key));
        return buildRedirectResponse(s3StorageService.generatePresignedGetUrl(
            key, s3StorageService.getDefaultPresignDuration(), disposition));
      } catch (BusinessException e) {
        return ResponseEntity.notFound().build();
      }
    }

    try {
      File subtitleFile = getSubtitleFile(episodeId, language);
      Resource resource = new FileSystemResource(subtitleFile);
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION,
          buildContentDisposition("inline", subtitleFile.getName()));
      headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
      MediaType mediaType = getMediaTypeByFileName(subtitleFile.getName());
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(mediaType)
          .body(resource);
    } catch (BusinessException e) {
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("[media] subtitle file response failed: episodeId={} language={} format={}",
          episodeId, language, format, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  public ResponseEntity<?> buildChaptersFileResponse(String episodeId) {
    if (isS3ModeEnabled()) {
      try {
        Episode episode = requireEpisode(episodeId, false);
        String key = buildChaptersKeyForEpisode(episode);
        String disposition = buildContentDisposition("inline", extractFileName(key));
        return buildRedirectResponse(s3StorageService.generatePresignedGetUrl(
            key, s3StorageService.getDefaultPresignDuration(), disposition));
      } catch (BusinessException e) {
        return ResponseEntity.notFound().build();
      }
    }

    try {
      File chaptersFile = getChaptersFile(episodeId);
      Resource resource = new FileSystemResource(chaptersFile);
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION,
          buildContentDisposition("inline", chaptersFile.getName()));
      headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(MediaType.parseMediaType("application/json;charset=utf-8"))
          .body(resource);
    } catch (BusinessException e) {
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("[media] chapters file response failed: episodeId={}", episodeId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  public ResponseEntity<?> buildEpisodeDownloadToLocalResponse(String episodeId) {
    if (isS3ModeEnabled()) {
      try {
        Episode episode = requireEpisode(episodeId, true);
        String mediaKey = episode.getMediaFilePath();
        if (!StringUtils.hasText(mediaKey)) {
          return ResponseEntity.notFound().build();
        }
        String filename = extractFileName(mediaKey);
        String disposition = buildContentDisposition("attachment", filename);
        return buildRedirectResponse(s3StorageService.generatePresignedGetUrl(
            mediaKey, s3StorageService.getDefaultPresignDuration(), disposition));
      } catch (BusinessException e) {
        log.warn("[media] local download response rejected: episodeId={} reason={}", episodeId,
            e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
      } catch (Exception e) {
        log.error("[media] local download response failed: episodeId={}", episodeId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
      }
    }

    try {
      File mediaFile = getDownloadableMediaFile(episodeId);
      Resource resource = new FileSystemResource(mediaFile);
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION,
          buildContentDisposition("attachment", mediaFile.getName()));
      headers.add("X-Content-Type-Options", "nosniff");
      MediaType mediaType = getMediaTypeByFileName(mediaFile.getName());
      // Omit contentLength() to let Spring handle Range requests properly
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(mediaType)
          .body(resource);
    } catch (BusinessException e) {
      log.warn("[media] local download response rejected: episodeId={} reason={}", episodeId,
          e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    } catch (Exception e) {
      log.error("[media] local download response failed: episodeId={}", episodeId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  public void serveEpisodeDownloadToLocal(String episodeId, HttpServletRequest request,
      HttpServletResponse response) throws ServletException, IOException {
    if (isS3ModeEnabled()) {
      ResponseEntity<?> entity = buildEpisodeDownloadToLocalResponse(episodeId);
      sendResponseEntity(entity, response);
      return;
    }

    try {
      File mediaFile = getDownloadableMediaFile(episodeId);
      request.setAttribute("requestedResource", new FileSystemResource(mediaFile));
      response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
          buildContentDisposition("attachment", mediaFile.getName()));
      response.setHeader("X-Content-Type-Options", "nosniff");
      mediaFileResourceHandler.handleRequest(request, response);
    } catch (BusinessException e) {
      if (!response.isCommitted()) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
      }
    } catch (Exception e) {
      if (isClientAbortException(e)) {
        log.debug("[media] client aborted download request: episodeId={}", episodeId);
      } else {
        log.error("[media] optimized media file download failed: episodeId={}", episodeId, e);
        if (!response.isCommitted()) {
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
      }
    }
  }

  public String resolveMediaUrlForRss(String appBaseUrl, Episode episode) {
    if (episode == null || !StringUtils.hasText(episode.getMediaFilePath())) {
      return null;
    }
    if (isS3ModeEnabled()) {
      return s3StorageService.generatePresignedGetUrl(
          episode.getMediaFilePath(), s3StorageService.getDefaultPresignDuration(), null);
    }
    String suffix = MediaKeyUtil.extractExtension(episode.getMediaFilePath());
    return appBaseUrl + "/media/" + episode.getId() + "." + suffix;
  }

  public long resolveMediaLengthForRss(Episode episode) throws IOException {
    if (episode == null || !StringUtils.hasText(episode.getMediaFilePath())) {
      throw new IOException("No media file for episode");
    }
    if (episode.getMediaSizeBytes() != null && episode.getMediaSizeBytes() > 0) {
      return episode.getMediaSizeBytes();
    }
    if (isS3ModeEnabled()) {
      return s3StorageService.headContentLength(episode.getMediaFilePath());
    }
    return Files.size(Paths.get(episode.getMediaFilePath()));
  }

  public String resolveSubtitleUrlForRss(String appBaseUrl, Episode episode, SubtitleInfo subtitle) {
    if (subtitle == null) {
      return null;
    }
    if (isS3ModeEnabled()) {
      if (!StringUtils.hasText(subtitle.getObjectKey())) {
        return null;
      }
      return s3StorageService.generatePresignedGetUrl(
          subtitle.getObjectKey(), s3StorageService.getDefaultPresignDuration(), null);
    }
    return appBaseUrl + "/media/" + episode.getId() + "/subtitle/"
        + subtitle.getLanguage() + "." + subtitle.getFormat();
  }

  public String resolveChaptersUrlForRss(String appBaseUrl, Episode episode) {
    if (episode == null || !StringUtils.hasText(episode.getMediaFilePath())) {
      return null;
    }
    if (isS3ModeEnabled()) {
      String chapterKey = buildChaptersKeyForEpisode(episode);
      if (!s3StorageService.keyExists(chapterKey)) {
        return null;
      }
      return s3StorageService.generatePresignedGetUrl(
          chapterKey, s3StorageService.getDefaultPresignDuration(), null);
    }
    File chaptersFile = resolveLocalChaptersFile(episode);
    if (chaptersFile == null || !chaptersFile.exists() || !chaptersFile.isFile()) {
      return null;
    }
    return appBaseUrl + "/media/" + episode.getId() + "/chapters.json";
  }

  public List<SubtitleInfo> getAvailableSubtitles(Episode episode) {
    List<SubtitleInfo> subtitles = new ArrayList<>();
    if (episode == null || !StringUtils.hasText(episode.getMediaFilePath())) {
      return subtitles;
    }

    if (isS3ModeEnabled()) {
      String prefix = MediaKeyUtil.buildEpisodeAssetPrefixByMediaKey(episode.getMediaFilePath());
      if (!StringUtils.hasText(prefix)) {
        return subtitles;
      }
      String listPrefix = prefix + ".";
      Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "\\.([^.]+)\\.(vtt|srt)$");
      List<String> keys = s3StorageService.listKeysByPrefix(listPrefix);
      for (String key : keys) {
        Matcher matcher = pattern.matcher(key);
        if (!matcher.matches()) {
          continue;
        }
        subtitles.add(new SubtitleInfo(matcher.group(1), matcher.group(2), key));
      }
      return subtitles;
    }

    String mediaFilePath = episode.getMediaFilePath();
    File mediaFile = new File(mediaFilePath);
    if (!mediaFile.exists() || !mediaFile.getParentFile().exists()) {
      return subtitles;
    }

    String mediaDir = mediaFile.getParent();
    String mediaBaseName = mediaFile.getName().replaceFirst("\\.[^.]+$", "");
    File dir = new File(mediaDir);
    Pattern pattern = Pattern.compile(Pattern.quote(mediaBaseName) + "\\.(\\w+)\\.(vtt|srt)$");

    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        Matcher matcher = pattern.matcher(file.getName());
        if (matcher.find()) {
          subtitles.add(new SubtitleInfo(matcher.group(1), matcher.group(2), null));
        }
      }
    }
    return subtitles;
  }

  public File getAudioFile(String episodeId) throws BusinessException {
    return getEpisodeMediaFileInternal(episodeId, false);
  }

  public File getDownloadableMediaFile(String episodeId) throws BusinessException {
    return getEpisodeMediaFileInternal(episodeId, true);
  }

  public File getSubtitleFile(String episodeId, String language) throws BusinessException {
    Episode episode = requireEpisode(episodeId, false);
    String mediaFilePath = episode.getMediaFilePath();
    if (!StringUtils.hasText(mediaFilePath)) {
      throw new BusinessException(messageSource.getMessage("media.file.not.found",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    File mediaFile = new File(mediaFilePath);
    String mediaDir = mediaFile.getParent();
    String mediaBaseName = mediaFile.getName().replaceFirst("\\.[^.]+$", "");
    File subtitleFile = null;
    for (String ext : new String[]{"vtt", "srt"}) {
      File candidate = new File(mediaDir, mediaBaseName + "." + language + "." + ext);
      if (candidate.exists() && candidate.isFile()) {
        subtitleFile = candidate;
        break;
      }
    }
    if (subtitleFile == null) {
      throw new BusinessException(messageSource.getMessage("subtitle.file.not.found",
          new Object[]{episodeId, language}, LocaleContextHolder.getLocale()));
    }

    if (isFileInAllowedDirectory(subtitleFile)) {
      throw new BusinessException(messageSource.getMessage("media.file.access.denied",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }
    return subtitleFile;
  }

  public File getChaptersFile(String episodeId) throws BusinessException {
    Episode episode = requireEpisode(episodeId, false);
    File chapterFile = resolveLocalChaptersFile(episode);
    if (chapterFile == null || !chapterFile.exists() || !chapterFile.isFile()) {
      throw new BusinessException(messageSource.getMessage("media.file.not.found",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }
    if (isFileInAllowedDirectory(chapterFile)) {
      throw new BusinessException(messageSource.getMessage("media.file.access.denied",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }
    return chapterFile;
  }

  private File getEpisodeMediaFileInternal(String episodeId, boolean requireCompleted)
      throws BusinessException {
    Episode episode = requireEpisode(episodeId, requireCompleted);
    String mediaFilePath = episode.getMediaFilePath();
    if (!StringUtils.hasText(mediaFilePath)) {
      throw new BusinessException(messageSource.getMessage("media.file.not.found",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    File mediaFile = new File(mediaFilePath);
    if (!mediaFile.exists() || !mediaFile.isFile()) {
      throw new BusinessException(messageSource.getMessage("media.file.not.exists",
          new Object[]{mediaFilePath}, LocaleContextHolder.getLocale()));
    }
    if (isFileInAllowedDirectory(mediaFile)) {
      throw new BusinessException(messageSource.getMessage("media.file.access.denied",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }
    return mediaFile;
  }

  private Episode requireEpisode(String episodeId, boolean requireCompleted) throws BusinessException {
    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      throw new BusinessException(messageSource.getMessage("episode.not.found",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }
    if (requireCompleted && !EpisodeStatus.COMPLETED.name().equals(episode.getDownloadStatus())) {
      throw new BusinessException(messageSource.getMessage("episode.download.invalid.status",
          new Object[]{episode.getDownloadStatus()}, LocaleContextHolder.getLocale()));
    }
    return episode;
  }

  private String findFeedCoverObjectKey(String feedId) {
    List<String> keys = s3StorageService.listKeysByPrefix(MediaKeyUtil.buildFeedCoverPrefix(feedId));
    if (keys.isEmpty()) {
      return null;
    }
    return keys.get(0);
  }

  private String buildSubtitleKeyForEpisode(Episode episode, String language, String format)
      throws BusinessException {
    String mediaKey = episode.getMediaFilePath();
    if (!StringUtils.hasText(mediaKey)) {
      throw new BusinessException("invalid media key");
    }
    return MediaKeyUtil.buildEpisodeSubtitleKeyByMediaKey(mediaKey, language, format);
  }

  private String buildChaptersKeyForEpisode(Episode episode) throws BusinessException {
    String mediaKey = episode.getMediaFilePath();
    if (!StringUtils.hasText(mediaKey)) {
      throw new BusinessException("invalid media key");
    }
    return MediaKeyUtil.buildEpisodeChaptersKeyByMediaKey(mediaKey);
  }

  private File resolveLocalChaptersFile(Episode episode) {
    if (episode == null || !StringUtils.hasText(episode.getMediaFilePath())) {
      return null;
    }
    File mediaFile = new File(episode.getMediaFilePath());
    File mediaDir = mediaFile.getParentFile();
    if (mediaDir == null || !mediaDir.exists()) {
      return null;
    }
    String mediaBaseName = mediaFile.getName().replaceFirst("\\.[^.]+$", "");
    return new File(mediaDir, mediaBaseName + ".chapters.json");
  }

  private ResponseEntity<?> buildRedirectResponse(String url) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.LOCATION, url);
    return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
  }

  private String buildContentDisposition(String mode, String filename) {
    String encodedFileName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    return mode + "; filename*=UTF-8''" + encodedFileName;
  }

  private String extractFileName(String pathOrKey) {
    int slash = pathOrKey.lastIndexOf('/');
    return slash >= 0 ? pathOrKey.substring(slash + 1) : pathOrKey;
  }

  private MediaType getMediaTypeByFileName(String fileName) {
    String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    return switch (extension) {
      case "mp3" -> MediaType.valueOf("audio/mpeg");
      case "m4a" -> MediaType.valueOf("audio/aac");
      case "wav" -> MediaType.valueOf("audio/wav");
      case "ogg" -> MediaType.valueOf("audio/ogg");
      case "mp4" -> MediaType.valueOf("video/mp4");
      case "vtt" -> MediaType.valueOf("text/vtt;charset=utf-8");
      case "srt" -> MediaType.valueOf("application/x-subrip;charset=utf-8");
      case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
      case "png" -> MediaType.IMAGE_PNG;
      case "webp" -> MediaType.valueOf("image/webp");
      case "gif" -> MediaType.IMAGE_GIF;
      case "json" -> MediaType.APPLICATION_JSON;
      default -> MediaType.APPLICATION_OCTET_STREAM;
    };
  }

  private boolean isFileInAllowedDirectory(File file) {
    boolean disallowed;
    disallowed = isFileInAllowedDirectory(file, mediaPathProperties.getAudioFilePath());
    disallowed = disallowed && isFileInAllowedDirectory(file, mediaPathProperties.getVideoFilePath());
    disallowed = disallowed && isFileInAllowedDirectory(file, mediaPathProperties.getCoverFilePath());
    return disallowed;
  }

  private boolean isFileInAllowedDirectory(File file, String allowedPath) {
    if (!StringUtils.hasText(allowedPath)) {
      return true;
    }
    try {
      String canonicalFilePath = file.getCanonicalPath();
      String canonicalAllowedPath = new File(allowedPath).getCanonicalPath();
      return !canonicalFilePath.startsWith(canonicalAllowedPath);
    } catch (IOException e) {
      log.error("[media] allowed directory check failed", e);
      return true;
    }
  }
}
