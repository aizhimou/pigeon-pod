package top.asimov.pigeon.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public SaResult login(@RequestBody User user) {
    User loginUser = authService.login(user.getUsername(), user.getPassword());
    return SaResult.data(loginUser);
  }

  @PostMapping("/logout")
  public SaResult logout() {
    StpUtil.logout();
    return SaResult.ok("Logout successful");
  }

}
