package top.asimov.pigeon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.asimov.pigeon.model.entity.FeedDefaults;
import top.asimov.pigeon.model.entity.SystemConfig;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.model.enums.StorageType;
import top.asimov.pigeon.model.request.ApplyFeedDefaultsRequest;
import top.asimov.pigeon.model.request.ExportFeedsOpmlRequest;
import top.asimov.pigeon.model.request.SwitchYtDlpRuntimeRequest;
import top.asimov.pigeon.model.request.UpdateLoginCaptchaRequest;
import top.asimov.pigeon.model.request.UpdateYoutubeApiSettingsRequest;
import top.asimov.pigeon.model.request.UpdateYtDlpArgsRequest;
import top.asimov.pigeon.model.request.UpdateYtDlpVersionRequest;
import top.asimov.pigeon.service.AccountService;
import top.asimov.pigeon.service.FeedDefaultsService;
import top.asimov.pigeon.service.YoutubeQuotaService;
import top.asimov.pigeon.service.YtDlpRuntimeService;
import top.asimov.pigeon.util.YtDlpArgsValidator;

@SaCheckLogin
@RestController
@RequestMapping("/api/account")
public class AccountController {

  private final AccountService accountService;
  private final FeedDefaultsService feedDefaultsService;
  private final YtDlpRuntimeService ytDlpRuntimeService;
  private final YoutubeQuotaService youtubeQuotaService;

  public AccountController(AccountService accountService,
      FeedDefaultsService feedDefaultsService,
      YtDlpRuntimeService ytDlpRuntimeService,
      YoutubeQuotaService youtubeQuotaService) {
    this.accountService = accountService;
    this.feedDefaultsService = feedDefaultsService;
    this.ytDlpRuntimeService = ytDlpRuntimeService;
    this.youtubeQuotaService = youtubeQuotaService;
  }

  @SaCheckRole("admin")
  @GetMapping("/users")
  public SaResult listUsers() {
    return SaResult.data(accountService.listUsers());
  }

  @SaCheckRole("admin")
  @PostMapping("/admin/reset-password")
  public SaResult adminResetPassword(@RequestBody User user) {
    accountService.adminResetPassword(user.getId(), user.getNewPassword());
    return SaResult.ok();
  }

  @SaCheckRole("admin")
  @DeleteMapping("/user/{id}")
  public SaResult deleteUser(@PathVariable String id) {
    accountService.deleteUser(id);
    return SaResult.ok();
  }

  @PostMapping("/change-username")
  public SaResult changeUsername(@RequestBody User user) {
    return SaResult.data(accountService.changeUsername(
        StpUtil.getLoginIdAsString(), user.getUsername()));
  }

  @SaCheckRole("admin")
  @PostMapping("/add-user")
  public SaResult addUser(@RequestBody User user) {
    return SaResult.data(accountService.addUser(user.getUsername(), user.getPassword()));
  }

  @GetMapping("/generate-api-key")
  public SaResult generateApiKey() {
    String apiKey = accountService.generateApiKey();
    return SaResult.data(apiKey);
  }

  @PostMapping("/reset-password")
  public SaResult resetPassword(@RequestBody User user) {
    accountService.resetPassword(
        StpUtil.getLoginIdAsString(), user.getPassword(), user.getNewPassword());
    return SaResult.data(user);
  }

  @SaCheckRole("admin")
  @PostMapping("/update-youtube-api-key")
  public SaResult updateYoutubeApiKey(@RequestBody UpdateYoutubeApiSettingsRequest request) {
    return SaResult.data(accountService.updateYoutubeApiSettings(
        request.getId(),
        request.getYoutubeApiKey(),
        request.getYoutubeDailyLimitUnits()));
  }

  @SaCheckRole("admin")
  @GetMapping("/youtube-quota/today")
  public SaResult getYoutubeQuotaToday() {
    return SaResult.data(youtubeQuotaService.getTodayUsage());
  }

  @PostMapping("/update-date-format")
  public SaResult updateDateFormat(@RequestBody User user) {
    return SaResult.data(accountService.updateDateFormat(
        StpUtil.getLoginIdAsString(), user.getDateFormat()));
  }

