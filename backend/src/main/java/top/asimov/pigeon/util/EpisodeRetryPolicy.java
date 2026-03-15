package top.asimov.pigeon.util;

import java.time.LocalDateTime;

public final class EpisodeRetryPolicy {

  /**
   * 自动重试上限。
   *
   * <p>这里的数字表示“失败后允许再自动尝试多少次下载”，而不是包含首次下载在内的总执行次数。
   * 例如：
   * <ul>
   *   <li>首次下载失败后，retryNumber = 1，允许调度第 1 次自动重试。</li>
   *   <li>当 retryNumber = 5 时，仍允许调度第 5 次自动重试。</li>
   *   <li>当 retryNumber = 6 时，说明已经超过上限，不再自动重试，后续交给通知和人工处理。</li>
   * </ul>
   *
   * <p>如果你要改自动重试总次数，先改这里，再同步更新 {@code EpisodeRetryPolicyTest} 里的断言。
   */
  public static final int MAX_AUTO_RETRY_ATTEMPTS = 5;

  /**
   * 第一次自动重试的等待时间（单位：分钟）。
   *
   * <p>当前策略不是“立即重试”，而是第一次失败后等待 30 分钟。
   */
  private static final long INITIAL_BACKOFF_MINUTES = 30L;

  /**
   * 指数退避的最大等待时间上限（单位：分钟）。
   *
   * <p>当前封顶为 8 小时，避免失败任务等待时间无限增长。
   */
  private static final long MAX_BACKOFF_MINUTES = 8L * 60L;

  private EpisodeRetryPolicy() {
  }

  public static boolean canScheduleNextRetry(Integer retryNumber) {
    return retryNumber != null && retryNumber <= MAX_AUTO_RETRY_ATTEMPTS;
  }

  /**
   * 根据失败发生时间计算下一次允许自动重试的时间点。
   *
   * <p>当前退避节奏固定为：
   * <ul>
   *   <li>retryNumber = 1 -> 30 分钟后</li>
   *   <li>retryNumber = 2 -> 60 分钟后</li>
   *   <li>retryNumber = 3 -> 120 分钟后</li>
   *   <li>retryNumber = 4 -> 240 分钟后</li>
   *   <li>retryNumber = 5 -> 480 分钟后（8 小时）</li>
   *   <li>retryNumber > 5 -> 不再自动重试，返回 null</li>
   * </ul>
   *
   * <p>测试最常直接依赖这个方法。修改退避节奏时，优先从这里和
   * {@link #resolveBackoffMinutes(int)} 入手，再同步调整测试断言。
   */
  public static LocalDateTime calculateNextRetryAt(Integer retryNumber, LocalDateTime failedAt) {
    if (!canScheduleNextRetry(retryNumber) || failedAt == null) {
      return null;
    }
    return failedAt.plusMinutes(resolveBackoffMinutes(retryNumber));
  }

  /**
   * 计算某一次自动重试对应的退避分钟数。
   *
   * <p>实现方式是从 {@link #INITIAL_BACKOFF_MINUTES} 开始倍增，并受
   * {@link #MAX_BACKOFF_MINUTES} 限制：
   * <pre>
   * 1 -> 30
   * 2 -> 60
   * 3 -> 120
   * 4 -> 240
   * 5 -> 480
   * 6 -> 480（虽然这里仍返回 480，但上层不会再调度，因为已超过重试上限）
   * </pre>
   *
   * <p>如果你只是想改测试里的等待时间预期，通常看这个方法最直接。
   */
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
