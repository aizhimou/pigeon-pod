# PigeonPod 媒体存储 S3 兼容方案（全局单存储模式）

## 1. 背景与目标

当前 PigeonPod 的媒体资产（节目音视频、字幕、章节、Feed 自定义封面）全部依赖本地文件系统目录，核心流程与 `java.io.File` / `java.nio.file.Files` 强耦合。

本方案目标是在不改变用户核心使用方式的前提下，新增对 S3 协议存储的完整支持，并保持与现有功能一致：

- 保持 `/media/**` 播放/访问能力；
- 保持 `/api/episode/download/local/{id}` 下载到本地能力；
- 保持 RSS enclosure 可用；
- 保持字幕与章节能力可用；
- 保持本地模式可继续工作。

## 2. 已确认产品决策（本方案输入）

以下决策已确认，作为设计边界：

1. 存储模式全局二选一：`LOCAL` 或 `S3`，不做混合存储。
2. `S3` 模式下所有媒体资产都在 S3（节目媒体、字幕、章节、封面），不做本地持久化存储。
3. 允许“运行期临时文件”；上传 S3 成功后必须自动清理本地临时文件。
4. 历史已下载媒体不做自动迁移，用户自行迁移。
5. 切换存储模式时，数据迁移由用户负责。
6. RSS `enclosure.length` 可通过 S3 `HeadObject` 获取。
7. 目标存储范围是“所有兼容 S3 协议的对象存储”，不只 AWS。

## 3. 非目标（本期不做）

- 不实现自动化历史数据迁移工具。
- 不实现多存储后端并存或按 Feed 选择存储后端。
- 不引入独立“媒体资产总表”（`media_asset`）作为 MVP 必选项。
- 不改造前端为直接上传到对象存储。

## 4. 现状问题与改造动机

当前实现存在以下耦合点：

- 下载完成后把本地绝对路径写入 `episode.media_file_path`；
- 媒体/字幕/章节读取通过本地文件路径拼接和目录扫描；
- 删除/清理依赖本地文件删除与空目录清理；
- RSS enclosure 长度通过本地文件大小获取；
- cookies 临时文件目录依赖 `pigeon.audio-file-path`。

结论：即使沿用 `media_file_path` 字段，也必须引入“代码层存储抽象”，否则业务逻辑会大量散落 `if (isS3)` 分支，复杂度和回归风险更高。

## 5. 总体方案

### 5.1 存储架构

新增统一存储抽象层（代码层，不是数据库表）：

- `MediaStorage`（接口）
- `LocalMediaStorage`（本地实现）
- `S3MediaStorage`（S3 协议实现）

业务服务只依赖 `MediaStorage`，不再直接调用 `File` / `Files`。

### 5.2 全局模式选择

- `LOCAL`：完全沿用本地文件逻辑（通过 `LocalMediaStorage` 承载）。
- `S3`：媒体资产最终全部落 S3；本地仅允许任务期临时目录。

模式由配置决定，应用启动后固定，不支持运行中切换。

### 5.3 兼容策略

- 外部 API 路径保持不变：`/media/**`、`/api/episode/download/local/{id}`。
- 前端播放器与现有调用无需改路径。
- `S3` 模式下媒体数据面不经应用服务转发：应用仅做鉴权/签名后 `302` 重定向到对象存储直连 URL。
- RSS enclosure 在 `S3` 模式下直接输出对象存储可访问 URL（预签名 URL）。

## 6. 配置设计

## 6.1 新增配置项

```yaml
pigeon:
  storage:
    type: LOCAL # LOCAL | S3
    temp-dir: /tmp/pigeon-pod # 运行期临时目录（S3 模式必需）
    s3:
      endpoint: http://127.0.0.1:9000
      region: us-east-1
      bucket: pigeon-pod
      access-key: ${PIGEON_S3_ACCESS_KEY:}
      secret-key: ${PIGEON_S3_SECRET_KEY:}
      path-style-access: true
      connect-timeout-seconds: 10
      socket-timeout-seconds: 30
      read-timeout-seconds: 60
      presign-expire-hours: 72
```

说明：

