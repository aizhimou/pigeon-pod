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
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.service.MediaService;

@Slf4j
@RestController
@RequestMapping("/media")
public class MediaController {

  private final MediaService mediaService;

  public MediaController(MediaService mediaService) {
    this.mediaService = mediaService;
  }

  @GetMapping("/feed/{feedId}/cover")
  public ResponseEntity<?> getFeedCover(@PathVariable String feedId) {
    return mediaService.buildFeedCoverResponse(feedId);
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
