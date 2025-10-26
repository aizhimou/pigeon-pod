package top.asimov.pigeon.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
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
import top.asimov.pigeon.model.Episode;

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

    if (!isFileInAllowedDirectory(audioFile)) {
      log.error("尝试访问不被允许的文件路径: {}", audioFilePath);
      throw new BusinessException(messageSource.getMessage("media.file.access.denied",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    log.info("找到音频文件: {}", audioFilePath);
    return audioFile;
  }

  private boolean isFileInAllowedDirectory(File file) {
    return isFileInAllowedDirectory(file, audioStoragePath) || isFileInAllowedDirectory(file, coverStoragePath);
  }

  private boolean isFileInAllowedDirectory(File file, String allowedPath) {
    try {
      String canonicalFilePath = file.getCanonicalPath();
      String canonicalAllowedPath = new File(allowedPath).getCanonicalPath();
      return canonicalFilePath.startsWith(canonicalAllowedPath);
    } catch (IOException e) {
      log.error("安全检查时发生错误", e);
      return false;
    }
  }
}