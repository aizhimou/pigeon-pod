package top.asimov.pigeon.controller;

import cn.dev33.satoken.util.SaResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.response.PublicEpisodeShareResponse;
import top.asimov.pigeon.service.PublicEpisodeService;

@RestController
@RequestMapping("/api/public")
public class PublicEpisodeController {

  private final PublicEpisodeService publicEpisodeService;

  public PublicEpisodeController(PublicEpisodeService publicEpisodeService) {
    this.publicEpisodeService = publicEpisodeService;
  }

  @GetMapping("/episode/{id}")
  public ResponseEntity<SaResult> getPublicEpisode(@PathVariable String id) {
    PublicEpisodeShareResponse response = publicEpisodeService.getPublicEpisode(id);
    if (response == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(SaResult.code(HttpStatus.NOT_FOUND.value())
              .setMsg(publicEpisodeService.localizeUnavailableMessage()));
    }
    return ResponseEntity.ok(SaResult.data(response));
  }
}
