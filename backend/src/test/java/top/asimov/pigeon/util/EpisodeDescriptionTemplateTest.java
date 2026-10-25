package top.asimov.pigeon.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import top.asimov.pigeon.exception.BusinessException;

class EpisodeDescriptionTemplateTest {

  private static final Map<String, String> VALUES = Map.of(
      "description", "Original text",
      "title", "Episode 1",
      "channel", "My Channel",
      "date", "2026-10-01",
      "source_url", "https://youtu.be/abc",
      "source_link", "<a href=\"https://youtu.be/abc\">youtu.be/abc</a>");

  @Test
  void rendersKnownPlaceholders() {
    String rendered = EpisodeDescriptionTemplate.render(
        "{title} ({date})\n{description}\n\nWatch: {source_url}", VALUES);

    assertEquals("Episode 1 (2026-10-01)\nOriginal text\n\nWatch: https://youtu.be/abc", rendered);
  }

  @Test
  void leavesUnknownPlaceholdersAndSpecialCharactersAlone() {
    String rendered = EpisodeDescriptionTemplate.render("{unknown} costs $5 {channel}", VALUES);

    assertEquals("{unknown} costs $5 My Channel", rendered);
  }

  @Test
  void emptyValuesDoNotLeaveGaps() {
    String rendered = EpisodeDescriptionTemplate.render("{description}\n\n\n\nSource: {source_url}",
        Map.of("source_url", "https://youtu.be/abc"));

    assertEquals("Source: https://youtu.be/abc", rendered);
  }

  @Test
  void valuesContainingBackslashesAreInsertedLiterally() {
    String rendered = EpisodeDescriptionTemplate.render("{description}",
        Map.of("description", "C:\\path and $1"));

    assertEquals("C:\\path and $1", rendered);
  }

  @Test
  void normalizeTrimsAndClearsBlankFormats() {
    assertNull(EpisodeDescriptionTemplate.normalize(null));
    assertNull(EpisodeDescriptionTemplate.normalize("   \n "));
    assertEquals("{description}\nmore", EpisodeDescriptionTemplate.normalize(
        "  {description}\r\nmore \n"));
  }

  @Test
  void normalizeRejectsOverlongFormats() {
    String tooLong = "x".repeat(EpisodeDescriptionTemplate.MAX_LENGTH + 1);

    assertThrows(BusinessException.class, () -> EpisodeDescriptionTemplate.normalize(tooLong));
  }
}
