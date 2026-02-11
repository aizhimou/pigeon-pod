# PigeonPod YouTube Playlist 增量同步方案（最终评审版）

## 1. 文档目标

本文是 Playlist 增量同步方案的最终评审稿，目标是：

- 明确“定时增量 + 手动全量修复”的最终实现口径。
- 把 `HEAD/TAIL` 两种模式的算法写清楚，方便开发、测试与排障。
- 将关键状态字段、漂移检测规则、日志观察点固化，降低后续维护成本。

---

## 2. 最终决策（本次评审结论）

基于评审讨论，确认以下 5 项决策：

1. 不对用户暴露 `UNKNOWN` 模式。首次判定失败时，默认按 `HEAD` 处理（内部可标低置信度）。
2. `HEAD` 模式扫描上限固定为 `2` 页（最多 100 条）。
3. 引入并强化 `checkpoint`（`pageIndex -> pageToken`），并提高缓存密度（尽量多存）。
4. 漂移告警后支持“一键修复并全量同步”。
5. 不提供用户手动指定模式入口，模式全自动推断与纠偏。

---

## 3. YouTube Data API v3 约束（方案前提）

本方案建立在以下官方能力约束上：

- `playlistItems.list` 无 `order` 参数，不能指定固定排序方式。
- 只能通过 `pageToken` 正向翻页，不能直接请求“最后一页”。
- `snippet.position` 表示 Playlist 当前顺序（0-based）。
- 总量可取：
  - `playlists.list(part=contentDetails)` -> `contentDetails.itemCount`
  - `playlistItems.list` -> `pageInfo.totalResults`

结论：`TAIL` 模式必须依赖“总量变化 + token 跳转工具”来逼近尾部，无法直接随机访问末页。

---

## 4. 整体同步策略

## 4.1 同步职责

- **定时任务（自动）**：只执行增量同步。
- **手动按钮（用户）**：执行全量同步（修复/重建）。

## 4.2 核心原则

- 平时用低配额增量覆盖大多数场景。
- 一旦检测到排序漂移或异常，立刻停止自动增量并提示手动全量。
- 全量成功后，清理告警并重建锚点与 checkpoint。

---

## 5. 数据模型设计

## 5.1 `playlist` 表新增字段

建议新增以下字段（示例命名，可在实现时统一风格）：

- `sync_mode` TEXT NOT NULL DEFAULT `'HEAD'`
- `sync_confidence` TEXT NOT NULL DEFAULT `'HIGH'`  
  - 可选值：`HIGH` / `LOW`（仅内部调试，不给用户选择）
- `known_item_count` INTEGER NULL
- `head_anchor_video_id` TEXT NULL
- `tail_anchor_video_id` TEXT NULL
- `tail_anchor_position` INTEGER NULL
- `needs_full_resync` INTEGER NOT NULL DEFAULT 0
- `incremental_sync_error` TEXT NULL
- `last_incremental_sync_at` TIMESTAMP NULL
- `last_full_sync_at` TIMESTAMP NULL

说明：

- `sync_mode` 对外只会是 `HEAD` 或 `TAIL`。
- 首次判定不确定时仍写 `HEAD`，并将 `sync_confidence=LOW`。
- `needs_full_resync=1` 时，定时任务跳过该订阅，等待用户手动修复。

## 5.2 `playlist_sync_checkpoint` 表（翻页索引）

建议新增表：

- `playlist_id` TEXT NOT NULL
- `page_index` INTEGER NOT NULL（1-based）
- `page_token` TEXT NULL
- `created_at` TIMESTAMP NOT NULL
- `updated_at` TIMESTAMP NOT NULL
- 主键：`(playlist_id, page_index)`

### `page_token` 的精确定义

- 第 1 页请求 token 恒为 `null`。
- 拉完第 N 页后，响应里的 `nextPageToken` 可用于请求第 N+1 页。
- 因此建议保存规则为：  
  `checkpoint[1] = null`，`checkpoint[N+1] = nextPageToken(第N页响应)`

### 为什么 checkpoint 很关键

YouTube 不能直接“请求第 18 页”，必须从已知 token 连续跳转。  
有 checkpoint 才能快速接近尾部，否则 `TAIL` 模式每次都会退化为从头翻页。

### 本方案的 checkpoint 密度策略（按评审意见）

