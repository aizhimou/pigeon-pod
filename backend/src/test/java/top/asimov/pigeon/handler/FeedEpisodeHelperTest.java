package top.asimov.pigeon.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import top.asimov.pigeon.model.entity.Episode;

public class FeedEpisodeHelperTest {

  @Test
  public void testFindLatestEpisode_NullOrEmptyList() {
    assertTrue(FeedEpisodeHelper.findLatestEpisode(null).isEmpty());
    assertTrue(FeedEpisodeHelper.findLatestEpisode(List.of()).isEmpty());
  }

  @Test
  public void testFindLatestEpisode_AllNullPublishedAtReturnsEmpty() {
    Episode ep1 = Episode.builder().id("1").title("Ep 1").publishedAt(null).build();
    Episode ep2 = Episode.builder().id("2").title("Ep 2").publishedAt(null).build();

    Optional<Episode> result = FeedEpisodeHelper.findLatestEpisode(List.of(ep1, ep2));
    assertTrue(result.isEmpty());
  }

  @Test
  public void testFindLatestEpisode_WithNullElementsAndNullPublishedAt() {
    LocalDateTime now = LocalDateTime.now();
    Episode ep1 = Episode.builder().id("1").title("Ep 1").publishedAt(null).build();
    Episode ep2 = Episode.builder().id("2").title("Ep 2").publishedAt(now.minusDays(2)).build();
    Episode ep3 = Episode.builder().id("3").title("Ep 3").publishedAt(now).build();

    List<Episode> episodes = Arrays.asList(ep1, null, ep2, ep3);
    Optional<Episode> result = FeedEpisodeHelper.findLatestEpisode(episodes);

    assertTrue(result.isPresent());
    assertEquals("3", result.get().getId());
  }
}
