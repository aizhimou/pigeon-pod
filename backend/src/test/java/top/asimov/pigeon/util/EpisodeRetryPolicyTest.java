package top.asimov.pigeon.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EpisodeRetryPolicyTest {

  @Test
  void shouldUseExponentialBackoffWithEightHourCap() {
    assertEquals(30L, EpisodeRetryPolicy.resolveBackoffMinutes(1));
    assertEquals(60L, EpisodeRetryPolicy.resolveBackoffMinutes(2));
    assertEquals(120L, EpisodeRetryPolicy.resolveBackoffMinutes(3));
    assertEquals(240L, EpisodeRetryPolicy.resolveBackoffMinutes(4));
    assertEquals(480L, EpisodeRetryPolicy.resolveBackoffMinutes(5));
    assertEquals(480L, EpisodeRetryPolicy.resolveBackoffMinutes(6));
  }

  @Test
  void shouldAllowFiveAutomaticRetriesAfterFailures() {
    assertTrue(EpisodeRetryPolicy.canScheduleNextRetry(1));
    assertTrue(EpisodeRetryPolicy.canScheduleNextRetry(5));
    assertFalse(EpisodeRetryPolicy.canScheduleNextRetry(6));
    assertFalse(EpisodeRetryPolicy.canScheduleNextRetry(null));
  }

  @Test
  void shouldCalculateNextRetryAtFromFailureTime() {
    LocalDateTime failedAt = LocalDateTime.of(2026, 3, 11, 10, 0);

    assertEquals(LocalDateTime.of(2026, 3, 11, 10, 30),
        EpisodeRetryPolicy.calculateNextRetryAt(1, failedAt));
    assertEquals(LocalDateTime.of(2026, 3, 11, 18, 0),
        EpisodeRetryPolicy.calculateNextRetryAt(5, failedAt));
    assertNull(EpisodeRetryPolicy.calculateNextRetryAt(6, failedAt));
  }
}
