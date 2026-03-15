package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.asimov.pigeon.model.entity.Episode;

class RssServiceTest {

  private final RssService rssService = new RssService(null, null, null, null, null, null);

  @Test
  void shouldAppendSourceVideoAfterEpisodeDescription() {
    Episode episode = Episode.builder()
        .id("abc123")
        .description("Episode description")
        .build();

    String summary = ReflectionTestUtils.invokeMethod(
        rssService, "buildEpisodeSummary", episode, "YOUTUBE", false);

    assertEquals(
        "Episode description\n\nSource video: <a href=\"https://youtu.be/abc123\">youtu.be/abc123</a>",
        summary);
  }

  @Test
  void shouldKeepPlaylistSourcePrefixAndAppendSourceVideoAtEnd() {
    Episode episode = Episode.builder()
        .id("abc123")
        .description("Episode description")
        .sourceChannelName("Channel Name")
        .sourceChannelUrl("https://www.youtube.com/@channel")
        .build();

    String summary = ReflectionTestUtils.invokeMethod(
        rssService, "buildEpisodeSummary", episode, "YOUTUBE", true);

    assertEquals(
        "Source channel: <a href=\"https://www.youtube.com/@channel\">Channel Name</a>\n"
            + "Episode description\n\n"
            + "Source video: <a href=\"https://youtu.be/abc123\">youtu.be/abc123</a>",
        summary);
  }

  @Test
  void shouldNotInsertBlankLineWhenPlaylistEpisodeHasNoDescription() {
    Episode episode = Episode.builder()
        .id("abc123")
        .sourceChannelName("Channel Name")
        .sourceChannelUrl("https://www.youtube.com/@channel")
        .build();

    String summary = ReflectionTestUtils.invokeMethod(
        rssService, "buildEpisodeSummary", episode, "YOUTUBE", true);

    assertEquals(
        "Source channel: <a href=\"https://www.youtube.com/@channel\">Channel Name</a>\n"
            + "Source video: <a href=\"https://youtu.be/abc123\">youtu.be/abc123</a>",
        summary);
  }
}
