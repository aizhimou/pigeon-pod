# PigeonPod 接入 B站订阅（UP 视频列表 + UP 合集）最小化设计

## 1. 目标与范围

目标：在不改变 PigeonPod 现有交互结构的前提下，以最小工作量增加 B站订阅能力：

- 支持订阅 **UP 视频列表**（对齐现有 `CHANNEL`）。
- 支持订阅 **UP 合集/播放列表**（Season/Series，对齐现有 `PLAYLIST`）。
- 复用现有 `fetch -> preview -> add -> refresh -> download -> rss` 主流程。

本期不做：

- 不新增前端页面，不改路由结构。
- 不重构现有 YouTube 主链路。
- 不实现复杂的“全量重排/删除精确追踪”算法（以可用为先）。

---

## 2. 现状约束（基于当前代码）

当前系统已经具备多源扩展基础，但存在 YouTube 强耦合点：

1. `Feed.source` 字段已存在，但 `FeedSource` 只有 `YOUTUBE`。
2. `FeedType` 只有 `CHANNEL/PLAYLIST`，与本需求是兼容的（无需新增类型）。
3. 外链与下载 URL 写死 YouTube：
   - `DownloadHandler` 固定使用 `https://www.youtube.com/watch?v={episodeId}`。
   - `RssService` entry link 固定 YouTube。
   - `frontend/src/pages/Feed/index.jsx` 未下载节目跳转固定 YouTube。
4. 定时同步器受 YouTube 配额阻断影响（会误伤未来非 YouTube feed）。
5. OPML 导出 `htmlUrl/category` 固定 `youtube/*`。

结论：本期重点是“平台解耦 + B站抓取实现”，不是重新设计 Feed 模型。

---

## 3. 设计原则

1. 继续使用现有 `CHANNEL/PLAYLIST` 两类，不引入新业务类型。
2. 以 `source` 维度做平台区分（`YOUTUBE` / `BILIBILI`）。
3. 复用现有数据库表（`channel/playlist/episode/playlist_episode`），尽量不改表结构。
4. B站 API 仅使用 `dev-docs/bilibili-api-documentation.md` 已给出的公开接口。
5. 同步策略以“稳定可用”为先，避免过度工程化。

---

## 4. B站能力映射

## 4.1 接口映射

| 场景 | B站 API | 用途 |
| --- | --- | --- |
| UP 基本信息 | `/x/web-interface/card` | 生成频道标题、头像、简介 |
| UP 视频列表 | `/x/series/recArchivesByKeywords` | `CHANNEL` 预览/初始化/增量/历史 |
| Series 元信息 | `/x/series/series` | 获取 series 标题、简介、mid |
| Series 视频列表 | `/x/series/archives` | `PLAYLIST`（series）预览/同步/历史 |
| Season 信息+视频 | `/x/polymer/web-space/seasons_archives_list` | `PLAYLIST`（season）预览/同步/历史 |
| 视频详情（兜底） | `/x/web-interface/view` | 单视频补详情（必要时） |

## 4.2 请求约束

按文档要求，B站请求统一带以下头部：

- `Accept`
- `Accept-Language`
- `Origin`
- `Referer`
- `User-Agent`

并统一检查 `code == 0`，否则抛业务异常。

---

## 5. 数据与ID策略（最小改动）

## 5.1 Feed Source

- 扩展 `FeedSource`：新增 `BILIBILI`。
- `channel.source` / `playlist.source` 存储 `BILIBILI`。

## 5.2 Feed ID 规范

不新增字段，直接在现有 `id` 编码平台与子类型信息，避免冲突：

- B站 UP（CHANNEL）：`bili-mid-{mid}`
- B站 Season（PLAYLIST）：`bili-season-{seasonId}`
- B站 Series（PLAYLIST）：`bili-series-{seriesId}`

收益：

- 不改表结构即可同时支持 season/series 且避免 ID 冲突。
- 与 YouTube ID 空间完全隔离。

## 5.3 Episode ID 规范

- `episode.id` 使用 B站 `bvid`（如 `BV1xx...`）。
- B站返回 `duration` 为秒，本地统一转换为 ISO8601（`PT{n}S`）复用现有时长过滤与展示逻辑。

---

## 6. 业务流程设计

## 6.1 订阅识别（`/api/feed/fetch`）

在 `FeedService.fetch` 增加“来源 + 类型”识别：

1. `source` 为 bilibili 域名或 `bili:*` 输入时，走 B站解析。
2. B站输入分三类：
   - UP：`https://space.bilibili.com/{mid}` 或纯 `mid`
   - Season：URL 含 `type=season`，或 `season:{id}`
   - Series：URL 含 `type=series`，或 `series:{id}`
3. 输出仍是现有 `FeedPack<Channel|Playlist>`，前端交互无变化。

## 6.2 CHANNEL（UP 视频列表）

- `fetch`: 拉 UP 信息 + 第一页视频（最多取 5 条预览）。
- `save/init`: 异步拉最近一页入库，按现有自动下载策略处理。
- `refresh`: 每轮拉第一页，按 `episode.id` 去重，新增入库。
- `history`: 按当前已入库数量计算下一页继续拉取。

备注：保持与当前 YouTube channel 近似行为，工作量最小。

## 6.3 PLAYLIST（Season / Series）

