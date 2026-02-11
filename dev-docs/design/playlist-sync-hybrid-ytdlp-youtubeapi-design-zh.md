# PigeonPod Playlist 同步方案（yt-dlp + YouTube Data API 混合版）

## 1. 文档目的

本文定义一个新的 Playlist 同步方案，用于替代“纯 YouTube Data API 全量扫描 + 复杂 HEAD/TAIL 增量判定”方案。

核心目标：

- 显著降低 YouTube Data API 配额消耗。
- 大幅简化 Playlist 增量更新规则与维护复杂度。
- 保留现有过滤能力（关键词/时长/live）与自动下载能力。
- 保证异常时可排障、可回退、可手动修复。

---

## 2. 方案一句话总结

**定时任务使用 `yt-dlp --flat-playlist -J` 获取 Playlist 全量“轻量快照”（ID+顺序+基础字段），本地做差集；仅对新增 ID 使用 YouTube Data API（`videos.list`）补齐关键详情。**

这意味着：

- Playlist 列表“发现变化”不再依赖 Data API。
- Data API 只用于“新增项详情补齐”，配额接近与新增数量线性相关。
- 不再需要复杂的 `HEAD/TAIL/UNKNOWN + checkpoint` 状态机。

---

## 3. 背景问题（现状）

当前 `PlaylistService.refreshPlaylist(...)` 会全量拉取 YouTube Playlist，并做差集判断。问题是：

1. 每次刷新都要翻页抓取，配额与 Playlist 规模直接相关。
2. Playlist 排序变化（新在前/新在后/重排）导致增量判定复杂。
3. 维护复杂规则（方向判定、漂移检测、token 跳转）成本高、排障难。

---

## 4. 设计前提（能力确认）

## 4.1 yt-dlp 能力

- `--flat-playlist`：可快速拿到 playlist entries 的轻量信息（id/url/title/index 等）。
- `-J/--dump-single-json`：可一次输出整个 playlist 的 JSON。
- flat 模式下部分字段可能缺失（官方明确）。
- `youtubetab:approximate_date` 可在 flat 模式提供“近似 upload_date/timestamp”。

## 4.2 YouTube Data API 能力

- `videos.list`（成本低）可批量补视频详情（时长、描述、发布时间、直播状态等）。
- `playlistItems.list` / `playlists.list` 在本方案中不是定时主路径，可降级为排障备用。

---

## 5. 目标与非目标

## 5.1 目标

- 将定时同步的 Data API 用量降到“仅新增项详情补齐”。
- 同步逻辑以“快照差集”为核心，减少策略分叉。
- 在字段缺失和外部波动时，保持系统可运行并可告警。

## 5.2 非目标

- 不追求完全脱离 YouTube Data API。
- 不在本阶段改动下载流水线核心（下载调度、任务模型等）。
- 不在本阶段引入复杂模式学习模型。

---

## 6. 新同步流程（端到端）

## 6.1 定时增量同步流程

对每个 playlist 的一次定时同步：

1. **抓快照（yt-dlp）**  
   执行：

   ```bash
   yt-dlp --flat-playlist -J \
     --extractor-args "youtubetab:approximate_date" \
     "https://www.youtube.com/playlist?list=<playlistId>"
   ```

2. **解析远端条目**
   - 构建 `remoteEntries`（按输出顺序）
   - 每个 entry 提取：
     - `videoId`
     - `position`（优先 `playlist_index`，否则按数组顺序重建）
     - `title`（可选）
     - `approxPublishedAt`（可选）

3. **读取本地关联**
   - 查询 `playlist_episode` + `episode`，构建 `localEntries`。

4. **做差集**
   - `addedIds = remote - local`
   - `removedIds = local - remote`
   - `movedIds = intersection && position changed`

5. **更新关联（轻量事务）**
   - 对 `movedIds` 更新 `playlist_episode.position/published_at`
   - 对 `removedIds` 删除 `playlist_episode` 关联
   - 对 `addedIds` 先插入占位关联（或待 episode 入库后再插）

6. **补齐新增详情（仅 addedIds）**
   - 主路径：`videos.list` 批量（每批 <= 50）
   - 失败兜底：可选 `yt-dlp -J --no-playlist <watchUrl>` 单条补齐
   - 生成 `Episode` 后走现有过滤规则（关键词/时长/live）
   - 符合条件才入 `episode` 与 `playlist_episode`

7. **自动下载处理**
   - 仅对本次真正新增且通过过滤的 episode，沿用当前 `autoDownloadLimit/Delay` 逻辑。

