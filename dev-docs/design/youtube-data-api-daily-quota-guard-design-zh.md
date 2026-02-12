# PigeonPod YouTube Data API 每日调用统计与自动同步熔断方案

## 1. 文档目标

为 PigeonPod 设计一套可落地的配额治理机制，满足以下目标：

- 精确统计每天 YouTube Data API v3 的请求次数与配额消耗（quota units）。
- 在免费额度（默认 10000 units/天）触顶后，自动停止“当天自动同步”。
- 保留手动操作能力（可配置是否一并阻断）。
- 为后续审计与排障提供可观测数据。

---

## 2. 官方配额规则（用于本方案计算口径）

基于 YouTube Data API 官方文档（通过 Context7 + 官方页面）：

- 默认每日配额：`10000 units/day`
- 每个 API 请求至少消耗 `1` unit（包含无效请求）
- 分页请求按“每一页请求”分别计费
- 每日配额在 `Pacific Time (PT)` 午夜重置

本项目当前相关方法单位成本：

| API 方法 | 单次成本 |
| --- | --- |
| `search.list` | 100 |
| `channels.list` | 1 |
| `playlists.list` | 1 |
| `playlistItems.list` | 1 |
| `videos.list` | 1 |

---

## 3. 当前代码中的 YouTube Data API 调用点梳理

核心调用集中在：

- `backend/src/main/java/top/asimov/pigeon/helper/YoutubeHelper.java`
- `backend/src/main/java/top/asimov/pigeon/helper/YoutubeVideoHelper.java`
- `backend/src/main/java/top/asimov/pigeon/helper/YoutubeChannelHelper.java`
- `backend/src/main/java/top/asimov/pigeon/helper/YoutubePlaylistHelper.java`
- `backend/src/main/java/top/asimov/pigeon/service/PlaylistService.java`（通过 `youtubeVideoHelper` 补详情/重试）

调度入口：

- `backend/src/main/java/top/asimov/pigeon/scheduler/ChannelSyncer.java`（每 1 小时）
- `backend/src/main/java/top/asimov/pigeon/scheduler/PlaylistSyncer.java`（每 3 小时）
- `backend/src/main/java/top/asimov/pigeon/scheduler/PlaylistDetailRetrySyncer.java`（每 15 分钟）

---

## 4. 现状调用量计算（按代码行为）

## 4.1 单次操作成本

| 场景 | 当前调用链 | 成本（units） |
| --- | --- | --- |
| 频道抓取（输入是 Channel ID 或 `/channel/` URL） | `channels.list` + 预览页（`channels.list` + `playlistItems.list` + `videos.list`） | `4` |
| 频道抓取（输入是 `@handle` URL） | `search.list` + `channels.list` + 预览页（3） | `104` |
| 频道新增后异步初始化 | `channels.list` + `playlistItems.list` + `videos.list` | `3` |
| 频道手动/定时刷新 | 同上 | `3`/次 |
| 频道历史抓取第 `p` 页 | `channels.list` + `playlistItems.list * p` + `videos.list` | `p + 2` |
| 播放列表抓取预览 | `playlists.list` + `playlistItems.list` + `videos.list` | `3` |
| 播放列表初始化/同步（混合同步） | yt-dlp 快照 + `videos.list`（仅新增/缺失详情） | `ceil(newIds/50)` |
| 播放列表历史抓取第 `p` 页 | `playlistItems.list * p` + `videos.list` | `p + 1` |
| 播放列表详情重试队列单轮 | `videos.list` 批量重试 | `ceil(retryIds/50)` |

## 4.2 自动同步每日基线估算

设：

- `C` = 频道订阅数
- `A_i` = 第 `i` 个播放列表在单次 3 小时同步周期中需要补详情的新视频数
- `R_j` = 第 `j` 次 15 分钟重试任务中待补详情视频数

则：

```text
频道自动同步日耗 = 24 * C * 3 = 72C
播放列表自动同步日耗 ≈ Σ(8 次/天 * ceil(A_i / 50))
重试队列日耗 ≈ Σ ceil(R_j / 50)
总自动同步日耗 U_auto = 72C + U_playlist + U_retry
```

仅看频道定时同步时：

```text
10000 / 72 ≈ 138.88
```

即频道约到 `139` 个时，理论上仅频道自动同步就可能逼近/超过免费额度（未计手动与播放列表补详情）。

---

## 5. 方案设计（统计 + 熔断）

## 5.1 设计原则

- 统一计量：所有 YouTube Data API 请求必须通过统一执行器。
- 先扣减后调用：自动同步场景先做“配额预占用”，防止超限后继续请求。
- 自动同步可熔断：当天触顶后，定时任务立刻停止后续同步。
- 可观测：支持按天、按方法查看 `request_count` 与 `quota_units`。

## 5.2 组件设计

新增组件建议：

1. `YoutubeApiMethod`（枚举）
- 定义方法名与成本：`SEARCH_LIST(100)`, `CHANNELS_LIST(1)`, `PLAYLISTS_LIST(1)`, `PLAYLIST_ITEMS_LIST(1)`, `VIDEOS_LIST(1)`。

2. `YoutubeApiCallContext`（调用上下文）
- `AUTO_SYNC`（定时任务）
- `MANUAL`（用户手动接口）

3. `YoutubeQuotaService`
- 核心职责：当日统计、预占用、剩余额度计算、自动同步阻断状态管理。

4. `YoutubeApiExecutor`
- 统一执行 `request.execute()`；调用前后接入 `YoutubeQuotaService`。
- 负责识别远端 `quotaExceeded`，并触发本地阻断标记。

