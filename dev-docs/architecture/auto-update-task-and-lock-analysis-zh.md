# 自动更新任务流与 SQLite 读写竞争分析

## 1. 目的与范围

本文基于当前实际代码，梳理“节目自动更新 -> 自动发布下载任务 -> 下载状态流转”的完整链路，并在状态流转过程中标注最容易出现 SQLite 读写竞争、状态残留和任务卡住的位置。

本文聚焦以下场景：

- 定时自动同步频道
- 定时自动同步播放列表
- 延迟自动下载到期后的任务提升
- 下载线程池补位与失败重试
- 下载成功/失败后的最终状态回写

本文暂不展开：

- yt-dlp 的 JS Challenge 失败
- 手动下载、手动重试、手动取消的交互细节

但需要注意，手动下载和初始化订阅最终也复用了同一条下载状态机，因此这里识别出的风险点同样会影响这些路径。

本文同时记录当前代码中已经完成的风险缓解项，方便后续继续处理剩余放大器风险点时有一致视图。

## 2. 当前后台参与者

当前与自动更新和下载状态流转直接相关的后台组件如下：

- `ChannelSyncer`
  - 每 1 小时执行一次
  - 遍历需要同步的频道并调用 `ChannelService.refreshChannel`
- `PlaylistSyncer`
  - 每 3 小时执行一次
  - 遍历需要同步的播放列表并调用 `PlaylistService.refreshPlaylist`
- `PlaylistDetailRetrySyncer`
  - 每 15 分钟执行一次
  - 为播放列表中缺失详情的节目补抓详情，并可能再次触发自动下载
- `DownloadScheduler`
  - 每 30 秒执行一次
  - 提升到期的延迟自动下载任务
  - 在下载线程池有空位时补位 `PENDING` / `FAILED` 任务
- `EpisodeCleaner`
  - 每 2 小时执行一次
  - 清理超出保留数量的 `COMPLETED` 节目，并将其重置为 `READY`
- `StaleTaskCleaner`
  - 应用启动时执行一次
  - 将遗留的 `DOWNLOADING` 重置回 `PENDING`
- `EpisodeEventListener`
  - 事务提交后监听 `EpisodesCreatedEvent`
  - 立即逐条调用 `DownloadTaskHelper.submitDownloadTask`

这些组件会并发读写 `episode`、`playlist_episode`、`channel`、`playlist` 等表，因此自动同步高峰期不是单一路径写库，而是多条后台任务同时争用 SQLite 写锁。

## 3. 状态模型

`EpisodeStatus` 当前只有 5 个正式状态：

| 状态 | 含义 | 典型进入方式 |
| --- | --- | --- |
| `READY` | 节目元数据已入库，但尚未进入下载队列 | 新节目首次入库；清理已下载文件后回退 |
| `PENDING` | 已排队，等待拿到线程池执行机会 | 自动下载立即入队；延迟自动下载到期提升；启动时清理残留；极少数提交兜底回滚 |
| `DOWNLOADING` | 已拿到执行资格，准备/正在执行 `yt-dlp` | `TaskStatusHelper.tryMarkDownloading` |
| `COMPLETED` | 下载成功且媒体路径等元数据已写回 | `DownloadHandler` 成功结束 |
| `FAILED` | 下载失败，保留错误信息和重试次数 | `DownloadHandler` 失败或异常结束 |

还有一个“伪状态”需要单独理解：

- `READY + auto_download_after != null`
  - 表示该节目已经被选中为“延迟自动下载”
  - 还没有真正进入下载队列
  - 只有 `DownloadScheduler` 提升后才会变成 `PENDING`

## 4. 自动更新主链路

### 4.1 频道自动同步链路

频道自动同步的主路径如下：

1. `ChannelSyncer.syncDueChannels`
   - 查出需要同步的频道
   - 逐个调用 `ChannelService.refreshChannel`

2. `ChannelService.refreshChannel`
   - 只是薄包装，实际进入 `AbstractFeedService.refreshFeed`