8. **写入同步状态**
   - 更新 `lastSnapshotAt`、`lastSyncTimestamp`、统计信息、错误状态。

---

## 7. 为什么这个方案更简单

旧方案关注“从哪里开始增量抓”。  
新方案直接全量拿“轻量索引”，关注“哪些 ID 变化了”。

换句话说：

- 把“排序方向问题”转化为“集合差集问题”。
- 排序变化不再是主难题，直接通过 `movedIds` 处理即可。
- 数据一致性更直观，代码路径更少。

---

## 8. 数据模型改造建议

## 8.1 `playlist` 表新增字段（最小集）

建议新增：

- `last_snapshot_at` TIMESTAMP NULL
- `last_snapshot_size` INTEGER NULL
- `last_sync_added_count` INTEGER NOT NULL DEFAULT 0
- `last_sync_removed_count` INTEGER NOT NULL DEFAULT 0
- `last_sync_moved_count` INTEGER NOT NULL DEFAULT 0
- `sync_error` TEXT NULL
- `sync_error_at` TIMESTAMP NULL

说明：

- 不再需要 `sync_mode/head_anchor/tail_anchor/checkpoint` 相关字段。
- 关注“本轮差集结果”和“错误信息”即可。

## 8.2 可选：新增 `playlist_sync_run` 记录表（推荐）

用于排障与审计：

- `id`（自增）
- `playlist_id`
- `started_at` / `finished_at`
- `snapshot_size`
- `added_count` / `removed_count` / `moved_count`
- `api_video_detail_calls`
- `status`（SUCCESS/FAILED/PARTIAL）
- `error_message`

---

## 9. 关键实现细节

## 9.1 快照解析规范（重要）

需要一个统一解析器，避免后续字段波动导致逻辑混乱：

- `videoId` 优先级：
  1. `entry.id`
  2. 从 `entry.url` 解析 `v=...`
- `position` 优先级：
  1. `entry.playlist_index - 1`（若字段是 1-based）
  2. 数组下标
- `publishedAt`：
  - 若有 `timestamp`/`upload_date` 则转换
  - 没有则置空，后续由 `videos.list` 补齐

## 9.2 差集计算建议

使用 `Map<String, Entry>` 结构：

- 时间复杂度 `O(n)`。
- 输出三个集合 `added/removed/moved`。

## 9.3 Episode 入库策略

- 对 `addedIds`，先查 `episode` 是否已存在（全局 ID 去重）。
- 已存在：
  - 不重复插 `episode`
  - 仅维护 `playlist_episode` 关联
- 不存在：
  - 通过 `videos.list` 补全详情后再插入

## 9.4 过滤策略保持不变

继续复用当前过滤：

- 标题包含/排除关键词
- 描述包含/排除关键词
- 时长上下限
- 跳过 live/upcoming

这样能最大程度降低行为回归风险。

---

## 10. 容错与降级策略

## 10.1 yt-dlp 调用失败

- 不删除任何本地数据。
- 标记 `sync_error` 并记录失败日志。
- 下轮定时重试。
- 前端可提示“最近一次同步失败”。

## 10.2 videos.list 失败（配额/网络）

- 本轮先完成“关联差集”同步（顺序变更可先落地）。
- `addedIds` 进入“详情待补队列”（可新增轻量任务表，或复用现有任务机制）。
- 后续重试补详情。

## 10.3 部分 entry 缺字段

- 缺 `publishedAt`：先用 `position` + 占位时间，不阻断流程。
- 缺 `title`：用空字符串或占位文案，详情补齐后刷新。
- 严禁因单条脏数据导致整轮失败。

---

## 11. 配额收益估算（示意）

以 1000 条 playlist、每 3 小时同步一次为例：

- 旧方案：每轮都要多页 `playlistItems.list` + 多轮 `videos.list`
- 新方案：
  - 定时主链路：0 次 Data API（只跑 yt-dlp flat）
  - 仅当有新增时：`videos.list` 按新增量调用

如果新增很少，Data API 成本接近“按新增量线性增长”，而不是“按总量增长”。

---

## 12. 代码改造建议（按现有结构）

## 12.1 新增/调整组件

- 新增 `PlaylistSnapshotService`（或 `YtDlpPlaylistSnapshotHelper`）
  - 负责调用 yt-dlp 与解析 JSON
- `PlaylistService.refreshPlaylist(...)`
  - 改为：`snapshot -> diff -> patch mappings -> backfill new details`
- `YoutubePlaylistHelper`
  - 从“主抓取入口”退为“详情补齐辅助（可保留）”

