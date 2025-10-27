package top.asimov.pigeon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.util.SaResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.service.AccountService;

@SaCheckLogin
@RestController
@RequestMapping("/api/account")
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
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

}

