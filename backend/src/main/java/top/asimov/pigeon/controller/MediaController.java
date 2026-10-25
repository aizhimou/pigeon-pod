package top.asimov.pigeon.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.service.EpisodeCoverService;
import top.asimov.pigeon.service.MediaService;

@Slf4j
@RestController
@RequestMapping("/media")
public class MediaController {

  private final MediaService mediaService;
  private final EpisodeCoverService episodeCoverService;

  public MediaController(MediaService mediaService, EpisodeCoverService episodeCoverService) {
    this.mediaService = mediaService;
    this.episodeCoverService = episodeCoverService;
  }

  @GetMapping("/feed/{feedId}/cover")
  public ResponseEntity<?> getFeedCover(@PathVariable String feedId) {
    return mediaService.buildFeedCoverResponse(feedId);
  }

  @GetMapping("/feed/{type}/{feedId}/episodes/{episodeId}/cover.jpg")
  public ResponseEntity<?> getStyledEpisodeCover(@PathVariable String type,
      @PathVariable String feedId, @PathVariable String episodeId,
      @RequestParam(required = false) String size) {
    log.info("[media] styled episode cover requested: feedType={} feedId={} episodeId={} size={}",
        type, feedId, episodeId, size);
    return episodeCoverService.buildCoverResponse(type, feedId, episodeId, "small".equals(size));
  }

  @GetMapping({"/{episodeId}/cover", "/{episodeId}/cover.jpg"})
  public ResponseEntity<?> getEpisodeCover(@PathVariable String episodeId) {
    log.info("[media] episode cover requested: episodeId={}", episodeId);
    return mediaService.buildEpisodeCoverResponse(episodeId);
  }

  @GetMapping({"/{episodeId}.mp3", "/{episodeId}.mp4", "/{episodeId}.m4a"})
  public void getMediaFile(@PathVariable String episodeId, HttpServletRequest request,
      HttpServletResponse response) throws ServletException, IOException {
    log.info("[media] media file requested: episodeId={}", episodeId);
    mediaService.serveEpisodeMediaFile(episodeId, request, response);
  }

  @GetMapping("/{episodeId}/subtitle/{languageWithExt:.+}")
  public ResponseEntity<?> getSubtitleFile(@PathVariable String episodeId,
      @PathVariable String languageWithExt) {
    log.info("[media] subtitle file requested: episodeId={} languageWithExt={}", episodeId,
        languageWithExt);
    return mediaService.buildSubtitleFileResponse(episodeId, languageWithExt);
  }

  @GetMapping("/{episodeId}/chapters.json")
  public ResponseEntity<?> getChaptersFile(@PathVariable String episodeId) {
    log.info("[media] chapters file requested: episodeId={}", episodeId);
    return mediaService.buildChaptersFileResponse(episodeId);
  }
}
