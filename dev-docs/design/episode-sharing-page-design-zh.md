# PigeonPod 单集分享页设计（评审稿）

## 1. 背景与目标

Issue `#112` 提出的核心诉求是：当用户在 PigeonPod 中下载完成某一集后，希望能够把该单集分享给朋友，对方点开链接即可在网页或手机浏览器中直接收听，而不需要先订阅整个 Feed，也不需要登录实例。

本方案的目标是在不引入复杂分享权限模型的前提下，为“已下载单集”提供一个可长期访问的公开分享页，并在已登录的单集列表中提供分享入口。

## 2. 已确认产品决策

以下输入已确认，作为本方案边界：

1. 分享链接永久有效，MVP 不做过期时间、撤销分享、一次性链接。
2. 分享按 `episode` 控制，不做 Feed 级分享控制。
3. 只有 `COMPLETED` 状态且本地/对象存储媒体仍可访问的单集才允许分享。
4. 分享页展示内容固定为：
   - 封面
   - 标题
   - 节目来源 URL
   - 简介（`episode.description`）
   - 播放器
5. 本轮目标是“公开单集页”，不是“复制媒体直链”。

## 3. 非目标

本期不做以下内容：

- 不新增分享访问统计。
- 不新增“分享历史”管理页。
- 不在数据库中记录“某单集是否已分享”的持久状态。
- 不为 `READY` / `FAILED` / `PENDING` / `DOWNLOADING` 单集提供分享入口。
- 不扩展到“分享整个 Feed”或“分享某个播放列表”。

## 4. 现状分析

## 4.1 当前已有能力

- 已支持生成 Feed 订阅链接，但对象是 channel/playlist，不是单集。
- 已下载单集可在前端明细页点击“Save”下载到本地。
- `/media/**` 已是匿名可访问媒体入口，`LOCAL` 模式直接回源本地文件，`S3` 模式返回预签名下载/播放跳转。
- 前端已有复制文本能力，适合复用为分享链接兜底方案。

## 4.2 当前缺失能力

- 没有公开单集页路由。
- 没有匿名可读的单集元数据接口。
- 没有“分享单集”按钮。
- 受登录保护的 `/api/episode/**` 不能直接被分享页复用。

## 4.3 关键约束

1. `LOCAL` 与 `S3` 都要兼容。
2. 分享链接要长期稳定，但 `S3` 预签名链接本身会过期，因此不能把预签名 URL 直接当成分享链接。
3. 现有数据模型里没有分享 token、过期时间、撤销状态字段，因此 MVP 应避免引入数据库迁移。

## 5. 核心设计判断

## 5.1 分享链接不应直接指向媒体文件

如果只是复制 `/media/{episodeId}.m4a` 一类媒体地址，会有几个问题：

- 无法展示标题、封面、简介。
- `S3` 模式下底层依赖预签名 URL，直接分享预签名地址无法满足“长期有效”。
- 用户预期是“分享一个可打开的单集页面”，不是“分享一个裸媒体文件”。

结论：分享对象应为一个稳定的公开页面 URL，而不是媒体直链。

## 5.2 分享可用性由 Episode 当前状态动态决定

本期不做“分享开关持久化”，因此单集是否可分享、分享页是否可访问，统一由当前业务状态决定：

- `download_status = COMPLETED`
- `media_file_path` 非空
- 底层媒体文件或对象仍然存在

这意味着：

- 当历史清理任务把单集从 `COMPLETED` 重置为 `READY` 后，原分享页自动失效。
- 当媒体文件被删除或对象丢失后，分享页自动失效。

这个语义与“只有已下载单集才能分享”的产品决策一致，而且无需引入额外状态表。

## 5.3 MVP 不新增数据库迁移

由于分享页 URL 可直接使用 `episodeId`，并且分享能力由当前 Episode 状态推导，MVP 无需新增：

- `episode.share_enabled`
- `episode.share_token`
- `episode.share_expires_at`
- 独立 `episode_share` 表

这样可以显著降低首版实现复杂度与回归风险。

## 6. 总体方案

## 6.1 路由与链接形态

分享链接固定为：

```text
{baseUrl}/share/episode/{episodeId}
```