3. `AbstractFeedService.refreshFeed`
   - 当前方法带 `@Transactional`
   - 调用 `fetchIncrementalEpisodes(feed)` 抓取最新节目
   - 若没有新节目，只更新 `lastSyncTimestamp`
   - 若有新节目，则进入 `persistEpisodesAndPublish`

4. `ChannelService.fetchIncrementalEpisodes`
   - 拉取远端最近一页节目
   - 对命中当前频道过滤条件但 `channel_id` 缺失的已有节目做 `backfillChannelIdIfMissing`
   - 调用 `filterNewEpisodes`，把真正未入库的节目筛出来

5. `AbstractFeedService.persistEpisodesAndPublish`
   - `prepareEpisodesForPersistence`
   - `EpisodeService.saveEpisodes`
   - `afterEpisodesPersisted`
   - `selectEpisodesForAutoDownload`
   - `markAndPublishAutoDownloadEpisodes`

6. `EpisodeService.saveEpisodes`
   - 先在内存里按 `episodeId` 去重
   - 再查一次数据库找出现有记录
   - 最后对剩余新节目逐条 `insert`
   - 新节目的初始状态为 `READY`

7. `ChannelService.afterEpisodesPersisted`
   - 继续执行 `backfillChannelIdIfMissing`
   - 这一步会对当前批次逐条做 `update channel_id if missing`

8. `AbstractFeedService.markAndPublishAutoDownloadEpisodes`
   - 若无延迟下载配置：
     - `EpisodeService.markEpisodesPending`
     - 将自动下载候选从 `READY -> PENDING`
     - 发布 `EpisodesCreatedEvent`
   - 若有延迟下载配置：
     - 未来时刻到期的节目保留 `READY`，只写 `auto_download_after`
     - 已到期的节目进入 `PENDING`
     - 只对立即下载那部分发布 `EpisodesCreatedEvent`

9. `EpisodeEventListener.handleEpisodesCreated`
   - `AFTER_COMMIT` 触发
   - 逐条调用 `DownloadTaskHelper.submitDownloadTask`

10. `DownloadTaskHelper.submitDownloadTask`
    - 先调用 `TaskStatusHelper.tryMarkDownloading`
    - 允许 `READY/PENDING/FAILED -> DOWNLOADING`
    - 标记成功后才真正提交给 `downloadTaskExecutor`

11. `DownloadHandler.download`
    - 启动 `yt-dlp`
    - 根据退出结果写回 `COMPLETED` 或 `FAILED`
    - 在 `finally` 中持久化最终状态

### 4.2 播放列表自动同步链路

播放列表自动同步与频道类似，但写入更重，步骤更多：

1. `PlaylistSyncer.syncDuePlaylists`
   - 查出需要同步的播放列表
   - 逐个调用 `PlaylistService.refreshPlaylist`

2. `PlaylistService.refreshPlaylist`
   - YouTube 播放列表走 `syncPlaylistWithSnapshot`
   - Bilibili 播放列表走 `refreshFeed`

3. `PlaylistService.syncPlaylistWithSnapshot`
   - 使用 `yt-dlp` 获取播放列表快照
   - 读取本地 `playlist_episode` 映射
   - 计算 `added / removed / moved / mappingRefresh`

4. 处理 removed / moved / mappingRefresh
   - 删除失效映射
   - 更新需要刷新位置或来源频道信息的映射
   - 必要时删除孤儿节目

5. `processAddedEntries`
   - 对新增视频分批处理
   - 先查本地 `episode`
   - 本地不存在的，再向 YouTube API 拉视频详情
   - 将新节目批量切片后调用 `EpisodeService.saveEpisodes`
   - 再逐条 `upsertPlaylistEpisodes`
   - 同时收集自动下载候选

6. `markAndPublishAutoDownloadEpisodes`
   - 与频道完全共用相同逻辑
   - 也会把节目转成 `PENDING` 并发布 `EpisodesCreatedEvent`

7. `EpisodeEventListener -> DownloadTaskHelper -> DownloadHandler`
   - 与频道路径完全复用同一套下载状态机

播放列表路径的特点是：