- 不是每 5/10 页稀疏缓存，而是“尽量多存”：
  - 每次增量或全量访问到的页，都写入/更新 checkpoint。
  - 至少保证最近窗口（例如最近 30 页）完整可跳转。
- 存储占用很小（token 字符串），优先换取准确性与可排障性。

---

## 6. 模式判定与初始化

## 6.1 首次订阅后的模式判定

首次全量初始化后，取样本判断模式：

1. 读取前两页（最多 100 条）样本。
2. 对比 `position` 与 `publishedAt` 的趋势：
   - 趋势表现为“前新后旧” -> `HEAD`
   - 趋势表现为“前旧后新” -> `TAIL`
3. 若不明显，按最终决策：回退为 `HEAD + LOW_CONFIDENCE`。

## 6.2 为什么可以接受“判不准就 HEAD”

- 真实成熟 Playlist 往往存在稳定排序偏好。
- 即使误判为 `HEAD`，漂移检测会在后续尽快识别并触发手动全量修复。
- 避免引入用户不可理解的 `UNKNOWN` 分支，提高产品可用性。

---

## 7. 定时增量总流程（统一入口）

每轮 `PlaylistSyncer` 对单个订阅执行：

1. 若 `needs_full_resync=1`，直接跳过并记录日志。
2. 读取 `sync_mode`：
   - `HEAD` -> 执行 HEAD 增量算法
   - `TAIL` -> 执行 TAIL 增量算法
3. 成功则更新 `last_incremental_sync_at`。
4. 失败或漂移则设置 `needs_full_resync=1` + `incremental_sync_error`。

---

## 8. HEAD 模式增量算法（固定 2 页）

## 8.1 输入

- `head_anchor_video_id`
- `maxHeadScanPages = 2`

## 8.2 步骤

1. 从第 1 页开始抓（每页 50 条）。
2. 按返回顺序遍历，直到命中 `head_anchor_video_id`：
   - 命中前的项记为“新增候选”。
3. 若第 1 页未命中，继续抓第 2 页。
4. 抓满 2 页仍未命中：
   - 判定可能发生排序漂移/大规模插入
   - 设置 `needs_full_resync=1` 并停止
5. 对新增候选做现有过滤（关键词/时长/live）并入库。
6. 更新锚点（新头部视频 ID）、`known_item_count`、checkpoint。

## 8.3 设计解释

- 3 小时轮询 + 每页 50 条，2 页上限（100 条）对常规更新足够。
- 该限制能显著控制上限配额，也把异常快速转为“人工修复”。

---

## 9. TAIL 模式增量算法（基于总量 + checkpoint）

## 9.1 输入

- `known_item_count`（上次确认总量）
- `tail_anchor_video_id` / `tail_anchor_position`
- checkpoint 映射

## 9.2 步骤

1. 调 `playlists.list(part=contentDetails)` 获取 `currentItemCount`。
2. 计算 `delta = currentItemCount - known_item_count`。
3. 分支处理：
   - `delta == 0`：本轮无新增，结束。
   - `delta < 0`：出现删除/重排风险，设置 `needs_full_resync=1`，结束。
   - `delta > 0`：进入尾部增量抓取。
4. 页定位：
   - `oldLastPage = ceil(known_item_count / 50.0)`
   - `newLastPage = ceil(currentItemCount / 50.0)`
5. 使用 checkpoint 跳到 `oldLastPage`（或最接近且不大于它的页）。
6. 顺序抓到 `newLastPage`，收集 `position >= known_item_count` 的项作为新增候选。
7. 若候选数量与 `delta` 偏差过大（可设阈值，例如 >20% 或绝对差>5）：
   - 标记漂移，设置 `needs_full_resync=1`。
8. 对候选做过滤、入库、关联更新。
9. 更新 `known_item_count=currentItemCount`、尾锚点、checkpoint。

## 9.3 示例（便于理解）

- 上次 `known_item_count=230`（旧最后位置 229）
- 本次 `currentItemCount=238`，`delta=8`
- 则只应新增位置 `230..237` 的 8 条
- 若实际抓到新增候选只有 2 条或 20 条，说明排序已漂移，应要求手动全量

---

## 10. 漂移检测与一键修复

## 10.1 触发漂移的条件

