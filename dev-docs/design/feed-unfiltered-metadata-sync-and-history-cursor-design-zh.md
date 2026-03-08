# PigeonPod 订阅元数据全量入库与历史游标同步设计

## 1. 文档目的

本文定义 `channel` / `playlist` 两类订阅在“元数据同步、过滤器生效时机、历史回填推进方式”上的统一设计，解决当前“过滤器影响入库数量，继而导致历史分页推断错误”的问题。

本文只描述设计，不包含代码实现。

---

## 2. 本次已确认决策

基于评审讨论，本次先固定以下产品与工程口径：

1. **过滤器不再决定元数据是否入库。**
   - 同步过程中，凡是被认定为“应同步的节目”的条目，都写入本地元数据。
   - 过滤器只作用于“是否可见 / 是否参与自动下载 / 是否出现在 RSS 视图”。

2. **自动下载仍然只针对过滤后的可见节目。**
   - 被过滤掉的节目只保存元数据，不展示，不自动下载。

3. **`live/upcoming/private/unavailable` 不算“应同步的节目”。**
   - 这些条目继续在同步阶段排除，不进入本地节目集合。

4. **过滤器修改后，已存在但之前被隐藏的节目可以立刻在 UI 中出现。**
   - 但不会自动出现在 RSS 中，因为 RSS 仍只输出已下载节目。

5. **历史回填不再通过“本地已入库数量 / 页大小”推断下一页。**
   - 后续统一改为“持久化历史游标”推进。

6. **本次明确不做：**
   - “显示全部 / 显示过滤后”双视图。
   - 为已存在订阅补一次历史元数据回填。

---

## 3. 问题背景

当前实现中，过滤器在写时生效：

- `channel` 的 YouTube / Bilibili 同步会在抓取阶段直接按标题、描述、时长、live 状态过滤。
- `playlist` 的 YouTube snapshot 同步虽然能先发现远端全集合，但最终只为命中过滤器的条目保存 `Episode` 元数据与 playlist 关联。

这会产生两个直接问题：

1. **本地已入库数量不再等于远端已消费数量。**
   - 一旦用户配置了较强过滤器，本地可能只保存每页中的一小部分节目。
   - 历史回填再用 `count / 50` 去推断下一页时，就会重复请求错误页。

2. **过滤器语义混乱。**
   - 用户配置“过滤器”时，实际上同时改变了：
     - 元数据是否存在
     - 是否展示
     - 是否自动下载
   - 这使得“改过滤器后立刻看到历史节目”变得不可能。

本设计的核心目标，就是把“远端同步集合”和“用户可见集合”拆开。

---

## 4. 目标与非目标

## 4.1 目标

- 让本地元数据集合更接近“平台上可同步的节目集合”。
- 让过滤器退化为稳定的“读时裁剪规则”。
- 彻底去掉历史回填中的数量推页逻辑。
- 保持当前自动下载、RSS 输出、批量下载等功能的产品语义基本不变。

## 4.2 非目标

- 不在本次引入“全部节目视图”和“过滤后视图”并存。
- 不在本次对老订阅做全量历史修复。
- 不在本次改变 RSS 只输出已下载节目的原则。
- 不在本次重做 channel 周期同步的窗口策略（例如超过 50 条新增时的补偿策略）。

---

## 5. 设计总原则

新的模型统一为两层：

### 5.1 写层：同步层

同步层只回答一个问题：

> 这个远端条目是否属于“应同步的节目”？

若答案是“是”，则写入元数据。

这里的“应同步”不看 feed 过滤器，只看平台可见性与基础元数据完备度，例如：

- 排除 `live`
- 排除 `upcoming`
- 排除 `private / unavailable`
- 排除无法构造有效节目主键或基础元数据的异常条目

### 5.2 读层：视图层

视图层再回答另一个问题：

> 这个已同步节目，是否应对当前订阅可见？

这里才应用：

- 标题包含 / 排除关键词
- 描述包含 / 排除关键词
- 最小时长
- 最长时长

这个判断用于：

- UI 节目列表
- 批量下载弹窗节目列表
- 自动下载候选筛选
- RSS 输出候选筛选

---

## 6. “应同步节目”定义

为了避免“全部节目”概念过宽，本文明确限定为：

> 对当前平台接口可枚举、可识别出稳定节目 ID、且不属于 `live/upcoming/private/unavailable` 的条目。

这意味着：

