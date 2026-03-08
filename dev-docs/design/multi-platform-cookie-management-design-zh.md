# PigeonPod 多平台 Cookie 管理设计方案

## 1. Requirement Summary

- User request:
  - 将当前仅面向单一平台的 Cookie 管理能力升级为“可为多个不同平台分别配置 Cookie”的能力。
  - 本期只输出开发方案，不写代码。
  - 方案需要覆盖数据存储、功能逻辑、前端交互、迁移兼容与后续平台扩展。
- Requirement type:
  - `enhancement`
- Assumptions:
  - 当前产品仍是单用户、自托管、SQLite 单机部署模型。
  - 当前实际下载能力主要覆盖 `YOUTUBE` 与 `BILIBILI`，后续可能新增 `RUMBLE` 等平台。
  - Cookie 仍然采用“用户自行导出 `cookies.txt`，服务端保存后按需传给 yt-dlp”的产品路线，不引入浏览器直连读取。

## 2. Value Assessment

- User value:
  - 用户可以为不同平台分别维护 Cookie，不再被“单一 Cookie 文件”绑死。
  - Bilibili 412、YouTube 风控、未来 Rumble 登录态等问题可以通过统一能力承接。
  - 设置入口与文案会从“YouTube Cookie”升级为“平台 Cookie”，减少误解。
- Product/business value:
  - 为多平台扩展建立统一基础设施，避免每新增一个平台都在 `system_config` 上继续堆字段。
  - 将 Cookie 能力从“临时补丁”提升为“平台级系统能力”，更符合 PigeonPod 的平台扩展方向。
  - 降低未来接入新平台时的开发成本和前端重复工作。
- Priority suggestion:
  - High

## 3. 当前基线与问题归因

以下结论基于当前代码，而不是旧文档描述：

### 3.1 当前实现事实

1. 当前 Cookie 存储位于 `system_config.cookies_content`，不是 `user` 表。
2. 后端只有一份全局 Cookie 内容：
   - `backend/src/main/resources/db/migration/V32__Add_system_config.sql`
   - `backend/src/main/java/top/asimov/pigeon/model/entity/SystemConfig.java`
   - `backend/src/main/java/top/asimov/pigeon/service/SystemConfigService.java`
3. 下载时会把这份全局 Cookie 写成临时文件，再统一通过 `--cookies` 传给 yt-dlp：
   - `backend/src/main/java/top/asimov/pigeon/service/CookiesService.java`
   - `backend/src/main/java/top/asimov/pigeon/handler/DownloadHandler.java`
4. 前端设置页和多语言文案明确把它表述为 “YouTube Cookies”：
   - `frontend/src/pages/Setting/index.jsx`
   - `frontend/src/locales/*.json`
5. `yt-dlp` 自定义参数白名单明确屏蔽了 `--cookies` / `--cookiefile`，说明项目已经决定“Cookie 必须由系统托管，而不是由用户通过高级参数绕过”：
   - `backend/src/main/java/top/asimov/pigeon/util/YtDlpArgsValidator.java`

### 3.2 当前问题本质

当前能力并不是“没有 Cookie 支持”，而是“只有一份全局 Cookie，且产品表述和交互全部写死为 YouTube Cookie”。

这在单平台时代勉强可用，但在多平台场景下会立刻暴露出以下问题：

1. 无法同时维护 YouTube 与 Bilibili 的独立 Cookie。
2. 无法表达“某个平台启用 Cookie，另一个平台不启用”。
3. 无法给不同平台展示不同的说明文案、导出指引、风险提示。
4. 新增平台时只能继续给 `system_config` 加字段，扩展性很差。
5. 当前下载逻辑在解析 `FeedContext` 之前就创建 Cookie 临时文件，天然不适合按平台分配 Cookie。

### 3.3 与架构文档的关系

`dev-docs/architecture/architecture-design-zh.md` 中“设置中心包含 Cookies”这一点仍然成立，但第 6 节里仍把 Cookies 归在 `User` 上，这与当前代码不一致。  
本方案以代码现实为准，并建议在后续落地后同步更新架构文档。

## 4. 已确认外部约束

基于 `yt-dlp` 官方文档与 FAQ，可确认以下事实：

1. `--cookies cookies.txt` 是 yt-dlp 的通用能力，不是 YouTube 专属能力。
2. yt-dlp 支持对任意受支持站点传入 Netscape/Mozilla 格式的 Cookie 文件。
3. Cookie 文件仍然是 `cookies.txt` 文本文件，适合延续当前“前端上传文本内容，后端临时落盘”的实现路线。
4. `--cookies-from-browser` 也是通用能力，但它要求服务端能直接读取浏览器配置，不适合当前 PigeonPod 的自托管后端模型。

