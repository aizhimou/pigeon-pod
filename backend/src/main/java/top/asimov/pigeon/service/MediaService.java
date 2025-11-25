package top.asimov.pigeon.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.dto.SubtitleInfo;
import top.asimov.pigeon.model.entity.Episode;

@Log4j2
@Service
public class MediaService {

  @Autowired
  private EpisodeMapper episodeMapper;

  @Autowired
  private MessageSource messageSource;

  @Value("${pigeon.audio-file-path}")
  private String audioStoragePath;

  @Value("${pigeon.cover-file-path}")
  private String coverStoragePath;

  public String saveFeedCover(String feedId, MultipartFile file) throws IOException {
    String contentType = file.getContentType();
    if (!Arrays.asList("image/jpeg", "image/png", "image/webp").contains(contentType)) {
      throw new IOException("Invalid file type. Only JPG, JPEG, PNG, and WEBP are allowed.");
    }

    Path coverPath = Path.of(coverStoragePath);
    if (!Files.exists(coverPath)) {
      Files.createDirectories(coverPath);
    }

    String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
    String filename = feedId + "." + extension;
    Path destinationFile = coverPath.resolve(filename);
    Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
    return extension;
  }

  public void deleteFeedCover(String feedId, String extension) throws IOException {
    if (!StringUtils.hasText(feedId) || !StringUtils.hasText(extension)) {
      return;
    }
    Path coverPath = Path.of(coverStoragePath);
    if (!Files.exists(coverPath)) {
      return;
    }
    Path fileToDelete = coverPath.resolve(feedId + "." + extension);
    if (Files.exists(fileToDelete)) {
      Files.delete(fileToDelete);
    }
  }

  public File getFeedCover(String feedId) throws IOException {
    Path coverPath = Path.of(coverStoragePath);
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

  public File getAudioFile(String episodeId) throws BusinessException {
    log.info("获取音频文件，episode ID: {}", episodeId);

    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      log.warn("未找到episode: {}", episodeId);
      throw new BusinessException(messageSource.getMessage("episode.not.found",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    String audioFilePath = episode.getMediaFilePath();
    if (!StringUtils.hasText(audioFilePath)) {
      log.warn("Episode {} 没有关联的音频文件路径", episodeId);
      throw new BusinessException(messageSource.getMessage("media.file.not.found",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    File audioFile = new File(audioFilePath);
    if (!audioFile.exists() || !audioFile.isFile()) {
      log.warn("音频文件不存在: {}", audioFilePath);
      throw new BusinessException(messageSource.getMessage("media.file.not.exists",
          new Object[]{audioFilePath}, LocaleContextHolder.getLocale()));
    }

    if (isFileInAllowedDirectory(audioFile)) {
      log.error("尝试访问不被允许的文件路径: {}", audioFilePath);
      throw new BusinessException(messageSource.getMessage("media.file.access.denied",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    log.info("找到音频文件: {}", audioFilePath);
    return audioFile;
  }

  /**
   * 获取字幕文件
   * 
   * @param episodeId 节目ID
   * @param language 语言代码（如 zh, en）
   * @return 字幕文件
   * @throws BusinessException 如果找不到文件或访问被拒绝
   */
  public File getSubtitleFile(String episodeId, String language) throws BusinessException {
    log.info("获取字幕文件，episode ID: {}, language: {}", episodeId, language);

    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      log.warn("未找到episode: {}", episodeId);
      throw new BusinessException(messageSource.getMessage("episode.not.found",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    String mediaFilePath = episode.getMediaFilePath();
    if (!StringUtils.hasText(mediaFilePath)) {
      log.warn("Episode {} 没有关联的媒体文件路径", episodeId);
      throw new BusinessException(messageSource.getMessage("media.file.not.found",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    // 构建字幕文件路径：媒体文件同目录，文件名格式为 {basename}.{lang}.{vtt|srt}
    File mediaFile = new File(mediaFilePath);
    String mediaDir = mediaFile.getParent();
    String mediaBaseName = mediaFile.getName().replaceFirst("\\.[^.]+$", ""); // 去除扩展名

    // 尝试查找 vtt 或 srt 格式的字幕文件
    File subtitleFile = null;
    for (String ext : new String[]{"vtt", "srt"}) {
      File candidateFile = new File(mediaDir, mediaBaseName + "." + language + "." + ext);
      if (candidateFile.exists() && candidateFile.isFile()) {
        subtitleFile = candidateFile;
        break;
      }
    }

    if (subtitleFile == null) {
      log.warn("字幕文件不存在: {}.{}.{{vtt|srt}}", mediaBaseName, language);
      throw new BusinessException(messageSource.getMessage("subtitle.file.not.found",
          new Object[]{episodeId, language}, LocaleContextHolder.getLocale()));
    }

    if (isFileInAllowedDirectory(subtitleFile)) {
      log.error("尝试访问不被允许的字幕文件路径: {}", subtitleFile.getPath());
      throw new BusinessException(messageSource.getMessage("media.file.access.denied",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    log.info("找到字幕文件: {}", subtitleFile.getPath());
    return subtitleFile;
  }

  /**
   * 获取节目的所有可用字幕信息
   * 
   * @param episode 节目实体
   * @return 字幕信息列表，每项包含 {language, format, file}
   */
  public List<SubtitleInfo> getAvailableSubtitles(Episode episode) {
    List<SubtitleInfo> subtitles = new ArrayList<>();
    
    String mediaFilePath = episode.getMediaFilePath();
    if (!StringUtils.hasText(mediaFilePath)) {
      return subtitles;
    }

    File mediaFile = new File(mediaFilePath);
    if (!mediaFile.exists() || !mediaFile.getParentFile().exists()) {
      return subtitles;
    }

    String mediaDir = mediaFile.getParent();
    String mediaBaseName = mediaFile.getName().replaceFirst("\\.[^.]+$", "");

    // 查找所有字幕文件：{basename}.{lang}.{vtt|srt}
    File dir = new File(mediaDir);
    Pattern pattern = Pattern.compile(Pattern.quote(mediaBaseName) + "\\.(\\w+)\\.(vtt|srt)$");
    
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        Matcher matcher = pattern.matcher(file.getName());
        if (matcher.find()) {
          String language = matcher.group(1);
          String format = matcher.group(2);
          subtitles.add(new SubtitleInfo(language, format, file));
          log.debug("发现字幕文件: {} (language={}, format={})", file.getName(), language, format);
        }
      }
    }

    return subtitles;
  }

  private boolean isFileInAllowedDirectory(File file) {
    return isFileInAllowedDirectory(file, audioStoragePath) && isFileInAllowedDirectory(file, coverStoragePath);
  }

  private boolean isFileInAllowedDirectory(File file, String allowedPath) {
    try {
      String canonicalFilePath = file.getCanonicalPath();
      String canonicalAllowedPath = new File(allowedPath).getCanonicalPath();
      return !canonicalFilePath.startsWith(canonicalAllowedPath);
    } catch (IOException e) {
      log.error("尝试访问的文件不在系统允许的安全路径内", e);
      return true;
    }
  }
}