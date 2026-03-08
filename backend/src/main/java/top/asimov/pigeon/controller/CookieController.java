package top.asimov.pigeon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.util.SaResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.enums.CookiePlatform;
import top.asimov.pigeon.model.request.UpsertCookieRequest;
import top.asimov.pigeon.service.CookieService;

@SaCheckLogin
@RestController
@RequestMapping("/api/cookies")
public class CookieController {

  private final CookieService cookieService;

  public CookieController(CookieService cookieService) {
    this.cookieService = cookieService;
  }

  @GetMapping
  public SaResult listCookies() {
    return SaResult.data(cookieService.listSummaries());
  }

  @PutMapping("/{platform}")
  public SaResult upsertCookie(@PathVariable("platform") CookiePlatform platform,
      @RequestBody UpsertCookieRequest request) {
    cookieService.upsert(platform, request.getCookiesContent());
    return SaResult.ok();
  }

  @DeleteMapping("/{platform}")
  public SaResult deleteCookie(@PathVariable("platform") CookiePlatform platform) {
    cookieService.delete(platform);
    return SaResult.ok();
  }
}
