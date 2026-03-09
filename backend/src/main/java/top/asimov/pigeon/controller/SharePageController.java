package top.asimov.pigeon.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import top.asimov.pigeon.service.SharePageHtmlService;
import top.asimov.pigeon.service.SharePageHtmlService.SharePageHtmlResult;

@Controller
public class SharePageController {

  private final SharePageHtmlService sharePageHtmlService;

  public SharePageController(SharePageHtmlService sharePageHtmlService) {
    this.sharePageHtmlService = sharePageHtmlService;
  }

  @GetMapping(value = "/share/episode/{id}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> getEpisodeSharePage(@PathVariable String id,
      HttpServletRequest request) {
    SharePageHtmlResult result = sharePageHtmlService.buildEpisodeSharePage(id,
        request.getRequestURL().toString());
    return ResponseEntity.status(result.status())
        .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
        .body(result.html());
  }
}
