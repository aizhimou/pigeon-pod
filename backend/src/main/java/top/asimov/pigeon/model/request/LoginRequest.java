package top.asimov.pigeon.model.request;

import lombok.Data;

@Data
public class LoginRequest {

  private String username;
  private String password;
  private String captchaId;
  private String captchaCode;

}