- YouTube playlist snapshot 继续保留对 unavailable/private 条目的排除。
- YouTube channel / playlist 的直播和预约直播继续不入库。
- 若第三方接口未返回足够的节目基础字段，也不强行创建残缺 `Episode`。

因此，本设计追求的是：

**“本地元数据集合 = 远端可同步节目集合”**

而不是：

**“本地元数据集合 = 平台页面上肉眼可见的所有条目”**

---

## 7. 各订阅类型的目标行为

## 7.1 Channel

### 初始化

- 继续保留当前“仅抓取首批元数据”的产品行为。
- 但对本次抓到的远端页内条目，不再按 feed 过滤器裁剪。
- 自动下载仍只从其中“过滤后可见”的节目里选取前 N 条。

### 周期同步

- 继续抓取当前增量窗口。
- 对窗口中的“应同步节目”全部入库。
- 再用过滤器决定哪些节目对当前频道可见、哪些节目可自动下载。

### 历史回填

- 从“按本地数量推下一页”改为“按持久化游标推进下一页”。

## 7.2 Playlist

### YouTube playlist

- 现有 snapshot 同步已经具备“发现远端全集合”的能力。
- 需要修改的是：
  - 不再因为 feed 过滤器而跳过 `Episode` 与 playlist 关联的保存。
  - 过滤器改为只影响展示、自动下载、RSS。

### Bilibili playlist

- 与 channel 一样，抓到的远端页内条目全部入库。
- 历史回填不再通过 `count / pageSize` 推页，而是持久化“下一历史页”游标。

### 关于 playlist 的 history 接口

- 对 YouTube playlist 来说，常规 snapshot 同步已经覆盖“发现远端完整元数据集合”。
- 因此长期看，playlist 的 `/history` 更像兼容性接口，而不是主路径。
- 本次不要求删除该接口，但若保留，也应改成游标式推进，而不是数量推断。

---

## 8. 数据模型改造建议

## 8.1 `episode` 表新增 `duration_seconds`

### 背景

现有 `episode.duration` 为 ISO 8601 字符串，例如 `PT12M34S`。

如果过滤器改为读时生效，后端分页查询必须同时满足：

- 过滤条件准确
- 总数准确
- 分页稳定

标题 / 描述过滤可以直接在 SQL 里做，但**时长过滤无法高效、稳定地在 SQLite 中对 ISO 8601 字符串直接比较**。

### 建议

新增字段：

- `duration_seconds` INTEGER NULL

写入 `Episode` 时同步填充：

- YouTube：由 `contentDetails.duration` 解析得到秒数
- Bilibili：本身就有秒级时长，可直接写入

保留原有 `duration` 字段：

- 继续用于 RSS 输出、前端展示、下载元数据兼容

### 影响

- 读时过滤可以直接在 SQL 中使用 `duration_seconds`
- 分页总数可以准确统计

### 历史数据回填方案

本字段确认需要对历史数据做回填。

但这里**不建议使用纯 SQL migration 直接在 SQLite 中解析 ISO 8601 duration**，原因如下：

- 当前 `episode.duration` 存的是类似 `PT1H2M3S` 的 ISO 8601 duration 字符串。
- SQLite 虽然支持常见日期时间函数，但不提供把 ISO 8601 duration 直接解析成总秒数的内建函数。
- 若强行用 SQL 写字符串拆分逻辑，需要覆盖如下多种形态：
  - `PT45S`
  - `PT3M`
  - `PT1H`
  - `PT1H2S`
  - `PT1H2M3S`
- 这类 SQL 可读性差、容错差、后续维护成本高，也不利于发现脏数据。

因此建议采用“两段式迁移”：

#### 第一步：Flyway SQL migration 加列

只负责 schema 变更：

- 给 `episode` 表增加 `duration_seconds INTEGER NULL`

#### 第二步：Flyway Java migration 做数据回填

新增 Java-based Flyway migration，逐批读取旧数据并回填：

- 读取条件：
  - `duration_seconds IS NULL`
  - `duration IS NOT NULL`
- 解析方式：
  - 使用 Java 标准库 `java.time.Duration.parse(duration)`
- 回填结果：
  - 解析成功：写入总秒数
  - 解析失败：保留 `NULL`，并记录日志 / 计数

#### 为什么用 Java migration