## 12.2 可复用现有逻辑

- `filterNewEpisodes(...)` 思路可保留，但输入改为 `addedIds` 对应候选 Episode。
- `markAndPublishAutoDownloadEpisodes(...)` 可直接复用。
- `upsertPlaylistEpisodes(...)` 可扩展为支持批量 patch（insert/update/delete）。

---

## 13. 伪代码（核心路径）

```text
syncPlaylistIncremental(playlistId):
  snapshot = ytdlp.fetchFlatSnapshot(playlistId)
  local = repo.loadPlaylistEntries(playlistId)

  diff = computeDiff(snapshot, local)  # added/removed/moved

  tx:
    repo.applyMoved(playlistId, diff.moved)
    repo.applyRemoved(playlistId, diff.removed)

  if diff.added not empty:
    details = youtubeApi.fetchVideos(diff.added)  # batch <= 50
    episodes = buildEpisodes(details)
    episodes = applyFilters(episodes, playlistConfig)
    tx:
      episodeRepo.upsert(episodes)
      repo.applyAdded(playlistId, episodes, snapshot.positions)
    autoDownload(episodes)

  repo.updateSyncStats(...)
```

---

## 14. 测试与验收建议

## 14.1 功能测试

1. 新增在头部：能正确发现并入库。
2. 新增在尾部：能正确发现并入库。
3. 中间重排：`movedIds` 能正确更新 position。
4. 删除条目：关联能正确移除。
5. 新增但详情补齐失败：系统部分成功且可重试。

## 14.2 回归测试

- 关键词过滤结果与旧逻辑一致。
- 时长过滤与 live 过滤一致。
- 自动下载限额和延迟逻辑不变。

## 14.3 性能测试

- 大列表（1000+）下单轮同步耗时与内存占用。
- `added=0` 场景应接近最小成本。

---

## 15. 可观测性（建议必须做）

每轮日志至少记录：

- `playlistId`
- `snapshotSize`
- `added/removed/moved`
- `videosListBatchCount`
- `filteredOutCount`
- `persistedCount`
- `durationMs`
- `status`
- `error`

指标建议：

- `playlist_sync_success_total`
- `playlist_sync_failed_total`
- `playlist_sync_added_count`
- `playlist_sync_removed_count`
- `playlist_sync_moved_count`
- `playlist_sync_videos_api_calls`

---

## 16. 与旧方案对比（结论）

| 维度 | 旧方案（方向判定） | 新方案（快照差集） |
|---|---|---|
| 增量逻辑复杂度 | 高 | 低 |
| Data API 配额 | 与总量相关 | 与新增量相关 |
| 对排序变化敏感度 | 高 | 低（天然支持） |
| 排障难度 | 高 | 中低 |
| 维护成本 | 高 | 中低 |

结论：在 PigeonPod 已高度依赖 yt-dlp 的前提下，新方案更符合工程收益。

---

## 17. 关键决策（已确认）

本方案的实现口径已确认如下：

1. **`addedIds` 详情补齐采用强一致**
   - 新增项必须补齐详情并通过过滤后，才写入 `episode/playlist_episode`。

2. **`removedIds` 清理孤儿 episode**
   - 删除 `playlist_episode` 关联后，若该 episode 已无任何 channel/playlist 引用，则清理：
     - `episode` 记录
     - 媒体文件
     - 字幕/封面/章节等附属文件

3. **Data API 失败走重试队列**
   - 不在当轮阻塞整体同步。
   - 将失败的新增详情任务写入重试队列，后续异步重试。

4. **`approximate_date` 默认启用**
   - 作为快照辅助字段，提高可用性与可观测性。
   - 仍以 `videos.list` 的详情为准，不把近似时间作为严格判定依据。

5. **手动全量语义为“快照差集重建”**
   - 手动全量执行“快照拉取 + 差集重建 + 缺失详情补齐”，而非旧的 Data API 全量翻页。

---

## 18. 推荐落地路径（两阶段）

### 阶段 1（先可用）

- 实现 yt-dlp 快照差集主流程
- 新增/重排/删除关联同步
- 新增项 `videos.list` 详情补齐
- 基础日志与失败告警

### 阶段 2（再增强）

- 详情补齐重试队列
- 手动全量修复增强
- 更细粒度监控与运营面板

---

## 19. 总结

该混合方案把 Playlist 同步从“复杂方向推断问题”转换为“快照差集问题”，显著降低设计和维护难度，同时把 YouTube Data API 用量压到新增明细补齐层面，是当前 PigeonPod 场景下性价比最高的工程方案之一。
