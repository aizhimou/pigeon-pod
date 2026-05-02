# PigeonPod DOWNLOADING 任务自动回收方案

## 1. 背景与目标

当前下载任务进入 `DOWNLOADING` 后，主要依赖 `DownloadHandler.download()` 正常结束来写回最终状态。如果 yt-dlp 进程、网络连接、外部 downloader、S3 上传或 Java 线程异常卡住，任务可能长期停留在 `DOWNLOADING`。

现有系统只在应用启动时通过 `StaleTaskCleaner` 将遗留 `DOWNLOADING` 任务重置为 `PENDING`。这意味着系统运行期间出现卡住任务时，除重启外缺少自动恢复手段。

本方案目标：

- 为 yt-dlp 下载进程增加单次执行超时，避免下载线程无限等待。
- 为 `DOWNLOADING` 状态增加持久化开始时间，并由调度器定期回收超时任务。
- 超时任务复用现有 `FAILED + retryNumber + nextRetryAt` 自动重试机制。
- 保持改动集中在下载执行、任务状态和调度层，不重构下载流水线。

## 2. 现状与约束

- 下载状态机为 `READY/PENDING/DOWNLOADING/COMPLETED/FAILED`。
- `DownloadTaskHelper.submitDownloadTask()` 在提交线程池前将任务标记为 `DOWNLOADING`。
- `DownloadHandler.download()` 当前使用 `process.waitFor()` 等待 yt-dlp 退出。该调用没有超时限制。
- `DownloadScheduler` 每 30 秒补位消费 `PENDING` 和到期 `FAILED`，不会扫描运行中过久的 `DOWNLOADING`。
- `retryNumber`、`nextRetryAt` 已持久化在 `episode` 表，失败自动重试计划可跨重启恢复。
- 下载线程池为 3 线程、无队列模式。少量卡住任务就可能耗尽下载槽位。

结论：需要同时处理“进程仍由当前 JVM 管理但卡住”和“DB 中已经遗留 DOWNLOADING 状态”两类问题。

## 3. 设计决策

- 采用两层防护：
  - 进程级超时：`DownloadHandler` 等待 yt-dlp 时设置上限，超时后终止进程树并标记失败。
  - 状态级兜底：`DownloadScheduler` 定期扫描超过阈值的 `DOWNLOADING` 任务，转为 `FAILED` 并安排自动重试。
- 超时后统一进入现有失败重试策略，而不是直接无限重新排队。
- 新增 `episode.download_started_at` 字段，用于判断运行中任务是否超时。
- `DOWNLOADING` 启动时间只在状态切换为 `DOWNLOADING` 时写入，任务结束时清空。
- 只暴露单次 yt-dlp 进程超时配置；stale 回收阈值和扫描批量在代码中固定，减少运行配置面。

## 4. 数据模型改造

新增 Flyway 迁移：

```sql
ALTER TABLE episode
    ADD COLUMN download_started_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_episode_downloading_started_at
    ON episode (download_status, download_started_at);
```

实体改造：

- `Episode` 新增 `LocalDateTime downloadStartedAt`。
- 字段使用 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`，确保任务完成或失败时可以清空。

兼容策略：

- 旧数据默认 `download_started_at = NULL`。
- 启动时已有 `DOWNLOADING` 任务仍由 `StaleTaskCleaner` 处理，保持现有行为。
- 运行中的新下载任务会在进入 `DOWNLOADING` 时写入开始时间。

## 5. 配置项

建议新增：

```yaml
pigeon:
  download:
    process-timeout-minutes: 60