- 解析规则与运行时逻辑一致，避免“双份解析标准”
- 更容易处理异常和脏数据
- 更容易做批量提交和日志观测
- 这类“复杂数据转换”本身也更符合 Flyway Java migration 的适用场景

#### 回填执行细节建议

- 使用固定批次大小，例如 `200 ~ 500`
- 每批按主键顺序读取，避免一次性加载全部 `episode`
- 对解析失败的记录打印聚合日志，不逐条刷屏
- 回填完成后输出：
  - 总扫描数
  - 成功回填数
  - 失败保留 NULL 数

#### 对旧数据的兼容口径

少量无法解析的历史记录允许保留 `NULL`，但查询层必须明确：

- `minimumDuration` / `maximumDuration` 生效时，`duration_seconds IS NULL` 的记录按“不满足时长过滤”处理

这样可以保证：

- 分页总数稳定
- 行为可预测
- 不会把“时长未知”的节目错误暴露到过滤后结果中

## 8.2 `channel` / `playlist` 表新增历史游标字段

建议在两类订阅表中都增加一组统一字段：

- `history_cursor_type` TEXT NULL
- `history_cursor_value` TEXT NULL
- `history_cursor_page` INTEGER NULL
- `history_cursor_exhausted` INTEGER NOT NULL DEFAULT 0
- `history_cursor_updated_at` TIMESTAMP NULL

字段语义：

- `history_cursor_type`
  - `YOUTUBE_PAGE_TOKEN`
  - `BILIBILI_PAGE_NUM`
- `history_cursor_value`
  - YouTube 存 `nextPageToken`
  - Bilibili 可为空
- `history_cursor_page`
  - 记录下一次历史请求对应的逻辑页号，仅用于日志和观测
- `history_cursor_exhausted`
  - 标记历史已经到末尾，无需再请求

### 为什么不用继续算页码

因为即使过滤器不再参与入库，本地数量仍可能与远端可枚举数量不完全相等，例如：

- 远端条目后续被删除
- private / unavailable 不在同步集合内
- 历史上某些条目未曾触达

因此，“以本地数量反推远端页码”在模型上仍然不可靠。

## 8.3 是否新增“可见性缓存字段”

本次不建议在 `episode` 或 `playlist_episode` 上增加“是否可见”缓存字段。

原因：

- 同一个 `Episode` 可以同时属于不同 feed
- 不同 feed 的过滤器不同
- 过滤器可以随时修改

因此可见性应保持为**查询时计算**，而不是持久化状态。

---

## 9. 写路径改造

## 9.1 统一拆分为两个步骤

所有同步写路径拆成：

1. **构建原始 Episode**
   - 只判断是否属于“应同步节目”
2. **按 feed 过滤器评估可见性**
   - 不影响入库
   - 只影响读时结果和自动下载候选

## 9.2 Channel 写路径

当前 channel 路径中，YouTube helper 会在构建 `Episode` 时直接把不匹配过滤器的节目丢掉。

调整后应改成：

- `buildEpisodeIfSyncable(...)`
  - 判断：
    - 视频详情是否完整
    - 是否 live / upcoming
    - 是否可读到有效 duration
  - 返回可入库的 `Episode`
- `matchesFeedFilter(feed, episode)`
  - 判断标题 / 描述 / 时长过滤
  - 供后续读时、自动下载时复用

## 9.3 Playlist 写路径

### YouTube playlist snapshot

当前 `processAddedEntries(...)` 中，新增条目只有在 `matchesPlaylistFilters(...)` 返回 true 时才保存。

调整后应改成：

- 只要条目属于“应同步节目”，就保存 `Episode`
- 只要条目属于“应同步节目”，就维护 `playlist_episode` 关联
- 自动下载候选再额外做一次 `matchesFeedFilter(...)`

### Bilibili playlist

与 channel 保持一致：

- 远端页内应同步节目全部入库
- 过滤器仅用于可见性和自动下载

---

## 10. 读路径改造

## 10.1 UI 列表查询

`/api/episode/list/{feedId}` 需要改成“状态过滤 + 搜索过滤 + feed 过滤器”联合查询。

### Channel

查询条件应包含：

- `channel_id = ?`
- 关键字搜索（如果用户输入）
- 下载状态过滤（如果用户选择）
- feed 自身的标题 / 描述 / 时长过滤

### Playlist

查询条件应包含：

- `playlist_episode.playlist_id = ?`
- 关键字搜索
- 下载状态过滤
- feed 自身的标题 / 描述 / 时长过滤

