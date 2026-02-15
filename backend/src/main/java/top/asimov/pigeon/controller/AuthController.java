package top.asimov.pigeon.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.model.request.LoginRequest;
import top.asimov.pigeon.service.AuthService;
import top.asimov.pigeon.service.CaptchaService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final CaptchaService captchaService;

  public AuthController(AuthService authService, CaptchaService captchaService) {
    this.authService = authService;
    this.captchaService = captchaService;
  }

  @PostMapping("/login")
  public SaResult login(@RequestBody LoginRequest request) {
    User loginUser = authService.login(request);
    return SaResult.data(loginUser);
  }

  @PostMapping("/logout")
  public SaResult logout() {
    StpUtil.logout();
    return SaResult.ok("Logout successful");
  }

  @GetMapping("/captcha")
  public SaResult captcha() {
    return SaResult.data(captchaService.generateCaptcha());
  }

  @GetMapping("/captcha-config")
  public SaResult captchaConfig() {
    return SaResult.data(authService.isLoginCaptchaEnabled());
  }

}