- 不仅会写 `episode`
- 还会频繁写 `playlist_episode`
- 并且 `count -> update/insert` 的映射维护是逐条进行的
- 因此它比频道路径更容易在单次同步里放大 SQLite 写竞争

### 4.3 延迟自动下载链路

延迟自动下载的主路径如下：

1. 节目首次被选为自动下载候选时
   - 状态保持 `READY`
   - 只写 `auto_download_after`

2. `DownloadScheduler.processPendingDownloads`
   - 先调用 `EpisodeService.promoteDueDelayedAutoDownloadEpisodes`
   - 查出到期的 `READY + auto_download_after`
   - 逐条执行条件更新：
     - `READY -> PENDING`
     - 清空 `auto_download_after`
   - 然后发布 `EpisodesCreatedEvent`

3. 事务提交后
   - 再走一次 `EpisodeEventListener -> submitDownloadTask`
   - 即 `PENDING -> DOWNLOADING`

这意味着延迟自动下载不是直接由 `DownloadScheduler` 执行下载，而是先提升状态，再复用同一套事件驱动的提交流程。

### 4.4 线程池补位与失败重试链路

`DownloadScheduler` 每 30 秒还承担两个额外职责：

1. 用当前线程池空位补位 `PENDING`
   - 计算 `availableSlots = maxPoolSize - activeCount`
   - 按 `created_at ASC` 取 `PENDING`
   - 逐条调用 `submitDownloadTask`

2. 失败自动重试
   - 若 `PENDING` 不足以填满空位
   - 会再取 `FAILED and retry_number < 3`
   - 直接提交
   - 也就是 `FAILED -> DOWNLOADING`，不经过 `PENDING`

因此当前系统中，下载任务并不是由一个持久化队列统一消费，而是两种方式并存：

- 事务提交后立刻同步尝试提交
- 定时器按线程池空位补位

## 5. 当前实际状态流转

### 5.1 正常主路径

最常见的状态流转如下：

```text
远端新节目
  -> saveEpisodes
  -> READY
  -> markEpisodesPending
  -> PENDING
  -> tryMarkDownloading
  -> DOWNLOADING
  -> download success
  -> COMPLETED
```

下载失败时：

```text
DOWNLOADING
  -> download fail / exception
  -> FAILED
```

失败重试时：

```text
FAILED
  -> DownloadScheduler 补位
  -> tryMarkDownloading
  -> DOWNLOADING
```

### 5.2 延迟自动下载路径

```text
远端新节目
  -> saveEpisodes
  -> READY
  -> 写 auto_download_after
  -> READY (延迟自动下载待到期)
  -> 到期提升
  -> PENDING
  -> tryMarkDownloading
  -> DOWNLOADING
```

### 5.3 当前槽位控制与兜底回滚路径

```text
PENDING
  -> submitDownloadTask
  -> downloadSlots.tryAcquire 失败
  -> 保持 PENDING
  -> 等待后续 DownloadScheduler 补位
```

当前代码仍保留极少数兜底回滚路径：

```text
PENDING
  -> 已拿到下载槽位
  -> tryMarkDownloading
  -> DOWNLOADING
  -> 线程池意外拒绝
  -> rollbackFromDownloadingToPending (带重试)
  -> PENDING
```

### 5.4 启动恢复路径

```text
DOWNLOADING
  -> 应用重启
  -> StaleTaskCleaner
  -> PENDING
```

这个恢复路径依然是系统级兜底补偿，用于回收应用异常退出后遗留的 `DOWNLOADING` 状态。

## 6. 锁风险与状态卡死风险标注

下面按实际链路标注高风险点，并同步说明当前代码中的处理状态。

### [R1] `refreshFeed` 的事务边界过大（当前状态：待处理）

涉及路径：

- `AbstractFeedService.refreshFeed`
- `ChannelService.processChannelInitializationAsync`
- `PlaylistService.processPlaylistInitializationAsync`

现象：

- 这些方法带 `@Transactional`
- 事务边界覆盖了远端抓取、差异计算、批量入库、自动下载标记、事件发布等多个步骤

风险：

