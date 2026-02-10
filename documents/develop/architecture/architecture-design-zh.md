# PigeonPod 架构设计

## 1. 项目定位与目标

PigeonPod 是一个自托管的 YouTube 到 Podcast 桥接系统，目标是：

- 将 YouTube 频道/播放列表转换为可订阅 RSS。
- 自动抓取新节目元数据，并按策略下载媒体文件。
- 提供本地在线播放、下载到本地、字幕与章节等 Podcast 扩展能力。
- 保持单机部署可控，同时支持后续功能扩展。

## 2. 当前功能版图（以代码为准）

- 订阅入口：支持频道/播放列表 URL 或 ID，自动识别类型并预览最近节目。
- 订阅配置：关键词过滤、时长过滤、自动下载开关、自动下载数量、延迟下载分钟数、最大本地保留数、音视频下载参数、字幕参数、自定义标题与封面。
- 异步初始化：新增订阅后后台拉取节目并分发下载任务。
- 增量同步：频道每 1 小时同步、播放列表每 3 小时同步。
- 下载流水线：`READY/PENDING/DOWNLOADING/COMPLETED/FAILED` 状态流转，支持自动下载、手动下载、重试、取消、批量操作。
- 延迟自动下载：按 `autoDownloadAfter` 到期提升为 `PENDING`。
- 清理任务：按 `maximumEpisodes` 自动清理已下载文件并将状态回置为 `READY`。
- 媒体与 RSS：
  - 媒体流：`/media/{episodeId}.mp3|mp4|m4a`
  - 字幕流：`/media/{episodeId}/subtitle/{lang}.{ext}`
  - 章节流：`/media/{episodeId}/chapters.json`
  - RSS：频道/播放列表均支持，并带 API Key 校验。
- 设置中心：账号管理、API Key、YouTube API Key、Cookies、日期格式、登录验证码开关、yt-dlp 参数白名单策略、Feed 默认配置、yt-dlp 运行时升级、OPML 导出。
- 前端能力：全局播放器（音频底栏 + 视频弹窗）、Dashboard 状态面板、分页与懒加载、状态轮询、8 语言国际化。

## 3. 技术栈与版本

| 层级 | 技术 |
| --- | --- |
| 前端 | React 19.1、Vite 7、React Router 7、Mantine 8、mantine-datatable、i18next、Axios、Plyr |
| 后端 | Java 17、Spring Boot 3.5.3、Sa-Token 1.44、MyBatis-Plus 3.5.12、Flyway、Spring Retry |
| 数据 | SQLite（WAL） |
| 外部依赖 | YouTube Data API v3、yt-dlp、Rome + iTunes Modules |

## 4. 代码结构与职责

- `backend/src/main/java/top/asimov/pigeon`
  - `controller`：REST 入口，包含 `Auth/Account/Feed/Episode/Dashboard/Media/Rss`。
  - `service`：核心业务编排（订阅、剧集、账号、RSS、yt-dlp 运行时等）。
  - `handler`：Feed 类型分发与下载执行（`ChannelFeedHandler`、`PlaylistFeedHandler`、`DownloadHandler`）。
  - `helper`：YouTube 拉取与任务状态辅助（`Youtube*Helper`、`DownloadTaskHelper`、`TaskStatusHelper`）。
  - `scheduler`：同步、下载补位、过量清理、启动修复。
  - `event/listener`：`DownloadTaskEvent`、`EpisodesCreatedEvent` 与事务后监听。
  - `mapper`：MyBatis-Plus + 注解 SQL。
- `frontend/src`
  - `pages`：`Home`、`Feed`、`DashboardEpisodes`、`Setting`、`Login`、`NotFound`。
  - `components`：`FeedHeader`、`EditFeedModal`、`GlobalPlayer`、`VersionUpdateAlert` 等。
  - `context`：`UserContext` 与 `PlayerContext`。
  - `helpers`：Axios 实例、错误提示、日期/时长格式化、复制回退。

