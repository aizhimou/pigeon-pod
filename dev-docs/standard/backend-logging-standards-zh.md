# Backend 日志编写规范

本文定义 PigeonPod 后端日志的代码编写规范。

目标是让日志在自托管部署、Docker stdout、可选文件日志和本地开发环境中都容易检索、理解和排障，同时保持实现简单、直接、低维护成本。

本文关注业务代码如何写日志，不重复描述日志采集、部署和外部 observability 系统。PigeonPod 当前不强绑定 Loki、Grafana、ELK 或第三方 logging SDK。

## 1. 适用范围

适用于后端 Java 代码：

- `backend/src/main/java/top/asimov/pigeon/**`
- `backend/src/main/java/db/migration/**`

重点适用于：

- controller / service / helper / handler 中的业务日志
- scheduler / listener / `@Async` 任务日志
- yt-dlp、YouTube Data API、Bilibili API、S3、SMTP、webhook 等外部调用日志
- 下载、同步、RSS、媒体分发、存储清理、配置变更等排障日志

## 2. 基础原则

### 2.1 保持 Spring Boot 默认日志体系

后端继续使用 Spring Boot 默认的 `SLF4J + Logback`。

默认日志输出到 console。生产环境可以继续通过 `PIGEON_LOG_FILE` 开启文件日志，或由 Docker / systemd / 平台侧采集 stdout。

不要为了业务日志新增日志服务、日志 SDK、日志数据库或复杂 agent。

### 2.2 代码统一使用 SLF4J

新代码统一使用 Lombok `@Slf4j`：

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChannelService {
}
```

规则：

- 新代码不要使用 `System.out.println`、`printStackTrace`。
- 新代码不要直接创建 `LoggerFactory.getLogger(...)`，除非是 Flyway Java migration 等不适合引入 Lombok 的特殊场景。
- 不要使用 `@Log4j2`。

### 2.3 不新增全局日志封装层

不要创建 `LogHelper`、`BusinessLogger`、`LoggerFacade` 这类全局封装层。

日志调用应停留在发生业务事件的代码附近，方便从日志直接回到代码。允许在单个类内部为重复业务事件抽私有方法，见第 9 节。

### 2.4 所有服务器日志统一遵循规范

本规范适用于新增日志，也适用于现有服务器日志的全量重构。

要求：

- 所有后端服务器输出日志都必须遵循本文。
- 当前系统中的中文日志、无业务前缀日志、字段名不统一日志，应按本文统一重构。
- 日志重构只调整服务器日志输出，不改变 API response、业务行为、数据库结构或 i18n 文案。
- 重构时按业务区域分批推进，避免一次提交同时混入日志格式调整和业务逻辑修改。

## 3. 日志格式

### 3.1 统一使用业务前缀 + event + key=value

推荐格式：

```java
log.info("[download] completed: episodeId={} feedId={} mediaType={} elapsedMs={}",
    episodeId, feedId, mediaType, elapsedMs);