示例：

```text
https://demo.example.com/share/episode/dQw4w9WgXcQ
```

说明：

- 链接本身永久稳定。
- 页面加载时再按当前状态拉取单集公开元数据。
- 媒体播放仍通过现有 `/media/**` 能力完成。

## 6.2 方案分层

新增三个最小能力：

1. 后端公开只读接口：返回分享页所需元数据。
2. 前端公开页面：渲染封面、标题、来源 URL、简介、播放器。
3. 已登录页面分享按钮：在合格单集上生成并复制/调用系统分享。

## 6.3 推荐的 MVP 页面流转

1. 登录用户在单集列表中看到 `Share` 按钮。
2. 点击后，前端拼出分享页 URL。
3. 在支持 `navigator.share` 的环境下优先调用系统分享。
4. 否则回退到现有复制逻辑。
5. 被分享者打开 `/share/episode/{episodeId}`。
6. 页面请求公开接口获取元数据。
7. 页面使用稳定媒体入口进行播放。

## 7. 后端设计

## 7.1 新增公开接口

建议新增控制器：

- `GET /api/public/episode/{id}`

接口职责：

- 匿名访问，无登录要求。
- 只返回分享页所需的最小字段。
- 仅允许访问 `COMPLETED` 且媒体仍可用的单集。
- 不暴露管理操作，不返回敏感配置。

建议返回 DTO：

```json
{
  "id": "dQw4w9WgXcQ",
  "title": "Episode title",
  "description": "Episode description",
  "coverUrl": "/media/feed/xxx/cover or episode cover url",
  "sourceUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "mediaUrl": "/media/dQw4w9WgXcQ.m4a",
  "mediaType": "audio/mp4",
  "publishedAt": "2026-03-01T10:00:00",
  "duration": "PT12M34S"
}
```

字段说明：

- `coverUrl` 优先使用 `episode.maxCoverUrl`，其次 `episode.defaultCoverUrl`。
- `sourceUrl` 使用 `FeedSourceUrlBuilder.buildEpisodeUrl(source, episodeId)` 生成。
- `mediaUrl` 返回应用内稳定媒体地址，不直接返回底层 `S3` 预签名 URL。

## 7.2 公开接口的判定规则

公开接口在返回数据前必须校验：

1. Episode 存在。
2. `download_status = COMPLETED`。
3. `media_file_path` 非空。
4. 底层媒体实际可访问：
   - `LOCAL`：文件存在。
   - `S3`：对象 key 有效，后续通过 `/media/**` 生成实时预签名跳转。

不满足时返回：

- `404 Not Found`

采用 `404` 而不是 `403`，可以减少对外暴露内部状态。

## 7.3 分享页数据组装服务

建议新增独立服务，例如：

- `PublicEpisodeService`

职责：

1. 校验单集分享可用性。
2. 查找单集所属 Feed 的 `source`。
3. 生成 `sourceUrl`。
4. 生成分享页播放器使用的稳定 `mediaUrl`。
5. 返回最小化 DTO。

建议不要直接复用登录态 `EpisodeService.episodePage(...)` 或管理接口返回模型，避免把后台管理字段暴露到公开接口。

## 7.4 Feed Source 的解析方式

生成 `sourceUrl` 需要知道该单集来自 `YOUTUBE` 还是 `BILIBILI`。

推荐做法：

1. 优先通过 `episode.channel_id -> channel.source` 获取。
2. 若 `channel_id` 为空，则通过 `playlist_episode -> playlist.source` 获取。

建议新增一条面向公开分享的查询方法，返回：

- `episode` 基础信息
- `feed source`

这样避免在 Service 中散落多次查表和分支拼装。

## 7.5 `mediaUrl` 的设计取舍

`mediaUrl` 建议返回应用内稳定地址，而不是直接返回底层对象存储地址：

- 音频：`/media/{episodeId}.m4a`
- 视频：`/media/{episodeId}.mp4`

原因：

1. `LOCAL` 与 `S3` 统一。
2. 页面链接永久有效，而底层 `S3` 预签名 URL 可由 `/media/**` 按需实时生成。
3. 前端无需感知存储模式。

注意：