5. `YoutubeQuotaContextHolder`（ThreadLocal）
- 在调度器入口设置 `AUTO_SYNC`；Web 请求默认 `MANUAL`。

## 5.3 数据库设计（Flyway）

建议新增两张表：

### 表 1：`youtube_api_daily_usage`

- `usage_date_pt` `TEXT` 主键（`yyyy-MM-dd`，PT 日期）
- `request_count` `INTEGER NOT NULL DEFAULT 0`
- `quota_units` `INTEGER NOT NULL DEFAULT 0`
- `auto_sync_blocked` `INTEGER NOT NULL DEFAULT 0`
- `blocked_reason` `TEXT NULL`
- `blocked_at` `TIMESTAMP NULL`
- `created_at` `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at` `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

### 表 2：`youtube_api_daily_usage_method`

- `usage_date_pt` `TEXT NOT NULL`
- `api_method` `TEXT NOT NULL`
- `request_count` `INTEGER NOT NULL DEFAULT 0`
- `quota_units` `INTEGER NOT NULL DEFAULT 0`
- `created_at` `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at` `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- 主键：`(usage_date_pt, api_method)`

---

## 6. 核心流程

## 6.1 自动同步请求流程（关键）

1. 识别当前上下文为 `AUTO_SYNC`。
2. 按 API 方法成本执行 `tryReserve(cost)`。
3. 若 `used + cost > dailyLimit`：设置 `auto_sync_blocked=1`，抛出 `AutoSyncQuotaExceededException`，本次请求不再发送。
4. 若预占成功，再执行 `request.execute()`。
5. 预占成功时即完成 `request_count/quota_units` 记账；请求执行结果仅用于补充成功/失败日志与错误原因。

## 6.2 调度器熔断逻辑

在以下调度任务开头增加短路判断：

- `ChannelSyncer.syncDueChannels()`
- `PlaylistSyncer.syncDuePlaylists()`
- `PlaylistDetailRetrySyncer.processRetryQueue()`

若 `isAutoSyncBlockedToday()` 为 true：

- 直接 `return`
- 记录 warn 日志（包含 PT 日期、已用额度、阻断原因）

任务循环中也要二次判断，避免“中途触顶后仍继续处理后续 feed”。

## 6.3 远端配额异常兜底

如果外部系统也在复用同一 API Key，本地统计可能低估。  
在 `YoutubeApiExecutor` 捕获 `GoogleJsonResponseException` 时，若 reason 包含 `quotaExceeded`：

- 立即标记当日 `auto_sync_blocked=1`
- 记录 `blocked_reason=REMOTE_QUOTA_EXCEEDED`

---

## 7. 代码改造点

## 7.1 Helper 层改造（必须）

将以下位置的 `request.execute()` 全部替换为统一执行器调用：

- `YoutubeHelper`：`search.list` / `channels.list` / `playlists.list`
- `YoutubeVideoHelper`：`channels.list(contentDetails)` / `playlistItems.list` / `videos.list`

## 7.2 Scheduler 层改造（必须）

- 在 3 个同步调度器中加“当日阻断检查 + 中途 break”。

## 7.3 API 可观测接口（建议）

新增：

- `GET /api/account/youtube-quota/today`
- 返回：`usageDatePt`, `dailyLimit`, `usedUnits`, `remainingUnits`, `requestCount`, `autoSyncBlocked`, `methodBreakdown`

前端 `frontend/src/pages/Setting/index.jsx` 显示当日配额进度条与阻断状态。

---

## 8. 配置与存储

本轮决策后，不在 `application.yml` 增加 `daily-limit-units`。  
`daily-limit-units` 改为在“设置页 -> YouTube API Key 配置”中设置，并存入 `user` 表：

- 字段：`user.youtube_daily_limit_units`
- 规则：未设置（`NULL`）表示“不限制”
- 生效范围：全局（当前系统单用户）

---

## 9. 测试与验收

## 9.1 单元测试

- 成本映射正确（`search.list=100` 等）
- `tryReserve` 并发下不超卖
- `AUTO_SYNC` 超限时阻断，`MANUAL` 可继续（按策略）

## 9.2 集成测试

- 模拟当天额度接近上限，验证调度任务自动停机
- 第二天 PT 日期切换后自动恢复同步
- 远端 `quotaExceeded` 后，当天后续调度全部跳过

## 9.3 回归测试

- 频道/播放列表抓取、预览、新增、历史、手动刷新行为不回归
- Playlist 混合同步与重试机制不受影响

---

## 10. 已确认决策（2026-02-12）

1. 超限后只停“自动同步”，不限制手动接口。  
2. 不设置单独提前保护阈值；仅使用 `daily-limit-units` 作为阈值，且阈值来源于设置页并存储到 `user` 表，未设置即不限制。  
3. 配额信息默认展示在设置页；当天用量达到阈值 `80%` 时，在首页展示红色告警，并提示“达到阈值后当天自动同步会停止，次日自动恢复”。  

---

## 11. 参考文档

- YouTube Data API 配额成本表：  
  https://developers.google.com/youtube/v3/determine_quota_cost
- YouTube Data API 配额与 10000 默认额度说明：  
  https://developers.google.com/youtube/v3/getting-started
- `search.list` 参考（Quota impact）：  
  https://developers.google.com/youtube/v3/docs/search/list
- `channels.list` 参考：  
  https://developers.google.com/youtube/v3/docs/channels/list
- `playlistItems.list` 参考：  
  https://developers.google.com/youtube/v3/docs/playlistItems/list
- `videos.list` 参考：  
  https://developers.google.com/youtube/v3/docs/videos/list
