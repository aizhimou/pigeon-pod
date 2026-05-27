package top.asimov.pigeon.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.config.DownloadProperties;
import top.asimov.pigeon.config.MediaPathProperties;
import top.asimov.pigeon.config.StorageProperties;
import top.asimov.pigeon.helper.TaskStatusHelper;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.dto.FeedContext;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.model.entity.FeedDefaults;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.enums.CookiePlatform;
import top.asimov.pigeon.model.enums.DownloadType;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.service.CookieService;
import top.asimov.pigeon.service.FeedDefaultsService;
import top.asimov.pigeon.service.SystemConfigService;
import top.asimov.pigeon.service.YtDlpProxyService;
import top.asimov.pigeon.service.YtDlpRuntimeService;
import top.asimov.pigeon.service.storage.S3StorageService;
import top.asimov.pigeon.util.DownloadFileNamePatternUtil;
import top.asimov.pigeon.util.EpisodeRetryPlanner;
import top.asimov.pigeon.util.FeedSourceUrlBuilder;
import top.asimov.pigeon.util.MediaFileNameUtil;
import top.asimov.pigeon.util.MediaKeyUtil;
import top.asimov.pigeon.util.YtDlpArgsValidator;
import top.asimov.pigeon.util.YtDlpOutputTemplateUtil;

@Slf4j
@Component
public class DownloadHandler {

  private static final String SUBTITLE_DISABLED_VALUE = "__DISABLED__";
  private static final String FINAL_FILEPATH_PRINT_PREFIX = "PIGEON_FINAL_FILEPATH:";
  private static final int MAX_FILE_NAME_SUFFIX_ATTEMPTS = 10_000;
  private static final int PROCESS_OUTPUT_TAIL_CHARS = 12_000;

  @Value("${pigeon.ffmpeg-location:}")
  private String ffmpegLocation;
  private final Set<String> reservedOutputBaseNames = ConcurrentHashMap.newKeySet();
  private final EpisodeMapper episodeMapper;
  private final CookieService cookieService;
  private final ChannelMapper channelMapper;
  private final PlaylistMapper playlistMapper;
  private final MessageSource messageSource;
  private final ObjectMapper objectMapper;
  private final YtDlpRuntimeService ytDlpRuntimeService;
  private final FeedDefaultsService feedDefaultsService;
  private final StorageProperties storageProperties;
  private final S3StorageService s3StorageService;
  private final MediaPathProperties mediaPathProperties;
  private final SystemConfigService systemConfigService;
  private final TaskStatusHelper taskStatusHelper;
  private final YtDlpProxyService ytDlpProxyService;
  private final DownloadProperties downloadProperties;

  public DownloadHandler(EpisodeMapper episodeMapper, CookieService cookieService,
      ChannelMapper channelMapper, PlaylistMapper playlistMapper,
      MessageSource messageSource, ObjectMapper objectMapper,
      YtDlpRuntimeService ytDlpRuntimeService, FeedDefaultsService feedDefaultsService,
      StorageProperties storageProperties, S3StorageService s3StorageService,
      MediaPathProperties mediaPathProperties, SystemConfigService systemConfigService,
      TaskStatusHelper taskStatusHelper, YtDlpProxyService ytDlpProxyService,
      DownloadProperties downloadProperties) {
    this.episodeMapper = episodeMapper;
    this.cookieService = cookieService;
    this.channelMapper = channelMapper;
    this.playlistMapper = playlistMapper;
    this.messageSource = messageSource;
    this.objectMapper = objectMapper;
    this.ytDlpRuntimeService = ytDlpRuntimeService;
    this.feedDefaultsService = feedDefaultsService;
    this.storageProperties = storageProperties;
    this.s3StorageService = s3StorageService;
    this.mediaPathProperties = mediaPathProperties;
    this.systemConfigService = systemConfigService;
    this.taskStatusHelper = taskStatusHelper;
    this.ytDlpProxyService = ytDlpProxyService;
    this.downloadProperties = downloadProperties;
  }

  public void download(String episodeId) {
    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      log.error("[download] episode not found: episodeId={}", episodeId);
      return;
    }

    // 在提交阶段已标记为 DOWNLOADING；若因竞态未被设置，此处兜底设置
    if (!EpisodeStatus.DOWNLOADING.name().equals(episode.getDownloadStatus())) {
      episode.setDownloadStatus(EpisodeStatus.DOWNLOADING.name());
      episode.setNextRetryAt(null);
      episode.setFailureNotifiedAt(null);
      episode.setDownloadStartedAt(LocalDateTime.now());
      taskStatusHelper.persistEpisodeWithRetry(episode);
    }

    String tempCookiesFile = null;
    String outputDirPath = null;
    OutputBaseNameReservation outputBaseNameReservation = null;
    List<String> uploadedKeys = new ArrayList<>();