这意味着：  
“多平台 Cookie 管理”不需要改变 yt-dlp 的调用方式，只需要把“Cookie 选择与注入逻辑”从单一全局模型升级为“按平台解析并注入”。

## 5. 设计目标与非目标

## 5.1 目标

1. 支持为不同平台分别上传、替换、清除 Cookie。
2. 下载时根据 Feed 所属平台自动选择对应 Cookie。
3. 保持现有 `cookies.txt` 上传方式，不改变用户心智。
4. 对未来新增平台保持低改动扩展。
5. 提供向下兼容迁移路径，避免老用户升级后丢失现有 Cookie 配置。

## 5.2 非目标

1. 本期不做浏览器直连导入 Cookie。
2. 本期不做 Cookie 加密存储，继续沿用当前系统级明文存储决策。
3. 本期不做“每个平台多个 Cookie 配置档案”的高级能力。
4. 本期不做按单个 Feed 覆盖平台默认 Cookie 的能力。

## 6. 设计决策

本方案直接采用“新增独立平台 Cookie 配置表”的实现路线，不再比较其他备选方案。

采用该设计的原因：

1. 模型边界清晰，天然面向多平台。
2. 平台增删改查、前端摘要展示、后续迁移治理都更自然。
3. 更符合 `architecture-design-zh.md` 中现有的 `controller/service/mapper` 分层。
4. 新增平台时只需扩展平台注册与前端展示，不需要继续扩 `system_config`。

## 7. 推荐总体方案

核心决策：

1. 新增独立表 `cookie_config`。
2. 当前 UI 从“单一 YouTube Cookie 弹窗”升级为“平台 Cookie 管理区”。
3. 后端新增 `PlatformCookieService`，专门负责平台 Cookie 的存储、校验、摘要返回、临时文件创建与清理。
4. 下载流程基于 `FeedSource` 自动解析平台，再注入对应 Cookie。
5. 老版本中的单一 `cookies_content` 在升级时直接迁移为 `YOUTUBE` 平台 Cookie。
6. 本期 UI 只展示 `YOUTUBE` / `BILIBILI`。
7. 本期前端直接同步切换到新接口，不保留旧 Cookie 接口兼容层。
8. 本期平台选择逻辑使用简单常量分支，避免过度设计。

## 8. 数据模型设计

## 8.1 新增表设计

建议新增：

```sql
CREATE TABLE IF NOT EXISTS cookie_config (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    platform            TEXT                                NOT NULL,
    cookies_content     TEXT                                NULL,
    enabled             INTEGER                             NOT NULL DEFAULT 1,
    source_type         TEXT                                NOT NULL DEFAULT 'UPLOAD',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE(platform)
);
```

字段说明：

| 字段 | 含义 | 说明 |
| --- | --- | --- |
| `platform` | 平台标识 | 如 `YOUTUBE`、`BILIBILI`、`RUMBLE` |
| `cookies_content` | Cookie 原文 | 保持与现有 `cookies.txt` 文本一致 |
| `enabled` | 是否启用 | 支持保留内容但临时停用 |
| `source_type` | 来源类型 | 本期固定 `UPLOAD`，为未来扩展保留 |
| `created_at` / `updated_at` | 时间戳 | 用于前端展示“最后更新于” |

### 为什么本期不设计“每个平台多个 Cookie 档案”

因为当前系统是单用户模型，下载流程只需要“给某个平台找一份当前可用 Cookie”。  
本期若引入多档案、多优先级、多选择器，会显著增加 UI 与调度复杂度，但实际收益有限。

## 8.2 新增后端枚举

建议新增：

- `CookiePlatform` 枚举

建议平台枚举值：

- `YOUTUBE`
- `BILIBILI`
- `RUMBLE`

本期前端只展示：

- `YOUTUBE`
- `BILIBILI`

`RUMBLE` 作为未来扩展预留即可，本期不需要在 UI 中展示，也不要求本期实现对应的前端交互。

## 8.3 `system_config` 的处理建议

`system_config.cookies_content` 不再作为长期主数据源。

建议策略：

1. 新版本上线时把旧数据迁移到 `cookie_config(platform='YOUTUBE')`。
2. 业务代码停止读写 `system_config.cookies_content`。
3. `system_config` 中该字段暂不删除，保留一个版本作为回滚缓冲。
4. 等新方案稳定后，再单独加 migration 删除该字段。

