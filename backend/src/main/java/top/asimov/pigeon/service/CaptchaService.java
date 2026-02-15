package top.asimov.pigeon.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.response.CaptchaResponse;

@Service
public class CaptchaService {

  private static final long EXPIRE_MILLIS = Duration.ofMinutes(5).toMillis();
  private static final int WIDTH = 120;
  private static final int HEIGHT = 35;
  private static final int CODE_COUNT = 4;
  private static final int LINE_COUNT = 30;

  private final Map<String, CaptchaEntry> captchaStore = new ConcurrentHashMap<>();

  public CaptchaResponse generateCaptcha() {
    cleanupExpired();
    LineCaptcha captcha = CaptchaUtil.createLineCaptcha(WIDTH, HEIGHT, CODE_COUNT, LINE_COUNT);
    String captchaId = UUID.randomUUID().toString().replace("-", "");
    captchaStore.put(captchaId, new CaptchaEntry(captcha.getCode(), System.currentTimeMillis()));

    String imageBase64 = captcha.getImageBase64();
    String imageData = "data:image/png;base64," + imageBase64;
    return CaptchaResponse.builder()
        .captchaId(captchaId)
        .imageData(imageData)
        .build();
  }

  public boolean validateCaptcha(String captchaId, String captchaCode) {
    cleanupExpired();
    if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
      return false;
    }
    CaptchaEntry entry = captchaStore.get(captchaId);
    if (entry == null) {
      return false;
    }
    boolean matched = entry.code.equalsIgnoreCase(captchaCode.trim());
    if (matched) {
      captchaStore.remove(captchaId);
    }
    return matched;
  }

  private void cleanupExpired() {
    long now = System.currentTimeMillis();
    captchaStore.entrySet().removeIf(entry -> now - entry.getValue().createdAt > EXPIRE_MILLIS);
  }

  private static class CaptchaEntry {

    private final String code;
    private final long createdAt;

    private CaptchaEntry(String code, long createdAt) {
      this.code = code;
      this.createdAt = createdAt;
    }
  }

}
