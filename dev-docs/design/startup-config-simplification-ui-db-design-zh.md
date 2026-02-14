# PigeonPod 启动配置简化与 UI/数据库承接设计

## 1. 背景

当前 PigeonPod 在部署时需要通过 `application.yml` / environment 配置多项系统参数（`base-url`、存储模式与 S3 参数等）。

这带来的问题：

- 首次安装门槛偏高（尤其是非技术用户）。
- 配置修改需要改文件并重启，不够直观。
- 已有 Web 设置中心，但系统级配置仍分散在启动参数与代码 `@Value` 中。

本方案目标是在保持现有能力不回归的前提下，把“运行期可配置项”迁移到 **Web UI + SQLite 数据库**，把启动配置收敛到最小集合。

## 2. 已确认输入决策

基于你当前确认，本方案采用以下边界：

1. S3 密钥允许明文入库（不做加密存储）。
2. `base-url` 允许为空运行，不做自动推断；用户复制 RSS 链接时若为空，提示“base-url 为空，请先设置 base-url”。
3. 存储配置（`LOCAL/S3`、S3 连接参数）迁移到 UI + 数据库。
4. 不做“存在已下载数据时禁止切换存储模式”的硬保护，但必须明确提示：切换后旧媒体需手动迁移。
5. 老用户升级兼容：若启动时存在 `PIGEON_BASE_URL`、`PIGEON_AUDIO_FILE_PATH`、`PIGEON_VIDEO_FILE_PATH`、`PIGEON_COVER_FILE_PATH`，首启自动回填到数据库。
6. 将 `user` 表中的系统级配置迁移到 `system_config`：`youtube_api_key`、`cookies_content`、`yt_dlp_args`、`login_captcha_enabled`、`youtube_daily_limit_units`。

## 3. 当前配置现状（代码基线）

### 3.1 目前直接依赖启动配置的关键项

- `pigeon.base-url`
  - 使用点：`AccountService`、`ChannelService`、`PlaylistService`、`RssService`
- `pigeon.storage.*`
  - 使用点：`StorageProperties`、`S3ClientConfig`、`S3StorageService`、`DownloadHandler`、`MediaService`、`EpisodeService`、`CookiesService`
- `pigeon.audio-file-path` / `pigeon.video-file-path` / `pigeon.cover-file-path`
  - 使用点：`DownloadHandler`、`MediaService`

### 3.2 现有设置中心

前端已有 `Setting` 页面，后端已有 `/api/account/**` 设置接口（YouTube API Key、Cookies、默认参数、yt-dlp 参数等），适合扩展“系统配置”能力。

## 4. 目标状态

## 4.1 启动配置最小化

建议“长期最小必需”只保留：

- `SPRING_DATASOURCE_URL`（或默认路径）
- `server.port`（可选）

可选保留：

- `pigeon.yt-dlp.*`（如果用户需要自定义运行时目录）

`base-url` 与媒体存储配置不再要求部署时手填。

## 4.2 运行期配置来源

系统配置优先级：

1. 数据库 `system_config`（主配置源）
2. 启动参数（仅首启初始化时使用）
3. 代码默认值（最后兜底）

## 5. environment -> UI/DB 迁移清单

| 现有环境变量 | 迁移后数据库字段 | UI 维护 | 备注 |
| --- | --- | --- | --- |
| `PIGEON_BASE_URL` | `base_url` | 是 | 升级首启自动回填；运行期可为空 |
| `PIGEON_STORAGE_TYPE` | `storage_type` | 是 | `LOCAL` / `S3` |
| `PIGEON_STORAGE_TEMP_DIR` | `storage_temp_dir` | 是 | 任务临时目录 |
| `PIGEON_AUDIO_FILE_PATH` | `local_audio_path` | 是 | 升级首启自动回填；LOCAL 模式生效 |
| `PIGEON_VIDEO_FILE_PATH` | `local_video_path` | 是 | 升级首启自动回填；LOCAL 模式生效 |
| `PIGEON_COVER_FILE_PATH` | `local_cover_path` | 是 | 升级首启自动回填；LOCAL 模式生效 |
| `PIGEON_STORAGE_S3_ENDPOINT` | `s3_endpoint` | 是 | S3 模式必填 |
| `PIGEON_STORAGE_S3_REGION` | `s3_region` | 是 | R2 推荐 `auto` |
| `PIGEON_STORAGE_S3_BUCKET` | `s3_bucket` | 是 | S3 模式必填 |
| `PIGEON_STORAGE_S3_ACCESS_KEY` | `s3_access_key` | 是 | 明文存储（按你的决策） |
| `PIGEON_STORAGE_S3_SECRET_KEY` | `s3_secret_key` | 是 | 明文存储（按你的决策） |
| `PIGEON_STORAGE_S3_PATH_STYLE_ACCESS` | `s3_path_style_access` | 是 | MinIO 通常 `true` |
| `PIGEON_STORAGE_S3_CONNECT_TIMEOUT_SECONDS` | `s3_connect_timeout_seconds` | 是 | 网络参数 |
| `PIGEON_STORAGE_S3_SOCKET_TIMEOUT_SECONDS` | `s3_socket_timeout_seconds` | 是 | 网络参数 |
| `PIGEON_STORAGE_S3_READ_TIMEOUT_SECONDS` | `s3_read_timeout_seconds` | 是 | 网络参数 |
| `PIGEON_STORAGE_S3_PRESIGN_EXPIRE_HOURS` | `s3_presign_expire_hours` | 是 | 当前统一 72h |