## 10.2 RSS 输出

RSS 当前只输出已下载节目，这个原则不变。

但在生成 RSS 时，应补上 feed 过滤器判断：

- 先取属于该 feed 的已下载节目
- 再按 feed 过滤器筛掉不可见节目

这样可以保证：

- 过滤器修改后，UI 可见集合立即变化
- RSS 仍只输出“已下载且可见”的节目

## 10.3 自动下载候选

自动下载仍然是：

> 从“本次新增、已入库”的节目中，选出“过滤后可见”的前 N 条

因此自动下载逻辑需要从“同步前过滤”改为“同步后筛选”。

## 10.4 批量下载与搜索

批量下载弹窗、前端搜索、分页总数都必须共享同一套读时过滤逻辑，否则会出现：

- 页面列表与总数不一致
- 批量下载弹窗出现 UI 不可见节目
- 搜索命中条目与 RSS / 自动下载行为不一致

---

## 11. 历史游标同步设计

## 11.1 总体原则

历史回填只依赖“上次成功推进到哪里”，不依赖“当前本地有多少节目”。

每次历史请求完成后，都把“下一次应该从哪里继续”持久化下来。

## 11.2 Channel - YouTube

### 游标含义

- `history_cursor_type = YOUTUBE_PAGE_TOKEN`
- `history_cursor_value = 下一次请求应使用的 nextPageToken`
- `history_cursor_page = 下一次逻辑页号`

### 初始化

对新订阅：

- 初始化完成后，若已抓取第 1 页，则把第 1 页响应返回的 `nextPageToken` 作为历史游标
- `history_cursor_page = 2`

### 推进

用户点“加载历史节目”时：

1. 若 `history_cursor_exhausted = 1`，直接返回空
2. 使用 `history_cursor_value` 请求下一页
3. 将该页中的应同步节目全部入库
4. 读取响应中的新 `nextPageToken`
5. 更新游标：
   - 有 token：写回 token，页号 +1
   - 无 token：标记 `history_cursor_exhausted = 1`

### 好处

- 不受过滤器影响
- 不受本地已下载数量影响
- 不受“某些历史视频后来不可见”导致的本地数量漂移影响

## 11.3 Playlist - YouTube

### 常规同步

YouTube playlist 的主路径是 snapshot 全量同步，不依赖历史游标。

### 若保留 history 接口

若仍保留 playlist `/history`：

- 同样按 `nextPageToken` 推进
- 禁止再用 `countByPlaylistId / 50` 推导目标页

## 11.4 Channel / Playlist - Bilibili

### 游标含义

- `history_cursor_type = BILIBILI_PAGE_NUM`
- `history_cursor_page = 下一次历史页号`
- `history_cursor_value = null`

### 初始化

若初始化已抓过第 1 页：

- `history_cursor_page = 2`

### 推进

每次历史请求直接请求 `history_cursor_page`，完成后：

- 若有下一页数据，`history_cursor_page += 1`
- 若返回空页，标记 `history_cursor_exhausted = 1`

虽然 Bilibili 是页号式接口，但关键点仍然是：

**页号来自持久化游标，而不是来自本地节目数量。**

---

## 12. 迁移与兼容策略

## 12.1 新订阅

新订阅在新模型下创建时：

- 写路径直接走“无过滤入库”
- 初始化结束后立即写入 history cursor

新订阅从第一天起即可完全享受新语义。

## 12.2 已存在订阅

本次明确不做历史元数据补录，因此需要接受一个现实：

> 已存在订阅在升级前未入库的“被过滤节目”，不会自动补齐。

升级后的影响是：

- 新触达的远端页会按新规则无过滤入库
- 老的、从未触达过的历史节目仍然缺失
- 用户修改过滤器后，只能立刻看到“已经在库里”的隐藏节目

这与本次“不为已存在订阅补一次历史元数据回填”的决策保持一致。

## 12.3 `duration_seconds` 对旧数据的处理

为了让读时分页过滤可用，已有 `episode` 数据需要执行一次**字段级回填**：

- 读取旧 `duration`
- 解析出秒数
- 回填 `duration_seconds`

这不是“历史元数据补录”，只是现有行的结构补齐。

具体方案见本文 `8.1`：

- SQL migration 只加列
- Java Flyway migration 负责解析并回填

若不做这一步，会出现：

- 旧节目无法准确参与时长过滤
- 列表总数与分页不稳定