- `HEAD` 两页内找不到 `head_anchor_video_id`
- `TAIL` 出现 `delta < 0`
- `TAIL` 新增候选与 `delta` 严重不匹配
- checkpoint 断裂且无法低成本恢复
- 锚点位置与预期明显冲突

## 10.2 漂移后的系统行为

- `needs_full_resync=1`
- 定时自动增量暂停
- 前端展示告警 + “一键全量同步”按钮

## 10.3 一键全量同步成功后的回收动作

1. 清除 `needs_full_resync`、`incremental_sync_error`
2. 重建 `known_item_count`、`head/tail anchor`
3. 重建 checkpoint（至少覆盖本次扫描访问的所有页）
4. 刷新 `last_full_sync_at`

---

## 11. 接口与服务改造建议

## 11.1 Service 层

建议拆分：

- `refreshPlaylistIncremental(Playlist playlist)`（定时调用）
- `refreshPlaylistFull(Playlist playlist)`（手动按钮调用）

## 11.2 API 层建议

- 保留现有 `POST /api/feed/{type}/refresh/{id}` 语义为手动全量（最直观）
- 不新增“模式切换”接口
- 可选增加内部调试接口（非必需）

---

## 12. 可观测性与排障设计（重点）

## 12.1 每轮必须记录的核心日志字段

- `playlistId`
- `syncMode`
- `syncConfidence`
- `knownItemCount`
- `currentItemCount`
- `delta`
- `headAnchorVideoId`
- `tailAnchorVideoId` / `tailAnchorPosition`
- `scanPages`
- `newCandidateCount`
- `newPersistedCount`
- `needsFullResync`
- `errorReason`

## 12.2 推荐日志阶段

1. `incremental_start`
2. `incremental_mode_decision`
3. `incremental_page_fetch`（每页）
4. `incremental_candidate_filtered`
5. `incremental_done`
6. `incremental_drift_detected`

## 12.3 排障手册（最常见 4 类）

1. **“明明有新节目却没更新”**  
   查 `needs_full_resync` 是否已置 1；若是，要求手动全量。

2. **“TAIL 模式配额突然飙升”**  
   查 checkpoint 是否断裂，是否频繁从第 1 页重建。

3. **“增量总是进入漂移告警”**  
   查该 Playlist 是否经常重排；若是，考虑短期降为手动全量策略。

4. **“新增数量不稳定”**  
   对比 `delta` 与候选数，查看是否被关键词/时长/live 过滤大量剔除。

---

## 13. 前端交互方案

- Feed 详情展示：
  - 同步模式：`HEAD` 或 `TAIL`
  - 状态：正常 / 需手动修复
- 当 `needs_full_resync=1`：
  - 显示告警文案：“检测到 Playlist 排序可能变化，自动增量已暂停。”
  - 提供按钮：“一键全量同步”
- 不提供手动选择模式开关，减少用户认知负担。

---

## 14. 实施顺序（建议）

## 阶段 A（先上线）

1. 新增字段与迁移（`sync_mode`、anchors、`needs_full_resync` 等）
2. 实现 `HEAD` 增量（2 页）+ 漂移检测 + 一键全量修复
3. 前端告警与修复按钮

## 阶段 B（增强）

1. 引入 `playlist_sync_checkpoint`
2. 完整实现 `TAIL` 增量（总量驱动 + token 跳转）
3. 补齐指标与排障面板

---

## 15. 验收标准

功能验收：

- `HEAD` 正常列表可持续增量，无需全量。
- `TAIL` 稳定列表可通过总量+尾部抓取增量。
- 排序突变后能在 1~2 轮内触发漂移告警。
- 手动全量后状态恢复正常，后续继续增量。

配额验收：

- 与当前“每轮全量”相比，常规 Playlist 的日均请求数显著下降。

可维护性验收：

- 通过日志可还原每轮同步决策路径。
- 线上出现漏更时，能明确归因到“过滤规则”或“排序漂移”或“token 断裂”。

---

## 16. 结论

该最终方案在“准确性、配额、可排障性”之间取得平衡：

- 用 `HEAD/TAIL` 自动模式覆盖主流场景；
- 用高密度 checkpoint 支撑 `TAIL` 可用性；
- 用漂移告警 + 一键全量修复应对排序变化；
- 保持用户操作简单：平时自动、异常手动一键修复。
