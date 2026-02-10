# PigeonPod 自动下载“延迟下载”方案（简化实现版）

## 1. 需求目标

为每个 Feed（频道/播放列表）新增一个“延迟下载”配置，用于自动下载时延后入队。

目标行为：

- 定时扫描（`ChannelSyncer` / `PlaylistSyncer`）发现新节目后，不立即下载。
- 仅当 `节目发布时间 + 延迟时长 <= 当前时间` 时，才进入下载队列。
- 该能力主要用于提升 `--sponsorblock-remove all` 在新发布节目上的生效概率。
- 实现尽量复用现有架构，不重构下载流水线。

## 2. 现状与约束（基于当前架构）

- 自动下载触发点在 `AbstractFeedService.persistEpisodesAndPublish(...)`：目前会把选中的新节目直接标记为 `PENDING` 并发布 `EpisodesCreatedEvent`。
- 增量扫描只对“本次新发现的节目”做一次自动下载选择；如果当次跳过，后续不会再被“新节目逻辑”命中。
- 下载执行由 `DownloadScheduler`（每 30 秒）补位消费 `PENDING`/`FAILED`，是天然的“队列入口节流点”。
- `Episode` 状态是全局状态（不是 feed 维度），所以方案应避免引入复杂的 feed-episode 状态机。

结论：延迟下载必须“持久化记录待自动下载资格”，否则会被永久跳过。

## 3. 方案概览（推荐）

### 3.1 核心思路

1. Feed 新增延迟配置字段（如 `auto_download_delay_minutes`，0 表示不延迟）。
2. Episode 新增“自动下载到期时间”字段（如 `auto_download_after`）。
3. 扫描到新节目时，仍按 `autoDownloadLimit` 选出“自动下载候选”：
   - 已到期：立即按现有逻辑入队（`PENDING + event`）。
   - 未到期：保持 `READY`，只写入 `auto_download_after`。
4. `DownloadScheduler` 每轮先把“已到期且仍为 READY”的候选节目提升为 `PENDING`，再由现有流程下载。

这样不改动现有下载执行链路，只在“何时入队”这一层加控制。

### 3.2 为什么不放到 `ChannelSyncer/PlaylistSyncer` 做到期判断

- 频道 1 小时、播放列表 3 小时才扫描一次；如果只靠扫描器到期触发，会产生额外不确定等待。
- `DownloadScheduler` 已有 30 秒周期，更适合做“到期入队”。
- 扫描器仍负责“发现候选”，调度器负责“到期入队”，职责清晰且改动小。

## 4. 数据与接口改造

## 4.1 数据库

新增迁移（建议 `V24__Add_auto_download_delay.sql`）：

- `channel` 表新增 `auto_download_delay_minutes INTEGER NULL DEFAULT 0`
- `playlist` 表新增 `auto_download_delay_minutes INTEGER NULL DEFAULT 0`
- `episode` 表新增 `auto_download_after TIMESTAMP NULL`
- 建议索引：`episode(download_status, auto_download_after)`，提升到期扫描效率

兼容策略：

- 旧数据默认延迟为 0，行为与现在完全一致。

## 4.2 实体字段

- `Feed` 增加：
  - `Integer autoDownloadDelayMinutes`（默认 0）
- `Episode` 增加：
  - `LocalDateTime autoDownloadAfter`（可空）

## 4.3 API 层

无需新增接口，复用现有：

- `PUT /api/feed/{type}/config/{id}`
- `POST /api/feed/{type}/add`
- `POST /api/feed/{type}/preview`（仅回显配置）

原因：`FeedFactory` 通过 `ObjectMapper.convertValue(...)` 反序列化，新增字段可直接透传。

## 5. 业务流程改造点

### 5.1 新节目入库后的自动下载选择（`AbstractFeedService`）

保留当前“先选前 N 条候选”的逻辑，只修改候选处理方式：

- 若 `delay=0`：沿用现有逻辑（`mark pending + publish event`）。
- 若 `delay>0`：按每条节目的 `publishedAt + delay` 计算 `auto_download_after`。
  - 已到期：立即入队。
  - 未到期：仅更新 `auto_download_after`，状态保持 `READY`。

说明：`publishedAt` 为空时可回退到 `now` 计算。

### 5.2 到期提升为 PENDING（`DownloadScheduler` + `EpisodeService`）

在 `DownloadScheduler.processPendingDownloads()` 开头增加一步：

1. 查询 `download_status=READY and auto_download_after <= now` 的节目（分批）。
2. 原子更新为 `PENDING`（带条件，避免并发误更新）。
3. 清空 `auto_download_after`（一次性标记，防止后续重复自动入队）。
4. 发布 `EpisodesCreatedEvent`，复用现有下载执行流程。

然后继续当前已有的 “PENDING/FAILED 补位” 逻辑。

## 6. 前端改造（Feed 设置页）

位置：沿用现有 `EditFeedModal` 中自动下载配置区域（`Home` + `Feed` 页面均可编辑）。

新增字段：

- `auto_download_delay`（标签）
- 一个数字输入框（默认 0）
- 提示文案：`0 = 不延迟；>0 表示节目发布时间后延迟 N 分钟才自动入队`

表单行为：

- 当 `autoDownloadEnabled=false` 时可禁用该输入（与自动下载数量一致）。
- 保存时随 feed 配置一起提交，无需新接口。

## 7. 日志与可观测性（建议）

为便于排查，建议新增关键日志：

- 扫描发现新节目时：
  - `feedId`、候选数、立即入队数、延迟入队数
- 到期提升任务时：
  - 本轮扫描到期数、成功提升数、失败数
- 当延迟字段生效时，打印样例节目 `publishedAt -> auto_download_after`

## 8. 边界行为定义

- 手动下载 `READY` 节目：不受延迟限制，立即走手动下载流程。
- 自动下载关闭后：
  - 已写入 `auto_download_after` 的节目不会被提升（推荐在提升时额外校验 feed 当前 `autoDownloadEnabled`）。
- 自动下载重新开启后：
  - 到期节目可继续被提升入队。
- 已完成或失败节目不受该字段影响。
- 为避免重复自动入队，节目一旦被提升到 `PENDING`，应清空 `auto_download_after`。

## 9. 实施步骤（最小改动）

1. Flyway 迁移：新增 3 个字段 + 索引。
2. 实体：`Feed`、`Episode` 增加字段。
3. Service：
   - `AbstractFeedService` 增加延迟计算与候选分流。
   - `EpisodeService` 增加“到期 READY -> PENDING 并发布事件”的方法。
4. Scheduler：
   - `DownloadScheduler` 开头调用“到期提升”方法。
5. 前端：
   - `EditFeedModal` 增加延迟输入。
   - `Home` / `Feed` 编辑弹窗接入字段。
   - i18n 增加 8 语言文案。
6. 验证：
   - `delay=0` 回归测试（行为不变）。
   - `delay=60` 场景测试（发布后 1 小时才入队）。
   - 关闭/重开自动下载、手动下载并存、重启后继续到期提升。

## 10. 已确认决策

1. 延迟单位：分钟（字段 `autoDownloadDelayMinutes`）。
2. 延迟作用范围：同时作用于“首次订阅初始化自动下载”和增量同步自动下载。
3. 配置变更策略：不追溯历史已登记的 READY 节目，仅影响后续新发现节目。