- 事务范围过大，失败时回滚面更大
- 在批量同步场景下，多个同步任务会更容易在同一时间窗口集中进入写阶段
- 对 SQLite 来说，这种写入高峰比“平均分散的小写入”更危险

结论：

- 这是写竞争的放大器，不一定单独制造卡死，但会显著增加 `database is locked` 概率

### [R2] 新节目入库采用“先查后插 + 逐条 insert”（当前状态：待处理）

涉及路径：

- `EpisodeService.saveEpisodes`

现象：

- 先 `selectList`
- 再对剩余节目逐条 `insert`

风险：

- 单次同步新增节目越多，写入次数越多
- 每个频道/播放列表同步都可能在同一时间做这类批量逐条写入
- 与后续 `markEpisodesPending`、`backfillChannelIdIfMissing` 叠加后，写放大明显

结论：

- 这是自动同步高峰期的核心写热点之一

### [R3] 频道路径会对同一批节目再做一次逐条 `channel_id` 回填（当前状态：待处理）

涉及路径：

- `ChannelService.fetchIncrementalEpisodes`
- `ChannelService.afterEpisodesPersisted`
- `EpisodeService.backfillChannelIdIfMissing`

现象：

- 节目保存前后都会尝试把 `channel_id` 补回

风险：

- 同步一批节目时，同一张 `episode` 表会经历更多额外 `update`
- 在 SQLite 中这是纯粹的额外写压力

结论：

- 这不是“卡死点”，但会放大频道同步时的写竞争

### [R4] 播放列表路径会额外逐条维护 `playlist_episode` 映射（当前状态：待处理）

涉及路径：

- `PlaylistService.syncPlaylistWithSnapshot`
- `PlaylistService.upsertPlaylistEpisodeMapping`
- `PlaylistService.upsertPlaylistEpisodes`

现象：

- 对每个节目先 `count`
- 再决定 `update` 还是 `insert`
- 删除、移动、来源频道刷新、新增节目都要写映射表

风险：

- 播放列表同步的写入次数通常高于频道同步
- `episode` 与 `playlist_episode` 两张表会交错写入
- 快照量很大时会拉长一次同步的写操作持续时间

结论：

- 这是播放列表路径最重要的锁竞争来源

### [R5] 自动下载立即入队时，会把一批 `READY` 逐条改成 `PENDING`（当前状态：待处理）

涉及路径：

- `AbstractFeedService.markAndPublishAutoDownloadEpisodes`
- `EpisodeService.markEpisodesPending`

现象：

- 对自动下载候选逐条 `update episode set download_status = 'PENDING'`

风险：

- 一次同步发现的新节目越多、自动下载上限越大，这里的集中写入越大
- 这一步通常刚好发生在同步事务提交前，是“写高峰”的最后一段

结论：

- 这是将“同步发现新节目”转化为“下载任务洪峰”的关键桥接点

### [R6] `EpisodesCreatedEvent` 提交后会立即同步提交下载任务（当前状态：已缓解）

涉及路径：

- `EpisodeEventListener.handleEpisodesCreated`
- `DownloadTaskHelper.submitDownloadTask`
- `TaskStatusHelper.tryMarkDownloading`

现象：

- 事务一提交，监听器立刻逐条调用 `submitDownloadTask`
- 每条任务都会启动一个新的 `REQUIRES_NEW` 事务
- 在真正启动下载前，先把状态改成 `DOWNLOADING`

历史风险：

- 同一批节目会在提交点瞬间集中触发多次 `select + update`
- 写压力从“同步事务”直接传导到“下载提交事务”
- 如果其中一条因为 SQLite 锁失败，`submitDownloadTask` 不会吞掉该异常
- `handleEpisodesCreated` 也没有对单条任务做隔离捕获

结论：

- 一个批次里一旦某条任务在 `tryMarkDownloading` 上碰到锁，可能会中断后续同批任务的即时提交
- 被中断的任务会留在 `PENDING`，只能等下一轮 `DownloadScheduler` 再补位

当前处理方式：