---

## 13. 对现有代码结构的建议调整

## 13.1 增加统一过滤器组件

建议新增统一组件，例如：

- `FeedEpisodeVisibilityHelper`
- 或 `FeedEpisodeFilterMatcher`

职责：

- `isSyncable(...)`
- `matchesFeedFilter(feed, episode)`

避免当前 channel / playlist / YouTube / Bilibili 各自维护一套近似逻辑。

## 13.2 查询层新增按 feed 过滤器的组合查询

建议在 `EpisodeService` / `Mapper` 层增加：

- channel 列表查询的 feed 过滤器版本
- playlist 列表查询的 feed 过滤器版本
- RSS 查询的 feed 过滤器版本

不要把读时过滤放到前端，也不要在拿到整页后再做内存裁剪，否则分页总数会错误。

## 13.3 自动下载与读时过滤共享同一套规则

为了避免“看不见但被自动下载”或“看得见但永远不自动下载”的语义漂移，自动下载候选筛选应直接复用读时过滤判定。

---

## 14. 风险与收益评估

## 14.1 主要收益

- 彻底消除“过滤器导致历史分页错位”的主因
- 过滤器语义更稳定，更符合用户直觉
- 修改过滤器后，已在库节目可立即显隐切换
- YouTube playlist snapshot 与 channel / Bilibili 的模型趋于一致

## 14.2 主要风险

### 数据量上升

- 每个 feed 会保存更多仅元数据节目
- `episode` / `playlist_episode` 表增长速度更快

### 查询复杂度上升

- 过滤器从写时移到读时后，列表查询和 RSS 查询都更复杂

### 旧订阅不具备完全对称的新行为

- 因为不做历史补录，旧订阅只能对“已在库”的隐藏节目实现即时显隐

### 迁移阶段语义混合

- 若写路径先改而读路径未同时改，用户会突然看到大量原本应隐藏的节目

因此上线顺序必须确保：

1. Schema 到位
2. 读路径过滤到位
3. 写路径改成无过滤入库
4. 历史游标切换

---

## 15. 测试建议

## 15.1 单元测试

- `matchesFeedFilter(...)`
  - 标题包含 / 排除
  - 描述包含 / 排除
  - 时长上下限
- `isSyncable(...)`
  - live
  - upcoming
  - 缺失 duration
  - 不可用条目
- duration 解析
  - `PT59S`
  - `PT1M`
  - `PT1H2M3S`

## 15.2 集成测试

- channel 初始化后：
  - 本地保存全部应同步节目
  - UI 只显示过滤后节目
  - 自动下载只挑过滤后节目
- 修改过滤器后：
  - 已在库隐藏节目立即出现 / 消失
- RSS：
  - 只输出 `COMPLETED`
  - 且仍受 feed 过滤器约束
- history cursor：
  - 连续两次点击历史加载，第二次必须从第一次之后继续，而不是重复

## 15.3 回归测试

- batch download 弹窗分页
- search + status + feed filter 组合查询
- playlist source channel 信息展示
- `maximumEpisodes` 清理逻辑

---

## 16. 实施分期建议

## Phase 1：基础设施

- 增加 `duration_seconds`
- 增加 history cursor 字段
- 增加统一过滤匹配组件

## Phase 2：读路径切换

- UI 列表查询接入 feed 过滤器
- RSS 查询接入 feed 过滤器
- 自动下载候选筛选改为同步后过滤

## Phase 3：写路径切换

- channel 写路径改为无过滤入库
- playlist 写路径改为无过滤入库
- snapshot / helper / mapper 的过滤职责迁移到读层

## Phase 4：历史游标切换

- channel history 改为游标推进
- playlist history 改为游标推进或逐步弱化
- 去掉数量推页逻辑

---

## 17. 需要进一步确认的关键问题

以下问题建议在开发前再明确一次：

### 17.1 已存在订阅的 history cursor 如何初始化

这是本设计里最关键的迁移问题之一。

因为本次不做“自动全库历史补录”，老订阅升级到新模型时，并没有天然可信的 history cursor。  
旧模型下，本地已入库数量可能已经被以下因素污染：

- 旧过滤器曾阻止大量节目入库
- `live/upcoming/private/unavailable` 一直不入库
- 某些历史页从未被请求过
- 远端条目后来被删除或变为不可见

因此，**不能把“本地节目数量”直接当成正式 cursor 来源**。  
如果直接用数量推断出的页号去落库，风险不是“会多扫几页”，而是：

