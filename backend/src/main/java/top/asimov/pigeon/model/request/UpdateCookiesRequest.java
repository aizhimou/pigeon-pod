package top.asimov.pigeon.model.request;

import lombok.Data;

@Data
public class UpdateCookiesRequest {

  private String id;
  private String cookiesContent;
}
