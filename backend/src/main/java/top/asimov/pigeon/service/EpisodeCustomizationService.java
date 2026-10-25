package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.entity.Episode;

/**
 * Per-episode edits to the title, description and thumbnail shown in RSS feeds and the app. The
 * originals from the source are kept, so every edit can be undone.
 */
@Slf4j
@Service
public class EpisodeCustomizationService {

  static final int MAX_TITLE_LENGTH = 500;
  static final int MAX_DESCRIPTION_LENGTH = 20000;

  private final EpisodeMapper episodeMapper;
  private final MediaService mediaService;
  private final EpisodeCoverService episodeCoverService;

  public EpisodeCustomizationService(EpisodeMapper episodeMapper, MediaService mediaService,
      EpisodeCoverService episodeCoverService) {
    this.episodeMapper = episodeMapper;
    this.mediaService = mediaService;
    this.episodeCoverService = episodeCoverService;
  }

  /**
   * Sets the custom title and description. Blank values, or values equal to the original, clear
   * the override.
   */
  public Episode updateText(String episodeId, String customTitle, String customDescription) {
    Episode episode = requireEpisode(episodeId);
    String title = normalizeOverride(customTitle, episode.getTitle(), MAX_TITLE_LENGTH, true);
    String description = normalizeOverride(customDescription, episode.getDescription(),
        MAX_DESCRIPTION_LENGTH, false);
    episodeMapper.update(null, new LambdaUpdateWrapper<Episode>()
        .eq(Episode::getId, episodeId)
        .set(Episode::getCustomTitle, title)
        .set(Episode::getCustomDescription, description)
        .set(Episode::getCustomizedAt, LocalDateTime.now()));
    log.info("[episode] custom text updated: episodeId={} title={} description={}", episodeId,
        title != null, description != null);
    return episodeMapper.selectById(episodeId);
  }

  public Episode updateCover(String episodeId, MultipartFile file) throws IOException {
    Episode episode = requireEpisode(episodeId);
    deleteStoredCover(episode);
    String extension = mediaService.saveEpisodeCustomCover(episodeId, file);
    episodeMapper.update(null, new LambdaUpdateWrapper<Episode>()
        .eq(Episode::getId, episodeId)
        .set(Episode::getCustomCoverExt, extension)
        .set(Episode::getCustomizedAt, LocalDateTime.now()));
    episodeCoverService.evictEpisode(episodeId);
    log.info("[episode] custom cover uploaded: episodeId={} extension={}", episodeId, extension);
    return episodeMapper.selectById(episodeId);
  }

  public Episode clearCover(String episodeId) throws IOException {
    Episode episode = requireEpisode(episodeId);
    if (!StringUtils.hasText(episode.getCustomCoverExt())) {
      return episode;
    }
    deleteStoredCover(episode);
    episodeMapper.update(null, new LambdaUpdateWrapper<Episode>()
        .eq(Episode::getId, episodeId)
        .set(Episode::getCustomCoverExt, null)
        .set(Episode::getCustomizedAt, LocalDateTime.now()));
    episodeCoverService.evictEpisode(episodeId);
    log.info("[episode] custom cover cleared: episodeId={}", episodeId);
    return episodeMapper.selectById(episodeId);
  }

  private void deleteStoredCover(Episode episode) throws IOException {
    if (StringUtils.hasText(episode.getCustomCoverExt())) {
      mediaService.deleteEpisodeCustomCover(episode.getId(), episode.getCustomCoverExt());
    }
  }

  private Episode requireEpisode(String episodeId) {
    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      throw new BusinessException("Episode not found");
    }
    return episode;
  }

  private static String normalizeOverride(String value, String original, int maxLength,
      boolean singleLine) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String normalized = value.replace("\r\n", "\n").strip();
    if (singleLine) {
      normalized = normalized.replaceAll("\\s*\\n\\s*", " ");
    }
    if (normalized.length() > maxLength) {
      throw new BusinessException("Text is too long (maximum " + maxLength + " characters)");
    }
    if (original != null && normalized.equals(original.replace("\r\n", "\n").strip())) {
      return null;
    }
    return normalized;
  }
}
