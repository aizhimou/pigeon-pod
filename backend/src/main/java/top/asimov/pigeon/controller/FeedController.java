package top.asimov.pigeon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.util.SaResult;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.asimov.pigeon.model.enums.FeedType;
import top.asimov.pigeon.service.FeedService;

@SaCheckLogin
@RestController
@RequestMapping("/api/feed")
public class FeedController {

  private final FeedService feedService;

  public FeedController(FeedService feedService) {
    this.feedService = feedService;
  }

  @GetMapping("/list")
  public SaResult listAll() {
    return SaResult.data(feedService.listAll());
  }

  @GetMapping("/{type}/detail/{id}")
  public SaResult detail(@PathVariable String type, @PathVariable String id) {
    FeedType feedType = feedService.resolveType(type);
    return SaResult.data(feedService.detail(feedType, id));
  }

  @GetMapping("/{type}/subscribe/{id}")
  public SaResult subscribe(@PathVariable String type, @PathVariable String id) {
    FeedType feedType = feedService.resolveType(type);
    return SaResult.data(feedService.getSubscribeUrl(feedType, id));
  }

  @PostMapping("/{type}/history/{id}")
  public SaResult fetchHistory(@PathVariable String type, @PathVariable String id) {
    FeedType feedType = feedService.resolveType(type);
    return SaResult.data(feedService.fetchHistory(feedType, id));
  }

  @PutMapping("/{type}/config/{id}")
  public SaResult updateConfig(@PathVariable String type, @PathVariable String id,
      @RequestBody Map<String, Object> payload) {
    payload.remove("customCoverExt");
    FeedType feedType = feedService.resolveType(type);
    return SaResult.data(feedService.updateConfig(feedType, id, payload));
  }

  @PostMapping("/{type}/{id}/cover")
  public SaResult uploadCover(@PathVariable String type, @PathVariable String id,
      @RequestParam("file") MultipartFile file) {
    try {
      FeedType feedType = feedService.resolveType(type);
      feedService.updateCustomCover(feedType, id, file);
      return SaResult.ok();
    } catch (IOException e) {
      return SaResult.error(e.getMessage());
    }
  }

  @DeleteMapping("/{type}/{id}/cover")
  public SaResult deleteCustomCover(@PathVariable String type, @PathVariable String id) {
    try {
      FeedType feedType = feedService.resolveType(type);
      feedService.clearCustomCover(feedType, id);
      return SaResult.ok();
    } catch (IOException e) {
      return SaResult.error(e.getMessage());
    }
  }

  @PostMapping("/fetch")
  public SaResult fetch(@RequestBody Map<String, String> request) {
    return SaResult.data(feedService.fetch(request));
  }

  @PostMapping("/{type}/preview")
  public SaResult preview(@PathVariable String type, @RequestBody Map<String, Object> payload) {
    FeedType feedType = feedService.resolveType(type);
    return SaResult.data(feedService.preview(feedType, payload));
  }

  @PostMapping("/{type}/add")
  public SaResult add(@PathVariable String type, @RequestBody Map<String, Object> payload) {
    FeedType feedType = feedService.resolveType(type);
    return SaResult.data(feedService.add(feedType, payload));
  }

  @PostMapping("/{type}/refresh/{id}")
  public SaResult refresh(@PathVariable String type, @PathVariable String id) {
    FeedType feedType = feedService.resolveType(type);
    return SaResult.data(feedService.refresh(feedType, id));
  }

  @DeleteMapping("/{type}/delete/{id}")
  public SaResult delete(@PathVariable String type, @PathVariable String id) {
    FeedType feedType = feedService.resolveType(type);
    feedService.delete(feedType, id);
    return SaResult.ok();
  }
}