- cursor 可能被初始化到真实边界之后
- 中间一段历史页以后永远不会再被扫到
- 形成静默漏数，后续很难排查

基于这个风险，本设计采用：

### 最终建议：有界历史补齐式 bootstrap

核心思想是：

> 对老订阅首次触发 history 时，不只是为了“建立 cursor”，而是从远端头部开始顺序扫描，直到命中“本地当前最早已保存节目”为止；在这个过程中，把历史上因旧过滤器被漏掉、但属于 `syncable` 的节目元数据一并补齐；命中锚点后，再把锚点之后的位置写成正式 history cursor。

这个方案相对于“纯锚点定位”多了一层收益：

- 不只是解决 cursor 初始化
- 还顺带补齐了“头部到本地最早节目之间”的历史缺口

同时它仍然不是“全量扫描整个 feed 的全部历史”：

- 只扫描到本地当前最早已保存节目为止
- 若本地最早节目距离头部不深，成本明显低于完整历史回填

#### 17.1.1 适用范围

该方案作为主方案，**主要适用于 channel 类型订阅**：

- YouTube channel
- Bilibili channel

原因：

- channel 的历史流通常天然按发布时间倒序排列
- “本地当前最早已保存节目”可以作为相对稳定的历史边界锚点

对于 playlist：

- 不建议把“扫到本地最早节目”为通用主模型
- 因为 playlist 可能重排、插入旧视频、删除条目
- “本地最早节目”不一定代表稳定边界

因此 playlist 仍保持：

- YouTube playlist 以 snapshot 同步为主
- playlist history 若保留，也继续使用独立游标模型

#### 17.1.2 锚点定义

对老订阅初始化 history cursor 时，为 feed 选择一个“历史边界锚点节目”：

- `channel`
  - 取当前本地该频道中**最早的一条已同步节目**

锚点需要记录：

- `anchorEpisodeId`
- `anchorPublishedAt`（可选，仅用于日志辅助）

锚点的目的不是“代表最老节目”，而是提供一个：

- 本地已知存在
- 一旦在远端扫描中命中，就能确定“新旧边界”的稳定标记

#### 17.1.3 bootstrap 总流程

对老订阅第一次触发 history 时：

1. 若该订阅已经存在正式 history cursor：
   - 直接使用，不走 bootstrap

2. 若不存在 history cursor：
   - 进入 bootstrap 模式

3. bootstrap 模式先读取本地锚点：
   - 取该 feed 当前最早的本地节目作为 `anchorEpisodeId`

4. 从远端头部开始顺序扫描：
   - YouTube：从第一页开始按 `nextPageToken` 正向翻页
   - Bilibili：从第 1 页开始按页号递增扫描

5. 每扫到一页时，执行两件事：
   - 检查该页是否包含 `anchorEpisodeId`
   - 将该页中所有 `syncable` 节目按新模型入库

6. 如果命中锚点：
   - 说明“从头部到锚点所在页”为止的历史边界已经被精确定位
   - 且这段范围内过去因过滤器漏掉的节目已被补齐
   - 此时把“锚点下一页”的位置写成正式 history cursor
   - bootstrap 完成，后续 history 全部按正式 cursor 推进

7. 如果扫描到远端末尾仍未命中锚点：
   - 不写猜测 cursor
   - 但这次扫描本身已经覆盖了当前远端全部 `syncable` 节目
   - 因此可直接把该订阅标记为：
     - 历史已补齐
     - `history_cursor_exhausted = 1`

这意味着：

- “命中锚点”不是完成 bootstrap 的唯一成功条件
- “扫到远端末尾”也是一个有效收敛条件

#### 17.1.4 为什么这个方案比“纯锚点 bootstrap”更有价值

如果为了找到锚点，本来就必须从头顺序扫描远端页，那么只做“定位锚点”而不补齐中间缺失节目，收益偏低。

采用“有界历史补齐式 bootstrap”后，一次扫描可以同时完成：

1. 建立准确的 history cursor
2. 补齐头部到本地最早节目之间的历史缺口

因此它比纯锚点 bootstrap 更符合成本收益比。

#### 17.1.5 为什么必须“命中锚点后”才能写正式 cursor

因为只有真正命中锚点，系统才能确认：

- 当前扫描路径没有跳过应同步页
- 锚点之后才是真正尚未补齐的历史区间
- 写下来的 cursor 真的是“锚点之后的下一段历史”

