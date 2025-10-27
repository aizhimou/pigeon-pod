package top.asimov.pigeon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.service.EpisodeService;

@SaCheckLogin
@RestController
@RequestMapping("/api/episode")
public class EpisodeController {

  private final EpisodeService episodeService;

  public EpisodeController(EpisodeService episodeService) {
    this.episodeService = episodeService;
  }

  @GetMapping("/list/{feedId}")
  public SaResult programsOfChannel(@PathVariable(name = "feedId") String feedId,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "10") Integer size) {
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

}
