package top.asimov.pigeon.controller;

import cn.dev33.satoken.apikey.annotation.SaCheckApiKey;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.service.RssService;

@RestController
@RequestMapping("/api/rss")
@SaCheckApiKey
public class RssController {

  private final RssService rssService;

  public RssController(RssService rssService) {
    this.rssService = rssService;
  }

  @GetMapping(value = {"/all.xml", "/unified.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getAllRssFeed() {
    try {
      String rssXml = rssService.generateAllRssFeed();
      return ResponseEntity.ok(rssXml);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body("无法生成 RSS feed。");
    }
  }

  @GetMapping(value = "/{channelIdentification}.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getRssFeed(@PathVariable String channelIdentification) {
    try {
      String rssXml = rssService.generateRssFeed(channelIdentification);
      return ResponseEntity.ok(rssXml);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body("无法生成 RSS feed。");
    }
  }

  @GetMapping(value = "/playlist/{playlistId}.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getPlaylistRssFeed(@PathVariable String playlistId) {
    try {
      String rssXml = rssService.generatePlaylistRssFeed(playlistId);
      return ResponseEntity.ok(rssXml);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body("无法生成 RSS feed。");
    }
  }

}