这样比“本次就删列”更稳妥。

## 9. 后端功能设计

## 9.1 新增模块划分

建议新增：

- `entity/PlatformCookieConfig`
- `mapper/CookieConfigMapper`
- `service/PlatformCookieService`
- `model/request/UpsertPlatformCookieRequest`
- `model/response/PlatformCookieSummaryResponse`
- `controller` 中新增 `/api/account/platform-cookies/**`

职责边界：

- `SystemConfigService`
  - 继续负责系统配置。
  - 不再承接平台 Cookie 读写。
- `PlatformCookieService`
  - 负责平台 Cookie 的增删改查、校验、摘要、临时文件生命周期。
- `DownloadHandler`
  - 只负责“根据 FeedContext 找平台，再向 `PlatformCookieService` 要临时 Cookie 文件”。

## 9.2 平台解析逻辑

本期不建议引入额外的注册中心或解析器模式，直接在下载链路中使用简单常量分支即可。

建议规则：

1. 优先根据 `FeedContext.source` 做常量映射。
2. 当前只处理 `YOUTUBE` / `BILIBILI`。
3. 若无法识别，则不注入 Cookie。

当前映射建议：

| FeedSource | CookiePlatform |
| --- | --- |
| `YOUTUBE` | `YOUTUBE` |
| `BILIBILI` | `BILIBILI` |

实现建议保持类似：

```java
if (FeedSource.YOUTUBE.name().equals(feedContext.source())) {
  return CookiePlatform.YOUTUBE;
}
if (FeedSource.BILIBILI.name().equals(feedContext.source())) {
  return CookiePlatform.BILIBILI;
}
return null;
```

这样能保持代码清晰可读，也符合当前项目规模。

## 9.3 Cookie 选择策略

下载时建议采用以下规则：

1. 只使用与当前平台精确匹配的 Cookie。
2. 找不到对应平台 Cookie 时，不做跨平台回退。
3. 无对应 Cookie 时直接按“无 Cookie”执行下载。

也就是说：

```text
platform-specific only > none
```

这样更符合真实的站点行为：

1. 不同平台接受的 Cookie 域和风控凭据本来就是隔离的。
2. 引入跨平台全局 Cookie 只会增加理解成本与错误预期。
3. 当前已确认老版本 Cookie 语义就是 YouTube Cookie，因此没有必要引入额外的 `GLOBAL` 兼容层。

## 9.4 Cookie 校验策略

本期建议在上传时做基础校验，而不是完全裸存：

### 需要校验

1. 文件非空。
2. 文本长度限制，例如 1 MB 以内。
3. 至少包含 `# HTTP Cookie File` 或 `# Netscape HTTP Cookie File` 头，或允许无头但行结构合法。
4. 平台专属上传时，Cookie 内容中至少出现一个平台匹配域名：
   - `youtube.com`
   - `bilibili.com`
   - `rumble.com`

### 不建议本期校验

1. Cookie 是否已过期。
2. Cookie 是否一定可用于目标平台下载。
3. 平台登录态是否有效。

原因是这些都属于运行期外部环境问题，强校验成本高且容易误判。

## 9.5 下载流程改造点

当前 `DownloadHandler.download()` 中会在解析 `FeedContext` 之前创建临时 Cookie 文件，这是单一全局模型遗留。

建议改造为：

1. 先 `resolveFeedContext(episode)`。
2. 从 `FeedContext.source` 解析 `CookiePlatform`。
3. 调用 `PlatformCookieService.createTempCookieFile(platform, userId)`。
4. 获取到临时文件后再拼装 yt-dlp 命令。
5. `finally` 中统一清理临时文件。

同时建议把临时文件命名改成：

```text
cookies_{platform}_{userId}_{timestamp}.txt
```

便于排查问题。

## 9.6 API 设计

建议新增接口：

### 1. 获取平台 Cookie 摘要

`GET /api/account/platform-cookies`

返回建议：

```json
[
  {
    "platform": "YOUTUBE",
    "displayName": "YouTube",
    "enabled": true,
    "hasCookie": true,
    "updatedAt": "2026-03-08T10:00:00",
    "instructionUrl": "...",
    "domainHints": ["youtube.com"]
  }
]
```

注意：

- 绝不返回 `cookies_content` 原文。

### 2. 上传/替换某个平台 Cookie

`PUT /api/account/platform-cookies/{platform}`

请求体建议：

```json
{
  "cookiesContent": "..."
}
```

语义：

- 有则替换，无则创建。

