package top.asimov.pigeon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.model.request.ApplyDefaultMaximumEpisodesRequest;
import top.asimov.pigeon.model.request.ExportFeedsOpmlRequest;
import top.asimov.pigeon.model.request.UpdateLoginCaptchaRequest;
import top.asimov.pigeon.model.request.UpdateYtDlpArgsRequest;
import top.asimov.pigeon.model.request.UpdateYtDlpVersionRequest;
import top.asimov.pigeon.service.AccountService;
import top.asimov.pigeon.service.YtDlpRuntimeService;
import top.asimov.pigeon.util.YtDlpArgsValidator;

@SaCheckLogin
@RestController
@RequestMapping("/api/account")
public class AccountController {

  private final AccountService accountService;
  private final YtDlpRuntimeService ytDlpRuntimeService;

  public AccountController(AccountService accountService,
      YtDlpRuntimeService ytDlpRuntimeService) {
    this.accountService = accountService;
    this.ytDlpRuntimeService = ytDlpRuntimeService;
  }

  @PostMapping("/change-username")
  public SaResult changeUsername(@RequestBody User user) {
    return SaResult.data(accountService.changeUsername(user.getId(), user.getUsername()));
  }

  @GetMapping("/generate-api-key")
  public SaResult generateApiKey() {
    String apiKey = accountService.generateApiKey();
    return SaResult.data(apiKey);
  }

  @PostMapping("/reset-password")
  public SaResult resetPassword(@RequestBody User user) {
    accountService.resetPassword(user.getId(), user.getPassword(), user.getNewPassword());
    return SaResult.data(user);
  }

  @PostMapping("/update-youtube-api-key")
  public SaResult updateYoutubeApiKey(@RequestBody User user) {
    return SaResult.data(accountService.updateYoutubeApiKey(user.getId(), user.getYoutubeApiKey()));
  }

  @PostMapping("/cookies")
  public SaResult updateCookies(@RequestBody User user) {
    accountService.updateUserCookies(user.getId(), user.getCookiesContent());
    return SaResult.data(user);
  }

  @DeleteMapping("/cookies/{userId}")
  public SaResult deleteCookies(@PathVariable("userId") String userId) {
    accountService.deleteCookie(userId);
    return SaResult.ok();
  }

  @PostMapping("/update-date-format")
  public SaResult updateDateFormat(@RequestBody User user) {
    return SaResult.data(accountService.updateDateFormat(user.getId(), user.getDateFormat()));
  }

  @PostMapping("/update-subtitle-settings")
  public SaResult updateSubtitleSettings(@RequestBody User user) {
    accountService.updateSubtitleSettings(
        user.getId(), 
        user.getSubtitleLanguages(),
        user.getSubtitleFormat()
    );
    return SaResult.data(user);
  }

  @PostMapping("/update-default-maximum-episodes")
  public SaResult updateDefaultMaximumEpisodes(@RequestBody User user) {
    return SaResult.data(accountService.updateDefaultMaximumEpisodes(
        user.getId(), user.getDefaultMaximumEpisodes()));
  }

  @PostMapping("/apply-default-maximum-episodes")
  public SaResult applyDefaultMaximumEpisodes(
      @RequestBody ApplyDefaultMaximumEpisodesRequest request) {
    return SaResult.data(accountService.applyDefaultMaximumEpisodesToFeeds(
        request.getId(), request.getMode()));
  }

  @PostMapping("/update-yt-dlp-args")
  public SaResult updateYtDlpArgs(@RequestBody UpdateYtDlpArgsRequest request) {
    return SaResult.data(accountService.updateYtDlpArgs(request.getId(), request.getYtDlpArgs()));
  }

  @PostMapping("/update-login-captcha")
  public SaResult updateLoginCaptcha(@RequestBody UpdateLoginCaptchaRequest request) {
    String loginId = (String) StpUtil.getLoginId();
    return SaResult.data(
        accountService.updateLoginCaptchaEnabled(loginId, request.getEnabled()));
  }

  @GetMapping("/yt-dlp-args-policy")
  public SaResult getYtDlpArgsPolicy() {
    return SaResult.data(YtDlpArgsValidator.blockedArgs());
  }

  @GetMapping("/yt-dlp/runtime")
  public SaResult getYtDlpRuntime() {
    return SaResult.data(ytDlpRuntimeService.getRuntimeInfo());
  }

  @PostMapping("/yt-dlp/update")
  public SaResult updateYtDlp(@RequestBody UpdateYtDlpVersionRequest request) {
    return SaResult.data(ytDlpRuntimeService.submitUpdate(request.getChannel()));
  }

  @GetMapping("/yt-dlp/update-status")
  public SaResult getYtDlpUpdateStatus() {
    return SaResult.data(ytDlpRuntimeService.getUpdateStatus());
  }

  @PostMapping(value = "/export-opml", produces = "text/x-opml;charset=UTF-8")
  public ResponseEntity<byte[]> exportSubscriptionsOpml(@RequestBody ExportFeedsOpmlRequest request) {
    AccountService.OpmlExportFile exportFile = accountService.exportSubscriptionsOpml(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + exportFile.getFileName() + "\"")
        .contentType(MediaType.parseMediaType("text/x-opml;charset=UTF-8"))
        .body(exportFile.getContent().getBytes(StandardCharsets.UTF_8));
  }

}
