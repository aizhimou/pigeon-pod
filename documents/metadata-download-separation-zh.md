# 元数据获取与节目下载解耦方案（设计稿）

## 1. 背景与现状梳理

### 1.1 当前元数据获取流程（简述）
- **订阅创建**：`FeedService.add → ChannelService/PlaylistService.saveFeed` 发布 `DownloadTaskEvent(INIT)`。
- **初始化异步处理**：`EpisodeEventListener.handleDownloadTask` 调用 `processChannelInitializationAsync / processPlaylistInitializationAsync`。
  - 频道：固定抓取 1 页（50 条）。
  - 播放列表：抓取全量（所有页）。
  - **全部节目入库**，但根据 `autoDownloadLimit` 前 N 条标记为 `PENDING`，其余标记为 `READY`。
- **增量同步**：`ChannelSyncer`/`PlaylistSyncer` 定时触发 `refreshChannel/refreshPlaylist`，获取新增节目并入库，然后根据 `autoDownloadLimit` 触发自动下载。
- **历史节目**：`fetchChannelHistory / fetchPlaylistHistory` 仅入库 `READY`，不触发下载。

### 1.2 当前下载流程（简述）
- `EpisodesCreatedEvent` → `EpisodeEventListener.handleEpisodesCreated` → `DownloadTaskHelper.submitDownloadTask`。
- `TaskStatusHelper` 在新事务内将 `READY/PENDING/FAILED → DOWNLOADING`。
- `DownloadHandler.download` 负责 yt-dlp 调用、媒体/字幕处理、状态更新。
- `DownloadScheduler` 每 30 秒补充线程池执行任务，失败可重试。

### 1.3 现存痛点
- **自动下载与元数据同步强耦合**：`autoDownloadLimit <= 0` 会被归一化为默认值，无法完全禁止自动下载。
- **用户需要“只入库元数据，不自动下载”** 以避免磁盘被动增长。

## 2. 目标与非目标

### 2.1 目标
- 系统仍能**自动同步订阅频道/播放列表的新节目**并入库元数据。
- 用户可配置“**新节目不自动下载**”，仅在手动触发时下载。
- 行为默认保持向后兼容：老用户无感升级。

### 2.2 非目标
- 不引入分布式下载队列或多节点调度。
- 不改变已有的下载引擎（yt-dlp）与存储结构。

## 3. 设计方案概述

### 3.1 核心思路
**将“元数据同步”与“下载触发”拆为两个独立阶段**：
1. **元数据阶段**：所有新节目一律 `READY` 入库。
2. **下载阶段**：仅在“自动下载开启”的情况下，将部分节目转为 `PENDING` 并发布下载事件。

### 3.2 配置项调整（Feed 级别）
采用**字段重命名**而非新增字段：
- `initialEpisodes` → `autoDownloadLimit`
- `sync_state` → `auto_download_enabled`（对应实体字段 `autoDownloadEnabled`）

> 语义：
> - `autoDownloadEnabled=false` → 不自动下载新节目（仅保存元数据）。
> - `autoDownloadEnabled=true` → 仅下载新增节目中的前 N 条（N=autoDownloadLimit）。

### 3.3 兼容策略
- 直接迁移并重命名字段（数据库字段同步改名）。
- 默认值：`autoDownloadEnabled=true` + `autoDownloadLimit=3`（现有默认行为）。
- 升级后不影响历史订阅。
- **元数据同步始终开启**：不再提供“暂停同步”的能力。

## 4. 关键流程改造（逻辑层面）

### 4.1 初始化流程（订阅创建）
**现状**：抓取并入库，按 `autoDownloadLimit` 标记 PENDING。

**改造后**：
1. 抓取节目 → **全部标记为 `READY`** 入库。
2. 根据 `autoDownloadEnabled + autoDownloadLimit` 计算需自动下载的节目集合。
3. 仅对集合内节目 **转为 `PENDING` 并发布 `EpisodesCreatedEvent`**。

> 优点：元数据入库与下载触发彻底分离。

### 4.2 增量同步（定时任务）
**现状**：新增节目入库后直接选前 N 条进入下载。

**改造后**：
- `refreshFeed` 只负责**元数据入库**。
- `AutoDownloadPolicy`（新逻辑）判断是否需要自动下载：
  - 若关闭：不触发下载。
  - 若开启：选前 N 条，标记 `PENDING` 并发布 `EpisodesCreatedEvent`。

### 4.3 手动下载
保留 `EpisodeService.manualDownloadEpisode` 逻辑不变：
- `READY → PENDING`，发布 `EpisodesCreatedEvent`。

### 4.4 下载触发事件命名（可选优化）
当前 `DownloadTaskEvent` 用于初始化抓取 + 下载，语义偏重“下载”。
建议后续可重命名为 `FeedInitEvent / FeedSyncEvent`，避免语义误导。

## 5. 数据模型与迁移建议

### 5.1 数据库变更
在 `channel` 与 `playlist` 表**重命名列**：
- `initial_episodes` → `auto_download_limit`（INTEGER, default 3）
- `sync_state` → `auto_download_enabled`（BOOLEAN, default true）

### 5.2 实体与 Mapper
- `Feed` 增加字段 `autoDownloadEnabled`、`autoDownloadLimit`。
- 更新 MyBatis Mapper 与相关 SQL（若存在显式列清单）。

## 6. 接口与前端调整建议

### 6.1 后端接口
- `FeedController`/`ChannelService`/`PlaylistService` 的 config/update 请求使用新字段名。
- 旧字段不再保留，直接改名迁移。

### 6.2 前端界面
- 在 `EditFeedModal` 中使用：
  - 开关：**“自动下载新节目”**（对应 `autoDownloadEnabled`）
  - 数字输入：**“每次自动下载数量”**（对应 `autoDownloadLimit`）
- 不新增“批量下载历史节目”按钮。

## 7. 行为细节与边界

- **关闭自动下载时**：新节目全部保持 `READY`，不会占用磁盘。
- **重新开启自动下载**：默认仅影响“新产生的节目”。
- **EpisodeCleaner** 不受影响（仍只清理 COMPLETED）。

## 8. 迁移路径建议

### 8.1 分阶段改造
1. 数据库列重命名（`sync_state`/`initial_episodes`）。
2. 实体与服务层字段重命名（`autoDownloadEnabled`/`autoDownloadLimit`）。
3. 调整初始化与增量同步逻辑：**先入库 READY，再判断是否触发下载**。
4. 前端改用新字段名与文案。

### 8.2 回滚策略
- 若关闭 `autoDownloadEnabled` 不满足预期，只需重新开启即可恢复现有自动下载行为。

## 9. 与现有流程的对照（摘要）

| 阶段 | 现状 | 改造后 |
| --- | --- | --- |
| 初始化 | 入库 + 前 N 条 PENDING | 全部 READY 入库，再按策略转 PENDING |
| 定时同步 | 入库 + 前 N 条 PENDING | 仅入库 READY，策略决定是否转 PENDING |
| 手动下载 | READY → PENDING | 不变 |
| 历史获取 | 仅入库 READY | 不变 |

## 10. 已确认决策

1. `initialEpisodes` 直接改语义并重命名为 `autoDownloadLimit`（数据库字段同步改名）。
2. UI 不提供“批量下载历史节目”入口，仅保留手动下载单集。
3. 播放列表“全量刷新”频率保持默认策略。
4. `sync_state` 改名为 `auto_download_enabled`，语义改为“是否自动下载新节目”；元数据同步始终开启，不再提供暂停选项。