```

结构：

```text
[scope] event: key1={} key2={} key3={}
```

规则：

- `scope` 使用固定英文短前缀，便于搜索。
- `event` 使用简短英文动词或状态描述。
- 业务字段统一使用 `key=value` 风格。
- Java 日志字段名统一使用 lowerCamelCase。
- 使用参数化日志，不要字符串拼接。
- 同一个概念不要混用多个字段名。

不要这样写：

```java
log.info("Download completed: {}", episode.getTitle());
log.info("Download completed: " + episodeId);
log.info("[download] episode {} completed", episodeId);
log.error("Failed to delete custom cover for feed " + feedId, e);
```

### 3.2 日志正文语言

服务器输出日志全部使用英文，不使用中文或任何其他语言。

要求：

- `scope` 使用英文。
- `event` 使用英文。
- 字段名使用英文 lowerCamelCase。
- 固定日志文本使用英文。
- `reason`、`status`、`mode` 等字段值优先使用稳定的英文枚举值或英文短语。
- exception message 如果来自第三方库或系统底层，可以保留原始内容。

原因：

- 英文字段更适合搜索、聚合和后续接入 observability 工具。
- 项目中很多 runtime、API、状态机字段本身就是英文。
- 可以避免多语言标点、空格和翻译差异导致检索不稳定。

本文只规范服务器输出日志。面向用户的 API response、前端文案、业务错误消息和 `BusinessException` message 仍然按现有 i18n 机制处理，不受本文限制。

## 4. 常用 scope

优先使用以下 scope：

| Scope | 适用场景 |
| --- | --- |
| `[auth]` | 登录、登出、验证码、Sa-Token、权限 |
| `[account]` | 账号设置、系统设置、默认 feed 配置 |
| `[feed]` | feed 新增、删除、配置更新、封面、预览 |
| `[feed-sync]` | 频道/播放列表同步、历史补抓、初始化 |
| `[episode]` | episode 状态变更、批量操作、元数据清理 |
| `[download]` | 下载任务状态机、下载队列、下载成功/失败 |
| `[yt-dlp]` | yt-dlp 命令执行、参数、进程退出、超时 |
| `[yt-dlp-runtime]` | yt-dlp managed runtime 安装、升级、回滚、裁剪 |
| `[youtube-api]` | YouTube Data API 调用、配额、阻断 |
| `[bilibili-api]` | Bilibili API 调用、重试、解析 |
| `[rss]` | RSS 生成、RSS 访问、enclosure、Podcasting 2.0 标签 |
| `[media]` | `/media/**` 媒体、字幕、章节、封面响应 |
| `[storage]` | LOCAL/S3 存储、对象上传删除、临时目录清理 |
| `[notification]` | 失败下载通知编排 |
| `[notification-mail]` | SMTP Email 通知 |
| `[notification-webhook]` | Generic Webhook Plus 通知 |
| `[scheduler]` | 通用定时任务扫描、补位、清理 |
| `[database]` | SQLite busy、迁移、数据修复 |
| `[config]` | runtime config apply、启动配置回填 |
| `[migration]` | Flyway Java migration |

新增 scope 时保持短、稳定、英文小写。不要为一次性日志创建过细 scope。

## 5. 常用字段名

优先使用以下字段名：

| 概念 | 字段名 |
| --- | --- |
| 用户 | `userId` |
| feed | `feedId` |
| channel | `channelId` |
| playlist | `playlistId` |
| episode | `episodeId` |
| 下载任务 | `taskId` |
| 外部平台 | `platform` |
| 外部平台 ID | `platformId` |
| YouTube channel ID | `youtubeChannelId` |
| YouTube playlist ID | `youtubePlaylistId` |
| YouTube video ID | `videoId` |
| 状态 | `status` |
| 原因 | `reason` |
| 模式 | `mode` |
| 类型 | `type` |
| 耗时 | `elapsedMs` |
| 数量 | `count` / `scanned` / `created` / `updated` / `deleted` / `failed` / `skipped` |
| 文件路径 | `path` / `filePath` |
| 对象存储 key | `objectKey` |
| HTTP 状态码 | `statusCode` |
| 退出码 | `exitCode` |
| 重试次数 | `retryNumber` |
| 下次重试时间 | `nextRetryAt` |

同一个概念不要混用 `id`、`episode`、`episode_id`、`episodeId`。

## 6. 日志级别

### 6.1 debug

用于默认不需要看的排查细节。

适合：

- 外部请求的参数摘要
- 跳过的低价值分支
- 大量循环中的单条处理细节
- 文件扫描、章节解析、字幕清洗等辅助流程细节
- 只在本地或临时排障时有用的信息

不要在 `debug` 中输出敏感信息。

### 6.2 info

用于关键业务状态变化和可回溯的摘要日志。

适合：

- 用户登录成功、登出成功
- feed 创建、配置更新、删除完成
- scheduler 扫描开始/完成摘要
- 同步、历史补抓、初始化完成摘要
- 下载任务开始、成功、跳过摘要
- yt-dlp runtime 切换、升级完成、回滚完成
- 通知发送成功

`info` 日志应控制数量。批量处理大量数据时，优先输出批次摘要，不要为每一条普通成功记录打 `info`，否则日志会快速失去检索价值。

### 6.3 warn

用于可恢复、已兜底、需要关注但不代表当前请求或任务必然失败的问题。

适合：

- 外部服务临时失败，后续会重试
- SQLite busy，调用方会重试或返回明确错误
- 数据状态不一致但已跳过或修复
- 下载任务不满足执行条件，被跳过或回滚
- 删除临时文件失败但主流程已完成
- 自动重试耗尽，等待用户处理或通知汇总

如果需要排查堆栈，保留异常对象：

```java
log.warn("[download] enqueue failed, keep pending for scheduler: episodeId={} reason={}",
    episodeId, e.getMessage(), e);
```

### 6.4 error

用于请求失败、任务最终失败、人工需要关注的问题。

适合：

- 非预期异常导致 API 请求无法完成
- 下载、同步、通知、存储等后台任务最终失败
- 数据修复失败
- RSS 或媒体响应构建失败
- yt-dlp runtime 升级失败且回滚失败

不要把普通业务校验失败大量打成 `error`，否则错误日志会失真。

## 7. 异常日志

### 7.1 预期业务异常不要刷堆栈

例如参数非法、权限不足、资源不存在、登录失效、下载状态不允许操作等预期业务结果，通常不需要输出完整堆栈。

推荐：

```java
log.info("[episode] retry rejected: episodeId={} status={} reason={}",
    episodeId, status, reason);
```

全局异常处理里的 `BusinessException`、`NotLoginException`、`NotPermissionException` 默认不应打 `error` 堆栈。

### 7.2 非预期异常要保留上下文和堆栈

推荐：

```java
log.error("[rss] enclosure build failed: episodeId={} mediaFilePath={}",
    episodeId, mediaFilePath, e);
```

规则：

- 上下文字段放在异常对象之前。
- 异常对象作为最后一个参数。
- 不要只写 `e.getMessage()` 而丢掉堆栈，除非这是明确可忽略的辅助流程。

### 7.3 避免重复记录同一个异常

如果当前层记录了异常并继续抛出，上层 `GlobalExceptionHandler` 可能再次记录。

默认选择最有业务上下文的位置记录异常。只有上下层日志提供不同排障价值时，才允许重复记录。

## 8. 上下文与异步任务

### 8.1 API 请求

如果后续需要在所有 API 请求日志中附带用户 ID 或 request ID，优先使用 Filter / Interceptor + MDC：

- 请求进入时解析 `requestId`。
- 登录用户存在时写入 `userId`。
- 请求结束时必须清理 MDC，避免线程复用导致上下文泄漏。

业务日志中仍然可以显式写 `userId={}`。关键业务事件不要只依赖 MDC。

### 8.2 后台任务

`@Async`、scheduler、transaction listener 不应默认依赖 Web 请求上下文。

后台任务入口应显式记录关键业务 ID：

```java
log.info("[feed-sync] started: feedId={} type={} reason={}",
    feedId, type, reason);
```

下载任务应至少记录：

```java
log.info("[download] started: episodeId={} feedId={} type={}",
    episodeId, feedId, downloadType);
```

## 9. 重复日志的抽取规则

### 9.1 允许类内私有方法

同一个类里反复记录同一种业务事件时，可以抽成私有方法。

推荐：

```java
private void logDownloadSkipped(String episodeId, String reason) {
  log.info("[download] skipped: episodeId={} reason={}", episodeId, reason);
}
```

适用条件：

- 同一个类里重复 3 次以上。
- 字段组合稳定。
- 事件语义明确。
- 抽取后能减少漏字段、错字段顺序或文案不一致。

### 9.2 不要抽全局日志工具

不要为了统一格式创建跨模块日志工具，例如：

```java
BusinessLog.info("download", "skipped", fields);
```

这类封装会降低可读性，让日志调用和业务事件分离。

## 10. 敏感信息

日志中禁止输出：

- 密码、验证码、token、session、cookie
- API key、secret key、S3 access key / secret key
- SMTP 密码
- 完整 webhook URL 中的 secret query 参数
- 完整 Authorization header
- 用户上传的 cookies 文件内容
- 完整 yt-dlp 命令中的敏感参数

允许输出：

- 内部 ID，例如 `userId`、`feedId`、`episodeId`
- 公开平台 ID，例如 YouTube video ID、channel ID、playlist ID
- 脱敏后的 email
- 脱敏后的命令，例如通过 `ytDlpProxyService.redactCommand(command)` 处理后的命令
- 对象存储 key，但不要输出带签名参数的 URL

如果不确定是否敏感，默认不要打。

## 11. 高频场景规范

### 11.1 下载任务

下载主流程应记录：

- 任务开始：`episodeId`、`feedId`、`type`
- yt-dlp runtime：`mode`、`version`
- 命令执行摘要：脱敏 command、`timeoutMinutes`
- 成功：`episodeId`、`mediaType`、`filePath` 或 `objectKey`、`elapsedMs`
- 失败：`episodeId`、`exitCode`、`retryNumber`、`reason`

示例：

```java
log.info("[download] completed: episodeId={} feedId={} mediaType={} elapsedMs={}",
    episodeId, feedId, mediaType, elapsedMs);
```

### 11.2 同步任务

同步流程应记录批次摘要，而不是每个普通 episode 都打 `info`。

示例：

```java
log.info("[feed-sync] completed: feedId={} type={} scanned={} created={} skipped={} elapsedMs={}",
    feedId, feedType, scanned, created, skipped, elapsedMs);
```

### 11.3 外部 API

外部 API 日志应能回答：

- 调用了哪个 API。
- 针对哪个平台对象。
- 成功还是失败。
- 是否触发 quota guard 或 retry。
- 耗时和结果数量。

示例：

```java
log.info("[youtube-api] videos.list completed: count={} elapsedMs={}",
    videoIds.size(), elapsedMs);
```

### 11.4 RSS 与媒体分发

RSS 和媒体接口是用户排障的高频入口，失败日志要包含 episode/feed 上下文。

示例：

```java
log.warn("[rss] transcript skipped: episodeId={} reason={}", episodeId, reason);
log.error("[media] response build failed: episodeId={} path={}", episodeId, path, e);
```

### 11.5 存储与清理

LOCAL 和 S3 存储日志都使用 `[storage]`。对象上传/删除失败如果不影响主流程，用 `warn`；导致业务失败时用 `error`。

示例：

```java
log.warn("[storage] temp directory cleanup failed: path={}", outputDirPath, e);
```

## 12. 新增日志检查清单

新增或修改后端日志时检查：

1. 是否使用 `@Slf4j` 或项目允许的 SLF4J logger。
2. 是否使用固定 `[scope]`。
3. 是否使用 `event: key=value` 格式。
4. 字段名是否和本文常用字段名一致。
5. 日志级别是否准确。
6. 是否使用参数化日志，避免字符串拼接。
7. 是否避免输出敏感信息。
8. 异常日志是否保留必要上下文和堆栈。
9. 是否避免同一个异常重复记录。
10. 大量循环中是否避免高频 `info`。
11. 后台任务是否显式记录关键 ID，而不是依赖 Web 请求上下文。
12. 成功、跳过、重试、最终失败这些状态是否能通过日志串起来。

## 13. 示例

### 13.1 scheduler 摘要

```java
log.info("[scheduler] download scan completed: scanned={} promoted={} enqueued={} recovered={} elapsedMs={}",
    scanned, promoted, enqueued, recovered, elapsedMs);
```

### 13.2 可恢复失败

```java
log.warn("[download] mark downloading failed, will retry: episodeId={} status={}",
    episodeId, status, e);
```

### 13.3 最终失败

```java
log.error("[yt-dlp-runtime] update failed: channel={} beforeVersion={} stagingPath={}",
    channel, beforeVersion, stagingPath, e);
```

### 13.4 跳过

```java
log.info("[feed-sync] skipped: feedId={} reason={} nextSyncAt={}",
    feedId, reason, nextSyncAt);
```

### 13.5 外部通知

```java
log.warn("[notification-webhook] delivery failed: statusCode={} reason={}",
    statusCode, reason, e);
```

## 14. 全量重构建议

全量重构现有服务器日志时，建议按以下顺序处理：

1. `GlobalExceptionHandler`：预期业务异常降级，非预期异常补上下文。
2. `DownloadHandler`：统一 `[download]`、`[yt-dlp]`、`[storage]` scope，减少单条成功 `info` 噪声。
3. `ChannelService` / `PlaylistService`：同步、初始化、历史补抓统一 `[feed-sync]` 摘要。
4. `EpisodeService`：状态变更和文件清理统一 `[episode]` / `[storage]`。
5. `YoutubeHelper` / `YoutubeVideoHelper`：统一 `[youtube-api]` 字段和 quota 相关日志。
6. `MediaService` / `RssService`：失败日志补 `episodeId`、`feedId`、`path` 等上下文。

重构时保持小步提交。每次只改一个业务区域，避免日志格式调整和业务行为修改混在一起。
