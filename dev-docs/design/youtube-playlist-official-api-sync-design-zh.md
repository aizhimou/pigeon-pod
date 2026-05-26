# PigeonPod OP YouTube Playlist 官方 API 同步方案设计

## 1. 文档目的

本文记录 OP 版本当前已经落地的 YouTube Playlist 官方 API 同步方案。该方案用于替代 `yt-dlp --flat-playlist -J` + YouTube Data API 的 hybrid snapshot 方案；本文以当前代码实现为准，保留后续清理项说明。

核心目标：

- 移除 `yt-dlp --flat-playlist` 在 YouTube Playlist 同步入口中的事实源地位。
- 使用 YouTube Data API 的 `playlistItems.list` 作为 playlist 条目、顺序、删除与新增判定的唯一事实源。
- 保留 OP 当前的 `playlist`、`episode`、`playlist_episode`、自动下载、历史补抓、RSS 与媒体下载模型。
- 降低同步实现复杂度，让每轮同步结果可解释、可排障、可安全重试。
- 保留现有 YouTube API 日额度统计与自动同步熔断能力，但不引入 SaaS 版本的用户套餐、免费/付费限制或多租户配额逻辑。

参考官方文档：

- [PlaylistItems resource](https://developers.google.com/youtube/v3/docs/playlistItems)
- [PlaylistItems: list](https://developers.google.com/youtube/v3/docs/playlistItems/list)
- [Playlists resource](https://developers.google.com/youtube/v3/docs/playlists)
- [Playlists: list](https://developers.google.com/youtube/v3/docs/playlists/list)
- [YouTube Data API quota calculator](https://developers.google.com/youtube/v3/determine_quota_cost)

---

## 2. 背景与问题

hybrid snapshot 方案上线运行后暴露出明确问题：

1. `yt-dlp --flat-playlist` 对大 playlist 不稳定，可能返回不完整或波动的条目快照。
2. 旧实现把 flat snapshot 当作 destructive diff 的事实源，一旦快照缺失，就会误判大量本地节目已被移除。
3. 下一轮 snapshot 恢复后，旧节目又会被误判为新增，进而可能触发重复入库、重复关联或错误自动下载。
4. hybrid 方案同时存在 snapshot 解析、详情补齐、重试队列、过滤、mapping 修复等多段逻辑，排查一次异常需要跨多个组件。

因此，新方案的基本判断是：

- playlist 成员同步必须回到官方 API。
- `yt-dlp` 只保留在媒体下载链路中，不再参与 playlist 成员发现。
- 同步逻辑应以官方 `playlistItem.id` 为条目身份，避免把 `videoId` 当作 playlist 条目身份导致重复条目和重排场景难以解释。

---

## 3. 方案一句话总结

**YouTube Playlist 同步改为：定时任务每小时检查一次 playlist；每个 playlist 按自己的 `sync_interval_hours` 判断是否到期；到期后直接用 `playlistItems.list(part=snippet,contentDetails,status)` 完整翻页全量同步，以 `playlistItem.id` 更新本地条目真源，再派生 `episode` 和 `playlist_episode`。**

这意味着：

- `playlistItems.list` 是 playlist 成员事实源。
- `playlist_episode` 不再作为同步 diff 的事实源，而是由条目真源派生的展示/RSS/下载关联视图。
- 自动下载不再直接基于“本轮重新 linked 的 episode”，而是基于 playlist item 的一次性分发状态。
- 自动同步频率由 playlist 自己的 `sync_interval_hours` 控制；不再使用 `playlists.list(contentDetails).itemCount` 做轻量探测。

---

## 4. 官方 API 关键事实

## 4.1 `playlistItem.id` 是 playlist 条目身份

同一个 `videoId` 可能在同一个 playlist 中出现多次，也可能在不同 playlist 中出现。  
因此 playlist 同步层的主键应是：

- `playlistItem.id`

而不是：

- `videoId`

OP 当前 `episode.id` 仍保持视频 ID，这符合现有全局内容去重模型；但 playlist 条目真源必须额外保存 `playlistItem.id`。

## 4.2 `playlistItem.snippet.publishedAt` 是加入 playlist 的时间

对 playlist 视角，条目的时间应优先使用：

- `playlistItem.snippet.publishedAt`

它表示该条目加入 playlist 的时间。  
派生到 `playlist_episode.published_at` 时也应使用这个值，而不是视频发布时间。

## 4.3 `playlistItem.snippet.position` 是当前顺序

全量扫描成功后，本地 `playlist_episode.position` 应与远端 playlist 当前顺序保持一致。

## 4.4 不再使用 `playlist.contentDetails.itemCount` 做同步决策

`itemCount` 可低成本判断数量是否变化，但不能证明成员未变化。

例如：

- 删除 1 个旧条目，同时新增 1 个新条目。
- 替换 1 个条目，但总数不变。
- 大 playlist 重排，但总数不变。

因此当前实现不再引入“轻量探测 + 按需全量扫描”的双状态方案，而是到期后直接全量扫描。同步成本通过每个 playlist 的 `sync_interval_hours` 控制。

---

## 5. 目标与非目标

## 5.1 目标

- YouTube Playlist 自动同步、手动刷新、初始化统一使用官方 API 条目真源。
- 完整翻页成功后才应用 diff；任一页失败不修改本地关联，不误删。
- 支持新增、删除、移动、重新出现的条目状态追踪。
- Episode 详情补齐失败不影响条目真源保存，后续可重试。
- Bootstrap 阶段只建立本地事实源和派生视图，不把历史库存批量当作“新节目”自动下载。
- 在 YouTube playlist 的初始化、手动刷新、定时同步入口中停用 hybrid snapshot，移除其带来的误删、误新增和排障复杂度。

## 5.2 非目标

- 不引入 SaaS 的免费/付费 playlist 规模限制。
- 不引入多用户、多租户、用户级配额分摊。
- 不重构下载流水线、RSS 输出和媒体分发入口。
- 不在本阶段完整支持同一视频在同一 playlist 多次出现的 UI 展示；OP 仍以 `playlist_episode` 的 feed-episode 关联作为页面/RSS视图。
- 不删除 yt-dlp 下载能力；当前阶段只停用 YouTube playlist 同步入口中的 yt-dlp snapshot 能力，旧 snapshot 代码作为过渡遗留仍保留。

---

## 6. 同步频率策略

OP 当前 `PlaylistSyncer` 每 1 小时运行一次。调度任务只负责检查哪些 playlist 到期，真正同步时直接执行全量扫描。

## 6.1 每小时 due 检查

每个 playlist 保存：

```text
sync_interval_hours INTEGER NOT NULL DEFAULT 3
```

`PlaylistSyncer` 每小时运行时，调用 `PlaylistService.findDueForSync(now)` 筛选待同步 playlist。判断规则：

```text
last_sync_timestamp IS NULL
OR last_sync_timestamp + sync_interval_hours <= now
```

说明：

- 默认值为 `3` 小时，保持旧版本 playlist 自动同步节奏。
- `sync_interval_hours <= 0` 的异常值按默认 `3` 小时处理。
- 手动刷新和初始化不受该间隔限制。

## 6.2 全量扫描触发条件

满足任一条件时执行 `playlistItems.list` 全量扫描：

1. 新增订阅初始化。
2. 用户手动刷新 playlist。
3. 定时任务发现 `last_sync_timestamp + sync_interval_hours <= now`。
4. 当前 playlist 从未同步过，即 `last_sync_timestamp IS NULL`。

## 6.3 成本控制策略

为控制 OP 自托管环境中的 YouTube API 消耗：

- OP 不限制用户订阅大 playlist。
- 大 playlist 用户可把 `sync_interval_hours` 设置为 `12`、`24` 或更高。
- 自动同步仍受 YouTube API 日额度统计与熔断保护。
- 手动刷新始终尝试全量扫描，仍受用户配置的 YouTube API Key 和当前 OP 日额度熔断保护影响。

---

## 7. 数据模型设计

## 7.1 新增 `youtube_playlist_item` 表

新增表用于保存 YouTube playlist 条目真源。

当前字段：

```sql
CREATE TABLE youtube_playlist_item
(
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    playlist_id            TEXT      NOT NULL,
    playlist_item_id       TEXT      NOT NULL,
    video_id               TEXT      NOT NULL,
    episode_id             TEXT      NULL,
    item_added_at          TIMESTAMP NULL,
    video_published_at     TIMESTAMP NULL,
    position               INTEGER   NULL,
    item_privacy_status    TEXT      NULL,
    source_channel_id      TEXT      NULL,
    source_channel_name    TEXT      NULL,
    source_channel_url     TEXT      NULL,
    presence_status        TEXT      NOT NULL,
    materialization_status TEXT      NOT NULL,
    auto_dispatch_status   TEXT      NOT NULL,
    first_seen_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at             TIMESTAMP NULL,
    last_error             TEXT      NULL,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

当前索引：

```sql
CREATE UNIQUE INDEX uq_youtube_playlist_item_playlist_item
    ON youtube_playlist_item (playlist_id, playlist_item_id);

CREATE INDEX idx_youtube_playlist_item_presence_position
    ON youtube_playlist_item (playlist_id, presence_status, position);

CREATE INDEX idx_youtube_playlist_item_video
    ON youtube_playlist_item (playlist_id, video_id);

CREATE INDEX idx_youtube_playlist_item_materialization
    ON youtube_playlist_item (playlist_id, presence_status, materialization_status);

CREATE INDEX idx_youtube_playlist_item_dispatch
    ON youtube_playlist_item (playlist_id, auto_dispatch_status, materialization_status);
```

## 7.2 条目状态枚举

`presence_status`：

- `ACTIVE`：本轮远端仍存在。
- `REMOVED`：远端已不存在。

`materialization_status`：

- `PENDING`：待补视频详情并生成/关联 `episode`。
- `LINKED`：已关联到本地 `episode`。
- `SKIPPED`：不应进入节目列表，例如 live/upcoming、无有效时长、被当前 feed 过滤规则排除。
- `FAILED`：补详情或入库失败，后续可重试。

`auto_dispatch_status`：

- `SUPPRESSED_BOOTSTRAP`：bootstrap 历史库存，不自动下载。
- `PENDING`：新条目已就绪，等待自动下载分发。
- `DISPATCHED`：已分发过自动下载。
- `SKIPPED`：当前配置不允许自动下载或条目不可下载。

## 7.3 扩展 `playlist` 表同步字段

当前 hybrid 方案已有：

- `last_snapshot_at`
- `last_snapshot_size`
- `last_sync_added_count`
- `last_sync_removed_count`
- `last_sync_moved_count`
- `sync_error`
- `sync_error_at`

官方 API 方案已新增：

- `last_observed_item_count INTEGER NULL`
- `last_item_count_checked_at TIMESTAMP NULL`
- `last_full_scan_at TIMESTAMP NULL`
- `last_full_scan_size INTEGER NULL`
- `last_full_scan_pages INTEGER NULL`
- `bootstrap_completed_at TIMESTAMP NULL`
- `last_sync_inserted_item_count INTEGER NOT NULL DEFAULT 0`
- `last_sync_removed_item_count INTEGER NOT NULL DEFAULT 0`
- `last_sync_moved_item_count INTEGER NOT NULL DEFAULT 0`
- `last_sync_materialized_count INTEGER NOT NULL DEFAULT 0`
- `last_sync_dispatched_item_count INTEGER NOT NULL DEFAULT 0`
- `sync_interval_hours INTEGER NOT NULL DEFAULT 3`

兼容建议：

- 当前实现保留旧 hybrid 字段，并且官方 API 同步路径不再写入 `last_snapshot_*`。
- 当前实现不再写入 `last_observed_item_count` 和 `last_item_count_checked_at`；`last_full_scan_*` 仍作为最近一次 full scan 的观测字段写入。
- 当前实现仍保留 `playlist_episode_detail_retry` 表、mapper、scheduler 和旧 snapshot 方法，用于兼容/消化历史遗留重试数据；官方 API 同步路径不再向该队列新增任务。
- 新字段稳定后，再单独清理 `last_snapshot_*`、`last_observed_item_count`、`last_item_count_checked_at`、`playlist_episode_detail_retry` 和未被入口调用的 snapshot 代码。

## 7.4 `episode` 表保持不变

OP 当前 `episode.id` 使用 YouTube `videoId`，继续保持。

构建 YouTube playlist episode 时：

- `episode.id = videoId`
- `episode.channel_id = null`，避免污染频道视图。
- `episode.published_at = video.snippet.publishedAt`，表示视频发布时间。
- `episode.live_vod` 按当前 `YoutubeVideoHelper.isArchivedLiveVodPro(...)` 逻辑写入。

## 7.5 `playlist_episode` 作为派生视图

`playlist_episode` 继续服务于：

- Feed 详情页。
- RSS 输出。
- 播放列表维度清理。
- source channel 信息展示。

但它不再作为 YouTube playlist 同步 diff 的事实源。

派生规则：

- 来源：`youtube_playlist_item`
- 条件：`presence_status = ACTIVE` 且 `materialization_status = LINKED` 且 `episode_id IS NOT NULL`
- `playlist_episode.episode_id = youtube_playlist_item.episode_id`
- `playlist_episode.position = youtube_playlist_item.position`
- `playlist_episode.published_at = youtube_playlist_item.item_added_at`

同一 `videoId` 在同一 playlist 出现多次时，当前阶段默认选 `position` 最小的 active item 作为代表，保持现有 `playlist_episode` 模型不变。

---

## 8. 同步算法

## 8.1 执行入口

现有入口保持：

- 新增订阅后：`processPlaylistInitializationAsync(...)`
- 定时同步：`PlaylistSyncer.syncDuePlaylists()`
- 手动刷新：`refreshPlaylistById(...)`

YouTube playlist 分支统一进入：

```text
syncYoutubePlaylistWithOfficialApi(playlist, mode)
```

`mode` 建议：

- `INIT`
- `INCREMENTAL`
- `MANUAL_FULL`

Bilibili playlist 和 Individual Videos playlist 不进入本方案。

## 8.2 定时 due 判断

自动同步不再先调用 `playlists.list(part=contentDetails)` 探测 `itemCount`。

定时任务只做本地判断：

```text
last_sync_timestamp IS NULL
OR last_sync_timestamp + sync_interval_hours <= now
```

未到期时：

- 不调用 YouTube API。
- 不更新 `last_sync_timestamp`。
- 不修改 `youtube_playlist_item`。
- 不修改 `playlist_episode`。

到期、初始化或手动刷新时，直接进入全量扫描。

## 8.3 全量扫描

需要全量时调用：

```text
playlistItems.list(
  part=snippet,contentDetails,status,
  playlistId=<playlistId>,
  maxResults=50,
  pageToken=<nextPageToken>
)
```

持续翻页直到 `nextPageToken = null`。

每个远端条目解析为：

```text
RemoteYoutubePlaylistItem {
  playlistItemId
  videoId
  itemAddedAt
  videoPublishedAt
  position
  itemPrivacyStatus
  sourceChannelId
  sourceChannelName
  sourceChannelUrl
}
```

要求：

- 必须成功拉完全部页面，才允许应用本轮 diff。
- 任一页失败，本轮全量同步失败，不应用局部结果。
- 对缺少 `playlistItemId` 的脏数据直接跳过；对缺少 `videoId` 的脏数据跳过并记录 warn，不让单条异常中断整轮。

## 8.4 条目层 diff

本地 active 条目：

```text
localActiveItems = youtube_playlist_item
  WHERE playlist_id = ?
  AND presence_status = 'ACTIVE'
```

以 `playlist_item_id` 为 key 做差集：

- `insertedItems`：远端存在，本地不存在。
- `removedItems`：本地 active 存在，远端不存在。
- `movedItems`：两边都存在，但 `position` 变化。
- `changedItems`：两边都存在，但 `video_id`、`item_added_at`、`privacy_status` 等字段变化。

更新规则：

1. `insertedItems`
   - 插入 `youtube_playlist_item`。
   - `presence_status = ACTIVE`。
   - `materialization_status = PENDING`。
   - 如果是 bootstrap：`auto_dispatch_status = SUPPRESSED_BOOTSTRAP`。
   - 如果不是 bootstrap：`auto_dispatch_status = PENDING`。

2. 远端仍存在的旧条目
   - 更新 `position`、`item_added_at`、`video_published_at`、`item_privacy_status`。
   - 更新 `source_channel_id`、`source_channel_name`、`source_channel_url`。
   - `presence_status = ACTIVE`。
   - `last_seen_at = now`。
   - 若之前是 `REMOVED` 后重新出现，清空 `removed_at`。
   - 若 `video_id` 变化，将 `episode_id` 清空，`materialization_status` 重置为 `PENDING`，并按 bootstrap 状态重置 `auto_dispatch_status`。

3. `removedItems`
   - 不删除 `youtube_playlist_item`。
   - 标记 `presence_status = REMOVED`。
   - 写入 `removed_at = now`。
   - 不立即删除 `episode`。

## 8.5 Episode materialization

处理范围：

```text
presence_status = ACTIVE
AND materialization_status IN ('PENDING', 'FAILED')
```

步骤：

1. 先按 `video_id` 查询本地 `episode`。
2. 已存在：
   - 写回 `episode_id`。
   - `materialization_status = LINKED`。
3. 不存在：
   - 按 50 个一批调用 `videos.list(part=contentDetails,snippet,liveStreamingDetails)`。
   - 构建 `Episode`。
   - 写入 `episode`。
   - 写回 `episode_id`。
   - `materialization_status = LINKED`。
4. 不可 materialize：
   - live/upcoming：`SKIPPED`。
   - 无有效时长：`SKIPPED`。
   - 被当前 playlist 过滤规则排除：`SKIPPED`。
5. API 或数据库异常：
   - `materialization_status = FAILED`。
   - 写入 `last_error`。
   - 后续同步继续重试。

过滤规则必须复用当前 feed 过滤语义：

- 标题包含/排除关键词。
- 描述包含/排除关键词。
- 最小/最大时长。
- `excludeLiveVod`。

注意：过滤失败的 item 仍保留在 `youtube_playlist_item` 中，便于排障和后续配置变化后重算。

当前实现细节：

- `YoutubePlaylistItemMapper.selectPendingMaterialization(...)` 每轮处理 `ACTIVE + PENDING/FAILED` 条目。
- 已存在的 `episode` 会直接按 `video_id` 关联，且仍会重新套用当前 playlist 过滤规则；不匹配时标记为 `SKIPPED`。
- 缺失详情的 `video_id` 会去重后批量请求 `videos.list`。同一 `videoId` 在同一 playlist 出现多次且本地 episode 还不存在时，当前轮只会 materialize 该 `videoId` 的首个 item；后续轮次会通过已存在的 episode 关联其他重复 item。
- playlist 配置更新后，当前实现会把该 playlist 下 `SKIPPED` 的 official item 重置为 `PENDING`，让新过滤规则重新生效。

## 8.6 派生 `playlist_episode`

全量扫描和 materialization 完成后，重新派生当前 playlist 的 `playlist_episode`。

当前实现方式：

1. 查询所有 active + linked item。
2. 按 `episode_id` 去重，选择 `position` 最小的 item。
3. 对代表 item 执行 upsert：
   - `position`
   - `published_at = item_added_at`
   - source channel 字段
4. 删除不再属于派生集合的 `playlist_episode` 关联。
5. 对被删除关联对应的孤立 playlist episode，复用现有 `removeOrphanEpisodes(...)` 逻辑清理。

安全要求：

- 只有完整全量扫描成功后才能删除 `playlist_episode`。
- full scan 前置失败或任一页 `playlistItems.list` 失败时，不删除任何关联。

## 8.7 自动下载分发

自动下载候选不再使用“本轮新 linked episode”作为唯一依据，而是使用条目状态：

```text
presence_status = ACTIVE
AND materialization_status = LINKED
AND auto_dispatch_status = PENDING
```

处理规则：

1. 将 item 映射到 `episode_id`。
2. 同一个 `episode_id` 去重，选择 `position` 最小的 item。
3. 按 `position ASC` 排序。
4. 复用 `selectEpisodesForAutoRefresh(...)`、延迟自动下载、`markAndPublishAutoDownloadEpisodes(...)` 等现有链路。
5. 分发成功后，将所有映射到已分发 `episode_id` 的 pending item 标记为 `DISPATCHED`。
6. 如果 feed 关闭自动下载，或 episode 没有被本轮自动下载选择，标记为 `SKIPPED`。

Bootstrap 规则：

- 第一次成功全量扫描只建立本地状态。
- 历史 item 统一写为 `SUPPRESSED_BOOTSTRAP`。
- 本轮不批量触发自动下载。
- bootstrap 完成后新增的 item 才进入 `PENDING` 自动分发。

---

## 9. 历史补抓策略

当前 OP 的 playlist 历史补抓使用 `playlistItems.list` page token cursor。官方 API 新方案完成后，YouTube playlist 的自动同步已经覆盖完整 playlist，因此历史补抓对 YouTube playlist 的意义会降低。

当前实现：

1. YouTube playlist：
   - 若没有任何 `playlist_episode` 关联，直接返回空列表。
   - 若 history cursor 已耗尽，直接返回空列表。
   - 若未完成 bootstrap，触发一次 `MANUAL_FULL` 同步，然后返回空列表。
   - 若已完成 bootstrap，直接返回空列表。
2. Bilibili playlist：
   - 保持现有按页历史补抓逻辑。
3. Individual Videos：
   - 保持现有不支持刷新/历史补抓逻辑。

这样可以减少 YouTube playlist 的重复分页路径，降低排障复杂度。

---

## 10. 失败处理

## 10.1 full scan 前置失败

当前实现没有 `playlists.list(itemCount)` 前置探测。full scan 前置失败通常是 API Key、代理、配额熔断或创建 YouTube client 失败。

处理：

- 本轮同步失败。
- 更新 `playlist.sync_error`、`sync_error_at` 和 `last_sync_timestamp`。
- 不修改条目状态。
- 不修改 `playlist_episode`。
- 不推进自动下载。

## 10.2 `playlistItems.list` 任一页失败

- 本轮全量同步失败。
- 丢弃已拉取的局部页面结果。
- 不应用 diff。
- 不删除 `playlist_episode`。
- 不标记 item removed。
- 不推进自动下载。
- 更新 `playlist.sync_error`、`sync_error_at` 和 `last_sync_timestamp`。

## 10.3 `videos.list` 失败

- 只影响对应 item 的 materialization。
- 条目仍保持 `ACTIVE`。
- 标记 `materialization_status = FAILED`。
- 记录 `last_error`。
- 下轮 official API 同步继续补齐；当前 official API 路径不使用 `playlist_episode_detail_retry` 队列。

## 10.4 单条视频不可用

例如：

- 视频被删除。
- 视频设为 private。
- 缺少有效时长。
- live/upcoming。

处理：

- item 保留。
- `materialization_status = SKIPPED`。
- 不进入 `playlist_episode`。
- 不触发自动下载。

## 10.5 YouTube API 日额度熔断

OP 已有 `YoutubeQuotaService`、`YoutubeApiExecutor`、`YoutubeQuotaContextHolder`。

新方案继续沿用：

- 自动同步上下文为 `AUTO_SYNC`。
- `playlistItems.list`、`videos.list` 都通过 `YoutubeApiExecutor` 记账。
- 当天自动同步被熔断后，`PlaylistSyncer` 跳过后续 YouTube playlist。

本方案不增加 SaaS 用户级配额、套餐限制、免费/付费区隔。

---

## 11. 当前代码落点与后续清理

## 11.1 当前新增/复用组件

当前实现：

- `PlaylistService.syncYoutubePlaylistWithOfficialApi(...)`
  - 当前实际编排全量扫描、diff、materialization、派生、自动下载分发；没有拆出独立 `YoutubePlaylistOfficialApiSyncService`。
- `YoutubePlaylistItemMapper`
  - 管理 `youtube_playlist_item`。
- `YoutubePlaylistItem`
  - 条目真源实体。
- `YoutubePlaylistPresenceStatus` / `YoutubePlaylistMaterializationStatus` / `YoutubePlaylistAutoDispatchStatus`
  - 状态枚举。
- `YoutubePlaylistRemoteItem`
  - API 返回条目的内部 DTO。

## 11.2 调整现有组件

`PlaylistService`：

- YouTube playlist 的 `refreshPlaylist(...)` 已改为调用 `syncYoutubePlaylistWithOfficialApi(playlist, "INCREMENTAL")`。
- `processPlaylistInitializationAsync(...)` 对 YouTube playlist 调用 `INIT` 同步。
- `fetchPlaylistHistory(...)` 对 YouTube playlist 改为基于官方全量同步策略处理。
- `refreshPlaylistById(...)` 对 YouTube playlist 调用 `MANUAL_FULL`。
- `findDueForSync(...)` 使用 `syncIntervalHours` 和 `lastSyncTimestamp` 判断 playlist 是否到期。

`YoutubeVideoHelper`：

- 保留 `playlistItems.list` page fetch 能力。
- 通过 `fetchPlaylistPage(..., part)` 支持 `id,snippet,contentDetails,status`。
- 继续使用 `fetchVideoDetailsInBulk(...)` 补 episode 详情。

`YoutubePlaylistHelper`：

- `fetchAllPlaylistItemsOfficial(...)` 负责完整翻页，直到 `nextPageToken = null`。
- 老的 `fetchPlaylistVideos(...)` 仍服务预览、Bilibili 以外的 legacy helper 路径和 `AbstractFeedService` 覆写方法，但 YouTube playlist 的刷新/初始化主入口已不再走它。

`PlaylistSyncer`：

- 每 1 小时调度一次。
- 每次只同步已到期 playlist。
- 继续设置 `YoutubeQuotaContextHolder.AUTO_SYNC`。
- 保持日额度熔断短路。

## 11.3 hybrid 组件当前状态

当前已经停用：

- YouTube playlist 的初始化、手动刷新、定时同步入口不再调用 `syncPlaylistWithSnapshot(...)`。
- 官方 API 路径不再写入 `last_snapshot_*`。
- 官方 API 路径不再向 `playlist_episode_detail_retry` 新增任务。

当前仍保留，后续稳定后可删除：

- `YtDlpPlaylistSnapshotService`
- `PlaylistSnapshotEntry`
- `PlaylistDetailRetrySyncer`
- `PlaylistEpisodeDetailRetry` / `PlaylistEpisodeDetailRetryMapper`
- `playlist_episode_detail_retry`
- `PlaylistService.syncPlaylistWithSnapshot(...)`
- `last_snapshot_*` 字段和旧 `last_sync_added_count` / `last_sync_removed_count` / `last_sync_moved_count` 字段

保留：

- `YtDlpRuntimeService`
- `DownloadHandler` 中的 yt-dlp 下载逻辑
- yt-dlp 自定义参数与运行时升级功能

---

## 12. 迁移方案

## 12.1 第一阶段：加表与字段（已完成）

新增：

- `youtube_playlist_item`
- `playlist.last_observed_item_count`
- `playlist.last_item_count_checked_at`
- `playlist.last_full_scan_at`
- `playlist.last_full_scan_size`
- `playlist.last_full_scan_pages`
- `playlist.bootstrap_completed_at`
- 新的 `last_sync_*` 统计字段
- `playlist.sync_interval_hours`

保留旧字段，不做破坏性删除。

## 12.2 第二阶段：实现官方 API 同步分支

- 在 YouTube playlist 上启用官方 API 同步。（已完成）
- Bilibili 和 Individual Videos 不受影响。（已完成）
- 保留 hybrid 代码但不再从 YouTube playlist 调度/手动刷新/初始化入口调用。（已完成）
- 删除 `itemCount` 轻量探测，改为每小时 job + playlist 级 `sync_interval_hours` 到期后直接 full scan。（已完成）

## 12.3 第三阶段：现有 playlist bootstrap

对已订阅的 YouTube playlist：

1. 首次官方 API 全量扫描视为 bootstrap。
2. 写入 `youtube_playlist_item`。
3. 派生 `playlist_episode`。
4. 历史条目设置 `SUPPRESSED_BOOTSTRAP`。
5. 不触发历史库存自动下载。

当前实现中，`bootstrap_completed_at IS NULL` 即表示未完成 bootstrap；首次 full scan 成功后写入 `bootstrap_completed_at`，并把返回给调用方的 `newEpisodeCount` 置为 0。

## 12.4 第四阶段：清理旧 hybrid 数据和代码（待做）

验证稳定后：

- 删除 snapshot service 和 DTO。
- 删除 detail retry 队列表与调度器。
- 删除旧 hybrid 同步字段或仅保留兼容读取。
- 更新架构文档中 playlist 同步描述。

---

## 13. 验证方案

## 13.1 单元测试

覆盖：

- `last_sync_timestamp IS NULL` 时触发全量扫描。
- `last_sync_timestamp + sync_interval_hours <= now` 时触发全量扫描。
- 未到达 `sync_interval_hours` 时不调用 YouTube API。
- `sync_interval_hours <= 0` 时按默认 3 小时处理。
- `playlistItem.id` 差集：insert/remove/move/change。
- bootstrap item 不进入自动下载。
- 非 bootstrap 新 item 进入 `PENDING` 分发。
- `videos.list` 失败时 item 标记 `FAILED`。
- 过滤失败时 item 标记 `SKIPPED`。

## 13.2 集成测试

覆盖：

- 初始化一个 playlist。
- 删除远端条目后本地只在完整全量成功后移除 `playlist_episode`。
- 模拟 `playlistItems.list` 中途失败，本地关联保持不变。
- 模拟同一 `videoId` 多个 `playlistItem.id`，派生时只选择一个代表。
- 自动下载只对新增 item 触发一次。

## 13.3 回归测试

覆盖：

- YouTube playlist RSS 输出。
- Feed 详情页分页、排序、搜索、状态轮询。
- 手动刷新。
- 自动下载延迟策略。
- 最大保留数清理。
- YouTube API 日额度统计和自动同步熔断。

---

## 14. 开放决策

## 14.1 是否接受 bootstrap 不自动下载历史库存

建议：接受。

原因：

- 切换同步方案时，历史条目只是被新真源重新识别，不代表今天新增。
- 自动下载历史库存会造成大量非预期下载。

## 14.2 是否暂不完整支持 playlist 内重复视频展示

建议：接受。

原因：

- 当前 `playlist_episode` 结构只能表达一个 playlist 到一个 episode 的单次关联。
- 完整支持重复条目需要前端、RSS、下载状态都改为 item 级展示，超出本轮同步重构范围。

## 14.3 是否保留 OP YouTube API 日额度熔断

建议：保留。

原因：

- 官方 API 方案会增加 playlist 同步的 API 使用量。
- OP 是用户自带 API Key，自托管场景仍需要可观测和自动同步保护。
- 这不是 SaaS 用户配额逻辑，不涉及套餐或用户分摊。

---

## 15. 总结

新方案的核心是：

- `playlistItems.list` 替代 `yt-dlp --flat-playlist` 成为 YouTube playlist 成员事实源。
- `playlistItem.id` 替代 `videoId` 成为 playlist 条目 diff 主键。
- `youtube_playlist_item` 保存条目真源和一次性自动下载分发状态。
- `playlist_episode` 由条目真源派生，继续服务现有页面、RSS 和下载链路。
- 完整全量扫描成功后才允许删除/移动本地关联。
- Bootstrap 阶段不触发历史库存自动下载。

这会牺牲一部分 API 成本，但换来同步正确性、实现可解释性和排障可控性。