## 5. 后端架构设计

### 5.1 控制层

- 认证：`/api/auth/login|logout|captcha|captcha-config`
- 订阅：`/api/feed/**`（list/detail/fetch/preview/add/refresh/history/config/cover/delete）
- 剧集：`/api/episode/**`（分页、重试、手动下载、取消、批量、下载到本地）
- 看板：`/api/dashboard/statistics`、`/api/dashboard/episodes`
- 账号：`/api/account/**`（账号、默认配置、yt-dlp、OPML 等）
- RSS：`/api/rss/**`（`@SaCheckApiKey`）
- 媒体：`/media/**`

### 5.2 服务与工厂分发

- `FeedService` 通过 `FeedType` 路由到 `FeedHandler`。
- `FeedFactory` + `AbstractFeedHandler` 将 `Map payload` 转为强类型实体。
- `ChannelService` / `PlaylistService` 继承 `AbstractFeedService`，复用：
  - 配置更新模板
  - 保存/刷新流程
  - 自动下载选择与延迟策略
  - 事件发布

### 5.3 事件与异步执行

- `DownloadTaskEvent`：订阅初始化异步任务（频道/播放列表）。
- `EpisodesCreatedEvent`：通知下载执行。
- `EpisodeEventListener`：`AFTER_COMMIT` 监听并投递线程池。
- 线程池配置（`AsyncConfig`）：
  - `downloadTaskExecutor`: 3 线程，`queueCapacity=0`，拒绝策略 `AbortPolicy`。
  - `channelSyncTaskExecutor`: 2 线程，`queueCapacity=3`。

### 5.4 调度任务

- `ChannelSyncer`: 每 1 小时。
- `PlaylistSyncer`: 每 3 小时。
- `DownloadScheduler`: 每 30 秒，补位 PENDING/FAILED，提升延迟自动下载任务。
- `EpisodeCleaner`: 每 2 小时，按 feed 维度清理超限 COMPLETED。
- `StaleTaskCleaner`: 启动时将遗留 DOWNLOADING 回置为 PENDING。

## 6. 数据模型

- `Feed`（抽象）：
  - 过滤：标题/描述包含与排除关键词、最小/最大时长
  - 下载：`downloadType`、`audioQuality`、`videoQuality`、`videoEncoding`
  - 字幕：`subtitleLanguages`、`subtitleFormat`
  - 自动下载：`autoDownloadEnabled`、`autoDownloadLimit`、`autoDownloadDelayMinutes`
  - 容量：`maximumEpisodes`
  - 同步：`lastSyncVideoId`、`lastSyncTimestamp`
- `Channel`：附加 `handler`。
- `Playlist`：附加 `ownerId`。
- `Episode`：
  - 主键为视频 ID
  - 包含 `downloadStatus`、`mediaFilePath`、`mediaType`、`retryNumber`、`autoDownloadAfter`
- `PlaylistEpisode`：保存播放列表关联关系与 `position`。
- `FeedDefaults`：系统默认下载参数与字幕参数。
- `User`：账号、API Key、YouTube API Key、Cookies、日期格式、yt-dlp 自定义参数、登录验证码开关。

## 7. 核心流程

### 7.1 新建订阅

1. 前端 `Home` 调用 `/api/feed/fetch`，后端识别 `CHANNEL/PLAYLIST`。
2. `fetch` 返回 feed + 预览节目（默认展示前 5 条）。
3. 用户确认后 `/api/feed/{type}/add`。
4. `AbstractFeedService.saveFeed`：
   - 应用 `FeedDefaults`
   - 规范化自动下载参数
   - 持久化 feed
   - 发布 `DownloadTaskEvent(INIT)`。
5. 监听器异步执行 `processChannelInitializationAsync` 或 `processPlaylistInitializationAsync`。

### 7.2 同步与历史补抓