- `EpisodeEventListener.handleEpisodesCreated` 已改为对每个 `episodeId` 单独 `try/catch`
- 即使某条任务即时提交失败，也不会中断同批后续任务
- 监听器会记录 `submitted / deferred / failed` 结果，便于观察提交流量与补位情况

当前剩余边界：

- 事务提交后仍然会形成一波即时提交流量
- 也就是说，`R6` 的“整批被单条异常打断”已经缓解，但“提交风暴本身”并未消除

这会造成第一类“状态锁定”：

- `PENDING` 卡住
- 但通常仍可被后续定时补位恢复
- 不一定需要重启

### [R7] 线程池拒绝后的回滚也依赖数据库写入（当前状态：已缓解）

涉及路径：

- `DownloadTaskHelper.submitDownloadTask`
- `TaskStatusHelper.rollbackFromDownloadingToPending`

历史现象：

- 状态先改成 `DOWNLOADING`
- 如果线程池提交被拒绝，再尝试回滚到 `PENDING`

风险：

- 如果回滚这一步再次遇到 SQLite 锁失败
- 任务会被错误地遗留在 `DOWNLOADING`
- 但实际上下载线程并没有真正启动

结论：

- 这是第二类“状态锁定”来源
- 这种假 `DOWNLOADING` 很危险，因为后续 `DownloadScheduler` 不会再处理它

当前处理方式：

- `DownloadTaskHelper` 新增了与下载线程池容量对齐的 `Semaphore`
- 现在会先抢下载槽位，抢不到时任务直接保持原状态，不再进入“先标记 `DOWNLOADING` 再回滚”的正常路径
- `TaskStatusHelper.rollbackFromDownloadingToPending` 也已经补上 `@Retryable + REQUIRES_NEW`，作为线程池意外拒绝时的兜底回滚

当前剩余边界：

- 线程池若在拿到槽位后仍发生意外拒绝，仍会走回滚路径
- 但这条路径现在已经从“常规路径”降级为“异常兜底路径”

### [R8] `DOWNLOADING -> COMPLETED/FAILED` 的最终状态回写最容易造成长期残留（当前状态：已缓解）

涉及路径：

- `DownloadHandler.download`
- `TaskStatusHelper.persistEpisodeWithRetry`
- `StaleTaskCleaner`

历史现象：

- 下载结束后，最终状态在 `finally` 中回写
- 这是把中间状态 `DOWNLOADING` 结束掉的唯一正常出口
- 一旦这一步失败，任务就会残留在 `DOWNLOADING`
- 之前代码把 `@Retryable` 放在 `DownloadHandler` 的 `private` 方法上，按 Spring 代理模型看，这个重试实际上并不可靠

风险：

- 任务一旦残留在 `DOWNLOADING`
- `DownloadScheduler` 不会再挑选它
- 用户界面会看到“正在下载”或系统总有残留下载中任务
- 只有应用重启时，`StaleTaskCleaner` 才会把它改回 `PENDING`

结论：

- 这是最危险的状态残留点之一
- 风险核心不是单个下载失败，而是最终状态回写失败导致的 `DOWNLOADING` 残留

当前处理方式：

- 已将状态持久化从 `DownloadHandler` 中拆到独立 Bean `TaskStatusHelper.persistEpisodeWithRetry`
- 该方法使用 `@Retryable + REQUIRES_NEW`，确保通过 Spring 代理真正触发重试
- `DownloadHandler` 现在在“兜底标记 `DOWNLOADING`”和“finally 落最终状态”两个位置都统一走这个入口

当前剩余边界：

- 如果重试最终仍全部失败，任务仍可能残留在 `DOWNLOADING`
- 但相比原实现，状态回写已经从“可能未真正重试”变成“明确执行独立事务重试”

这会造成第三类“状态锁定”：

- `DOWNLOADING` 卡死
- 无法被正常调度器重新接管
- 只能依赖启动补偿恢复

### [R9] `DownloadScheduler`、同步任务、清理任务、播放列表详情重试任务会同时写 `episode`（当前状态：待处理）

涉及路径：

