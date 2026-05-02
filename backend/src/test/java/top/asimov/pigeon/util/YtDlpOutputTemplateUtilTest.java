package top.asimov.pigeon.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class YtDlpOutputTemplateUtilTest {

  @Test
  void shouldEscapeLiteralPercentSignsInFileName() {
    String template = YtDlpOutputTemplateUtil.buildMediaOutputTemplate(
        "/data/video/100% Eat/",
        "YOU Picked BANNED FOOD for Episode 100_ %% Jack");

    assertEquals(
        "/data/video/100%% Eat/YOU Picked BANNED FOOD for Episode 100_ %%%% Jack.%(ext)s",
        template);
  }

  @Test
  void shouldKeepYtDlpExtensionPlaceholderUnescaped() {
    String template = YtDlpOutputTemplateUtil.buildMediaOutputTemplate(
        "/data/video/",
        "literal %(title)s token");

    assertEquals("/data/video/literal %%(title)s token.%(ext)s", template);
  }
}
