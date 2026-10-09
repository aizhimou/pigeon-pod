package top.asimov.pigeon.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;

public class FeedEpisodeVisibilityHelperTest {

  @Test
  public void testMatchesFeedFilter_NullHandling() {
    Channel channel = Channel.builder().id("ch1").build();
    Episode episode = Episode.builder().id("ep1").title("Test").build();

    assertFalse(FeedEpisodeVisibilityHelper.matchesFeedFilter(null, episode));
    assertFalse(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, null));
    assertFalse(FeedEpisodeVisibilityHelper.matchesFeedFilter(null, null));
  }

  @Test
  public void testMatchesFeedFilter_DefaultLiveVodSettings() {
    Channel channel = Channel.builder()
        .id("ch1")
        .excludeLiveVod(false)
        .onlyLiveVod(false)
        .build();

    Episode regularEpisode = Episode.builder().id("ep1").title("Regular").liveVod(false).build();
    Episode liveVodEpisode = Episode.builder().id("ep2").title("Live VOD").liveVod(true).build();
    Episode nullLiveVodEpisode = Episode.builder().id("ep3").title("Unknown").liveVod(null).build();

    assertTrue(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, regularEpisode));
    assertTrue(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, liveVodEpisode));
    assertTrue(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, nullLiveVodEpisode));
  }

  @Test
  public void testMatchesFeedFilter_ExcludeLiveVod() {
    Channel channel = Channel.builder()
        .id("ch1")
        .excludeLiveVod(true)
        .onlyLiveVod(false)
        .build();

    Episode regularEpisode = Episode.builder().id("ep1").title("Regular").liveVod(false).build();
    Episode liveVodEpisode = Episode.builder().id("ep2").title("Live VOD").liveVod(true).build();
    Episode nullLiveVodEpisode = Episode.builder().id("ep3").title("Unknown").liveVod(null).build();

    assertTrue(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, regularEpisode));
    assertFalse(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, liveVodEpisode));
    assertTrue(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, nullLiveVodEpisode));
  }

  @Test
  public void testMatchesFeedFilter_OnlyLiveVod() {
    Channel channel = Channel.builder()
        .id("ch1")
        .excludeLiveVod(false)
        .onlyLiveVod(true)
        .build();

    Episode regularEpisode = Episode.builder().id("ep1").title("Regular").liveVod(false).build();
    Episode liveVodEpisode = Episode.builder().id("ep2").title("Live VOD").liveVod(true).build();
    Episode nullLiveVodEpisode = Episode.builder().id("ep3").title("Unknown").liveVod(null).build();

    assertFalse(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, regularEpisode));
    assertTrue(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, liveVodEpisode));
    assertFalse(FeedEpisodeVisibilityHelper.matchesFeedFilter(channel, nullLiveVodEpisode));
  }

  @Test
  public void testFilterVisibleEpisodes_OnlyLiveVod() {
    Channel channel = Channel.builder()
        .id("ch1")
        .onlyLiveVod(true)
        .build();

    Episode ep1 = Episode.builder().id("1").title("Video 1").liveVod(false).build();
    Episode ep2 = Episode.builder().id("2").title("Livestream 1").liveVod(true).build();
    Episode ep3 = Episode.builder().id("3").title("Video 2").liveVod(null).build();
    Episode ep4 = Episode.builder().id("4").title("Livestream 2").liveVod(true).build();

    List<Episode> visible = FeedEpisodeVisibilityHelper.filterVisibleEpisodes(channel, List.of(ep1, ep2, ep3, ep4));

    assertEquals(2, visible.size());
    assertEquals("2", visible.get(0).getId());
    assertEquals("4", visible.get(1).getId());
  }

  @Test
  public void testFilterVisibleEpisodes_ExcludeLiveVod() {
    Channel channel = Channel.builder()
        .id("ch1")
        .excludeLiveVod(true)
        .build();

    Episode ep1 = Episode.builder().id("1").title("Video 1").liveVod(false).build();
    Episode ep2 = Episode.builder().id("2").title("Livestream 1").liveVod(true).build();
    Episode ep3 = Episode.builder().id("3").title("Video 2").liveVod(null).build();
    Episode ep4 = Episode.builder().id("4").title("Livestream 2").liveVod(true).build();

    List<Episode> visible = FeedEpisodeVisibilityHelper.filterVisibleEpisodes(channel, List.of(ep1, ep2, ep3, ep4));

    assertEquals(2, visible.size());
    assertEquals("1", visible.get(0).getId());
    assertEquals("3", visible.get(1).getId());
  }
}
