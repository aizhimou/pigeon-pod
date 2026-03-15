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
    // 这组断言直接定义了当前指数退避分钟数。
    // 如果你要调小/调大每一轮等待时间，先改 EpisodeRetryPolicy，
    // 再同步修改这里的预期值即可。
    assertEquals(30L, EpisodeRetryPolicy.resolveBackoffMinutes(1));
    assertEquals(60L, EpisodeRetryPolicy.resolveBackoffMinutes(2));
    assertEquals(120L, EpisodeRetryPolicy.resolveBackoffMinutes(3));
    assertEquals(240L, EpisodeRetryPolicy.resolveBackoffMinutes(4));
    assertEquals(480L, EpisodeRetryPolicy.resolveBackoffMinutes(5));
    assertEquals(480L, EpisodeRetryPolicy.resolveBackoffMinutes(6));
  }

  @Test
  void shouldAllowFiveAutomaticRetriesAfterFailures() {
    // 这里验证的是“最多允许调度多少次自动重试”，不是包含首次下载在内的总尝试次数。
    // 所以当上限为 5 时，retryNumber = 5 仍然允许，retryNumber = 6 就停止自动重试。
    assertTrue(EpisodeRetryPolicy.canScheduleNextRetry(1));
    assertTrue(EpisodeRetryPolicy.canScheduleNextRetry(5));
    assertFalse(EpisodeRetryPolicy.canScheduleNextRetry(6));
    assertFalse(EpisodeRetryPolicy.canScheduleNextRetry(null));
  }

  @Test
  void shouldCalculateNextRetryAtFromFailureTime() {
    LocalDateTime failedAt = LocalDateTime.of(2026, 3, 11, 10, 0);

    // 这里验证“失败发生时间 + 退避分钟数 = nextRetryAt” 的最终落库结果。
    assertEquals(LocalDateTime.of(2026, 3, 11, 10, 30),
        EpisodeRetryPolicy.calculateNextRetryAt(1, failedAt));
    assertEquals(LocalDateTime.of(2026, 3, 11, 18, 0),
        EpisodeRetryPolicy.calculateNextRetryAt(5, failedAt));
    assertNull(EpisodeRetryPolicy.calculateNextRetryAt(6, failedAt));
  }
}
