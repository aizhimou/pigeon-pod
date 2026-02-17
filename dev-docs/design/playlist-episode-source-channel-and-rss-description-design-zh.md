# PigeonPod YouTube Playlist 单集来源频道展示与 RSS 描述增强设计（评审稿）

## 1. 目标与范围

本设计用于满足以下需求：

- 在 `frontend/src/pages/Feed/index.jsx` 的 **YouTube Playlist** 单集卡片中，增加来源频道 Badge（视觉位于 description 前方）。
- 在 Playlist RSS 输出中，给 description 前缀增加来源频道文本。
- 明确 `<description>` 是否可以放 `<a>` 标签，以及兼容性策略。

非目标：

- 不改下载流水线行为。
- 不在本轮引入新的 RSS namespace 扩展字段。

---

## 2. 现状分析（代码定位）

## 2.1 前端渲染现状

- 单集描述直接渲染 `episode.description`：`frontend/src/pages/Feed/index.jsx:1112`
- 当前页面只区分 `isPlaylist`，但没有“单集来源频道”字段可用：`frontend/src/pages/Feed/index.jsx:878`

## 2.2 后端分页与模型现状

- Playlist 明细页的单集列表来自 `EpisodeService.episodePage(...)` 的 playlist 分支：
  - `backend/src/main/java/top/asimov/pigeon/service/EpisodeService.java:85`
  - 由 `PlaylistEpisodeMapper.selectEpisodePageByPlaylistIdWithFilters(...)` 返回 `e.*`
  - `backend/src/main/java/top/asimov/pigeon/mapper/PlaylistEpisodeMapper.java:34`
- `playlist` 仅有 `owner_id`，表示播放列表拥有者，不是每个 episode 的来源频道：
  - `backend/src/main/java/top/asimov/pigeon/model/entity/Playlist.java:22`
- `playlist_episode` 只存 `playlist_id/episode_id/position/published_at`，没有来源频道字段：
  - `backend/src/main/java/top/asimov/pigeon/model/entity/PlaylistEpisode.java:21`

## 2.3 YouTube Playlist 同步路径现状

- Playlist 新增单集详情由 YouTube `videos.list` 获取，理论上可拿到 `snippet.channelId/channelTitle`。
- 但当前持久化时没有保存来源频道名/链接。
- 且 playlist 路径会主动清空 `episode.channelId`（避免污染频道视图）：
  - `backend/src/main/java/top/asimov/pigeon/service/PlaylistService.java:1182`

## 2.4 RSS 描述现状

- RSS 生成时将 `episode.description` 写入 `description`，类型 `text/html`，换行转 `<br/>`：
  - `backend/src/main/java/top/asimov/pigeon/service/RssService.java:145`

---

## 3. 关键设计判断

`playlist.owner_id` 不能满足“每个 episode 来源频道”的需求，原因：

1. `owner_id` 是 playlist 拥有者，不等于单集上传者。
2. playlist 内可能混入多个来源频道的视频。
3. 即使给 `playlist` 补 `owner_name/owner_url`，也只能得到“一个频道”，不能表达“每条 episode”。

结论：来源频道信息不能放在 `playlist`（feed 维度），必须至少到“playlist + episode”维度。

---

## 4. 方案对比与推荐

## 4.1 方案 A：扩展 `playlist`（不推荐）

- 做法：`playlist` 增加 `owner_name/owner_url`
- 问题：无法表达每条 episode 的来源，语义不满足需求。

## 4.2 方案 B：扩展 `playlist_episode`（推荐）

- 做法：`playlist_episode` 增加 `source_channel_*`
- 优点：
  - 字段语义与业务需求完全对齐（仅 playlist 视图使用）。
  - 同一 video 在不同 playlist 可保留各自来源快照信息。
  - 不污染 `episode` 主表的通用模型。
- 缺点：同一 episode 被多个 playlist 复用时会重复存储（可接受）。

## 4.3 方案 C：扩展 `episode`

- 做法：`episode` 增加 `source_channel_id/source_channel_name/source_channel_url`
- 优点：查询改造少。
- 缺点：字段只服务 playlist 需求，会把 playlist 语义带到通用 `episode` 模型。

最终推荐：**方案 B（扩展 `playlist_episode`）**。

---

## 5. 推荐方案详细设计

## 5.1 数据库与实体变更

新增迁移脚本（建议）：

- `backend/src/main/resources/db/migration/V34__Add_playlist_episode_source_channel_fields.sql`

新增列：

- `playlist_episode.source_channel_id TEXT NULL`
- `playlist_episode.source_channel_name TEXT NULL`
- `playlist_episode.source_channel_url TEXT NULL`

实体变更：

- `backend/src/main/java/top/asimov/pigeon/model/entity/PlaylistEpisode.java` 新增对应字段。
- `backend/src/main/java/top/asimov/pigeon/model/entity/Episode.java` 增加 `@TableField(exist = false)` 的临时返回字段：
  - `sourceChannelId/sourceChannelName/sourceChannelUrl`
  - 用于 playlist 明细 API 与 playlist RSS 输出。

## 5.2 数据写入链路改造

在 YouTube playlist 同步链路中，来源频道信息优先来自 `yt-dlp --flat-playlist -J` 快照：

- `YtDlpPlaylistSnapshotService` 解析 `entries[*].channel_id/channel/channel_url`。
- `PlaylistSnapshotEntry` 增加：
  - `sourceChannelId`
  - `sourceChannelName`
  - `sourceChannelUrl`