```

含义：

- `process-timeout-minutes`：单次 yt-dlp 下载进程最大运行时间。
- stale `DOWNLOADING` 回收阈值固定为 `process-timeout-minutes + 10` 分钟。
- stale `DOWNLOADING` 每轮扫描上限固定为 `100`。

默认值建议：

- 进程超时默认 60 分钟，避免少量卡住任务长时间占满 3 个下载槽位。
- 状态回收固定比进程超时晚 10 分钟，避免正常超时处理和 scheduler 回收重复竞争。

## 6. 进程级超时设计

### 6.1 当前问题点

`DownloadHandler` 当前流程是：

```java
Process process = getProcess(...);
// 读取 stdout/stderr
exitCode = process.waitFor();
```

主要风险：

- `waitFor()` 没有超时，yt-dlp 不退出时下载线程永久占用。
- stdout 和 stderr 当前按顺序读取，极端情况下某个输出流缓冲区写满可能导致子进程阻塞。
- 只终止父进程可能留下 ffmpeg、外部 downloader 等子进程。

### 6.2 推荐实现

新增一个小型进程执行辅助方法，职责限定在 `DownloadHandler` 内或独立为 `ProcessRunner`：

1. 使用 `ProcessBuilder` 启动 yt-dlp。
2. 将 stdout/stderr 合并到同一个日志文件，或分别用两个异步 reader 消费。
3. 使用 `process.waitFor(timeout, TimeUnit.MINUTES)`。
4. 超时后先温和终止进程树，再强制终止残留进程。
5. 返回 `exitCode + outputTail`，用于写入 `errorLog`。

伪代码：

```java
private ProcessResult runYtDlpWithTimeout(List<String> command, Map<String, String> env,
    long timeoutMinutes) {
  ProcessBuilder builder = new ProcessBuilder(command);
  builder.redirectErrorStream(true);
  Path outputLog = Files.createTempFile("pigeon-ytdlp-", ".log");
  builder.redirectOutput(outputLog.toFile());

  Process process = builder.start();
  boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
  if (!finished) {
    destroyProcessTree(process);
    String outputTail = readLogTail(outputLog);
    throw new DownloadProcessTimeoutException(
        "yt-dlp timed out after " + timeoutMinutes + " minutes\n" + outputTail);
  }

  return new ProcessResult(process.exitValue(), readLogTail(outputLog));
}
```

进程树终止策略：

```java
private void destroyProcessTree(Process process) {
  ProcessHandle handle = process.toHandle();
  handle.descendants().forEach(ProcessHandle::destroy);
  handle.destroy();

  if (!process.waitFor(10, TimeUnit.SECONDS)) {
    handle.descendants()
        .filter(ProcessHandle::isAlive)
        .forEach(ProcessHandle::destroyForcibly);
    if (handle.isAlive()) {
      handle.destroyForcibly();
    }
  }
}
```

说明：

- Java 17 支持 `Process.waitFor(long, TimeUnit)` 和 `ProcessHandle`，满足当前后端运行条件。
- 应优先复用或抽取 `YtDlpRuntimeService.runCommand(...)` 中已有的超时执行思路，但下载主流程需要保留输出文件、媒体文件、S3 上传和错误日志处理，因此不建议直接复用更新任务的私有方法。
- 超时异常最终由 `DownloadHandler` 现有 `catch` 捕获，调用 `markDownloadFailed()`，进入现有指数退避自动重试。

## 7. 状态级兜底回收设计

### 7.1 进入 DOWNLOADING 时写入开始时间

改造 `TaskStatusHelper.tryMarkDownloading()`：

- 当前允许 `READY/PENDING/FAILED -> DOWNLOADING`。
- 更新状态时同时设置：
  - `download_status = 'DOWNLOADING'`
  - `download_started_at = now`
  - `auto_download_after = null`
  - `next_retry_at = null`
  - `failure_notified_at = null`

建议新增 mapper 方法，避免复用只更新状态的旧 SQL 时遗漏字段。

### 7.2 任务结束时清空开始时间

`DownloadHandler` 在成功和失败最终落库前都应清空：

- `downloadStartedAt = null`

这样 `COMPLETED`、`FAILED`、`READY`、`PENDING` 都不会携带运行中时间戳。

### 7.3 调度器回收超时 DOWNLOADING

在 `DownloadScheduler.processPendingDownloads()` 开头增加一步，顺序建议：

1. 回收 stale `DOWNLOADING`。
2. 提升到期延迟自动下载任务。
3. 补位 `PENDING`。
4. 补位到期 `FAILED` 重试。

查询条件：

```sql
SELECT *
FROM episode
WHERE download_status = 'DOWNLOADING'
  AND download_started_at IS NOT NULL
  AND download_started_at <= #{staleBefore}
ORDER BY download_started_at ASC
LIMIT #{limit}
```

回收动作：

- 将任务标记为 `FAILED`。
- 写入错误日志，例如 `download task timed out in DOWNLOADING state and was recovered by scheduler`。
- 调用统一的重试计划逻辑，递增 `retryNumber` 并计算 `nextRetryAt`。
- 清空 `downloadStartedAt`、媒体路径、媒体大小、ETag、媒体类型。

为了避免把 `DownloadHandler.scheduleNextRetry()` 复制到 service 中，建议将重试计划抽成公共 helper，例如：

- `EpisodeRetryPlanner.scheduleNextRetry(Episode episode, LocalDateTime failedAt)`

`DownloadHandler` 和 scheduler 回收逻辑共同调用它。

## 8. 并发与一致性

存在一种可接受竞争：scheduler 刚判定任务超时，下载线程同时完成。

建议用条件更新降低误伤：

- 回收时带条件：`WHERE id = ? AND download_status = 'DOWNLOADING' AND download_started_at = ?`
- 下载成功写回时仍以 `updateById` 为主，但在进入最终写回前可重新读取当前状态；如果已被 scheduler 回收为 `FAILED`，需要避免把超时回收后的任务误写为 `COMPLETED`。

推荐的最小实现：

- scheduler 回收只处理明显超过阈值的任务，阈值大于进程超时。
- `DownloadHandler` 在超时路径会主动终止进程并写失败，正常情况下 scheduler 不会先于进程超时触发。
- 对于极端并发，以“后写入者覆盖”为可接受风险，但日志必须记录 episodeId、startedAt、timeout 配置，便于排查。

更严格的实现：

- 为下载写回增加条件更新或版本字段。
- MVP 不建议引入版本字段，避免扩大数据模型复杂度。

## 9. 与现有重试和通知的关系

超时回收后的任务复用现有失败重试：

```text
DOWNLOADING 超时
  -> FAILED
  -> retryNumber += 1
  -> nextRetryAt = now + backoff
  -> DownloadScheduler 到期后重新提交
