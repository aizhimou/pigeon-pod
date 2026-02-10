package top.asimov.pigeon.handler;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.dto.FeedContext;
import top.asimov.pigeon.model.enums.DownloadType;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.mapper.UserMapper;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.model.entity.FeedDefaults;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.service.CookiesService;
import top.asimov.pigeon.service.FeedDefaultsService;
import top.asimov.pigeon.service.YtDlpRuntimeService;
import top.asimov.pigeon.util.YtDlpArgsValidator;

  @Log4j2
  @Component
  public class DownloadHandler {

  @Value("${pigeon.audio-file-path}")
  private String audioStoragePath;
  @Value("${pigeon.video-file-path}")
  private String videoStoragePath;
  @Value("${pigeon.ffmpeg-location:}")
  private String ffmpegLocation;
  private final EpisodeMapper episodeMapper;
  private final CookiesService cookiesService;
  private final ChannelMapper channelMapper;
  private final PlaylistMapper playlistMapper;
  private final UserMapper userMapper;
  private final MessageSource messageSource;
  private final ObjectMapper objectMapper;
  private final YtDlpRuntimeService ytDlpRuntimeService;
  private final FeedDefaultsService feedDefaultsService;

  public DownloadHandler(EpisodeMapper episodeMapper, CookiesService cookiesService,
      ChannelMapper channelMapper, PlaylistMapper playlistMapper, UserMapper userMapper,
      MessageSource messageSource, ObjectMapper objectMapper,
      YtDlpRuntimeService ytDlpRuntimeService, FeedDefaultsService feedDefaultsService) {
    this.episodeMapper = episodeMapper;
    this.cookiesService = cookiesService;
    this.channelMapper = channelMapper;
    this.playlistMapper = playlistMapper;
    this.userMapper = userMapper;
    this.messageSource = messageSource;
    this.objectMapper = objectMapper;
    this.ytDlpRuntimeService = ytDlpRuntimeService;
    this.feedDefaultsService = feedDefaultsService;
  }

  @PostConstruct
  private void init() {
    // 在依赖注入完成后，处理 audioStoragePath 值
    if (audioStoragePath != null && !audioStoragePath.endsWith("/")) {
      audioStoragePath = audioStoragePath + "/";
      log.info("配置的audioStoragePath值末尾没有/，已调整为: {}", audioStoragePath);
    }
    if (videoStoragePath != null && !videoStoragePath.endsWith("/")) {
      videoStoragePath = videoStoragePath + "/";
      log.info("配置的videoStoragePath值末尾没有/，已调整为: {}", videoStoragePath);
    }
  }

  public void download(String episodeId) {
    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      log.error("找不到对应的Episode，ID: {}", episodeId);
      return;
    }

    // 在提交阶段已标记为 DOWNLOADING；若因竞态未被设置，此处兜底设置
    if (!EpisodeStatus.DOWNLOADING.name().equals(episode.getDownloadStatus())) {
      episode.setDownloadStatus(EpisodeStatus.DOWNLOADING.name());
      updateEpisodeWithRetry(episode);
    }

    String tempCookiesFile = null;

    try {
      // 单用户系统，直接使用默认用户的cookies
      tempCookiesFile = cookiesService.createTempCookiesFile("0");

      FeedContext feedContext = resolveFeedContext(episode);
      String feedName = feedContext.title();
      String safeTitle = getSafeTitle(episode.getTitle());

      // 根据下载类型选择存储根目录，并构建输出目录：{storagePath}/{feed name}/
      String storageRoot = getStorageRoot(feedContext.downloadType());
      String outputDirPath = storageRoot + sanitizeFileName(feedName) + File.separator;

      int exitCode;
      StringBuilder errorLog = new StringBuilder();

      Process process = getProcess(episodeId, tempCookiesFile, outputDirPath, safeTitle, feedContext);

      // 读取输出
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
          BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          log.debug("[yt-dlp-out] {}", line);
        }
        while ((line = errorReader.readLine()) != null) {
          log.warn("[yt-dlp-err] {}", line);
          errorLog.append(line).append("\n");
        }
      }
      exitCode = process.waitFor();

      // 设置详细的错误日志
      if (exitCode != 0 && !errorLog.isEmpty()) {
        episode.setErrorLog(errorLog.toString());
      }

      // 根据结果更新最终状态
      if (exitCode == 0) {
        // 在处理文件路径之前，先清洗字幕文件
        cleanSubtitleFiles(outputDirPath, safeTitle);
        generatePodcastChaptersFile(outputDirPath, safeTitle, episodeId);

        DownloadType downloadType = feedContext.downloadType();
        String extension = (downloadType == DownloadType.VIDEO) ? "mp4" : "m4a";
        String mimeType = (downloadType == DownloadType.VIDEO) ? "video/mp4" : "audio/aac";

        String finalPath = outputDirPath + safeTitle + "." + extension;

        episode.setMediaFilePath(finalPath);
        episode.setMediaType(mimeType);
        episode.setDownloadStatus(EpisodeStatus.COMPLETED.name());
        // 如果之前有错误日志，下载成功后清空
        episode.setErrorLog(null);
        log.info("下载成功: {}", episode.getTitle());
      } else {
        episode.setDownloadStatus(EpisodeStatus.FAILED.name());
        incrementRetryNumber(episode);
        log.error("下载失败，退出码 {}: {}", exitCode, episode.getTitle());
      }

    } catch (Exception e) {
      log.error("下载时发生异常: {}", episode.getTitle(), e);
      episode.setErrorLog(e.toString());
      episode.setDownloadStatus(EpisodeStatus.FAILED.name());
      incrementRetryNumber(episode);
    } finally {
      // 清理临时cookies文件
      if (tempCookiesFile != null) {
        cookiesService.deleteTempCookiesFile(tempCookiesFile);
      }
      // 无论成功失败，都保存最终状态（使用重试机制）
      updateEpisodeWithRetry(episode);
    }
  }

  private String getStorageRoot(DownloadType downloadType) {
    if (downloadType == DownloadType.VIDEO) {
      return videoStoragePath != null ? videoStoragePath : audioStoragePath;
    }
    return audioStoragePath;
  }

  private Process getProcess(String videoId, String cookiesFilePath, String outputDirPath,
      String safeTitle, FeedContext feedContext) throws IOException {

    prepareOutputDirectory(outputDirPath);

    YtDlpRuntimeService.YtDlpResolvedRuntime resolvedRuntime =
        ytDlpRuntimeService.resolveExecutionRuntime();
    YtDlpRuntimeService.YtDlpExecutionContext executionContext =
        resolvedRuntime.executionContext();

    log.info("本次下载使用 yt-dlp 运行时: mode={}, version={}, modulePath={}",
        resolvedRuntime.mode(),
        StringUtils.hasText(resolvedRuntime.version()) ? resolvedRuntime.version() : "unknown",
        StringUtils.hasText(resolvedRuntime.modulePath()) ? resolvedRuntime.modulePath() : "unknown");

    List<String> command = new ArrayList<>();
    command.addAll(executionContext.command());

    addDownloadSpecificOptions(command, feedContext);

    String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
    addCommonOptions(command, outputDirPath, safeTitle, cookiesFilePath);
    
    // 添加字幕下载选项
    addSubtitleOptions(command, feedContext);

    addCustomArgs(command, feedContext);
    addInfoJsonOptions(command);

    command.add(videoUrl);

    log.info("执行 yt-dlp 命令: {}", String.join(" ", command));

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(new File(outputDirPath));
    processBuilder.environment().putAll(executionContext.environment());
    return processBuilder.start();
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

  private void addDownloadSpecificOptions(List<String> command, FeedContext feedContext) {
    if (feedContext.downloadType() == DownloadType.VIDEO) {
      addVideoOptions(command, feedContext);
    } else if (feedContext.downloadType() == DownloadType.AUDIO){
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
      log.info("配置为视频下载模式，强制编码: {}, 最高质量: {}", videoEncoding,
          StringUtils.hasText(videoQuality) ? videoQuality + "p" : "最佳");

    } else {
      // 非强制编码，下载指定分辨率或最佳
      command.add("-f");
      if (StringUtils.hasText(videoQuality)) {
        String format = String.format(
            "bestvideo[height<=%s]+bestaudio/best[height<=%s]",
            videoQuality, videoQuality
        );
        command.add(format);
        log.info("配置为视频下载模式，最高质量: {}p", videoQuality);
      } else {
        // 不限制质量，下载最佳
        command.add("bestvideo+bestaudio/best");
        log.info("配置为视频下载模式，质量: 最佳");
      }
      command.add("--merge-output-format");
      command.add("mp4");
    }
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
      log.debug("使用音频质量参数: {}", normalizedQuality);
    }
    log.info("配置为音频下载模式，优先使用 AAC");
  }

  private void addCommonOptions(List<String> command, String outputDirPath, String safeTitle,
      String cookiesFilePath) {

    // downloading EJS script dependencies from npm for deno usage
    command.add("--remote-components");
    command.add("ejs:npm");

    command.add("-o");
    String outputTemplate = outputDirPath + safeTitle + ".%(ext)s";
    // 媒体及相关文件输出模板：{outputDir}/{title}.%(ext)s
    command.add(outputTemplate);

    // --- 健壮的缩略图与元数据配置 ---
    command.add("--add-metadata");
    command.add("--embed-chapters");

    // 下载缩略图到磁盘，并作为封面嵌入媒体文件，统一转换为 JPG
    command.add("--write-thumbnail");
    command.add("--embed-thumbnail");
    command.add("--convert-thumbnails");
    command.add("jpg");

    // 显式指定 FFmpeg 路径（如果配置了），否则交给 PATH 解析
    if (StringUtils.hasText(ffmpegLocation)) {
      command.add("--ffmpeg-location");
      command.add(ffmpegLocation);
      log.debug("使用自定义 FFmpeg 路径: {}", ffmpegLocation);
    }

    // 忽略一些非致命错误
    command.add("--ignore-errors");

    // 如果有cookies文件，添加cookies参数
    if (cookiesFilePath != null) {
      command.add("--cookies");
      command.add(cookiesFilePath);
      log.debug("使用cookies文件: {}", cookiesFilePath);
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
   * 强制写出 info.json，并固定文件名为 %(id)s.info.json。
   * 放在自定义参数之后，避免被用户参数中的 --no-write-info-json 覆盖。
   */
  private void addInfoJsonOptions(List<String> command) {
    command.add("--write-info-json");
    command.add("-o");
    command.add("infojson:%(id)s.info.json");
  }

  /**
   * 添加字幕下载选项到 yt-dlp 命令
   * 
   * @param command yt-dlp 命令列表
   * @param feedContext Feed 上下文信息
   */
  private void addSubtitleOptions(List<String> command, FeedContext feedContext) {
    String subtitleLanguages = feedContext.subtitleLanguages();
    String subtitleFormat = feedContext.subtitleFormat();
    DownloadType downloadType = feedContext.downloadType();
    
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
        log.info("启用字幕下载并嵌入：语言={}, 格式={}", subtitleLanguages, subtitleFormat);
      } else {
        log.info("启用字幕下载（仅独立文件）：语言={}, 格式={}", subtitleLanguages, subtitleFormat);
      }
    }
  }

  private FeedContext resolveFeedContext(Episode episode) {
    User defaultUser = userMapper.selectById("0");
    FeedDefaults defaults = feedDefaultsService.getEffectiveFeedDefaults();
    List<String> ytDlpArgs = parseYtDlpArgs(defaultUser);
    
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
        ytDlpArgs);
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
    String subtitleLanguages = StringUtils.hasText(feed.getSubtitleLanguages())
        ? feed.getSubtitleLanguages()
        : defaults.getSubtitleLanguages();
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
        ytDlpArgs
    );
  }

  private List<String> parseYtDlpArgs(User user) {
    if (user == null || !StringUtils.hasText(user.getYtDlpArgs())) {
      return List.of();
    }

    try {
      List<String> rawArgs = objectMapper.readValue(user.getYtDlpArgs(),
          new TypeReference<List<String>>() {});
      return YtDlpArgsValidator.validate(rawArgs);
    } catch (Exception e) {
      log.warn("Failed to parse yt-dlp args, ignoring.", e);
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
      log.warn("音频质量值 {} 超出范围，已调整为 {}", rawQuality, normalized);
    }
    return normalized;
  }

  // 处理title，按UTF-8字节长度截断，最多200字节，结尾加...，并去除非法字符
  private String getSafeTitle(String title) {
    if (title == null) {
      return "untitled";
    }
    String clean = sanitizeFileName(title);
    byte[] bytes = clean.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (bytes.length <= 200) {
      return clean;
    }
    // 截断到200字节以内，避免截断多字节字符
    int byteCount = 0;
    int i = 0;
    for (; i < clean.length(); i++) {
      int charBytes = String.valueOf(clean.charAt(i)).getBytes(StandardCharsets.UTF_8).length;
      if (byteCount + charBytes > 200) {
        break;
      }
      byteCount += charBytes;
    }
    return clean.substring(0, i) + "...";
  }


  /**
   * A more robust method to sanitize a string to be a safe filename.
   * It handles Windows/Linux illegal characters, shell metacharacters,
   * control characters, and other problematic edge cases.
   *
   * @param name The raw file name.
   * @return A sanitized, safe file name.
   */
  private String sanitizeFileName(String name) {
    if (name == null || name.trim().isEmpty()) {
      return "untitled";
    }

    // Replace various Unicode dashes (en-dash, em-dash, etc.) with a standard hyphen
    String safe = name.replaceAll("[–—―]", "-");

    // Collapse multiple whitespace characters into a single space for cleaner results
    safe = safe.replaceAll("\\s+", " ").trim();

    // Decompose Unicode characters (e.g., 'é' -> 'e' + '´')
    safe = Normalizer.normalize(safe, Normalizer.Form.NFD);

    // Regex to remove all combining diacritical marks (the accents)
    Pattern accentPattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    safe = accentPattern.matcher(safe).replaceAll("");

    // Replace all remaining illegal filesystem and shell characters with an underscore
    safe = safe.replaceAll("[\\\\/:*?\"<>|;&$`'()!{}]", "_");

    // Replace multiple consecutive underscores with a single one
    safe = safe.replaceAll("_+", "_");

    // Trim any leading or trailing underscores, dots, or spaces that might remain
    safe = safe.replaceAll("^[_.\\s]+|[_.\\s]+$", "");

    // If the name is now empty, provide a default
    if (safe.isEmpty()) {
      return "sanitized_name";
    }

    return safe;
  }

  /**
   * 使用重试机制更新 Episode 状态，处理可能的数据库锁定冲突
   *
   * @param episode 要更新的 Episode
   */
  @Retryable(
      retryFor = {Exception.class},
      maxAttempts = 5,
      backoff = @Backoff(delay = 200, multiplier = 2, maxDelay = 2000))
  private void updateEpisodeWithRetry(Episode episode) {
    try {
      episodeMapper.updateById(episode);
      log.debug("成功更新 Episode 状态: {} -> {}", episode.getId(), episode.getDownloadStatus());
    } catch (Exception e) {
      log.warn("更新 Episode 状态失败，将重试: {} -> {}, 错误: {}",
          episode.getId(), episode.getDownloadStatus(), e.getMessage());
      throw e; // 重新抛出异常以触发重试
    }
  }

  private void incrementRetryNumber(Episode episode) {
    Integer current = episode.getRetryNumber();
    int nextRetry = current == null ? 1 : current + 1;
    episode.setRetryNumber(nextRetry);
  }

  /**
   * 读取 yt-dlp 生成的 info.json，将章节转换为 Podcasting 2.0 的 chapters.json。
   * 章节文件采用节目文件同前缀命名（safeTitle.chapters.json），与媒体/字幕/缩略图保持一致。
   */
  private void generatePodcastChaptersFile(String outputDirPath, String safeTitle, String episodeId) {
    Path infoJsonPath = resolveInfoJsonPath(outputDirPath, safeTitle, episodeId);
    Path chaptersJsonPath = Path.of(outputDirPath, safeTitle + ".chapters.json");

    if (infoJsonPath == null || !Files.exists(infoJsonPath)) {
      log.debug("未找到 info.json，跳过章节生成: episodeId={}, outputDir={}", episodeId, outputDirPath);
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
        chapter.put("startTime", normalizeChapterSeconds(startSeconds));

        String title = chapterNode.path("title").asText();
        chapter.put("title", StringUtils.hasText(title) ? title : "Chapter " + chapterIndex);

        Double endSeconds = readSeconds(chapterNode.get("end_time"));
        if (endSeconds != null) {
          if (endSeconds > startSeconds) {
            chapter.put("endTime", normalizeChapterSeconds(endSeconds));
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
      log.info("已生成章节文件: {}", chaptersJsonPath);
    } catch (Exception e) {
      log.warn("生成章节文件失败 (不影响主流程): {}", e.getMessage());
    } finally {
      try {
        Files.deleteIfExists(infoJsonPath);
      } catch (IOException e) {
        log.debug("清理 info.json 失败: {}", infoJsonPath, e);
      }
    }
  }

  private Path resolveInfoJsonPath(String outputDirPath, String safeTitle, String episodeId) {
    Path byEpisodeId = Path.of(outputDirPath, episodeId + ".info.json");
    if (Files.exists(byEpisodeId)) {
      return byEpisodeId;
    }

    Path bySafeTitle = Path.of(outputDirPath, safeTitle + ".info.json");
    if (Files.exists(bySafeTitle)) {
      return bySafeTitle;
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
      log.debug("扫描 info.json 文件失败: {}", outputDirPath, e);
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
   * 清洗 VTT 字幕文件
   * 1. 移除 Kind: 和 Language: 开头的元数据行
   * 2. 确保 WEBVTT 头部后有空行
   * * @param outputDirPath 文件所在目录
   * @param safeTitle 文件名前缀（用于匹配）
   */
  private void cleanSubtitleFiles(String outputDirPath, String safeTitle) {
    try {
      File dir = new File(outputDirPath);
      // 筛选出该节目的所有 vtt 文件（因为可能有 .zh.vtt, .en.vtt 等多种语言）
      File[] vttFiles = dir.listFiles((d, name) -> name.startsWith(safeTitle) && name.endsWith(".vtt"));

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
        log.info("已清洗字幕文件: {}", vttFile.getName());
      }
    } catch (Exception e) {
      log.warn("清洗字幕文件时发生错误 (不影响主流程): {}", e.getMessage());
    }
  }

}
