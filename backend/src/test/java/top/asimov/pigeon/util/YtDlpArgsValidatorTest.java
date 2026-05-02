package top.asimov.pigeon.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import top.asimov.pigeon.exception.BusinessException;

class YtDlpArgsValidatorTest {

  @Test
  void shouldRejectOutputPathOverrides() {
    BusinessException error = assertThrows(BusinessException.class,
        () -> YtDlpArgsValidator.validate(List.of("--output=other.%(ext)s")));

    assertEquals("blocked yt-dlp argument: --output=other.%(ext)s", error.getMessage());
  }

  @Test
  void shouldAllowFilenamePolicyOverridesBecauseFinalPathIsResolvedAfterMove() {
    List<String> validated = YtDlpArgsValidator.validate(List.of("--restrict-filenames"));

    assertEquals(List.of("--restrict-filenames"), validated);
  }
}
