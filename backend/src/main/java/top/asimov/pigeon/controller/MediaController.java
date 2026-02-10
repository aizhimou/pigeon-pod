package top.asimov.pigeon.controller;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.exception.BusinessException;
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
  public ResponseEntity<Resource> getFeedCover(@PathVariable String feedId) {
    try {
      File coverFile = mediaService.getFeedCover(feedId);
      if (coverFile == null) {
        return ResponseEntity.notFound().build();
      }
      Resource resource = new FileSystemResource(coverFile);
      MediaType mediaType = getMediaTypeByFileName(coverFile.getName());
      return ResponseEntity.ok()
          .contentType(mediaType)
          .body(resource);
    } catch (Exception e) {
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping({ "/{episodeId}.mp3", "/{episodeId}.mp4", "/{episodeId}.m4a" })
  public ResponseEntity<Resource> getMediaFile(@PathVariable String episodeId) {
    try {
      log.info("请求媒体文件，episode ID: {}", episodeId);

      File audioFile = mediaService.getAudioFile(episodeId);

      Resource resource = new FileSystemResource(audioFile);

      HttpHeaders headers = new HttpHeaders();
      String encodedFileName = URLEncoder.encode(audioFile.getName(), StandardCharsets.UTF_8)
          .replace("+", "%20");
      headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName);
      headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

      MediaType mediaType = getMediaTypeByFileName(audioFile.getName());

      return ResponseEntity.ok()
          .headers(headers)
          .contentLength(audioFile.length())
          .contentType(mediaType)
          .body(resource);

    } catch (BusinessException e) {
      log.error("业务异常: {}", e.getMessage());
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("处理媒体文件请求时发生错误", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * 获取字幕文件
   * 支持 Podcasting 2.0 标准
   * URL 格式：/media/{episodeId}/subtitle/{language}.{format}
   * 示例：/media/episodeId/subtitle/zh.vtt
   * 
   * @param episodeId 节目ID
   * @param languageWithExt 语言代码和扩展名（如 zh.vtt, en.srt）
   * @return 字幕文件（VTT 或 SRT 格式）
   */
  @GetMapping("/{episodeId}/subtitle/{languageWithExt:.+}")
  public ResponseEntity<Resource> getSubtitleFile(@PathVariable String episodeId, 
                                                    @PathVariable String languageWithExt) {
    try {
      // 提取语言代码（去除文件扩展名）
      String language = languageWithExt.substring(0, languageWithExt.lastIndexOf('.'));
      
      log.info("请求字幕文件，episode ID: {}, language: {}", episodeId, language);

      File subtitleFile = mediaService.getSubtitleFile(episodeId, language);

      Resource resource = new FileSystemResource(subtitleFile);

      HttpHeaders headers = new HttpHeaders();
      String encodedFileName = URLEncoder.encode(subtitleFile.getName(), StandardCharsets.UTF_8)
          .replace("+", "%20");
      headers.add(HttpHeaders.CONTENT_DISPOSITION,
          "inline; filename*=UTF-8''" + encodedFileName);
      headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

      MediaType mediaType = getMediaTypeByFileName(subtitleFile.getName());

      return ResponseEntity.ok()
          .headers(headers)
          .contentLength(subtitleFile.length())
          .contentType(mediaType)
          .body(resource);

    } catch (BusinessException e) {
      log.error("业务异常: {}", e.getMessage());
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("处理字幕文件请求时发生错误", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * 获取 Podcasting 2.0 章节文件。
   * URL 格式：/media/{episodeId}/chapters.json
   */
  @GetMapping("/{episodeId}/chapters.json")
  public ResponseEntity<Resource> getChaptersFile(@PathVariable String episodeId) {
    try {
      log.info("请求章节文件，episode ID: {}", episodeId);

      File chaptersFile = mediaService.getChaptersFile(episodeId);
      Resource resource = new FileSystemResource(chaptersFile);

      HttpHeaders headers = new HttpHeaders();
      String encodedFileName = URLEncoder.encode(chaptersFile.getName(), StandardCharsets.UTF_8)
          .replace("+", "%20");
      headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName);
      headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

      return ResponseEntity.ok()
          .headers(headers)
          .contentLength(chaptersFile.length())
          .contentType(MediaType.parseMediaType("application/json;charset=utf-8"))
          .body(resource);
    } catch (BusinessException e) {
      log.error("业务异常: {}", e.getMessage());
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("处理章节文件请求时发生错误", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
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
      default -> MediaType.APPLICATION_OCTET_STREAM;
    };
  }
}