- 这里的 `.m4a/.mp4` 只是路由提示后缀，底层仍可由 `MediaService` 按真实文件类型返回内容。

## 8. 前端设计

## 8.1 新增公开页面路由

建议新增独立路由：

- `/share/episode/:episodeId`

推荐放在顶层 route，而不是继续挂在当前登录后业务页面分组下，原因：

1. 语义上它是公开页面，不属于后台管理流。
2. 避免把全局播放器、后台交互状态和轮询逻辑带入公开页。
3. 页面职责更清晰，更适合后续扩展 OG 与 SEO。
4. 已确认不复用当前 `Layout/Header`，而是做独立轻量页面。

## 8.2 新增页面组件

建议新增页面：

- `frontend/src/pages/ShareEpisode/index.jsx`

页面加载逻辑：

1. 从 `route params` 中读取 `episodeId`。
2. 请求 `GET /api/public/episode/{id}`。
3. 成功时渲染页面。
4. `404` 时渲染“该分享内容不可用”。

## 8.3 分享页展示结构

MVP 页面内容建议按以下顺序：

1. 封面
2. 标题
3. 节目来源 URL
4. 简介
5. 播放器

交互规则：

- 标题使用单集标题。
- 来源 URL 以可点击链接形式展示，跳转到原始视频页面。
- 简介直接展示 `episode.description`，不做复杂富文本增强。
- 播放器根据 `mediaType` 决定渲染：
  - `audio/*` -> `<audio controls>`
  - `video/*` -> `<video controls>`

本期建议直接使用原生媒体标签，理由：

- 实现成本最低。
- 移动端浏览器兼容性最好。
- 不需要把现有后台 `GlobalPlayer` 逻辑抽离到公开页。
- 更符合公开分享页“轻量、无后台壳”的定位。

## 8.4 已登录页面分享入口

MVP 先在 Feed 明细页已下载单集动作区增加 `Share` 按钮。

展示条件：

- `episode.downloadStatus === 'COMPLETED'`

点击行为：

1. 根据 `baseUrl` 和 `episodeId` 生成分享页链接。
2. 若浏览器支持 `navigator.share`，优先调用原生分享。
3. 否则回退到现有 `copyToClipboard` + `CopyModal`。

说明：

- `DashboardEpisodes` 的 `COMPLETED` 列表本期明确不加 `Share` 按钮。

## 8.5 `baseUrl` 依赖

因为分享对象是“可被外部访问的绝对 URL”，前端生成分享链接时必须依赖实例的 `baseUrl`。

建议：

1. 继续使用后端统一解析后的 `baseUrl`。
2. 当实例未配置 `baseUrl` 时，分享按钮仍然展示，但点击后直接提示：
   - “请先在系统设置中配置可外网访问的 Base URL”

这样做可以保留功能可发现性，同时避免因为实例域名配置不完整而生成错误分享链接。

这也比生成当前浏览器地址的临时相对链接更稳妥，因为反向代理、端口映射和内外网域名可能并不一致。

## 8.6 Open Graph / Twitter Card 设计

本轮将 Open Graph / Twitter Card 元标签纳入范围，但实现方式需要明确。

### 8.6.1 关键事实

当前项目是 SPA，后端对未知 GET 请求会统一转发到 `index.html`。如果只在 React 路由进入后动态改 `<title>` 和 `<meta>`：

- 浏览器可见效果通常没问题
- 但社交平台抓取器往往拿不到稳定的链接预览

结论：**OG/Twitter Card 不能仅依赖前端路由动态更新，必须由后端为分享页直接输出带动态 meta 的 HTML。**

### 8.6.2 推荐实现

建议把分享页分为两层：

1. `GET /share/episode/{id}`
   - 后端返回带动态 meta 的 HTML 外壳
   - 用于社交抓取器和浏览器首次访问
2. `frontend/src/pages/ShareEpisode/index.jsx`
   - 负责实际数据加载和播放器交互

推荐输出的元标签：

- `og:type=website`
- `og:title`
- `og:description`
- `og:image`
- `og:url`
- `twitter:card=summary_large_image`
- `twitter:title`
- `twitter:description`
- `twitter:image`