```

耗尽自动重试后仍走现有失败通知：

```text
FAILED + retryNumber > 5 + nextRetryAt = null + failureNotifiedAt = null
  -> FailedDownloadNotifier 汇总通知
```

这可以保持 Dashboard、失败通知、手动重试的用户心智一致。

## 10. 前端影响

MVP 不需要新增前端页面。

现有 Dashboard 已能展示 `DOWNLOADING`、`FAILED` 数量。任务被回收后会自然从 `DOWNLOADING` 变为 `FAILED`，随后按自动重试计划处理。

可选增强：

- 在失败详情中展示超时错误日志。
- 后续若增加系统设置 UI，可暴露下载进程超时和 stale 回收阈值。

## 11. 日志与可观测性

建议新增日志：

- yt-dlp 启动：`episodeId`、timeout、runtime mode、命令脱敏摘要。
- yt-dlp 超时：`episodeId`、timeout、进程 pid、输出尾部。
- 进程树终止结果：温和终止数量、强制终止数量。
- scheduler 回收：本轮扫描数量、成功回收数量、失败数量、episodeIds。
- 竞争保护未命中：任务状态已变化时跳过回收。

错误日志写入 `episode.errorLog` 时应控制长度，避免数据库中保存过大的 yt-dlp 输出。

## 12. 实施步骤

1. 新增 Flyway 迁移：`episode.download_started_at` + 索引。
2. 更新 `Episode` 实体字段。
3. 增加下载配置类或 `@Value` 配置：
   - `processTimeoutMinutes`
   - `staleDownloadingTimeoutMinutes = processTimeoutMinutes + 10`
   - `staleDownloadingScanLimit = 100`
4. 改造 `TaskStatusHelper.tryMarkDownloading()`，进入 `DOWNLOADING` 时写入 `downloadStartedAt`。
5. 改造 `DownloadHandler`：
   - 使用带超时的进程执行。
   - 避免 stdout/stderr 顺序读取造成阻塞。
   - 超时后杀进程树并标记失败。
   - 成功/失败时清空 `downloadStartedAt`。
6. 抽取重试计划 helper，供 `DownloadHandler` 和 scheduler 回收共用。
7. 扩展 `EpisodeMapper` / `EpisodeService`：
   - 查询 stale `DOWNLOADING`。
   - 条件回收并安排重试。
8. 改造 `DownloadScheduler`，每轮先回收 stale `DOWNLOADING`。
9. 保留 `StaleTaskCleaner`，用于应用重启后的启动期兜底。
10. 增加测试。

## 13. 测试计划

单元测试：

- `EpisodeRetryPolicy` 保持现有断言。
- 新增重试计划 helper 测试：首次超时、第五次超时、第六次耗尽。
- stale 查询和条件更新 mapper 测试。

集成测试：

- 构造 `DOWNLOADING + downloadStartedAt` 早于阈值的任务，执行 scheduler 后变为 `FAILED` 且写入 `nextRetryAt`。
- 构造未超时 `DOWNLOADING`，scheduler 不处理。
- 构造 `downloadStartedAt = null` 的 `DOWNLOADING`，启动清理仍按现有逻辑处理，运行期 scheduler 不误处理。

手工验证：

- 使用会长时间运行的 yt-dlp 命令或测试替身，确认进程超时后任务进入 `FAILED`。
- 确认超时后下载槽位释放，后续任务可以继续补位。
- 确认重启后遗留 `DOWNLOADING` 仍被 `StaleTaskCleaner` 重置为 `PENDING`。
- 确认失败重试耗尽后仍能触发失败通知。

## 14. 风险与取舍

- 超时时间过短会误杀大文件或慢网络下载。默认 60 分钟可以通过环境变量调整。
- 杀进程树在不同操作系统上行为可能略有差异。Java 17 `ProcessHandle` 能覆盖主流场景，但仍需日志记录残留进程信息。
- scheduler 回收无法真正杀死仍在运行的外部进程；它只修复 DB 状态。因此进程级超时是主防线，scheduler 是兜底。
- 不建议在 MVP 中增加复杂的“暂停/取消 DOWNLOADING”用户操作；先解决自动恢复和槽位释放问题。

## 15. 验收标准

1. yt-dlp 主下载进程超过配置超时后会被终止，任务进入 `FAILED` 并安排自动重试。
2. 长时间停留在 `DOWNLOADING` 且超过 stale 阈值的任务会被 scheduler 自动回收。
3. 回收后的任务复用现有 `retryNumber/nextRetryAt` 退避策略。
4. 已完成、失败、待下载、未超时下载中的任务不会被误回收。
5. 应用重启后的 `DOWNLOADING` 遗留任务仍由现有启动清理兜底。
6. 下载线程槽位在超时后能释放，后续下载任务可继续执行。
