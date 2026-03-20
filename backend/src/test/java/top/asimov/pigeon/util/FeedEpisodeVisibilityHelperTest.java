package top.asimov.pigeon.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;

class FeedEpisodeVisibilityHelperTest {

  @Test
  void shouldHideArchivedLiveVodWhenFeedExcludesIt() {
    Channel channel = Channel.builder()
        .id("channel-1")
        .excludeLiveVod(true)
        .build();
    Episode episode = Episode.builder()
        .id("episode-1")
        .title("Live replay")
        .description("Replay")
        .durationSeconds(3600)
        .liveVod(true)
        .build();

    assertFalse(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, episode));
  }

  @Test
  void shouldKeepRegularEpisodeVisibleWhenFeedExcludesLiveVod() {
    Channel channel = Channel.builder()
        .id("channel-1")
        .excludeLiveVod(true)
        .build();
    Episode episode = Episode.builder()
        .id("episode-1")
        .title("Regular upload")
        .description("Standard episode")
        .durationSeconds(900)
        .liveVod(false)
        .build();

    assertTrue(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, episode));
  }

  @Test
  void shouldStillHideLiveVodWhenDurationIsMissing() {
    Channel channel = Channel.builder()
        .id("channel-1")
        .excludeLiveVod(true)
        .build();
    Episode episode = Episode.builder()
        .id("episode-1")
        .title("Live replay")
        .description("Replay")
        .liveVod(true)
        .build();

    assertFalse(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, episode));
  }
}
