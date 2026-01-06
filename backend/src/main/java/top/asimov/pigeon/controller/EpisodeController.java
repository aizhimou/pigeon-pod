package top.asimov.pigeon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.request.EpisodeBatchRequest;
import top.asimov.pigeon.service.EpisodeService;
import top.asimov.pigeon.service.MediaService;

@SaCheckLogin
@RestController
@RequestMapping("/api/episode")
public class EpisodeController {

  private final EpisodeService episodeService;
  private final MediaService mediaService;

  public EpisodeController(EpisodeService episodeService, MediaService mediaService) {
    this.episodeService = episodeService;
    this.mediaService = mediaService;
  }

  @GetMapping("/list/{feedId}")
  public SaResult episodes(@PathVariable(name = "feedId") String feedId,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "25") Integer size) {
    Page<Episode> episodeList = episodeService.episodePage(feedId, new Page<>(page, size));
    return SaResult.data(episodeList);
  }

  @DeleteMapping("/{id}")
  public SaResult deleteEpisode(@PathVariable(name = "id") String id) {
    return SaResult.data(episodeService.deleteEpisodeById(id));
  }

  @PostMapping("/retry/{id}")
  public SaResult retryEpisode(@PathVariable(name = "id") String id) {
    episodeService.retryEpisode(id);
    return SaResult.ok();
  }

  @PostMapping("/download/{id}")
  public SaResult manualDownloadEpisode(@PathVariable(name = "id") String id) {
    episodeService.manualDownloadEpisode(id);
    return SaResult.ok();
  }

  @PostMapping("/cancel/{id}")
  public SaResult cancelEpisode(@PathVariable(name = "id") String id) {
    episodeService.cancelPendingEpisode(id);
    return SaResult.ok();
  }

  @PostMapping("/status")
  public SaResult getEpisodeStatusByIds(@RequestBody List<String> episodeIds) {
    List<Episode> episodes = episodeService.getEpisodeStatusByIds(episodeIds);
    return SaResult.data(episodes);
  }

  @PostMapping("/batch")
  public SaResult batchEpisodes(@RequestBody EpisodeBatchRequest request) {
    episodeService.batchProcessEpisodes(request.getAction(), request.getStatus(),
        request.getEpisodeIds());
    return SaResult.ok();
  }

  /**
   * 浏览器“下载到本地”用：只返回节目对应的媒体文件（音频/视频），不包含字幕/封面。
   */
  @GetMapping("/download/local/{id}")
  public ResponseEntity<Resource> downloadEpisodeToLocal(@PathVariable(name = "id") String id) {
    return mediaService.buildEpisodeDownloadToLocalResponse(id);
  }

}
