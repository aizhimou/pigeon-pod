package top.asimov.pigeon.util;

import java.time.Duration;

public final class EpisodeDurationHelper {

  private EpisodeDurationHelper() {
  }

  public static Integer parseDurationSeconds(String duration) {
    if (duration == null || duration.isBlank()) {
      return null;
    }
    try {
      return Math.toIntExact(Duration.parse(duration).toSeconds());
    } catch (Exception ex) {
      return null;
    }
  }
}