- `fetch`:
  - season：直接调用 season 接口拿 meta + videos。
  - series：先调用 series meta，再调 series archives。
- `save/init`: 拉第一页入库并建立 `playlist_episode` 关联。
- `refresh`: 拉前若干页（默认 1 页，必要时可配置为 2-3 页）并做 ID 去重新增。
- `history`: 按页向后拉取。

本期取舍：

- 不做 YouTube playlist 当前那套“快照 + 删除/重排精细追踪”。
- 只保证“新增节目可持续发现”，优先满足订阅可用性。

## 6.4 下载、RSS、外链统一解耦

新增统一“视频页面 URL 构建器”（按 source 生成）：

- YouTube: `https://www.youtube.com/watch?v={id}`
- B站: `https://www.bilibili.com/video/{bvid}`

改造使用点：

- `DownloadHandler` 下载目标 URL。
- `RssService` entry link。
- `Feed` 页未下载节目“跳转原站”行为。

## 6.5 定时同步与 YouTube 配额隔离

`ChannelSyncer`、`PlaylistSyncer` 改为按 source 区分：

- `YOUTUBE`：保持现有 quota guard。
- `BILIBILI`：不受 YouTube quota 阻断。

避免出现“YouTube 配额用尽导致 B站订阅不更新”。

## 6.6 OPML 导出

`AccountService.exportSubscriptionsOpml` 根据 `feed.source` 输出：

- `htmlUrl`：YouTube 或 B站对应地址。
- `category`：`youtube/channel`、`youtube/playlist`、`bilibili/channel`、`bilibili/playlist`。

---

## 7. 后端实现清单（建议）

## 7.1 新增组件

- `model/constant/Bilibili.java`
- `helper/BilibiliApiClient.java`：统一 GET、请求头、错误码处理、重试（`-500/-509` 指数退避）。
- `helper/BilibiliResolverHelper.java`：解析输入（mid/season/series）。
- `helper/BilibiliChannelHelper.java`：UP 列表抓取与 Episode 映射。
- `helper/BilibiliPlaylistHelper.java`：season/series 列表抓取与 Episode 映射。

## 7.2 主要修改点

- `model/enums/FeedSource.java`：新增 `BILIBILI`。
- `service/FeedService.java`：`fetch` 入口增加 source/type 识别分发。
- `service/ChannelService.java`：按 `source` 分支 fetch/refresh/history。
- `service/PlaylistService.java`：按 `source` 分支 fetch/refresh/history。
- `handler/DownloadHandler.java`：下载 URL 按 source 构建。
- `service/RssService.java`：entry link 按 source 构建。
- `scheduler/ChannelSyncer.java`、`scheduler/PlaylistSyncer.java`：source 维度调度。
- `service/AccountService.java`：OPML 导出根据 source 输出外链与分类。

## 7.3 数据库迁移

本方案默认不新增表/列。  
仅需确保历史数据 `source` 非空（已有默认 `YOUTUBE` 的话无需额外迁移）。

---

## 8. 前端实现清单（最小）

1. `frontend/src/pages/Feed/index.jsx`
   - 未下载节目跳转链接改为按 `feed.source` 生成。

2. `frontend/src/pages/Home/index.jsx` / 多语言文案
   - `enter_feed_source_url` 文案从“Youtube channel or playlist”扩成“YouTube/Bilibili”。
   - 其余 UI 结构保持不变。

3. 组件层（`FeedCard` / `FeedHeader`）
   - 保持 `CHANNEL/PLAYLIST` 徽标逻辑，不新增来源徽标（本期不改视觉结构）。

---

## 9. 验收标准

## 9.1 功能验收

1. 输入 B站 UP 链接可完成 `fetch -> preview -> add`。
2. 输入 B站 season/series 可完成 `fetch -> preview -> add`。
3. 定时同步能拉到新增视频并入库。
4. 自动下载、手动下载、重试、取消在 B站节目上可用。
5. RSS 正常产出，entry 外链指向 B站视频页。
6. 未下载节目点击“原站播放”可跳转 B站。
7. YouTube 配额达到上限时，B站自动同步不受影响。

## 9.2 回归验收

1. 现有 YouTube channel/playlist 流程不回归。
2. Dashboard 与状态机（READY/PENDING/DOWNLOADING/COMPLETED/FAILED）无行为变化。
3. OPML 导出仍可用，且包含正确来源链接。

---

## 10. 风险与处理

1. B站公开 API 频控（`-509`）
   - 处理：统一重试 + 分页请求限速（建议 >= 1s）。

2. 部分稿件不可见（`62002` / `state != 0`）
   - 处理：跳过入库并记录日志，不中断整轮同步。

3. B站输入形态复杂
   - 处理：首期明确支持范围（UP 链接、season/series 链接、`season:{id}`/`series:{id}`/`mid`），超出范围给明确错误提示。

4. playlist 复杂重排未精确追踪
   - 处理：首期仅保证新增订阅价值；后续若需要再引入“B站 playlist 快照增强”。

---

## 11. 分阶段交付建议

1. Phase 1（可用版）
   - 完成后端 B站抓取、入库、下载 URL 解耦、RSS 外链解耦、前端跳转解耦。

2. Phase 2（稳定版）
   - 增加更完整的输入解析覆盖、多语言文案补齐、播放列表增量页数优化与限流参数化。

该拆分可确保先以最小改动上线“可订阅可下载”的核心能力。