- `PlaylistService.upsertPlaylistEpisodeMapping(...)` / `upsertPlaylistEpisodes(...)` 在写入关联时同步写入 `source_channel_*`。
- `PlaylistEpisodeMapper` 改造：
  - `insertMapping/updateMapping` 新增 `sourceChannelId/sourceChannelName/sourceChannelUrl` 参数。
  - playlist 分页查询改为返回：
    - `e.*`
    - `pe.source_channel_id AS source_channel_id`
    - `pe.source_channel_name AS source_channel_name`
    - `pe.source_channel_url AS source_channel_url`
- `EpisodeMapper.selectEpisodesByPlaylistId(...)` 同步追加上述 alias 字段，供 RSS 直接复用。

说明：

- 继续保留 playlist 语义下 `episode.channelId = null` 的现有策略（不改变现有行为）。
- `source_channel_*` 只存在于 `playlist_episode`，不参与 channel feed 逻辑。
- 当快照缺字段时，可回退到视频详情 `snippet.channelId/channelTitle` 补齐。

## 5.3 历史数据回填（纳入本方案）

你给出的判断成立：当前 YouTube playlist 每轮同步都全量执行 `yt-dlp --flat-playlist -J`，而快照里已包含每个 entry 的 `channel_id/channel/channel_url`（参考：`dev-docs/yt-dlp --flat-playlist_data.json`）。

因此回填策略可以做成“随同步自然收敛”，无需额外全量任务：

1. 本次同步拿到快照后，不仅处理 `added/removed/moved`，还对命中的本地 mapping 执行来源字段补写。
2. 条件：`source_channel_name IS NULL` 或字段值变化时执行 update。
3. 多轮定时同步后，历史数据会逐步被回填完整。

## 5.4 前端展示改造（Feed 页）

文件：

- `frontend/src/pages/Feed/index.jsx`

规则：

1. 仅在 `isPlaylist && feed.source === YOUTUBE` 时展示来源频道 Badge。
2. Badge 显示文本：`sourceChannelName`（视觉上位于 description 前方）。
3. Badge 点击行为：新标签页打开 `sourceChannelUrl`（`target="_blank" rel="noopener noreferrer"`）。
4. `sourceChannelUrl` 缺失时，Badge 退化为不可点击。
5. description 继续显示原始内容，不再硬拼 `[name]` 文本。

## 5.5 RSS 输出改造

文件：

- `backend/src/main/java/top/asimov/pigeon/service/RssService.java`

规则：

1. 仅在 `generatePlaylistRssFeed(...)` 且来源为 YouTube 时拼接前缀。
2. 前缀格式采用纯文本：`[channel_name](channel_url) `。
3. `channel_url` 缺失时退化为：`[channel_name] `。
4. 同步应用到：
   - `<item><description>`
   - `itunes:summary`（当前由 `entryInfo.setSummary(...)` 设置）

---

## 6. `<description>` 中 `<a>` 标签策略

结论：

- **规范上允许 HTML（需实体编码）**，RSS 2.0 对 item description 明确写了“entity-encoded HTML is allowed”。
- 但在 podcast 客户端生态下，兼容性差异较大，且 XML 转义要求严格。

本轮策略（按本次决策）：

1. 不在 `<description>` 中注入 `<a>` HTML 标签。
2. 使用纯文本 Markdown 形式前缀：`[channel_name](channel_url)`，以兼容性优先。
3. 即便客户端不解析 Markdown，也会显示为可读文本，不影响消费。

依据（官方文档）：

- RSS 2.0 Specification（RSS Advisory Board）  
  https://www.rssboard.org/rss-specification
- Apple Podcast RSS feed requirements（字符转义要求）  
  https://podcasters.apple.com/support/823-podcast-requirements

---

## 7. 历史数据与回填策略

本方案默认包含回填：

- 利用现有每轮 yt-dlp 全量快照，把 `playlist_episode.source_channel_*` 逐步补齐。
- 不需要额外“一次性回填全库”任务。
- 只有长期未再次同步的 playlist 才可能保留旧空值。

---

## 8. 风险与兼容性

## 8.1 兼容性

- API 返回 `Episode` 仅新增可选字段，不破坏现有前端逻辑。
- 不影响 channel feed 的分页和下载链路。

## 8.2 风险点

1. 历史数据在回填完成前，前端可能暂时无来源 Badge，RSS 也可能无来源前缀（可接受）。
2. 快照偶发缺少 `channel/channel_url` 时，只显示 channel name 或直接降级为原描述。
3. 若未来要显示可点击来源频道，需要再评估 RSS 客户端兼容矩阵。

---

## 9. 测试建议

1. 后端单测/集成：
   - YouTube playlist 同步后，`playlist_episode.source_channel_*` 正确写入。
   - 既有 mapping 在后续同步中可被回填（空值 -> 有值）。
   - `GET /api/episode/list/{playlistId}` 返回 `sourceChannelName/sourceChannelUrl`。
   - `generatePlaylistRssFeed(...)` 的 description/itunes summary 前缀为 `[name](url)` 纯文本。
2. 前端联调：
   - Playlist 页在 description 前显示可点击来源 Badge。
   - 非 Playlist 页、Bilibili Playlist 页不受影响。
3. 兼容检查：
   - RSS 通过 Apple Connect 校验（确保字符转义与 XML 合法）。

---

## 10. 评审待确认项

1. `source_channel_*` 列命名是否最终采用 `source_channel_*`（或改为 `origin_channel_*`）？
2. Badge 文案是否固定只显示 `channel_name`，还是显示 `@handle`（若可拿到）？
3. `[channel_name](channel_url)` 是否同时加入 `itunes:summary`（当前建议是“是”）？