- 增量同步通过 `refreshChannel` / `refreshPlaylist` 完成：
  - 频道抓最新页并做差值。
  - 播放列表全量扫描并做差值，同时刷新顺序映射。
- 历史补抓通过 `/api/feed/{type}/history/{id}`：
  - 后端按页码向后抓取更早历史并入库。
  - 当前前端“获取历史节目”按钮只在频道详情页展示。

### 7.3 下载与状态机

1. 可下载节目（自动或手动）发布 `EpisodesCreatedEvent`。
2. `DownloadTaskHelper.submitDownloadTask`：
   - `TaskStatusHelper.tryMarkDownloading`（`REQUIRES_NEW`）将 `READY/PENDING/FAILED -> DOWNLOADING`
   - 提交 `DownloadHandler.download`。
3. `DownloadHandler`：
   - 解析 feed 上下文与全局默认配置
   - 拼装 yt-dlp 命令（音/视频、质量、编码、字幕、章节、自定义参数）
   - 写回 `mediaFilePath/mediaType/errorLog/retryNumber/downloadStatus`。
4. `DownloadScheduler` 负责持续补位队列。

### 7.4 延迟自动下载

- 若 feed 设置 `autoDownloadDelayMinutes > 0`：
  - 节目先保持 `READY`，并写入 `auto_download_after`。
  - 定时任务到期后提升为 `PENDING` 并发布下载事件。
- 到期提升时会检查所属频道/播放列表是否仍开启自动下载。

### 7.5 RSS 与媒体分发

- RSS 只输出 `COMPLETED` 节目（有本地媒体文件）。
- 使用 Rome + iTunes 模块，并注入 Podcasting 2.0 扩展：
  - `podcast:transcript`
  - `podcast:chapters`
- 媒体下载到本地入口：`/api/episode/download/local/{id}`。

## 8. 前端架构与交互

- 路由：
  - `/`：订阅列表 + 新建订阅 + Dashboard 统计卡
  - `/:type/:feedId`：Feed 详情页（分页、搜索、排序、状态轮询、操作）
  - `/dashboard/episodes/:status`：状态看板与批量处理
  - `/user-setting`：系统设置
- 状态管理：
  - `UserContext` 管理登录态与用户配置。
  - `PlayerContext` 管理全局播放器状态。
- 全局播放器：
  - 音频在底部栏播放。
  - 视频自动弹出居中 Modal 播放。
- 轮询策略：
  - 首页统计每 3 秒。
  - Feed 页活跃任务每 3 秒。
  - Dashboard 列表每 3 秒。

## 9. 国际化与错误处理

- 前后端均支持 `en/zh/es/ja/pt/fr/de/ko`。
- 前端 Axios 自动注入 `Accept-Language`。
- 后端 `HeaderLocaleResolver` + `MessageSource` 统一输出本地化消息。
- 统一异常：`BusinessException` + `GlobalExceptionHandler`。

## 10. 配置与部署

- 核心配置在 `backend/src/main/resources/application.yml`：
  - SQLite WAL、上传限制、Sa-Token、媒体目录、yt-dlp 管理目录等。
- `YtDlpRuntimeService` 支持：
  - managed runtime（`python3 -m yt_dlp`）
  - `stable/nightly` 通道升级
  - 版本保留与回滚
  - 无可用 managed runtime 时回退系统 `yt-dlp` 二进制。
- 前端开发态通过 `vite.config.js` 代理 `/api`、`/media` 到 `localhost:8080`。

## 11. 扩展建议

- 新 Feed 类型：实现新的 `FeedHandler + AbstractFeedService` 子类并注册到 `FeedFactory`。
- 新下载参数：扩展 `Feed/FeedDefaults` 字段，贯通 `EditFeedModal -> API -> DownloadHandler`。
- 新调度任务：沿用 `@Scheduled + Service` 模式，复用现有状态机。
- 文档同步：任何涉及流程、模型、接口的变更，应同步更新本文件与英文版本。