字段映射建议：

- `og:title` / `twitter:title`：单集标题
- `og:description` / `twitter:description`：简介摘要
- `og:image` / `twitter:image`：封面图
- `og:url`：稳定分享页 URL

### 8.6.3 对现有架构的影响

这项能力不是“前端顺手补几个 meta”那么简单，但也不需要全站 SSR 改造：

- 只需要为 `/share/episode/{id}` 单独输出动态 HTML
- 其他路由仍然继续使用现有 SPA fallback

两种实现方式的对比：

1. 单独模板文件
   - 优点：职责清晰，分享页 HTML 结构独立，便于以后继续扩展分享页专用 head 内容
   - 缺点：当前项目没有现成服务端模板体系，容易与现有 `index.html` 的脚本和样式入口产生重复维护
   - 缺点：前端构建产物路径若变化，模板同步成本更高
2. 基于现有 `index.html` 做服务端占位符替换
   - 优点：直接复用当前 SPA 壳和 Vite 入口，维护成本最低
   - 优点：和当前 `forward:/index.html` 的交付模式更一致，改动范围更小
   - 优点：不需要额外引入模板引擎
   - 缺点：需要在注入 meta 时仔细处理 HTML 转义和占位符替换逻辑

针对当前项目，**更推荐“基于现有 `index.html` 做服务端占位符替换”**，原因是：

- 当前 `frontend/index.html` 很轻，改造成本低
- 后端已经围绕 SPA fallback 组织路由
- 本项目没有现成模板引擎，引入独立模板体系的收益不高

因此推荐实现形态：

- 为分享页请求新增专用 Controller
- 读取并复用现有 `index.html`
- 对 `title`、`og:*`、`twitter:*` 等占位符做服务端替换
- 保持 `/api/public/episode/{id}` 继续负责公开数据接口

### 8.6.4 摘要生成规则

`og:description` / `twitter:description` 推荐直接使用简介前固定字符数作为摘要。

建议规则：

1. 先对 `episode.description` 做轻量清洗：
   - 去掉 HTML 标签
   - 把连续空白和换行折叠为空格
   - `trim`
2. 取前 **140 个字符** 作为摘要
3. 如果发生截断，尾部补 `...`
4. 如果简介为空，则回退为：
   - `Listen to {title} on PigeonPod`

推荐 140 字的原因：

- 比 100 字更能保留语义
- 比 160 字更稳妥，减少在常见社交预览中的截断概率
- 对英文和中日韩混排内容都比较均衡

## 9. API 与错误语义

## 9.1 接口返回示例

成功：

```json
{
  "code": 200,
  "msg": "ok",
  "data": {
    "id": "dQw4w9WgXcQ",
    "title": "Episode title",
    "description": "Episode description",
    "coverUrl": "https://i.ytimg.com/...",
    "sourceUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "mediaUrl": "/media/dQw4w9WgXcQ.m4a",
    "mediaType": "audio/mp4",
    "publishedAt": "2026-03-01T10:00:00",
    "duration": "PT12M34S"
  }
}
```

失败：

- 单集不存在：`404`
- 单集未下载完成：`404`
- 媒体文件已被清理：`404`

## 9.2 失效语义

因为分享能力不单独持久化，所以分享页失效条件非常明确：

1. 单集状态不再是 `COMPLETED`
2. `media_file_path` 丢失
3. 媒体文件或对象已不存在

这符合“分享仅对已下载单集开放”的要求，也自然与 EpisodeCleaner 保持一致。

对外统一文案：

- `该分享内容不可用`

不区分内部失败原因，降低产品复杂度和状态暴露。

## 10. 数据与迁移影响

MVP 结论：**无数据库迁移**。

原因：

- 分享 URL 直接基于 `episodeId`
- 分享状态不持久化
- 永久有效依赖的是稳定分享页 URL，而不是永久媒体签名

这也是本方案复杂度能保持在 `M` 级别的关键前提。

## 11. 安全与运维影响

## 11.1 安全边界

引入分享页后，匿名访问媒体将从“隐式可用能力”变成“显式产品功能”。

需要明确：