    try {
      FeedContext feedContext = resolveFeedContext(episode);
      CookiePlatform cookiePlatform = CookiePlatform.fromFeedSource(feedContext.source());
      tempCookiesFile = cookieService.createTempCookiesFile(cookiePlatform, "0");
      String feedName = feedContext.title();
      String renderedBaseName = DownloadFileNamePatternUtil.buildBaseName(
          systemConfigService.getCurrentConfig().getDownloadFileNamePattern(),
          feedName,
          episode.getTitle(),
          episode.getId(),
          episode.getPublishedAt());

      // 根据下载类型选择存储根目录，并构建输出目录：{storagePath}/{feed name}/
      outputDirPath = resolveOutputDirectoryPath(feedContext.downloadType(), feedName, episodeId);
      outputBaseNameReservation = reserveOutputBaseName(
          feedContext.downloadType(),
          feedName,
          outputDirPath,
          renderedBaseName,
          episodeId);
      String outputBaseName = outputBaseNameReservation.baseName();

      int exitCode;
      StringBuilder errorLog = new StringBuilder();

      ProcessBuilder processBuilder = getProcessBuilder(episodeId, tempCookiesFile, outputDirPath, outputBaseName,
          feedContext);

      ProcessExecutionResult processResult = runProcessWithTimeout(
          processBuilder, Path.of(outputDirPath), "yt-dlp", episodeId);
      exitCode = processResult.exitCode();
      if (StringUtils.hasText(processResult.outputTail())) {
        errorLog.append(processResult.outputTail());
      }

      // 设置详细的错误日志
      if (exitCode != 0 && !errorLog.isEmpty()) {
        episode.setErrorLog(errorLog.toString());
      }

      // 根据结果更新最终状态
      if (exitCode == 0) {
        DownloadType downloadType = feedContext.downloadType();
        String extension = (downloadType == DownloadType.VIDEO) ? "mp4" : "m4a";
        String mimeType = (downloadType == DownloadType.VIDEO) ? "video/mp4" : "audio/aac";

        Path mediaFilePath = resolveDownloadedMediaPath(
            outputDirPath,
            outputBaseName,
            extension,
            processResult.outputTail());
        String effectiveOutputBaseName = resolveBaseNameFromMediaPath(mediaFilePath, extension);
        if (!outputBaseName.equals(effectiveOutputBaseName)) {
          log.warn("[yt-dlp] final file base name differs from expected: episodeId={} expectedBaseName={} actualBaseName={}",
              episode.getId(), outputBaseName, effectiveOutputBaseName);
        }

        cleanSubtitleFiles(outputDirPath, effectiveOutputBaseName);
        generatePodcastChaptersFile(outputDirPath, effectiveOutputBaseName, episodeId);
        if (downloadType == DownloadType.AUDIO) {
          embedAudioChaptersWithYtDlpBestEffort(episodeId, outputDirPath, effectiveOutputBaseName);
        }
        LightweightMediaValidationResult validationResult = validateDownloadedMediaFile(mediaFilePath);
        if (!validationResult.valid()) {
          cleanupInfoJsonFile(outputDirPath, outputBaseName, episodeId);
          cleanupEpisodeOutputFiles(outputDirPath, outputBaseName, episodeId);
          if (!outputBaseName.equals(effectiveOutputBaseName)) {
            cleanupEpisodeOutputFiles(outputDirPath, effectiveOutputBaseName, episodeId);
          }
          markDownloadFailed(episode,
              composeErrorLog(errorLog.toString(), validationResult.message()));
          log.error("[download] media validation failed: episodeId={} title={} reason={}",
              episode.getId(), episode.getTitle(), validationResult.message());
          return;
        }
        cleanupInfoJsonFile(outputDirPath, outputBaseName, episodeId);
        if (storageProperties.isS3Mode()) {
          long downloadedSize = Files.exists(mediaFilePath) ? Files.size(mediaFilePath) : -1L;
          log.info("[download] local media ready for s3 upload: episodeId={} filePath={} sizeBytes={}",
              episode.getId(), mediaFilePath, downloadedSize);
          S3StorageService.UploadResult uploadResult = uploadEpisodeAssetsToS3(
              episode,
              feedContext.downloadType(),
              feedName,
              effectiveOutputBaseName,
              mediaFilePath,
              extension,
              mimeType,
              uploadedKeys);
          episode.setMediaFilePath(uploadResult.key());
          episode.setMediaSizeBytes(uploadResult.size());
          episode.setMediaEtag(uploadResult.etag());
          log.info("[storage] episode media uploaded: episodeId={} objectKey={} sizeBytes={}",
              episode.getId(), uploadResult.key(), uploadResult.size());
        } else {
          log.info("[download] local media ready: episodeId={} filePath={}",
              episode.getId(), mediaFilePath);
          episode.setMediaFilePath(mediaFilePath.toString());
          episode.setMediaSizeBytes(Files.exists(mediaFilePath) ? Files.size(mediaFilePath) : null);
          episode.setMediaEtag(null);
        }
        episode.setMediaType(mimeType);
        episode.setDownloadStatus(EpisodeStatus.COMPLETED.name());
        episode.setRetryNumber(0);
        episode.setNextRetryAt(null);
        episode.setFailureNotifiedAt(null);
        episode.setDownloadStartedAt(null);
        // 如果之前有错误日志，下载成功后清空
        episode.setErrorLog(null);
        log.info("[download] completed: episodeId={} title={} mediaType={}",
            episode.getId(), episode.getTitle(), mimeType);
      } else {
        markDownloadFailed(episode, errorLog.toString());
        log.error("[download] failed: episodeId={} title={} exitCode={} output={}",
            episode.getId(), episode.getTitle(), exitCode,
            formatProcessOutputForLog(errorLog.toString()));
      }

    } catch (Exception e) {
      log.error("[download] failed with exception: episodeId={} title={}", episode.getId(),
          episode.getTitle(), e);
      markDownloadFailed(episode, e.toString());
      rollbackUploadedKeys(uploadedKeys);
    } finally {
      if (outputBaseNameReservation != null) {
        reservedOutputBaseNames.remove(outputBaseNameReservation.reservationKey());
      }
      // 清理临时cookies文件
      if (tempCookiesFile != null) {
        cookieService.deleteTempCookiesFile(tempCookiesFile);
      }
      if (storageProperties.isS3Mode()) {
        cleanupTempOutputDirectory(outputDirPath);
      }
      // 无论成功失败，都保存最终状态（使用重试机制）
      taskStatusHelper.persistEpisodeWithRetry(episode);
    }
  }

  private OutputBaseNameReservation reserveOutputBaseName(DownloadType downloadType, String feedName,
      String outputDirPath, String renderedBaseName, String episodeId) {
    for (int suffixNumber = 0; suffixNumber < MAX_FILE_NAME_SUFFIX_ATTEMPTS; suffixNumber++) {
      String candidateBaseName = suffixNumber == 0
          ? renderedBaseName
          : MediaFileNameUtil.appendNumericSuffix(renderedBaseName, suffixNumber);
      String reservationKey = buildReservationKey(downloadType, feedName, outputDirPath,
          candidateBaseName);
      if (!reservedOutputBaseNames.add(reservationKey)) {
        continue;
      }
      if (isOutputBaseNameAvailable(downloadType, feedName, outputDirPath, candidateBaseName)) {
        if (suffixNumber > 0) {
          log.info("[download] output base name conflict resolved: episodeId={} baseName={}",
              episodeId, candidateBaseName);
        }
        return new OutputBaseNameReservation(candidateBaseName, reservationKey);
      }
      reservedOutputBaseNames.remove(reservationKey);
    }
    throw new IllegalStateException("unable to allocate unique output base name");
  }

  private boolean isOutputBaseNameAvailable(DownloadType downloadType, String feedName,
      String outputDirPath, String candidateBaseName) {
    String extension = downloadType == DownloadType.VIDEO ? "mp4" : "m4a";
    if (storageProperties.isS3Mode()) {
      String mediaKey = MediaKeyUtil.buildEpisodeMediaKey(downloadType, feedName, candidateBaseName,
          extension);
      return !s3StorageService.keyExists(mediaKey);
    }
    Path outputDir = Path.of(outputDirPath);
    if (!Files.isDirectory(outputDir)) {
      return true;
    }
    try (Stream<Path> stream = Files.list(outputDir)) {
      return stream
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .noneMatch(fileName -> fileName.startsWith(candidateBaseName + "."));
    } catch (IOException e) {
      log.warn("[download] output base name availability check failed: outputDir={} baseName={}",
          outputDirPath, candidateBaseName, e);
      return false;
    }
  }

  private String buildReservationKey(DownloadType downloadType, String feedName, String outputDirPath,
      String candidateBaseName) {
    if (storageProperties.isS3Mode()) {
      return MediaKeyUtil.buildEpisodeDirectory(downloadType, feedName) + candidateBaseName;
    }
    return outputDirPath + candidateBaseName;
  }

  private S3StorageService.UploadResult uploadEpisodeAssetsToS3(Episode episode,
      DownloadType downloadType, String feedName, String outputBaseName, Path mediaFilePath,
      String extension, String mimeType, List<String> uploadedKeys) throws IOException {
    if (!Files.exists(mediaFilePath)) {
      throw new IOException("Downloaded media file does not exist: " + mediaFilePath);
    }
    long uploadStart = System.currentTimeMillis();

    String mediaKey = MediaKeyUtil.buildEpisodeMediaKey(
        downloadType, feedName, outputBaseName, extension);
    log.info("[storage] episode media upload started: episodeId={} filePath={} objectKey={}",
        episode.getId(), mediaFilePath, mediaKey);
    S3StorageService.UploadResult mediaUpload =
        s3StorageService.uploadFile(mediaFilePath, mediaKey, mimeType);
    uploadedKeys.add(mediaKey);
    log.info("[storage] episode media upload completed: episodeId={} objectKey={} sizeBytes={}",
        episode.getId(), mediaKey, mediaUpload.size());

    uploadSubtitleAssetsToS3(mediaKey, outputBaseName, mediaFilePath.getParent(), uploadedKeys);
    uploadChapterAssetToS3(mediaKey, outputBaseName, mediaFilePath.getParent(), uploadedKeys);
    uploadThumbnailAssetsToS3(mediaKey, outputBaseName, mediaFilePath.getParent(), uploadedKeys);
    long elapsedMs = System.currentTimeMillis() - uploadStart;
    log.info("[storage] episode asset upload completed: episodeId={} elapsedMs={} uploadedObjectCount={}",
        episode.getId(), elapsedMs, uploadedKeys.size());
    return mediaUpload;
  }

  private void uploadSubtitleAssetsToS3(String mediaKey, String outputBaseName, Path outputDir,
      List<String> uploadedKeys) throws IOException {
    Pattern subtitlePattern = Pattern.compile(
        "^" + Pattern.quote(outputBaseName) + "\\.([^.]+)\\.(vtt|srt)$");
    try (Stream<Path> stream = Files.list(outputDir)) {
      List<Path> subtitleFiles = stream
          .filter(Files::isRegularFile)
          .filter(path -> subtitlePattern.matcher(path.getFileName().toString()).matches())
          .toList();
      for (Path subtitleFile : subtitleFiles) {
        var matcher = subtitlePattern.matcher(subtitleFile.getFileName().toString());
        if (!matcher.matches()) {
          continue;
        }
        String language = matcher.group(1);
        String format = matcher.group(2);
        String key = MediaKeyUtil.buildEpisodeSubtitleKeyByMediaKey(mediaKey, language, format);
        String contentType = "vtt".equals(format) ? "text/vtt" : "application/x-subrip";
        log.info("[storage] subtitle upload started: filePath={} objectKey={}", subtitleFile, key);
        s3StorageService.uploadFile(subtitleFile, key, contentType);
        uploadedKeys.add(key);
        log.info("[storage] subtitle upload completed: objectKey={}", key);
      }
    }
  }

  private void uploadChapterAssetToS3(String mediaKey, String outputBaseName, Path outputDir,
      List<String> uploadedKeys) {
    Path chaptersFile = outputDir.resolve(outputBaseName + ".chapters.json");
    if (!Files.exists(chaptersFile) || !Files.isRegularFile(chaptersFile)) {
      log.debug("[storage] chapters upload skipped: path={} reason=fileMissing", chaptersFile);
      return;
    }
    String key = MediaKeyUtil.buildEpisodeChaptersKeyByMediaKey(mediaKey);
    log.info("[storage] chapters upload started: filePath={} objectKey={}", chaptersFile, key);
    s3StorageService.uploadFile(chaptersFile, key, "application/json");
    uploadedKeys.add(key);
    log.info("[storage] chapters upload completed: objectKey={}", key);
  }

  private void uploadThumbnailAssetsToS3(String mediaKey, String outputBaseName, Path outputDir,
      List<String> uploadedKeys) throws IOException {
    Pattern thumbnailPattern = Pattern.compile(
        "^" + Pattern.quote(outputBaseName) + "\\.(jpg|jpeg|png|webp)$");
    try (Stream<Path> stream = Files.list(outputDir)) {
      List<Path> files = stream
          .filter(Files::isRegularFile)
          .filter(path -> thumbnailPattern.matcher(path.getFileName().toString()).matches())
          .toList();
      for (Path thumbnailFile : files) {
        var matcher = thumbnailPattern.matcher(thumbnailFile.getFileName().toString());
        if (!matcher.matches()) {
          continue;
        }
        String ext = matcher.group(1);
        String key = MediaKeyUtil.buildEpisodeThumbnailKeyByMediaKey(mediaKey, ext);
        String contentType = ("jpg".equals(ext) || "jpeg".equals(ext))
            ? "image/jpeg"
            : "image/" + ext;
        log.info("[storage] thumbnail upload started: filePath={} objectKey={}", thumbnailFile, key);
        s3StorageService.uploadFile(thumbnailFile, key, contentType);
        uploadedKeys.add(key);
        log.info("[storage] thumbnail upload completed: objectKey={}", key);
      }
    }
  }

  private void rollbackUploadedKeys(List<String> uploadedKeys) {
    if (!storageProperties.isS3Mode() || uploadedKeys.isEmpty()) {
      return;
    }
    for (String key : uploadedKeys) {
      s3StorageService.deleteObjectQuietly(key);
    }
  }

  private String resolveOutputDirectoryPath(DownloadType downloadType, String feedName, String episodeId)
      throws IOException {
    if (storageProperties.isS3Mode()) {
      Path tempRoot = Path.of(storageProperties.getTempDir());
      Path outputDir = tempRoot.resolve("jobs").resolve(episodeId + "-" + System.currentTimeMillis());
      Files.createDirectories(outputDir);
      return outputDir + File.separator;
    }
    String storageRoot = getStorageRoot(downloadType);
    return storageRoot + MediaFileNameUtil.sanitizeFileName(feedName) + File.separator;
  }

  private void cleanupTempOutputDirectory(String outputDirPath) {
    if (!StringUtils.hasText(outputDirPath)) {
      return;
    }
    Path path = Path.of(outputDirPath);
    if (!Files.exists(path)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder()).forEach(current -> {
        try {
          Files.deleteIfExists(current);
        } catch (IOException e) {
          log.warn("[storage] temp path cleanup failed: path={}", current, e);
        }
      });
    } catch (IOException e) {
      log.warn("[storage] temp directory cleanup failed: path={}", outputDirPath, e);
    }
  }

  private String getStorageRoot(DownloadType downloadType) {
    String audioStoragePath = ensureTrailingSeparator(mediaPathProperties.getAudioFilePath());
    String videoStoragePath = ensureTrailingSeparator(mediaPathProperties.getVideoFilePath());
    if (downloadType == DownloadType.VIDEO) {
      return videoStoragePath != null ? videoStoragePath : audioStoragePath;
    }
    return audioStoragePath;
  }

  private String ensureTrailingSeparator(String path) {
    if (!StringUtils.hasText(path)) {
      return path;
    }
    if (path.endsWith(File.separator)) {
      return path;
    }
    return path + File.separator;
  }

  private ProcessBuilder getProcessBuilder(String videoId, String cookiesFilePath, String outputDirPath,
      String outputBaseName, FeedContext feedContext) throws IOException {

    prepareOutputDirectory(outputDirPath);

    YtDlpRuntimeService.YtDlpResolvedRuntime resolvedRuntime =
        ytDlpRuntimeService.resolveExecutionRuntime();
    YtDlpRuntimeService.YtDlpExecutionContext executionContext =
        resolvedRuntime.executionContext();

    log.info("[yt-dlp] runtime selected: mode={} version={} modulePath={}",
        resolvedRuntime.mode(),
        StringUtils.hasText(resolvedRuntime.version()) ? resolvedRuntime.version() : "unknown",
        StringUtils.hasText(resolvedRuntime.modulePath()) ? resolvedRuntime.modulePath() : "unknown");

    List<String> command = new ArrayList<>(executionContext.command());

    addDownloadSpecificOptions(command, feedContext);

    String videoUrl = FeedSourceUrlBuilder.buildEpisodeUrl(feedContext.source(), videoId);
    addCommonOptions(command, outputDirPath, outputBaseName, cookiesFilePath);

    // 添加字幕下载选项
    addSubtitleOptions(command, feedContext);

    addCustomArgs(command, feedContext);
    ytDlpProxyService.appendCurrentProxyArgs(command);
    if (feedContext.downloadType() == DownloadType.AUDIO) {
      // 音频两阶段策略：第一阶段只下载与常规后处理，禁止隐式章节嵌入
      // 避免 --add-metadata 在主阶段触发章节写入导致整单失败
      command.add("--no-embed-chapters");
    }
    addInfoJsonOptions(command);
    addFinalFilepathPrintOption(command);

    command.add(videoUrl);

    log.info("[yt-dlp] command started: command={}", ytDlpProxyService.redactCommand(command));

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(new File(outputDirPath));
    processBuilder.environment().putAll(executionContext.environment());
    return processBuilder;
  }

  private void prepareOutputDirectory(String outputDirPath) {
    File outputDir = new File(outputDirPath);
    if (!outputDir.exists()) {
      if (!outputDir.mkdirs()) {
        // Re-check for existence in case of a race condition
        if (!outputDir.exists()) {
          throw new RuntimeException(messageSource.getMessage("system.create.directory.failed",
              new Object[]{outputDirPath}, LocaleContextHolder.getLocale()));
        }
      }
    }
  }

  private ProcessExecutionResult runProcessWithTimeout(ProcessBuilder processBuilder,
      Path outputDirectory, String label, String episodeId) throws IOException, InterruptedException {
    Path outputLog = Files.createTempFile(outputDirectory, ".pigeon-" + label + "-", ".log");
    processBuilder.redirectErrorStream(true);
    processBuilder.redirectOutput(outputLog.toFile());

    Process process = null;
    try {
      process = processBuilder.start();
      long timeoutMinutes = downloadProperties.getProcessTimeoutMinutes();
      boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
      if (!finished) {
        destroyProcessTree(process);
        String outputTail = readLogTail(outputLog, PROCESS_OUTPUT_TAIL_CHARS);
        log.warn("[yt-dlp] process timed out: label={} episodeId={} timeoutMinutes={} output={}",
            label, episodeId, timeoutMinutes, outputTail);
        String timeoutMessage = label + " process timed out after " + timeoutMinutes + " minutes";
        if (StringUtils.hasText(outputTail)) {
          timeoutMessage = timeoutMessage + System.lineSeparator() + outputTail;
        }
        throw new DownloadProcessTimeoutException(timeoutMessage);
      }

      int exitCode = process.exitValue();
      String outputTail = readLogTail(outputLog, PROCESS_OUTPUT_TAIL_CHARS);
      log.debug("[yt-dlp] process finished: label={} episodeId={} exitCode={}", label, episodeId,
          exitCode);
      return new ProcessExecutionResult(exitCode, outputTail);
    } catch (InterruptedException e) {
      if (process != null) {
        destroyProcessTree(process);
      }
      Thread.currentThread().interrupt();
      throw e;
    } finally {
      try {
        Files.deleteIfExists(outputLog);
      } catch (IOException e) {
        log.debug("[yt-dlp] process output log cleanup failed: path={}", outputLog, e);
      }
    }
  }

  private void destroyProcessTree(Process process) {
    ProcessHandle handle = process.toHandle();
    handle.descendants().forEach(ProcessHandle::destroy);
    handle.destroy();
    try {
      if (process.waitFor(10, TimeUnit.SECONDS)) {
        return;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    handle.descendants()
        .filter(ProcessHandle::isAlive)
        .forEach(ProcessHandle::destroyForcibly);
    if (handle.isAlive()) {
      handle.destroyForcibly();
    }
  }

  private String readLogTail(Path outputLog, int maxChars) {
    if (outputLog == null || maxChars <= 0 || !Files.exists(outputLog)) {
      return "";
    }
    try (RandomAccessFile file = new RandomAccessFile(outputLog.toFile(), "r")) {
      long length = file.length();
      long bytesToRead = Math.min(length, Math.max(maxChars * 4L, 1024L));
      file.seek(Math.max(0L, length - bytesToRead));
      byte[] buffer = new byte[(int) bytesToRead];
      int read = file.read(buffer);
      if (read <= 0) {
        return "";
      }
      String content = new String(buffer, 0, read, StandardCharsets.UTF_8);
      if (content.length() <= maxChars) {
        return content.trim();
      }
      return content.substring(content.length() - maxChars).trim();
    } catch (IOException e) {
      log.debug("[yt-dlp] process output log tail read failed: path={}", outputLog, e);
      return "";
    }
  }

  private Path resolveDownloadedMediaPath(String outputDirPath, String outputBaseName,
      String extension, String processOutput) {
    Path expectedPath = Path.of(outputDirPath, outputBaseName + "." + extension);
    Path printedPath = extractFinalFilepath(outputDirPath, extension, processOutput);
    if (printedPath != null) {
      return printedPath;
    }
    return expectedPath;
  }

  private LightweightMediaValidationResult validateDownloadedMediaFile(Path mediaFilePath) {
    if (mediaFilePath == null) {
      return LightweightMediaValidationResult.failure("media file path is missing");
    }
    if (!Files.exists(mediaFilePath) || !Files.isRegularFile(mediaFilePath)) {
      return LightweightMediaValidationResult.failure("media file does not exist");
    }
    try {
      long fileSize = Files.size(mediaFilePath);
      if (fileSize <= 0) {
        return LightweightMediaValidationResult.failure("media file is empty");
      }
    } catch (IOException e) {
      return LightweightMediaValidationResult.failure("unable to read media file size");
    }
    return LightweightMediaValidationResult.success();
  }

  private Path extractFinalFilepath(String outputDirPath, String extension, String processOutput) {
    if (!StringUtils.hasText(outputDirPath) || !StringUtils.hasText(extension)
        || !StringUtils.hasText(processOutput)) {
      return null;
    }

    Path outputDir = Path.of(outputDirPath).toAbsolutePath().normalize();
    String expectedSuffix = "." + extension;
    String[] lines = processOutput.split("\\R");
    for (int i = lines.length - 1; i >= 0; i--) {
      String line = lines[i].trim();
      if (!line.startsWith(FINAL_FILEPATH_PRINT_PREFIX)) {
        continue;
      }
      String rawPath = line.substring(FINAL_FILEPATH_PRINT_PREFIX.length()).trim();
      if (!StringUtils.hasText(rawPath) || !rawPath.endsWith(expectedSuffix)) {
        continue;
      }
      Path path = Path.of(rawPath);
      if (!path.isAbsolute()) {
        path = outputDir.resolve(path);
      }
      Path normalizedPath = path.toAbsolutePath().normalize();
      if (!normalizedPath.startsWith(outputDir)) {
        log.warn("[yt-dlp] printed output path ignored: path={} reason=outsideOutputDir",
            normalizedPath);
        continue;
      }
      return normalizedPath;
    }
    return null;
  }

  private String resolveBaseNameFromMediaPath(Path mediaFilePath, String extension) {
    if (mediaFilePath == null || mediaFilePath.getFileName() == null
        || !StringUtils.hasText(extension)) {
      return "";
    }
    String fileName = mediaFilePath.getFileName().toString();
    String suffix = "." + extension;
    if (!fileName.endsWith(suffix)) {
      return fileName;
    }
    return fileName.substring(0, fileName.length() - suffix.length());
  }

  private void addDownloadSpecificOptions(List<String> command, FeedContext feedContext) {
    if (feedContext.downloadType() == DownloadType.VIDEO) {
      addVideoOptions(command, feedContext);
    } else if (feedContext.downloadType() == DownloadType.AUDIO) {
      addAudioOptions(command, feedContext);
    } else {
      throw new IllegalArgumentException("Unsupported download type: " + feedContext.downloadType());
    }
  }

  private void addVideoOptions(List<String> command, FeedContext feedContext) {
    String videoEncoding = feedContext.videoEncoding();
    String videoQuality = feedContext.videoQuality();

    if (StringUtils.hasText(videoEncoding)) {
      // 强制编码
      String vcodec = "H265".equalsIgnoreCase(videoEncoding) ? "hevc" :
          "H264".equalsIgnoreCase(videoEncoding) ? "avc1" : videoEncoding;
      String qualityFilter =
          StringUtils.hasText(videoQuality) ? String.format("[height<=%s]", videoQuality) : "";

      String formatString = String.format(
          "bestvideo%s[vcodec^=%s]+bestaudio[ext=m4a]/bestvideo%s[vcodec!^=av01][vcodec!^=vp9]+bestaudio[ext=m4a]/bestvideo%s+bestaudio",
          qualityFilter, vcodec, qualityFilter, qualityFilter);

      command.add("-f");
      command.add(formatString);
      command.add("--recode-video");
      command.add("mp4");
      log.info("[yt-dlp] video options configured: videoEncoding={} videoQuality={}",
          videoEncoding, StringUtils.hasText(videoQuality) ? videoQuality + "p" : "best");

    } else {
      // 非强制编码，下载指定分辨率或最佳
      command.add("-f");
      if (StringUtils.hasText(videoQuality)) {
        String format = String.format(
            "bestvideo[height<=%s]+bestaudio/best[height<=%s]",
            videoQuality, videoQuality
        );
        command.add(format);
        log.info("[yt-dlp] video options configured: videoQuality={}p", videoQuality);
      } else {
        // 不限制质量，下载最佳
        command.add("bestvideo+bestaudio/best");
        log.info("[yt-dlp] video options configured: videoQuality=best");
      }
      command.add("--merge-output-format");
      command.add("mp4");
    }
    command.add("--embed-chapters");
  }

  private void addAudioOptions(List<String> command, FeedContext feedContext) {
    command.add("-x"); // 提取音频
    command.add("--audio-format");
    command.add("aac"); // 指定音频格式为 AAC
    //command.add("-f");
    // 优先下载 aac 格式 (m4a) 来避免转码，如果没有则回退到最佳音质（通常是 opus）
    //command.add("bestaudio[ext=m4a]/bestaudio");

    Integer normalizedQuality = normalizeAudioQuality(feedContext.audioQuality());
    if (normalizedQuality != null) {
      command.add("--audio-quality");
      command.add(String.valueOf(normalizedQuality));
      log.debug("[yt-dlp] audio quality configured: audioQuality={}", normalizedQuality);
    }
    log.info("[yt-dlp] audio options configured: audioFormat=aac");
  }

  private void addCommonOptions(List<String> command, String outputDirPath, String outputBaseName,
      String cookiesFilePath) {

    // downloading EJS script dependencies from npm for deno usage
    command.add("--remote-components");
    command.add("ejs:npm");

    command.add("-o");
    String outputTemplate = YtDlpOutputTemplateUtil.buildMediaOutputTemplate(
        outputDirPath, outputBaseName);
    // 媒体及相关文件输出模板：{outputDir}/{title}.%(ext)s
    command.add(outputTemplate);

    // --- 健壮的缩略图与元数据配置 ---
    command.add("--add-metadata");

    // 下载缩略图到磁盘，并作为封面嵌入媒体文件，统一转换为 JPG
    command.add("--write-thumbnail");
    command.add("--embed-thumbnail");
    command.add("--convert-thumbnails");
    command.add("jpg");

    // 显式指定 FFmpeg 路径（如果配置了），否则交给 PATH 解析
    if (StringUtils.hasText(ffmpegLocation)) {
      command.add("--ffmpeg-location");
      command.add(ffmpegLocation);
      log.debug("[yt-dlp] ffmpeg location configured: path={}", ffmpegLocation);
    }

    // 忽略一些非致命错误
    command.add("--ignore-errors");

    // 如果有cookies文件，添加cookies参数
    if (cookiesFilePath != null) {
      command.add("--cookies");
      command.add(cookiesFilePath);
      log.debug("[yt-dlp] cookies file configured: path={}", cookiesFilePath);
    }

  }

  private void addCustomArgs(List<String> command, FeedContext feedContext) {
    List<String> customArgs = feedContext.ytDlpArgs();
    if (customArgs == null || customArgs.isEmpty()) {
      return;
    }
    command.addAll(customArgs);
  }

  /**
   * 强制写出 info.json，并将基名固定为 %(id)s（最终由 infojson 类型自动补齐为 .info.json）。
   * 放在自定义参数之后，避免被用户参数中的 --no-write-info-json 覆盖。
   */
  private void addInfoJsonOptions(List<String> command) {
    command.add("--write-info-json");
    command.add("-o");
    command.add("infojson:%(id)s");
  }

  private void addFinalFilepathPrintOption(List<String> command) {
    command.add("--print");
    command.add("after_move:" + FINAL_FILEPATH_PRINT_PREFIX + "%(filepath)s");
  }

  /**
   * 添加字幕下载选项到 yt-dlp 命令
   *
   * @param command     yt-dlp 命令列表
   * @param feedContext Feed 上下文信息
   */
  private void addSubtitleOptions(List<String> command, FeedContext feedContext) {
    String subtitleLanguages = feedContext.subtitleLanguages();
    String subtitleFormat = feedContext.subtitleFormat();
    DownloadType downloadType = feedContext.downloadType();

    if (isSubtitleExplicitlyDisabled(subtitleLanguages)) {
      log.info("[yt-dlp] subtitle options skipped: reason=disabled");
      return;
    }

    // 如果配置了字幕语言，则添加字幕下载选项
    if (StringUtils.hasText(subtitleLanguages)) {
      // 写入人工制作的字幕
      command.add("--write-subs");

      // 写入自动生成的字幕作为回退（失败不影响主下载）
      command.add("--write-auto-subs");

      // 指定字幕语言（支持多语言，逗号分隔）
      command.add("--sub-langs");
      command.add(subtitleLanguages);

      // 转换字幕格式（统一为 vtt 或 srt）
      if (StringUtils.hasText(subtitleFormat)) {
        command.add("--convert-subs");
        command.add(subtitleFormat);
      }

      // 仅对 VIDEO 类型嵌入字幕（mp4/mkv/webm 容器支持）
      // AUDIO 类型（m4a）不支持嵌入字幕，会导致 "Encoder not found" 错误
      if (downloadType == DownloadType.VIDEO) {
        command.add("--embed-subs");
        log.info("[yt-dlp] subtitle options configured: languages={} format={} embed=true",
            subtitleLanguages, subtitleFormat);
      } else {
        log.info("[yt-dlp] subtitle options configured: languages={} format={} embed=false",
            subtitleLanguages, subtitleFormat);
      }
    }
  }

  private FeedContext resolveFeedContext(Episode episode) {
    FeedDefaults defaults = feedDefaultsService.getEffectiveFeedDefaults();
    List<String> ytDlpArgs = parseYtDlpArgs(systemConfigService.getYtDlpArgs());

    // 优先从 Playlist 获取配置
    Playlist playlist = playlistMapper.selectLatestByEpisodeId(episode.getId());
    if (playlist != null) {
      return buildFeedContext(playlist, defaults, ytDlpArgs);
    }

    // 从 Channel 获取配置
    Channel channel = channelMapper.selectById(episode.getChannelId());
    if (channel != null) {
      return buildFeedContext(channel, defaults, ytDlpArgs);
    }

    // 兜底返回默认配置
    return new FeedContext(
        "unknown",
        defaults.getDownloadType(),
        defaults.getAudioQuality(),
        defaults.getVideoQuality(),
        defaults.getVideoEncoding(),
        defaults.getSubtitleLanguages(),
        defaults.getSubtitleFormat(),
        ytDlpArgs,
        null);
  }

  private FeedContext buildFeedContext(Feed feed, FeedDefaults defaults, List<String> ytDlpArgs) {
    String title = safeFeedTitle(feed.getTitle());
    DownloadType downloadType = feed.getDownloadType() != null
        ? feed.getDownloadType()
        : defaults.getDownloadType();
    Integer audioQuality = feed.getAudioQuality() != null
        ? feed.getAudioQuality()
        : defaults.getAudioQuality();
    String videoQuality = StringUtils.hasText(feed.getVideoQuality())
        ? feed.getVideoQuality()
        : defaults.getVideoQuality();
    String videoEncoding = StringUtils.hasText(feed.getVideoEncoding())
        ? feed.getVideoEncoding()
        : defaults.getVideoEncoding();
    String feedSubtitleLanguages = trimToNull(feed.getSubtitleLanguages());
    String defaultSubtitleLanguages = trimToNull(defaults.getSubtitleLanguages());
    String subtitleLanguages = StringUtils.hasText(feedSubtitleLanguages)
        ? feedSubtitleLanguages
        : defaultSubtitleLanguages;
    String subtitleFormat = StringUtils.hasText(feed.getSubtitleFormat())
        ? feed.getSubtitleFormat()
        : defaults.getSubtitleFormat();

    return new FeedContext(
        title,
        downloadType,
        audioQuality,
        videoQuality,
        videoEncoding,
        subtitleLanguages,
        subtitleFormat,
        ytDlpArgs,
        feed.getSource()
    );
  }

  private boolean isSubtitleExplicitlyDisabled(String subtitleLanguages) {
    return StringUtils.hasText(subtitleLanguages)
        && SUBTITLE_DISABLED_VALUE.equalsIgnoreCase(subtitleLanguages.trim());
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private List<String> parseYtDlpArgs(String rawYtDlpArgsJson) {
    if (!StringUtils.hasText(rawYtDlpArgsJson)) {
      return List.of();
    }

    try {
      List<String> rawArgs = objectMapper.readValue(rawYtDlpArgsJson, new TypeReference<>() {
      });
      return YtDlpArgsValidator.validate(rawArgs);
    } catch (Exception e) {
      log.warn("[yt-dlp] custom args parse failed, ignoring", e);
      return List.of();
    }
  }

  private String safeFeedTitle(String rawTitle) {
    if (!StringUtils.hasText(rawTitle)) {
      return "unknown";
    }
    return rawTitle;
  }

  private Integer normalizeAudioQuality(Integer rawQuality) {
    if (rawQuality == null) {
      return null;
    }
    int normalized = Math.max(0, Math.min(rawQuality, 10));
    if (normalized != rawQuality) {
      log.warn("[yt-dlp] audio quality normalized: rawQuality={} normalizedQuality={}",
          rawQuality, normalized);
    }
    return normalized;
  }

  private void embedAudioChaptersWithYtDlpBestEffort(String episodeId, String outputDirPath,
      String outputBaseName) {
    Path mediaFilePath = Path.of(outputDirPath, outputBaseName + ".m4a");
    if (!Files.exists(mediaFilePath) || !Files.isRegularFile(mediaFilePath)) {
      log.warn("[yt-dlp] audio chapter embed skipped: episodeId={} filePath={} reason=mediaFileMissing",
          episodeId, mediaFilePath);
      return;
    }

    Path infoJsonPath = resolveInfoJsonPath(outputDirPath, outputBaseName, episodeId);
    if (infoJsonPath == null || !Files.exists(infoJsonPath) || !Files.isRegularFile(infoJsonPath)) {
      log.warn("[yt-dlp] audio chapter embed skipped: episodeId={} reason=infoJsonMissing",
          episodeId);
      return;
    }

    try {
      YtDlpRuntimeService.YtDlpResolvedRuntime resolvedRuntime =
          ytDlpRuntimeService.resolveExecutionRuntime();
      YtDlpRuntimeService.YtDlpExecutionContext executionContext =
          resolvedRuntime.executionContext();

      List<String> command = new ArrayList<>(executionContext.command());
      command.add("--load-info-json");
      command.add(infoJsonPath.toString());
      // 第二阶段仅做章节后处理：
      // 1) 保持与主阶段一致的音频输出策略，确保命中已下载的 m4a
      // 2) 禁止覆盖，避免误触发重新下载
      command.add("-x");
      command.add("--audio-format");
      command.add("m4a");
      command.add("--no-overwrites");
      command.add("--embed-chapters");
      command.add("--no-write-info-json");
      command.add("--no-write-thumbnail");
      command.add("--no-write-subs");
      command.add("--no-write-auto-subs");
      command.add("--no-embed-thumbnail");
      command.add("--no-embed-subs");
      command.add("--no-add-metadata");
      command.add("-o");
      command.add(outputDirPath + outputBaseName + ".%(ext)s");

      if (StringUtils.hasText(ffmpegLocation)) {
        command.add("--ffmpeg-location");
        command.add(ffmpegLocation);
      }

      log.info("[yt-dlp] audio chapter command started: command={}",
          ytDlpProxyService.redactCommand(command));

      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.directory(new File(outputDirPath));
      processBuilder.environment().putAll(executionContext.environment());
      ProcessExecutionResult result = runProcessWithTimeout(
          processBuilder, Path.of(outputDirPath), "yt-dlp-chapters", episodeId);
      int exitCode = result.exitCode();
      if (exitCode == 0) {
        log.info("[yt-dlp] audio chapter embed completed: episodeId={} filePath={}", episodeId,
            mediaFilePath);
      } else {
        log.warn("[yt-dlp] audio chapter embed failed, ignored: episodeId={} exitCode={} output={}",
            episodeId, exitCode, result.outputTail());
      }
    } catch (Exception e) {
      log.warn("[yt-dlp] audio chapter embed failed, ignored: episodeId={} reason={}",
          episodeId, e.getMessage(), e);
    }
  }

  private void cleanupInfoJsonFile(String outputDirPath, String outputBaseName, String episodeId) {
    Path infoJsonPath = resolveInfoJsonPath(outputDirPath, outputBaseName, episodeId);
    if (infoJsonPath == null) {
      return;
    }
    try {
      Files.deleteIfExists(infoJsonPath);
    } catch (IOException e) {
      log.debug("[storage] info json cleanup failed: path={}", infoJsonPath, e);
    }
  }

  private void cleanupEpisodeOutputFiles(String outputDirPath, String outputBaseName,
      String episodeId) {
    if (!StringUtils.hasText(outputDirPath)) {
      return;
    }
    Path outputDir = Path.of(outputDirPath);
    if (!Files.isDirectory(outputDir)) {
      return;
    }

    try (Stream<Path> stream = Files.list(outputDir)) {
      List<Path> filesToDelete = stream
          .filter(Files::isRegularFile)
          .filter(path -> matchesEpisodeOutputFile(path, outputBaseName, episodeId))
          .toList();
      for (Path file : filesToDelete) {
        Files.deleteIfExists(file);
      }
    } catch (IOException e) {
      log.warn("[storage] failed download artifact cleanup failed: episodeId={} outputDir={}",
          episodeId, outputDirPath, e);
    }
  }

  private boolean matchesEpisodeOutputFile(Path path, String outputBaseName, String episodeId) {
    if (path == null || path.getFileName() == null) {
      return false;
    }
    String fileName = path.getFileName().toString();
    if (StringUtils.hasText(outputBaseName) && fileName.startsWith(outputBaseName + ".")) {
      return true;
    }
    return StringUtils.hasText(episodeId) && fileName.equals(episodeId + ".info.json");
  }

  private void markDownloadFailed(Episode episode, String errorLog) {
    if (episode == null) {
      return;
    }
    episode.setMediaFilePath(null);
    episode.setMediaSizeBytes(null);
    episode.setMediaEtag(null);
    episode.setMediaType(null);
    episode.setErrorLog(StringUtils.hasText(errorLog) ? errorLog.trim() : null);
    episode.setDownloadStatus(EpisodeStatus.FAILED.name());
    episode.setDownloadStartedAt(null);
    scheduleNextRetry(episode, LocalDateTime.now());
  }

  private String composeErrorLog(String existingErrorLog, String extraErrorLog) {
    String existing = StringUtils.hasText(existingErrorLog) ? existingErrorLog.trim() : null;
    String extra = StringUtils.hasText(extraErrorLog) ? extraErrorLog.trim() : null;
    if (existing == null) {
      return extra;
    }
    if (extra == null) {
      return existing;
    }
    return existing + System.lineSeparator() + extra;
  }

  private String formatProcessOutputForLog(String output) {
    if (!StringUtils.hasText(output)) {
      return "<empty>";
    }
    return output.trim();
  }

  private void scheduleNextRetry(Episode episode, LocalDateTime failedAt) {
    EpisodeRetryPlanner.scheduleNextRetry(episode, failedAt);
  }

  /**
   * 读取 yt-dlp 生成的 info.json，将章节转换为 Podcasting 2.0 的 chapters.json。
   * 章节文件采用节目文件同前缀命名（basename.chapters.json），与媒体/字幕/缩略图保持一致。
   */
  private void generatePodcastChaptersFile(String outputDirPath, String outputBaseName,
      String episodeId) {
    Path infoJsonPath = resolveInfoJsonPath(outputDirPath, outputBaseName, episodeId);
    Path chaptersJsonPath = Path.of(outputDirPath, outputBaseName + ".chapters.json");

    if (infoJsonPath == null || !Files.exists(infoJsonPath)) {
      log.debug("[yt-dlp] podcast chapters generation skipped: episodeId={} outputDir={} reason=infoJsonMissing",
          episodeId, outputDirPath);
      return;
    }

    try {
      JsonNode infoJson = objectMapper.readTree(infoJsonPath.toFile());
      JsonNode chaptersNode = infoJson.path("chapters");
      if (!chaptersNode.isArray() || chaptersNode.isEmpty()) {
        Files.deleteIfExists(chaptersJsonPath);
        return;
      }

      ArrayNode chapters = objectMapper.createArrayNode();
      int chapterIndex = 1;
      for (JsonNode chapterNode : chaptersNode) {
        Double startSeconds = readSeconds(chapterNode.get("start_time"));
        if (startSeconds == null) {
          continue;
        }

        startSeconds = Math.max(0D, startSeconds);
        ObjectNode chapter = objectMapper.createObjectNode();
        chapter.put("start-time", normalizeChapterSeconds(startSeconds));

        String title = chapterNode.path("title").asText();
        chapter.put("title", StringUtils.hasText(title) ? title : "Chapter " + chapterIndex);

        Double endSeconds = readSeconds(chapterNode.get("end_time"));
        if (endSeconds != null) {
          if (endSeconds > startSeconds) {
            chapter.put("end-time", normalizeChapterSeconds(endSeconds));
          }
        }

        chapters.add(chapter);
        chapterIndex++;
      }

      if (chapters.isEmpty()) {
        Files.deleteIfExists(chaptersJsonPath);
        return;
      }

      ObjectNode root = objectMapper.createObjectNode();
      root.put("version", "1.2.0");
      root.set("chapters", chapters);
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(chaptersJsonPath.toFile(), root);
      log.info("[yt-dlp] podcast chapters generated: episodeId={} path={}", episodeId,
          chaptersJsonPath);
    } catch (Exception e) {
      log.warn("[yt-dlp] podcast chapters generation failed, ignored: episodeId={} reason={}",
          episodeId, e.getMessage(), e);
    }
  }

  private Path resolveInfoJsonPath(String outputDirPath, String outputBaseName, String episodeId) {
    Path byEpisodeId = Path.of(outputDirPath, episodeId + ".info.json");
    if (Files.exists(byEpisodeId)) {
      return byEpisodeId;
    }

    Path byBaseName = Path.of(outputDirPath, outputBaseName + ".info.json");
    if (Files.exists(byBaseName)) {
      return byBaseName;
    }

    try {
      Path outputDir = Path.of(outputDirPath);
      if (!Files.isDirectory(outputDir)) {
        return null;
      }
      try (var stream = Files.list(outputDir)) {
        return stream
            .filter(path -> path.getFileName().toString().endsWith(".info.json"))
            .findFirst()
            .orElse(null);
      }
    } catch (Exception e) {
      log.debug("[yt-dlp] info json scan failed: outputDir={}", outputDirPath, e);
      return null;
    }
  }

  private Double readSeconds(JsonNode valueNode) {
    if (valueNode == null || valueNode.isNull()) {
      return null;
    }
    if (valueNode.isNumber()) {
      return valueNode.asDouble();
    }
    if (valueNode.isTextual()) {
      try {
        return Double.parseDouble(valueNode.asText());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private double normalizeChapterSeconds(double seconds) {
    return Math.round(Math.max(0D, seconds) * 1000D) / 1000D;
  }

  /**
   * 清洗 VTT 字幕文件 1. 移除 Kind: 和 Language: 开头的元数据行 2. 确保 WEBVTT 头部后有空行 * @param outputDirPath 文件所在目录
   *
   * @param outputBaseName 文件名前缀（用于匹配）
   */
  private void cleanSubtitleFiles(String outputDirPath, String outputBaseName) {
    try {
      File dir = new File(outputDirPath);
      // 筛选出该节目的所有 vtt 文件（因为可能有 .zh.vtt, .en.vtt 等多种语言）
      File[] vttFiles = dir.listFiles(
          (d, name) -> name.startsWith(outputBaseName) && name.endsWith(".vtt"));

      if (vttFiles == null || vttFiles.length == 0) {
        return;
      }

      for (File vttFile : vttFiles) {
        Path path = vttFile.toPath();
        // 读取所有行
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> cleanedLines = new ArrayList<>();

        boolean firstLine = true;

        for (String line : lines) {
          // 0. 去除 UTF-8 BOM (如果存在)
          if (line.startsWith("\uFEFF")) {
            line = line.substring(1);
          }

          // 1. 保留 WEBVTT 头
          if (firstLine && line.trim().startsWith("WEBVTT")) {
            cleanedLines.add("WEBVTT "); //
            cleanedLines.add(""); // 强制在 WEBVTT 后加一个空行，解决某些解析器不识别的问题
            firstLine = false;
            continue;
          }

          // 2. 跳过 Kind: 和 Language: 开头的行 (无论后面跟什么语言)
          if (line.trim().startsWith("Kind:") || line.trim().startsWith("Language:")) {
            continue;
          }

          // 3. 避免在 WEBVTT 下面重复添加空行 (防止原来的文件已经有空行导致空行过多)
          if (cleanedLines.size() == 2 && cleanedLines.get(1).isEmpty() && line.trim().isEmpty()) {
            continue;
          }

          cleanedLines.add(line);
        }

        // 写回文件
        Files.write(path, cleanedLines, StandardCharsets.UTF_8);
        log.info("[yt-dlp] subtitle file cleaned: fileName={}", vttFile.getName());
      }
    } catch (Exception e) {
      log.warn("[yt-dlp] subtitle cleanup failed, ignored: reason={}", e.getMessage(), e);
    }
  }

  private record OutputBaseNameReservation(String baseName, String reservationKey) {
  }

  private record ProcessExecutionResult(int exitCode, String outputTail) {
  }

  private record LightweightMediaValidationResult(boolean valid, String message) {

    private static LightweightMediaValidationResult success() {
      return new LightweightMediaValidationResult(true, null);
    }

    private static LightweightMediaValidationResult failure(String message) {
      return new LightweightMediaValidationResult(false, message);
    }
  }

  private static class DownloadProcessTimeoutException extends RuntimeException {

    private DownloadProcessTimeoutException(String message) {
      super(message);
    }
  }

}