- `DownloadScheduler`
- `ChannelSyncer`
- `PlaylistSyncer`
- `PlaylistDetailRetrySyncer`
- `EpisodeCleaner`

现象：

- `episode` 表不仅服务于下载状态机
- 还承担新节目入库、延迟下载时间登记、清理回退 `READY`、失败重试等多种职责

风险：

- 即使下载线程池只有 3 个线程，SQLite 写竞争也不只来自下载线程
- 高订阅量时，后台写入源会在同一时间窗口相互叠加

结论：

- 问题本质不是“下载线程太多”
- 而是“多个后台流程同时依赖同一张 SQLite 表做频繁状态写入”

## 7. 当前风险画像

基于当前代码，自动更新与下载状态机在 SQLite 下的典型压力链如下：

1. 定时同步在某个时间窗口内发现较多新节目
2. 新节目入库、映射维护、自动下载标记产生一批集中写入
3. 事务提交后立即触发一批下载提交尝试
4. 下载执行过程中持续产生状态写回
5. 其他后台任务同时读写 `episode` 与相关映射表

因此，更准确的结论是：

- 这不是传统意义上的“数据库死锁”
- 更像是 SQLite 写竞争导致的状态机中间状态残留与吞吐下降
- 当前最危险的点已经从“容易留下假 `DOWNLOADING`”收缩为“同步高峰期的写入洪峰仍然存在”

## 8. 当前处理进展与后续关注点

当前已经完成的风险缓解项如下：

1. 已缓解 `R6`
   - 即时提交失败不再中断同批后续任务
2. 已缓解 `R7`
   - 线程池满载时不再默认走“先标记 `DOWNLOADING` 再回滚”的主路径
3. 已缓解 `R8`
   - 最终状态回写已经切换到真正可触发重试的独立事务入口

当前仍需重点关注的剩余方向如下：

1. 削减同步高峰期的写入洪峰
   - 包括 `R1`、`R2`、`R3`、`R4`、`R5`
2. 降低多后台任务对 `episode` 表的同时写入叠加
   - 对应 `R9`
3. 继续评估是否要调整“事务提交后立即尝试提交 + 定时补位”的双轨模式

换句话说，当前最需要继续优化的，不是单点状态回滚，而是写入风暴本身。

## 9. 风险点分类

基于当前代码和上面的风险分析，可以将 `R1` 到 `R9` 进一步归类如下。

### 9.1 已缓解的直接状态卡死风险点

- `R7`
  - 线程池拒绝后，`DOWNLOADING -> PENDING` 的回滚依赖额外数据库写入
  - 一旦回滚失败，会留下“假 `DOWNLOADING`”
- `R8`
  - 下载结束后的最终状态回写失败
  - 任务会残留在 `DOWNLOADING`
  - 正常调度器不会再接手，只能依赖应用重启后的 `StaleTaskCleaner`

### 9.2 已缓解的“软卡住但可自动恢复”风险点

- `R6`
  - `EpisodesCreatedEvent` 在事务提交后立即逐条提交下载任务
  - 如果其中一条在 `tryMarkDownloading` 上遇到 SQLite 锁冲突，可能中断同批后续任务的即时提交
  - 这类任务通常会停留在 `PENDING`
  - 后续仍可由 `DownloadScheduler` 补位恢复

### 9.3 当前仍待处理的写竞争放大器风险点

- `R1`
  - `refreshFeed` 事务边界过大
- `R2`
  - 新节目入库采用“先查后插 + 逐条 insert”
- `R3`
  - 频道路径会对同一批节目额外做 `channel_id` 回填
- `R4`
  - 播放列表路径会逐条维护 `playlist_episode` 映射
- `R5`
  - 自动下载立即入队时，会把一批 `READY` 集中改成 `PENDING`
- `R9`
  - 多个后台任务同时写 `episode`，形成写入叠加

### 9.4 当前仍需单独强调的“任务洪峰制造点”

- `R5`
  - 它本身不是直接状态卡死点
  - 但它直接决定一次同步完成后，有多少任务会被同时推入下载状态机
  - 因此它比一般的“写竞争放大器”更接近触发点