- `endpoint + path-style-access` 用于适配 MinIO/Ceph/COS/OSS(兼容网关) 等 S3 兼容服务；
- AWS 原生 S3 可不填 `endpoint`，使用默认端点；
- 不再要求 `audio-file-path/video-file-path/cover-file-path` 在 `S3` 模式下可用（可保留为 `LOCAL` 模式专用配置）。

## 6.2 配置绑定建议

新增 `@ConfigurationProperties(prefix = "pigeon.storage")`：

- `StorageProperties`
- `StorageS3Properties`

避免在各 Service 中散落 `@Value`。

## 7. 数据模型设计

## 7.1 不新增“媒体资产总表”

基于已确认决策，本期不新增独立文件表。

## 7.2 `episode.media_file_path` 字段语义调整

字段继续复用，但语义按模式区分：

- `LOCAL`：本地绝对路径（保持现状）；
- `S3`：对象 Key（例如 `audio/{channelName}/{safeTitle}-{episodeId}.m4a`）。

注意：代码中任何“把该字段当本地路径直接 `Paths.get(...)`”的逻辑都要迁移到存储抽象层。

## 7.3 本期新增字段（MVP）

为减少 RSS 构建期间 `HeadObject` 调用，本期直接新增：

- `episode.media_size_bytes`
- `episode.media_etag`

使用策略：

- 下载上传成功后，写入 `media_size_bytes` 与 `media_etag`；
- RSS 优先使用 `media_size_bytes` 输出 `enclosure.length`；
- 仅当字段为空时才回退 `HeadObject`。

Flyway 迁移建议：

- `ALTER TABLE episode ADD COLUMN media_size_bytes BIGINT;`
- `ALTER TABLE episode ADD COLUMN media_etag VARCHAR(255);`

## 8. 对象 Key 规范

为保持与 `LOCAL` 模式目录语义一致，S3 模式采用分媒体类型的目录结构（含 `safeTitle` 可读名）：

- 音频主媒体：`audio/{channelName}/{safeTitle}-{episodeId}.{ext}`
- 视频主媒体：`video/{channelName}/{safeTitle}-{episodeId}.{ext}`
- 字幕：`{audio|video}/{channelName}/{safeTitle}-{episodeId}.{lang}.{ext}`
- 章节：`{audio|video}/{channelName}/{safeTitle}-{episodeId}.chapters.json`
- 缩略图：`{audio|video}/{channelName}/{safeTitle}-{episodeId}.thumbnail.{ext}`（可选，供后续扩展）
- Feed 自定义封面：`feeds/{feedId}.{ext}`

规则：

- 使用 `{safeTitle}-{episodeId}`，兼顾可读性和唯一性；
- `safeTitle` 由现有 `MediaFileNameUtil#getSafeTitle` 生成，与本地命名尽量保持一致；
- `channelName` 使用 `MediaFileNameUtil#sanitizeFileName` 规整后参与 key 路径；
- 重试下载默认覆盖同 key；
- 删除 episode 时按主媒体 key 反推 basename 后批量清理（`{basename}.*`）。

### 8.1 `safeTitle` 作为 S3 文件名的可行性评估

结论：`safeTitle` 可满足 S3 key 安全性要求，但不建议“仅用 safeTitle 作为全局唯一 key”。

- 安全性：当前 `sanitizeFileName + getSafeTitle` 已移除路径分隔符、shell 元字符并限制长度，可满足对象 key 安全落库与访问。
- 风险点：`safeTitle` 存在同名冲突风险（同 Feed 下重名、截断后同名、标题后续变化）。
- 设计取舍：采用 `{audio|video}/{channelName}/{safeTitle}-{episodeId}.{ext}`，既保留可读文件名，又由 `episodeId` 保证唯一性和稳定性。

## 9. 核心流程设计

## 9.1 下载流程（S3 模式）

1. 创建任务临时工作目录：`${temp-dir}/jobs/{episodeId}-{timestamp}/`。
2. `yt-dlp` 输出媒体、字幕、info.json 到临时目录。
3. 执行现有字幕清洗与章节生成逻辑（基于临时目录）。
4. 将文件上传到 S3 规范 key：
   - 主媒体 -> `{audio|video}/{channelName}/{safeTitle}-{episodeId}.{ext}`
   - 字幕 -> `{audio|video}/{channelName}/{safeTitle}-{episodeId}.{lang}.{ext}`
   - 章节 -> `{audio|video}/{channelName}/{safeTitle}-{episodeId}.chapters.json`