### 3. 删除某个平台 Cookie

`DELETE /api/account/platform-cookies/{platform}`

### 4. 启用/停用某个平台 Cookie

可选：

`POST /api/account/platform-cookies/{platform}/enabled`

请求体：

```json
{
  "enabled": true
}
```

如果希望本期缩小范围，也可以先不做独立启停接口，而是只保留“上传/删除”。

## 9.7 接口切换策略

本期前端直接同步切换到新接口，不保留旧 Cookie 接口兼容层。

建议策略：

1. 设置页只调用 `/api/account/platform-cookies/**`
2. 后端实现以新接口为准
3. 升级兼容由数据库迁移承接，而不是由双接口并存承接

## 10. 前端交互设计

## 10.1 Setting 页面结构调整

当前单一弹窗：

- `Manage YouTube Cookies`

建议升级为单独的“平台 Cookie 管理”分组，放在设置中心现有系统能力区域中。

本期平台列表固定为：

- `YOUTUBE`
- `BILIBILI`

推荐交互：

1. 设置页展示一个 `Platform Cookies` 区块。
2. 区块内按平台展示卡片或列表项。
3. 每个平台项包含：
   - 平台名称
   - 当前状态：未配置 / 已配置
   - 最后更新时间
   - 支持域名提示
   - 导出说明链接
   - 操作按钮：上传/替换、清除

## 10.2 平台卡片建议字段

每个平台卡片展示：

- 平台图标或首字母徽标
- 平台名称
- 描述文本：
  - YouTube：年龄限制、会员内容、风控场景
  - Bilibili：412 风控、登录态或浏览器验证场景
- 状态 Badge：
  - `Configured`
  - `Not Configured`
- `Upload / Replace`
- `Clear`
- `View instructions`

## 10.3 上传交互建议

沿用当前模式：

1. 用户点击某个平台的 `Upload / Replace`
2. 打开单平台上传弹窗
3. 用户选择 `cookies.txt`
4. 前端读取文本内容
5. 调用 `PUT /api/account/platform-cookies/{platform}`

弹窗内容建议：

- 风险提示
- 平台专属导出说明链接
- 当前状态
- 文件选择器
- 上传按钮
- 清除按钮

## 10.4 国际化调整

当前所有 `manage_youtube_cookies`、`upload_update_youtube_cookies`、`youtube_cookies_file` 一类 key 都需要泛化。

建议新增或改造为：

- `platform_cookies`
- `manage_platform_cookie`
- `cookie_platform_youtube`
- `cookie_platform_bilibili`
- `cookie_platform_rumble`
- `platform_cookie_status_configured`
- `platform_cookie_status_not_configured`
- `platform_cookie_upload_replace`
- `platform_cookie_clear`
- `platform_cookie_instructions`

不建议继续让前端文案中出现写死的 “YouTube Cookie”。

## 11. 数据迁移与升级策略

## 11.1 Flyway 迁移步骤

建议新增 migration：

### `V36__Add_cookie_config.sql`

内容：

1. 创建 `cookie_config` 表。
2. 若 `system_config.cookies_content` 非空，则插入一条：

```sql
platform = 'YOUTUBE'
```

3. 不删除 `system_config.cookies_content` 字段。

## 11.2 运行期兼容策略

上线后兼容期内：

1. 下载优先读取平台 Cookie。
2. 无对应平台 Cookie 时直接按无 Cookie 执行。
3. 不再从 `system_config.cookies_content` 直接读。

说明：

- 迁移 SQL 已经把旧值搬入 `YOUTUBE`，所以业务层不需要双读旧列。

## 11.3 清理旧字段策略

建议分两步：

### 第一步

- 新功能上线。
- 保留 `system_config.cookies_content` 列，但业务不再使用。

### 第二步

- 等版本稳定后，新增 migration 删除该列与相关旧接口痕迹。

这样便于回滚与问题定位。

## 12. 架构适配分析

- Value Alignment:
  - 与 PigeonPod 的多平台订阅方向高度一致。
- Feasibility:
  - 高。yt-dlp 已支持通用 `--cookies`，现有代码也已有临时文件注入模式。
- Architecture Fit:
  - Good。可自然落在现有 `controller/service/mapper` 分层与 Setting 页面结构中。
- Data and Migration Impact:
  - 中等。需要新表和迁移，但不涉及海量历史数据重算。
- API and Contract Impact:
  - 中等。需要新增 API，并同步切换前端调用。
- Security and Compliance:
  - 中等风险。仍是明文存储与临时文件落盘，需要做好权限与日志控制。