- 分享页是公开访问内容。
- 只要别人拿到 URL，就可以访问该单集。
- 不做访问密码、不做 token 校验、不做过期失效。

因此该功能只适合“愿意公开分享已下载单集”的产品定位。

## 11.2 存储兼容性

- `LOCAL`：分享页播放器直接访问 `/media/**`，应用服务回源本地文件。
- `S3`：分享页播放器访问 `/media/**`，后端即时生成预签名 URL 并跳转到对象存储。

所以：

- 分享页 URL 长期稳定
- 每次播放时的真实媒体地址可以短期有效

两者并不冲突。

## 11.3 流量影响

公开分享页会引入额外匿名播放流量：

- `LOCAL` 模式压力在应用服务器与磁盘 I/O
- `S3` 模式压力主要在对象存储出口流量

MVP 不额外做限流，但应在文档中明确这是公开功能。

## 12. 实现清单

## 12.1 后端

建议新增或修改：

- `controller/PublicEpisodeController.java`
- `service/PublicEpisodeService.java`
- `model/response/PublicEpisodeShareResponse.java`
- `mapper/EpisodeMapper.java` 或新增面向分享场景的 Mapper 查询

可能需要复用：

- `MediaService`
- `FeedSourceUrlBuilder`
- `AppBaseUrlResolver`
- 分享页专用 HTML 模板或动态 HTML 渲染组件

## 12.2 前端

建议新增或修改：

- `frontend/src/pages/ShareEpisode/index.jsx`
- `frontend/src/App.jsx`
- `frontend/src/pages/Feed/index.jsx`
- `frontend/src/locales/*.json`

如果采用后端动态 HTML 外壳方案，还需要新增或调整：

- 分享页 HTML 模板文件
- 分享页元标签注入逻辑

## 13. 测试建议

## 13.1 后端测试

1. `COMPLETED` 音频单集可返回公开 DTO。
2. `COMPLETED` 视频单集可返回公开 DTO。
3. `READY/FAILED/PENDING/DOWNLOADING` 单集返回 `404`。
4. 媒体文件缺失时返回 `404`。
5. channel 单集可正确生成 `sourceUrl`。
6. playlist 单集可正确生成 `sourceUrl`。
7. `S3` 模式下分享页 `mediaUrl` 仍为稳定 `/media/**` 路径。

## 13.2 前端测试

1. 已下载单集显示 `Share` 按钮。
2. 未下载单集不显示 `Share` 按钮。
3. 支持 `navigator.share` 时触发系统分享。
4. 不支持时回退复制弹窗。
5. 分享页可正确展示封面、标题、来源 URL、简介、播放器。
6. 分享页遇到 `404` 时展示失效提示而不是空白页。
7. 分享链接在社交平台抓取时能拿到正确的 `og:*` / `twitter:*` 元信息。

## 14. 风险与取舍

## 14.1 已知取舍

1. 不做数据库状态，意味着无法手动关闭某个已生成的分享链接。
2. 分享页是否有效完全依赖 Episode 当前下载状态和媒体是否仍存在。
3. 若用户开启较激进的历史清理策略，旧分享链接可能自然失效。

## 14.2 为什么这个取舍可接受

这是当前 MVP 的刻意简化：

- 需求核心是“能分享单集”
- 不是“构建完整的外链权限系统”

先把公开页打通，再根据真实使用反馈决定是否引入：

- 分享撤销
- 过期时间
- 手动分享开关
- 分享统计

## 15. 推荐交付范围

本期建议严格控制在以下范围：

1. Feed 明细页新增 `Share` 按钮
2. 新增 `/share/episode/:episodeId` 页面
3. 新增 `/api/public/episode/{id}` 接口
4. 仅支持 `COMPLETED` 单集
5. `/share/episode/{id}` 输出带 OG/Twitter Card 的动态 HTML 外壳
6. 无数据库迁移

## 16. 已确认实现建议

本轮讨论后，以下建议已收敛为默认实现方向：

1. `/share/episode/{id}` 的动态 HTML 外壳采用“基于现有 `index.html` 做服务端占位符替换”的方案。
2. `og:description` / `twitter:description` 采用简介清洗后前 `140` 个字符作为摘要，截断时补 `...`。