5. 所有必需上传成功后，写库：
   - `media_file_path = 主媒体 key`
   - `media_type`、`download_status=COMPLETED`
   - `media_size_bytes`、`media_etag`
6. `finally` 清理临时目录与临时 cookies 文件。
7. 任一步骤失败：
   - episode 标记 `FAILED`；
   - 记录错误日志；
   - 对已上传对象做 best-effort 回滚删除（避免脏对象）。

## 9.2 媒体读取流程（`/media/**`）

`S3` 模式下不做后端代理流量读取：

- 后端仅定位对象 key、执行必要鉴权/校验；
- 生成对象存储直连 URL（预签名 URL）；
- 返回 `302` 重定向，客户端直接从对象存储拉流；
- 应用服务不承载媒体字节流转发。

`LOCAL` 模式继续使用本地文件读取响应。

## 9.3 下载到本地（`/api/episode/download/local/{id}`）

- 保持现有接口和响应头行为；
- `S3` 模式下签发带 `attachment` 参数的预签名 URL 并重定向；
- 不把对象落盘，也不通过后端代理下载流量。

## 9.4 RSS 生成

- `enclosure.url` 在 `LOCAL` 模式保持 `/media/{episodeId}.{ext}`；
- `enclosure.url` 在 `S3` 模式输出预签名直连 URL；
- `enclosure.length` 在 `S3` 模式优先使用 `media_size_bytes`，字段为空时通过 `HeadObject(contentLength)` 获取；
- 字幕/章节标签在 `S3` 模式同样输出预签名直连 URL（不走应用流量代理）。

## 9.5 删除与清理

### Episode 删除/清理

- `LOCAL`：沿用现有本地删除逻辑；
- `S3`：基于主媒体 key 去掉扩展名得到对象前缀，删除同前缀关联对象（媒体/字幕/章节/缩略图），不做“空目录删除”逻辑。

### Channel/Playlist 级联删除

- `S3`：复用 episode 维度删除，不再做本地目录存在性判断。

### Feed 封面

- `save/delete/get` 全部走 `MediaStorage`；
- `customCoverExt` 继续复用；
- URL 继续使用 `/media/feed/{feedId}/cover`。

## 10. 代码改造清单（后端）

## 10.1 新增

- `config/StorageProperties.java`
- `config/S3ClientConfig.java`（创建 `S3Client/S3Presigner`）
- `model/enums/StorageType.java`
- `service/storage/S3StorageService.java`
- `util/MediaKeyUtil.java`
- `util/MediaFileNameUtil.java`

## 10.2 重点改造

- `handler/DownloadHandler`：
  - 输出目录改为 `temp-dir`；
  - 下载后上传逻辑；
  - 成功后写 key，不写本地绝对路径；
  - 清理临时目录。
- `service/MediaService`：
  - 覆盖封面上传/读取/删除；
  - 覆盖媒体/字幕/章节读取；
  - 覆盖下载到本地响应构建。
- `controller/MediaController`：
  - `S3` 模式下改为签名后 `302` 重定向，不再返回媒体字节流。
- `service/RssService`：
  - `enclosure.length` 优先使用 `episode.media_size_bytes`，空值回退 `S3StorageService.headObject(...)`；
  - `S3` 模式输出预签名 `enclosure.url`。
- `service/EpisodeService`：
  - 删除/清理逻辑改为基于 `media_file_path` 反推对象前缀后批量删除，移除本地路径扫描依赖。
- `service/ChannelService`、`service/PlaylistService`：
  - 清理空目录逻辑在 `S3` 模式无意义，需抽离。
- `service/CookiesService`：
  - 临时文件目录不再依赖 `audio-file-path`，改用 `storage.temp-dir`。

## 11. S3 兼容实现细节

## 11.1 SDK 与客户端

依赖建议（Maven）：

- `software.amazon.awssdk:s3`

客户端要点：