- Performance and Cost:
  - 低。每次下载多一次平台解析与一次小表查询，影响可忽略。
- Testability and Operability:
  - 好。平台解析、上传校验、迁移兼容都适合单元与集成测试。

## 13. 安全、性能与运维影响

## 13.1 安全

本方案不会改变“明文存储 Cookie”这一已有产品决策，但建议补强以下细节：

1. 临时 Cookie 文件使用更严格的文件权限。
2. 日志中禁止打印 Cookie 内容。
3. API 只返回 `hasCookie`、`updatedAt` 等摘要，不回传原文。
4. 文档中继续提示用户不要上传主力账号 Cookie。

## 13.2 性能

影响极小：

1. 下载前多一次平台解析。
2. 多一次小表查询。
3. 临时文件大小与现状相同。

## 13.3 运维

运维影响主要体现在：

1. DB 中从 1 份 Cookie 变成多平台多行配置。
2. 排障时需要确认“当前平台是否存在对应 Cookie”。

因此建议日志增加两类摘要信息：

- `resolvedCookiePlatform=BILIBILI`
- `cookieSource=PLATFORM|NONE`

但仍不要记录 Cookie 内容。

## 14. 测试方案

## 14.1 后端单元测试

1. 平台映射逻辑
   - `YOUTUBE -> YOUTUBE`
   - `BILIBILI -> BILIBILI`
   - 未知来源 -> `null`
2. `PlatformCookieService`
   - 上传成功
   - 域名不匹配失败
   - 删除成功
   - 仅命中精确平台
   - 平台缺失时返回无 Cookie

## 14.2 后端集成测试

1. Flyway 执行后，旧 `system_config.cookies_content` 被迁移到 `YOUTUBE`
2. `GET /api/account/platform-cookies` 返回摘要但不返回原文
3. `PUT /api/account/platform-cookies/BILIBILI` 后，下载流程能选中 Bilibili Cookie
4. 前端仅使用新接口即可完成上传、清除、状态刷新

## 14.3 前端测试

1. 平台列表正确展示状态
2. 上传后状态刷新
3. 清除后状态刷新
4. 多语言文案不再写死 YouTube

## 15. 交付计划

## 15.1 MVP

1. 新增 `cookie_config` 表
2. 新增 `CookiePlatform` 与 `PlatformCookieService`
3. 下载流程改为按平台选择 Cookie
4. 新增平台 Cookie API
5. 设置页改为多平台 Cookie 管理
6. 支持 `YOUTUBE`、`BILIBILI`
7. 升级时把旧 `cookies_content` 自动迁移到 `YOUTUBE`

## 15.2 Next

1. 支持平台 Cookie 启用/停用而不删除内容
2. 提供更清晰的上传校验报错文案
3. 在 Dashboard 或下载错误提示中暴露“当前未配置对应平台 Cookie”的引导

## 15.3 Later

1. 多 Cookie 档案 / Profile
2. 按 Feed 覆盖平台默认 Cookie
3. Cookie 使用统计与诊断
4. 新平台接入模板化

## 15.4 Estimated Complexity

- `L`

原因：

- 涉及 DB migration、后端服务拆分、下载主链路调整、前端设置交互重构和兼容策略。

## 16. 风险与缓解

## 16.1 风险：旧 Cookie 平台归属不明

缓解：

- 该风险已被需求前提消除：当前老版本 Cookie 语义明确为 YouTube Cookie，因此升级时直接迁移到 `YOUTUBE`。

## 16.2 风险：前后端同时改动导致版本发布不一致

缓解：

- 本期按同一版本同步交付前后端与 migration，不依赖旧接口过渡。

## 16.3 风险：用户上传错误平台的 Cookie

缓解：

- 上传时做域名匹配校验。
- 前端每个平台给出明确的导出说明链接。

## 16.4 风险：后续平台扩展又出现硬编码

缓解：

- 当前只有两个平台，使用简单常量分支是可接受且更清晰的；等平台数量明显增长时再评估是否抽象。

## 17. Decision

- Recommendation:
  - Proceed with constraints
- Reasoning:
  - 该需求价值高、架构适配好、实现路径清晰，且能直接解决 Bilibili 412 这类现实问题。
  - 推荐以“独立平台 Cookie 表 + 平台精确匹配”的方式落地，兼顾长期扩展与当前产品语义一致性。
  - 当前只有 `YOUTUBE` / `BILIBILI` 两个平台，下载时用简单常量分支选择 Cookie，更符合代码清晰可读原则。