如果没命中锚点就落 cursor，本质上还是在“猜”，不是在“初始化”。

#### 17.1.6 YouTube 的 bootstrap 细节

对 YouTube channel：

- 正式 cursor 最终保存的是：
  - `history_cursor_type = YOUTUBE_PAGE_TOKEN`
  - `history_cursor_value = 锚点所在页响应返回的 nextPageToken`
  - `history_cursor_page = 锚点逻辑页号 + 1`

注意这里保存的是：

> “请求锚点下一页所需的 token”

而不是锚点页本身的 token。

这样后续点“加载历史节目”时，系统会直接从锚点之后继续请求，不重复锚点页。

#### 17.1.7 Bilibili 的 bootstrap 细节

对 Bilibili channel：

- 正式 cursor 最终保存的是：
  - `history_cursor_type = BILIBILI_PAGE_NUM`
  - `history_cursor_page = 锚点所在页号 + 1`

因为 Bilibili 是页号式接口，只要命中锚点页，就能直接把“下一页页号”作为正式 cursor。

#### 17.1.8 `count` 在 bootstrap 中的作用

`count` 仍然可以保留一个有限作用：

- 用作扫描预算参考
- 用作日志对比信息

例如：

- 若本地已有 180 条节目，可推测锚点大概率不在非常靠前的前几页
- 可在日志中输出：
  - `localCountEstimate`
  - `bootstrapScannedPages`
  - `anchorFoundAtPage`
  - `bootstrapMode=bounded_backfill`

但这里要明确：

- `count` 只是观测和预算信息
- 不是 cursor 可信来源
- 不参与最终 cursor 落库决策

#### 17.1.9 未命中锚点但扫到末尾时的处理

若扫描到远端末尾仍未命中锚点，可能原因包括：

- 本地锚点对应节目后来被删除
- 该节目变为 private / unavailable
- 本地数据曾发生异常污染

与“纯锚点 bootstrap”不同，这里不应直接视为失败。  
因为：

- 扫描已经覆盖了当前远端全部 `syncable` 节目
- 所有本应可同步的历史节目元数据都已经被补齐

因此此时建议：

1. 不写正式“下一页” cursor
2. 直接标记：
   - `history_cursor_exhausted = 1`
   - `history_cursor_updated_at = now`
3. 记录日志字段：
   - `anchorEpisodeId`
   - `localCountEstimate`
   - `scannedPages`
   - `remoteEndReached=true`
   - `anchorMatched=false`
   - `bootstrapResult=FULLY_BACKFILLED_WITHOUT_ANCHOR`

这样系统不会卡在“无法初始化”的中间状态。

#### 17.1.10 是否会造成“有些数据永远无法同步”

采用有界历史补齐式 bootstrap 后：

- **不会因为错误初始化 cursor 而把一段历史永久跳过**

因为：

- 只有命中锚点后才会写正式下一页 cursor
- 未命中锚点时也不会写猜测值
- 若已扫到远端末尾，则当前远端全部 `syncable` 节目已经补齐

这样最坏情况也只是：

- 该老订阅首次 history 触发时扫描成本较高

但不会出现：

- 错误 cursor 导致中间历史区间被永久静默跳过

#### 17.1.11 与“全量自动迁移补录”的关系

本方案仍然遵守本次决策：

- **不做升级后自动对所有老订阅统一跑一次历史补录**

但它允许：

- 在用户首次主动触发 history 时
- 对该单个老订阅执行一次“有界历史补齐式 bootstrap”

因此它属于：

- 按需补齐
- 非全局自动迁移
- 成本受本地历史边界限制

这比“一上来给全库所有订阅跑完整历史回填”更稳健，也更符合当前范围控制。

### 17.2 `duration_seconds` 是否允许做一次字段级回填

若不允许，则读时时长过滤很难做到分页准确。

本文建议：

- 允许做一次字段级回填
- 视其为结构迁移，而不是历史元数据补录

---

## 18. 最终建议

建议按本文方案推进，并遵守两个硬约束：

1. **过滤器迁移必须是“读路径 + 写路径 + 自动下载”一起切换。**
2. **历史回填切换到游标后，禁止再在主流程里使用数量推页。**

这样可以在不改变核心产品定位的前提下，真正解决当前 channel / playlist 订阅中“过滤器与远端分页冲突”的根本问题。