  @SaCheckRole("admin")
  @GetMapping("/feed-defaults")
  public SaResult getFeedDefaults() {
    return SaResult.data(feedDefaultsService.getFeedDefaults());
  }

  @SaCheckRole("admin")
  @GetMapping("/system-config")
  public SaResult getSystemConfig() {
    return SaResult.data(accountService.getSystemConfig());
  }

  @SaCheckRole("admin")
  @PostMapping("/system-config")
  public SaResult updateSystemConfig(@RequestBody SystemConfig config) {
    return SaResult.data(accountService.updateSystemConfig(config));
  }

  @SaCheckRole("admin")
  @PostMapping("/system-config/storage/test")
  public SaResult testSystemConfigStorage(@RequestBody SystemConfig config) {
    accountService.testSystemStorageConfig(config);
    return SaResult.ok();
  }

  @SaCheckRole("admin")
  @PostMapping("/system-config/proxy/test")
  public SaResult testSystemConfigProxy(@RequestBody SystemConfig config) {
    return SaResult.data(accountService.testProxyConfig(config));
  }

  @SaCheckRole("admin")
  @PostMapping("/system-config/ssl/upload-cert")
  public SaResult uploadSslCert(@RequestParam("file") MultipartFile file) {
    return SaResult.data(accountService.uploadSslCertificate(file));
  }

  @SaCheckRole("admin")
  @PostMapping("/system-config/ssl/upload-key")
  public SaResult uploadSslKey(@RequestParam("file") MultipartFile file) {
    return SaResult.data(accountService.uploadSslKey(file));
  }

  @SaCheckRole("admin")
  @GetMapping("/system-config/storage/switch-check")
  public SaResult checkSystemConfigStorageSwitch(@RequestParam StorageType targetType) {
    return SaResult.data(accountService.checkStorageSwitchAllowed(targetType));
  }

  @SaCheckRole("admin")
  @PostMapping("/update-feed-defaults")
  public SaResult updateFeedDefaults(@RequestBody FeedDefaults feedDefaults) {
    return SaResult.data(feedDefaultsService.updateFeedDefaults(feedDefaults));
  }

  @SaCheckRole("admin")
  @PostMapping("/apply-feed-defaults")
  public SaResult applyFeedDefaults(@RequestBody ApplyFeedDefaultsRequest request) {
    return SaResult.data(feedDefaultsService.applyFeedDefaultsToFeeds(request.getMode()));
  }

  @SaCheckRole("admin")
  @PostMapping("/update-yt-dlp-args")
  public SaResult updateYtDlpArgs(@RequestBody UpdateYtDlpArgsRequest request) {
    return SaResult.data(accountService.updateYtDlpArgs(request.getId(), request.getYtDlpArgs()));
  }

  @SaCheckRole("admin")
  @PostMapping("/update-login-captcha")
  public SaResult updateLoginCaptcha(@RequestBody UpdateLoginCaptchaRequest request) {
    return SaResult.data(accountService.updateLoginCaptchaEnabled(request.getEnabled()));
  }

  @SaCheckRole("admin")
  @GetMapping("/yt-dlp-args-policy")
  public SaResult getYtDlpArgsPolicy() {
    return SaResult.data(YtDlpArgsValidator.blockedArgs());
  }

  @SaCheckRole("admin")
  @GetMapping("/yt-dlp/runtime")
  public SaResult getYtDlpRuntime() {
    return SaResult.data(ytDlpRuntimeService.getRuntimeInfo());
  }

  @SaCheckRole("admin")
  @PostMapping("/yt-dlp/runtime/switch")
  public SaResult switchYtDlpRuntime(@RequestBody SwitchYtDlpRuntimeRequest request) {
    return SaResult.data(ytDlpRuntimeService.switchRuntime(request.getRuntimeKey()));
  }

  @SaCheckRole("admin")
  @PostMapping("/yt-dlp/update")
  public SaResult updateYtDlp(@RequestBody UpdateYtDlpVersionRequest request) {
    return SaResult.data(ytDlpRuntimeService.submitUpdate(request.getChannel()));
  }

  @SaCheckRole("admin")
  @GetMapping("/yt-dlp/update-status")
  public SaResult getYtDlpUpdateStatus() {
    return SaResult.data(ytDlpRuntimeService.getUpdateStatus());
  }

  @SaCheckRole("admin")
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
