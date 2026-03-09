package top.asimov.pigeon.service;

import java.io.File;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.config.AppBaseUrlResolver;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.response.PublicEpisodeShareResponse;
import top.asimov.pigeon.util.FeedSourceUrlBuilder;
import top.asimov.pigeon.util.MediaKeyUtil;

@Log4j2
@Service
public class PublicEpisodeService {

  private final EpisodeMapper episodeMapper;
  private final ChannelMapper channelMapper;
  private final PlaylistMapper playlistMapper;
  private final MediaService mediaService;
  private final AppBaseUrlResolver appBaseUrlResolver;
  private final MessageSource messageSource;

  public PublicEpisodeService(EpisodeMapper episodeMapper, ChannelMapper channelMapper,
      PlaylistMapper playlistMapper, MediaService mediaService,
      AppBaseUrlResolver appBaseUrlResolver, MessageSource messageSource) {
    this.episodeMapper = episodeMapper;
    this.channelMapper = channelMapper;
    this.playlistMapper = playlistMapper;
    this.mediaService = mediaService;
    this.appBaseUrlResolver = appBaseUrlResolver;
    this.messageSource = messageSource;
  }

  public PublicEpisodeShareResponse getPublicEpisode(String episodeId) {
    Episode episode = findShareableEpisode(episodeId);
    if (episode == null) {
      return null;
    }

    String source = resolveFeedSource(episode);
    String mediaUrl = buildStableMediaUrl(episode);
    if (!StringUtils.hasText(mediaUrl)) {
      return null;
    }

    return PublicEpisodeShareResponse.builder()
        .id(episode.getId())
        .title(resolveDisplayTitle(episode))
        .description(episode.getDescription())
        .coverUrl(resolveCoverUrl(episode))
        .sourceUrl(FeedSourceUrlBuilder.buildEpisodeUrl(source, episode.getId()))
        .mediaUrl(mediaUrl)
        .mediaType(episode.getMediaType())
        .publishedAt(episode.getPublishedAt())
        .duration(episode.getDuration())
        .build();
  }

  public String generateShareUrl(String episodeId) {
    Episode episode = findShareableEpisode(episodeId);
    if (episode == null) {
      throw new BusinessException(messageSource.getMessage("share.episode.unavailable", null,
          LocaleContextHolder.getLocale()));
    }
    return appBaseUrlResolver.requireBaseUrl() + "/share/episode/" + episode.getId();
  }

  public String localizeUnavailableMessage() {
    return messageSource.getMessage("share.episode.unavailable", null, LocaleContextHolder.getLocale());
  }

  private Episode findShareableEpisode(String episodeId) {
    if (!StringUtils.hasText(episodeId)) {
      return null;
    }

    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      return null;
    }
    if (!EpisodeStatus.COMPLETED.name().equals(episode.getDownloadStatus())) {
      return null;
    }
    if (!StringUtils.hasText(episode.getMediaFilePath())) {
      return null;
    }
    if (!hasAccessibleMedia(episode)) {
      log.info("Captain share skipped for episode {} because media is unavailable", episodeId);
      return null;
    }
    return episode;
  }

  private boolean hasAccessibleMedia(Episode episode) {
    if (episode == null || !StringUtils.hasText(episode.getMediaFilePath())) {
      return false;
    }
    if (mediaService.isS3ModeEnabled()) {
      return mediaService.objectKeyExists(episode.getMediaFilePath());
    }
    File mediaFile = new File(episode.getMediaFilePath());
    return mediaFile.exists() && mediaFile.isFile() && mediaService.isFilePathAllowed(mediaFile);
  }

  private String resolveFeedSource(Episode episode) {
    if (episode != null && StringUtils.hasText(episode.getChannelId())) {
      Channel channel = channelMapper.selectById(episode.getChannelId());
      if (channel != null && StringUtils.hasText(channel.getSource())) {
        return channel.getSource();
      }
    }

    Playlist playlist = playlistMapper.selectLatestByEpisodeId(episode.getId());
    if (playlist != null && StringUtils.hasText(playlist.getSource())) {
      return playlist.getSource();
    }
    return "YOUTUBE";
  }

  private String buildStableMediaUrl(Episode episode) {
    if (episode == null || !StringUtils.hasText(episode.getMediaFilePath())) {
      return null;
    }
    String extension = MediaKeyUtil.extractExtension(episode.getMediaFilePath());
    if (!StringUtils.hasText(extension)) {
      return null;
    }
    return "/media/" + episode.getId() + "." + extension;
  }

  private String resolveDisplayTitle(Episode episode) {
    if (episode == null) {
      return "";
    }
    if (StringUtils.hasText(episode.getTitle())) {
      return episode.getTitle().trim();
    }
    return episode.getId();
  }

  private String resolveCoverUrl(Episode episode) {
    if (episode == null) {
      return null;
    }
    if (StringUtils.hasText(episode.getMaxCoverUrl())) {
      return episode.getMaxCoverUrl().trim();
    }
    if (StringUtils.hasText(episode.getDefaultCoverUrl())) {
      return episode.getDefaultCoverUrl().trim();
    }
    return null;
  }
}