`user` 表既有系统配置字段迁移：

| 旧字段（user） | 迁移后数据库字段（system_config） | 迁移策略 |
| --- | --- | --- |
| `youtube_api_key` | `youtube_api_key` | Flyway SQL 一次性搬迁 |
| `cookies_content` | `cookies_content` | Flyway SQL 一次性搬迁 |
| `yt_dlp_args` | `yt_dlp_args` | Flyway SQL 一次性搬迁 |
| `login_captcha_enabled` | `login_captcha_enabled` | Flyway SQL 一次性搬迁 |
| `youtube_daily_limit_units` | `youtube_daily_limit_units` | Flyway SQL 一次性搬迁 |

不迁移（仍建议启动配置）：

- `SPRING_DATASOURCE_URL`
- `server.port`

## 6. 数据模型设计

新增系统配置表（建议，不混入 `user`）：

```sql
CREATE TABLE IF NOT EXISTS system_config (
  id INTEGER PRIMARY KEY CHECK (id = 0),
  base_url TEXT NULL,

  youtube_api_key TEXT NULL,
  cookies_content TEXT NULL,
  yt_dlp_args TEXT NULL,
  login_captcha_enabled INTEGER NOT NULL DEFAULT 0,
  youtube_daily_limit_units INTEGER NOT NULL DEFAULT 10000,

  storage_type TEXT NOT NULL DEFAULT 'LOCAL',
  storage_temp_dir TEXT NOT NULL DEFAULT '/data/tmp/',

  local_audio_path TEXT NOT NULL DEFAULT '/data/audio/',
  local_video_path TEXT NOT NULL DEFAULT '/data/video/',
  local_cover_path TEXT NOT NULL DEFAULT '/data/cover/',

  s3_endpoint TEXT NULL,
  s3_region TEXT NULL DEFAULT 'us-east-1',
  s3_bucket TEXT NULL,
  s3_access_key TEXT NULL,
  s3_secret_key TEXT NULL,
  s3_path_style_access INTEGER NOT NULL DEFAULT 1,
  s3_connect_timeout_seconds INTEGER NOT NULL DEFAULT 30,
  s3_socket_timeout_seconds INTEGER NOT NULL DEFAULT 1800,
  s3_read_timeout_seconds INTEGER NOT NULL DEFAULT 1800,
  s3_presign_expire_hours INTEGER NOT NULL DEFAULT 72,

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

说明：

- 单行模型（`id=0`）与当前单用户系统定位一致；
- 使用独立 `system_config` 表，避免 `user` 承担“账号 + 系统配置”双重职责；
- 明文密钥存储按当前产品决策执行。

## 7. 后端设计

## 7.1 配置访问抽象

新增：

- `SystemConfig` 实体 / Mapper / Service
- `SystemConfigService`：读取、更新、校验、连接测试
- `AppBaseUrlResolver`：统一解析 base URL
- `StorageConfigResolver`：统一提供当前存储配置快照

## 7.2 base-url 解析策略

目标：`base-url` 可为空运行，但 RSS 链接生成必须依赖显式配置值。

规则：

1. 系统运行时允许 `system_config.base_url` 为空；
2. 复制/生成 RSS 链接时，若 `base_url` 为空，返回业务提示“base-url 为空，请先设置 base-url”；
3. 不做按请求域名自动推断，不提供 `127.0.0.1` 兜底链接。

改造点：

- 移除 `AccountService` / `ChannelService` / `PlaylistService` / `RssService` 的 `@Value("${pigeon.base-url}")`。
- 统一调用 `AppBaseUrlResolver`；若为空由上层接口返回可读错误而非生成错误链接。

## 7.3 存储配置读取策略

采用简单模型（本期唯一方案）：

- UI 可写入数据库；
- 应用启动时加载 DB 配置；
- 存储配置改动后提示“重启后生效”；
- 不做复杂配置热更新与配置变更审计。

## 7.4 接口设计（建议）

建议在 `/api/account` 下扩展：

- `GET /api/account/system-config`
  - 返回当前系统配置（`s3_secret_key` 默认脱敏返回）
- `POST /api/account/system-config`
  - 保存配置（支持全量）
- `POST /api/account/system-config/storage/test`
  - 测试连接（S3：`HeadBucket` + 测试对象 put/delete；LOCAL：目录可写性测试）

## 7.5 校验规则（关键）

- `LOCAL` 模式：本地路径必填且可创建；
- `S3` 模式：`endpoint/region/bucket/accessKey/secretKey` 必填；
- 超时值范围限制（例如 1~7200）；
- `presign-expire-hours` 最小 1，默认 72。

## 8. 前端 UI 设计（Setting 页面）

新增“存储配置”分组：

1. 模式选择：`LOCAL` / `S3`（SegmentedControl）
2. `base-url` 输入（可空，附说明）
3. LOCAL 字段：音频/视频/封面路径、临时目录
4. S3 字段：endpoint、region、bucket、AK/SK、path-style、超时、预签名 TTL
5. “测试连接”按钮
6. 保存按钮

交互提醒：

- 当用户切换模式时，弹二次确认：
  - 不自动迁移历史媒体；
  - 必须手动迁移。
- 保存成功后提示“重启后生效”。
- 用户点击“复制 RSS”且 `base-url` 为空时，提示“base-url 为空，请先设置 base-url”。

## 9. 启动与迁移流程

## 9.1 Flyway 迁移

- 新增 `V32__Add_system_config.sql`（命名可按当前版本顺延）。

## 9.2 首次启动初始化

`SystemConfigBootstrap`（启动 runner）：

1. 若 `system_config` 无记录：按“旧环境变量 + 默认值”写入初始记录；
2. 若存在以下旧环境变量，升级首启自动回填到 `system_config`（仅对应字段为空时回填）：
   - `PIGEON_BASE_URL -> base_url`
   - `PIGEON_AUDIO_FILE_PATH -> local_audio_path`
   - `PIGEON_VIDEO_FILE_PATH -> local_video_path`
   - `PIGEON_COVER_FILE_PATH -> local_cover_path`
3. 若已存在记录：直接使用 DB 值，不覆盖。

这样可以兼容现有用户升级，不强制一次性改部署脚本。

## 9.3 回滚策略

- 若新配置不可用，可在 DB 手工改回旧值；
- 保留“重启生效”模型可简化故障恢复路径；
- 升级初期保留旧环境变量读取仅作 bootstrap 用，避免一次性破坏兼容。

## 9.4 user -> system_config 数据迁移

通过 Flyway 在创建 `system_config` 后执行一次性迁移 SQL：

- 把 `user` 表中的 `youtube_api_key`、`cookies_content`、`yt_dlp_args`、`login_captcha_enabled`、`youtube_daily_limit_units` 拷贝到 `system_config`；
- 迁移完成后，后端仅读写 `system_config` 对应字段；
- `user` 表旧字段可在后续版本删除（建议单独 migration，降低回滚风险）。

## 10. 风险与约束

## 10.1 明文密钥入库风险

按当前决策接受，但需文档明确：

- DB 备份与泄露即凭证泄露；
- 建议部署者控制 SQLite 文件权限。

## 10.2 存储模式切换风险

- 模式切换后旧数据不自动迁移；
- 若用户未迁移就切换，历史媒体将表现为“不可播放/不可下载”。

## 11. 分阶段落地建议

### M1（本期）

- `system_config` 表与后端 CRUD；
- Setting 页新增存储配置 UI；
- `base-url` 改为 DB 读取；为空时禁止复制 RSS；
- 存储配置保存后“重启生效”；
- 首启兼容回填 4 个旧环境变量；
- `user` 系统配置字段迁移到 `system_config`；
- 文档与 README 更新。
