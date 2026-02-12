# Playlist 大列表同步内存优化设计（评审稿）

## 1. 背景与问题

用户反馈：在 **1GB 内存**机器上，同步超大 YouTube Playlist（上千/上万节目）时，进程内存会冲高到接近 100%，导致主机无响应甚至应用挂掉。

当前 `MANUAL_FULL`、`INCREMENTAL`、`INIT` 三种模式都进入同一核心流程：

- `PlaylistService.syncPlaylistWithSnapshot(...)`
- 三种模式仅 `mode` 文案不同，核心逻辑一致（先快照，再差集，再详情补齐）。

因此“内存风险”本质上是**同一算法在大数据量场景下的峰值问题**。

---

## 2. 现状代码路径（定位结论）

### 2.1 三种模式统一走同一同步链路

- `MANUAL_FULL` -> `syncPlaylistWithSnapshot(..., "MANUAL_FULL")`
- `INCREMENTAL` -> `syncPlaylistWithSnapshot(..., "INCREMENTAL")`
- `INIT` -> `syncPlaylistWithSnapshot(..., "INIT")`

对应代码：

- `backend/src/main/java/top/asimov/pigeon/service/PlaylistService.java:246`
- `backend/src/main/java/top/asimov/pigeon/service/PlaylistService.java:251`
- `backend/src/main/java/top/asimov/pigeon/service/PlaylistService.java:810`

### 2.2 当前会产生高峰内存的关键点

#### A. yt-dlp 快照一次性读入内存 + 树模型解析

当前实现：

1. `ProcessBuilder` 输出到临时日志文件
2. `Files.readString(...)` 读完整字符串到内存
3. `objectMapper.readTree(...)` 构建完整 `JsonNode` 树

对应代码：

- `backend/src/main/java/top/asimov/pigeon/service/YtDlpPlaylistSnapshotService.java:90`
- `backend/src/main/java/top/asimov/pigeon/service/YtDlpPlaylistSnapshotService.java:122`

这会形成明显“峰值叠加”：**原始 JSON 文本 + JsonNode 树 + 业务对象列表**。

#### B. 差集阶段同时维护多份全量结构

`syncPlaylistWithSnapshot` 当前同时持有：

- `snapshotEntries`（全量快照列表）
- `remoteEntryMap`（全量 map）
- `localMappingMap`（本地全量 map）
- `addedIds / removedIds / movedEntries`（多个增量容器）

对应代码：

- `backend/src/main/java/top/asimov/pigeon/service/PlaylistService.java:259`
- `backend/src/main/java/top/asimov/pigeon/service/PlaylistService.java:261`
- `backend/src/main/java/top/asimov/pigeon/service/PlaylistService.java:262`

#### C. 新增详情补齐阶段把大列表一次性堆在内存

`processAddedEntries` 中：

- `getEpisodesByIds(addedIds)` 一次性查大 `IN (...)`
- `persistedNewEpisodes` 累积所有新增 `Episode` 后再统一 `saveEpisodes`
- `saveEpisodes` 内部再做一次 `toMap + in query + list/removeIf`，进一步放大峰值

对应代码：

- `backend/src/main/java/top/asimov/pigeon/service/PlaylistService.java:470`
- `backend/src/main/java/top/asimov/pigeon/service/PlaylistService.java:506`
- `backend/src/main/java/top/asimov/pigeon/service/PlaylistService.java:538`
- `backend/src/main/java/top/asimov/pigeon/service/EpisodeService.java:131`

#### D. 进程叠加

同步期间不仅有 Java 进程，另有 `yt-dlp` 子进程（Python）同时运行。  
在小内存机器上，双进程峰值叠加会更明显。

---

## 3. 为什么 1GB 机器容易打满

在大 playlist（尤其 INIT / MANUAL_FULL 且新增量大）场景，内存峰值来源不是单点，而是“阶段叠加”：

1. 快照 JSON 文本（完整）
2. Jackson 树模型（完整）
3. 快照对象 + 差集 map（完整）
4. 新增详情对象列表（大）
5. 入库去重中间结构（再复制一轮）
6. 同期 yt-dlp 子进程占用

这类峰值在 1GB 主机（系统、数据库、JVM、Python 共享）非常容易逼近 OOM 或严重 swap。

---

## 4. 优化目标

1. 将同步内存复杂度从“多份全量结构叠加”降到“有限窗口 + 批处理”。
2. 保持现有语义：差集更新、`addedIds` 详情强一致、失败进重试队列。
3. 对外行为尽量不变（前端状态栏字段仍可沿用）。

---

## 5. 方案总览（建议分两阶段）

## 5.1 阶段一（高收益、低风险，优先）

### 方案 A：快照解析改流式（避免 readString + readTree 全量树）

把 `YtDlpPlaylistSnapshotService` 改为基于 Jackson `JsonParser` 流式读取 `entries` 数组，逐条转换为 `PlaylistSnapshotEntry`。  
不再把整个 JSON 读成字符串再建树。

收益：

- 显著降低峰值内存
- 解析时延更稳定

参考（Context7 / Jackson Core）：