- 支持 `endpointOverride(URI)`；
- `S3Client` 与 `S3Presigner` 均需支持 `S3Configuration.pathStyleAccessEnabled(true)`；
- 支持静态 AK/SK 或默认凭证链；
- 增加连接超时、读取超时、重试策略配置。

## 11.2 关键 API 使用

- 上传：`putObject`
- 下载：`getObject`
- 元数据：`headObject`（RSS 长度）
- 删除：`deleteObject`（按 key）/ `list + delete`（按 prefix）
- 直连读取：`S3Presigner#presignGetObject`（播放器/下载/RSS 直连 URL）

## 11.3 兼容性边界

- 要求目标对象存储实现标准 S3 Put/Get/Head/Delete/List 语义；
- 若服务端不完全兼容（例如 `HeadObject` 异常行为），需在接入文档注明限制。

## 12. 临时文件与垃圾回收

## 12.1 临时文件范围

允许以下运行期临时文件：

- yt-dlp 输出目录（任务级）；
- cookies 临时文件；
- 章节生成中间文件（info.json）。

## 12.2 清理策略

- 正常路径：任务 `finally` 清理；
- 异常路径：任务 `finally` + 启动时清理过期临时目录；
- 建议新增 `StaleTempCleaner`（启动或定时）清理 `temp-dir` 下超时目录（如 24h）。

## 13. 安全设计

- S3 凭证只从环境变量/配置读取，不落库；
- 日志不打印 `secret-key`；
- 桶策略默认私有；
- `MediaController` 保持现有安全边界，不向外暴露对象存储真实凭证。

## 14. 性能与成本评估

## 14.1 性能影响

- 下载任务新增“上传对象存储”阶段，单任务总耗时上升；
- 媒体读取与下载走对象存储直连，应用带宽压力显著下降；
- RSS 生成在 `S3` 模式仅在 `media_size_bytes` 缺失时才需要额外 `HeadObject` 调用。

## 14.2 成本影响

- 增加对象存储请求费（PUT/GET/HEAD/LIST）与流量费用；
- 相比“每次 RSS 构建都 `HeadObject`”，S3 API 调用次数显著下降。

## 15. 测试方案

## 15.1 单元测试

- `S3StorageService` 的上传/删除/Head/Presign；
- key 生成规则；
- `DownloadHandler` 上传成功与失败回滚逻辑。

## 15.2 集成测试

- 使用 MinIO 作为 S3 兼容服务进行端到端验证；
- 覆盖：
  - 手动下载；
  - 自动下载；
  - 播放 `/media/{episodeId}.m4a|mp4`；
  - 字幕与章节读取；
  - RSS 生成与 enclosure length；
  - 下载到本地。

## 15.3 回归测试（LOCAL）

- `LOCAL` 模式行为与当前版本一致；
- 重点验证删除、清理、封面上传等历史功能不回归。

## 16. 发布与回滚策略

## 16.1 发布步骤

1. 发布包含双实现（`LOCAL` + `S3`）的新版本；
2. 默认配置保持 `LOCAL`；
3. 用户自行迁移后，将配置切换到 `S3` 并重启应用。

## 16.2 回滚策略

- 若 `S3` 模式异常，可切回 `LOCAL` 并重启；
- 由于不做自动迁移，回滚前需确保本地数据可用（用户自行保障）。

## 17. 里程碑拆分

### M1（MVP）

- 完成本地/S3 双模式存储链路；
- 完成 `DownloadHandler/MediaService/RssService/EpisodeService` 主链路改造；
- 完成 MinIO 集成测试；
- 保持全部核心功能可用。

### M2（优化）

- 可选字幕清单缓存（降低对象前缀 list 次数）。

## 18. 待确认关键决策（请审阅）

1. 字幕列表获取策略：MVP 使用“按主媒体 basename 前缀 list”还是“新增 DB 字段缓存字幕清单”？
   - 建议：MVP 先用前缀 list，降低改库复杂度；后续再视性能落缓存字段。

## 19. 参考资料

- AWS SDK for Java 2.x S3 code examples（Put/Get/Head/Presign）
  - https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_s3_code_examples
- AWS SDK Java v2 endpoint override / client config
  - https://github.com/aws/aws-sdk-java-v2/blob/master/docs/LaunchChangelog.md
