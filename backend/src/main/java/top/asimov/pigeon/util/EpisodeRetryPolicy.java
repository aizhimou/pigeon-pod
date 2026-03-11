package top.asimov.pigeon.util;

import java.time.LocalDateTime;

public final class EpisodeRetryPolicy {

  public static final int MAX_AUTO_RETRY_ATTEMPTS = 5;
  private static final long INITIAL_BACKOFF_MINUTES = 30L;
  private static final long MAX_BACKOFF_MINUTES = 8L * 60L;

  private EpisodeRetryPolicy() {
  }

  public static boolean canScheduleNextRetry(Integer retryNumber) {
    return retryNumber != null && retryNumber <= MAX_AUTO_RETRY_ATTEMPTS;
  }

  public static LocalDateTime calculateNextRetryAt(Integer retryNumber, LocalDateTime failedAt) {
    if (!canScheduleNextRetry(retryNumber) || failedAt == null) {
      return null;
    }
    return failedAt.plusMinutes(resolveBackoffMinutes(retryNumber));
  }

  static long resolveBackoffMinutes(int retryNumber) {
    if (retryNumber <= 0) {
      return INITIAL_BACKOFF_MINUTES;
    }
    long delayMinutes = INITIAL_BACKOFF_MINUTES;
    for (int attempt = 1; attempt < retryNumber; attempt++) {
      if (delayMinutes >= MAX_BACKOFF_MINUTES) {
        return MAX_BACKOFF_MINUTES;
      }
      delayMinutes = Math.min(delayMinutes * 2, MAX_BACKOFF_MINUTES);
    }
    return delayMinutes;
  }
}