- `JsonParser` 流式 token 解析可避免整文档加载

### 方案 B：新增补齐改“批次内闭环”，不累计全量 `persistedNewEpisodes`

当前是先积攒所有 `persistedNewEpisodes`，最后一次性落库。  
改为每个 batch（如 50/100）独立完成：

1. `videos.list(batch)`
2. 过滤 + 构建 `Episode`
3. 立即 `saveEpisodes(batchEpisodes)`
4. 立即 `upsertPlaylistEpisodes(batchEpisodes)`
5. 更新计数器与自动下载候选

收益：

- 内存由 O(新增总量) 降到 O(batch size)

### 方案 C：ID 查询与入库去重全部分片化

- `getEpisodesByIds(addedIds)` 改分片（例如 200~500）
- `saveEpisodes(List<Episode>)` 提供分片版本，避免超大 `IN (...)` 与一次性大集合

收益：

- 降低 SQL/内存尖峰
- 降低 SQLite 压力

### 方案 D：自动下载候选仅保留“必要上限”

当前会持有所有新增 `Episode` 再排序。  
可改为仅维护 top-N（N=autoDownloadLimit 或合理上限）候选，不需要保存全量新增对象用于排序。

补充说明（避免语义歧义）：

- 这里的“排序”不是前端节目列表排序，而是**自动下载候选选择排序**。
- 现状逻辑会先把 `newEpisodesForAutoDownload` 全量收集，再按 `publishedAt desc` 排序，再截前 N（`selectEpisodesForAutoDownload`）。
- 优化后改为在构建过程中维护一个固定容量 top-N 候选集（例如小顶堆）：
  - 内存占用从 O(新增总量) 降到 O(N)
  - 仍保持“按发布时间倒序选前 N 条自动下载”的语义不变

兼容性要求：

- 必须保留“本轮新增总数”单独计数，不能用 top-N 列表长度替代（否则会把 `newEpisodeCount` 错误截断为 N）。
- 对 `publishedAt` 为空或相同的场景，增加稳定次排序键（如 `id`）以保证结果可预期。

收益：

- 大幅减少 `Episode` 对象驻留时间

---

## 5.2 阶段二（增强鲁棒性）

### 方案 E：同步并发门控（防止叠加峰值）

增加 playlist 同步互斥（同一 playlist 串行），并限制全局并发（例如 1）。  
避免定时任务与手动刷新重叠导致内存峰值叠加。

### 方案 F：内存保护阈值

增加配置项（示例）：

- `pigeon.playlist-sync.max-added-per-round`
- `pigeon.playlist-sync.max-detail-batch-size`

当新增过大时，先分批入重试队列，分轮补齐，避免单轮拉爆。

### 方案 G：可观测性增强

在关键阶段打内存日志（used/total/max）：

1. 快照开始/结束
2. 差集完成
3. 每个详情批次开始/结束
4. 最终落库后

这样能快速定位现场瓶颈，而不是只看“总内存高”。

---

## 6. 建议实施顺序

1. **先做 A + B + C**（立即降峰值）
2. 再做 D（进一步削峰）
3. 再做 E/F/G（稳定性与可运维性）

---

## 7. 风险与兼容性评估

## 7.1 语义风险

- 只要保持“added 强一致 + 失败重试”不变，业务语义可保持一致。

## 7.2 数据一致性风险

- 批处理必须保证每 batch 事务边界清晰，避免“episode 已入库但 mapping 未入库”的半状态。

## 7.3 性能权衡

- 批次变小会增加 DB 往返次数，但能换取更低峰值内存和更高稳定性。
- 对 1GB 机器优先级应是“活下来”，不是单轮极致吞吐。

---

## 8. 建议配置基线（小内存场景）

建议在部署层增加 JVM 上限约束，避免挤爆主机：

- 例如：`-Xms128m -Xmx512m`（或按容器内存比例）

说明：这不是根治手段，但能减少“主机整体失联”风险，根治仍需代码层削峰。

---

## 9. 与三种模式的关系（MANUAL_FULL / INCREMENTAL / INIT）

三种模式逻辑一致，差异仅在“本轮新增量”：

- `INCREMENTAL`：大多小新增，峰值通常较低
- `MANUAL_FULL`：可能触发大规模对账
- `INIT`：首次订阅常见最大新增，最容易触发峰值

因此优化应做在**共享核心链路**，一处改动三处受益。

---

## 10. 本轮评审建议讨论点

1. 阶段一是否一次性落地 A+B+C+D，还是先 A+B+C？
2. `detail batch size` 默认值设为多少（建议 50 或 100）？
3. 是否默认启用同步互斥（全局并发=1）？
4. 小内存部署是否同时给出官方 `JAVA_OPTS` 建议？

---

## 11. 结论

当前高内存问题定位明确：**不是某一行代码，而是“全量对象链路 + 多结构叠加 + 双进程并行”导致的峰值问题**。  
建议先做“流式解析 + 批次闭环 + 分片查询/入库”，可在不改变业务语义的前提下显著降低内存峰值，优先解决 1GB 主机稳定性问题。
