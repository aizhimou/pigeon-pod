package top.asimov.pigeon.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.service.MediaService;

@Log4j2
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
  public ResponseEntity<?> getMediaFile(@PathVariable String episodeId) {
    log.info("请求媒体文件，episode ID: {}", episodeId);
    return mediaService.buildEpisodeMediaFileResponse(episodeId);
  }

  @GetMapping("/{episodeId}/subtitle/{languageWithExt:.+}")
  public ResponseEntity<?> getSubtitleFile(@PathVariable String episodeId,
      @PathVariable String languageWithExt) {
    log.info("请求字幕文件，episode ID: {}, languageWithExt: {}", episodeId, languageWithExt);
    return mediaService.buildSubtitleFileResponse(episodeId, languageWithExt);
  }

  @GetMapping("/{episodeId}/chapters.json")
  public ResponseEntity<?> getChaptersFile(@PathVariable String episodeId) {
    log.info("请求章节文件，episode ID: {}", episodeId);
    return mediaService.buildChaptersFileResponse(episodeId);
  }
}
