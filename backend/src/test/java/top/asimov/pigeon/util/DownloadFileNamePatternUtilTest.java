package top.asimov.pigeon.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DownloadFileNamePatternUtilTest {

  @Test
  void shouldRenderSupportedVariables() {
    String baseName = DownloadFileNamePatternUtil.buildBaseName(
        "{date}-{channel}-{title}-{id}",
        "NPR News",
        "Morning Edition",
        "abc123",
        LocalDateTime.of(2026, 4, 14, 9, 30));

    assertEquals("20260414-NPR News-Morning Edition-abc123", baseName);
  }

  @Test
  void shouldKeepRenderedNameWhenPatternOmitsId() {
    String baseName = DownloadFileNamePatternUtil.buildBaseName(
        "{title}",
        "NPR News",
        "Morning Edition",
        "abc123",
        LocalDateTime.of(2026, 4, 14, 9, 30));

    assertEquals("Morning Edition", baseName);
  }

  @Test
  void shouldFallbackToDefaultPatternWhenBlank() {
    String baseName = DownloadFileNamePatternUtil.buildBaseName(
        "   ",
        "NPR News",
        "Morning Edition",
        "abc123",
        LocalDateTime.of(2026, 4, 14, 9, 30));

    assertEquals("Morning Edition-abc123", baseName);
  }

  @Test
  void shouldAppendNumericSuffixWithoutDroppingSuffix() {
    String suffixed = MediaFileNameUtil.appendNumericSuffix("Morning Edition", 2);

    assertEquals("Morning Edition-2", suffixed);
  }

  @Test
  void shouldRejectUnsupportedVariables() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> DownloadFileNamePatternUtil.validatePattern("{playlist}-{title}"));

    assertEquals("unsupported file name variable: {playlist}", error.getMessage());
  }

  @Test
  void shouldRejectBrokenBraces() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> DownloadFileNamePatternUtil.validatePattern("{title"));

    assertEquals("invalid file name pattern", error.getMessage());
  }
}
