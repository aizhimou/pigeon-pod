# PigeonPod 存储使用说明（Local / S3）

本文说明如何配置和使用 PigeonPod 的媒体存储能力。

## 1. 存储模式

PigeonPod 目前支持两种互斥模式：

- `LOCAL`：媒体文件保存到本地磁盘目录。
- `S3`：媒体文件上传到兼容 S3 协议的对象存储。

S3 兼容服务包括但不限于：

- AWS S3
- MinIO
- Cloudflare R2
- 其他兼容 S3 API 的服务

注意：

- 同一时刻只能启用一种模式（`LOCAL` 或 `S3`）。
- 切换模式不会自动迁移历史媒体数据。

## 2. 各模式优劣

| 模式 | 优点 | 缺点 | 适用场景 |
| --- | --- | --- | --- |
| `LOCAL` | 配置简单，无外部依赖，无对象存储 API 成本 | 占用本机磁盘，容量扩展不便 | 单机自托管、家庭环境 |
| `S3` | 容量扩展方便，适合云部署/多节点，存储与应用解耦 | 需要对象存储服务与凭证，存在 API/网络成本 | 生产环境、云环境 |

## 3. 重要提醒（必须阅读）

切换存储模式时：

- 已下载媒体仍保留在原存储后端；
- 系统不会自动搬迁历史文件；
- 如需在新模式继续访问历史媒体，请手动迁移。

## 4. 关键配置项

可通过以下方式配置：

- `application.yml`
- 环境变量（Docker 场景推荐）

核心配置：

- `pigeon.storage.type`（`LOCAL` / `S3`）
- `pigeon.storage.temp-dir`
- `pigeon.audio-file-path`
- `pigeon.video-file-path`
- `pigeon.cover-file-path`
- `pigeon.storage.s3.*`（endpoint、bucket、凭证、超时等）

## 5. Docker 环境变量

```bash
PIGEON_STORAGE_TYPE=LOCAL
PIGEON_STORAGE_TEMP_DIR=/data/tmp/
PIGEON_AUDIO_FILE_PATH=/data/audio/
PIGEON_VIDEO_FILE_PATH=/data/video/
PIGEON_COVER_FILE_PATH=/data/cover/

# S3 模式参数（当 PIGEON_STORAGE_TYPE=S3 时生效）
PIGEON_STORAGE_S3_ENDPOINT=
PIGEON_STORAGE_S3_REGION=us-east-1
PIGEON_STORAGE_S3_BUCKET=
PIGEON_STORAGE_S3_ACCESS_KEY=
PIGEON_STORAGE_S3_SECRET_KEY=
PIGEON_STORAGE_S3_PATH_STYLE_ACCESS=true
PIGEON_STORAGE_S3_CONNECT_TIMEOUT_SECONDS=30
PIGEON_STORAGE_S3_SOCKET_TIMEOUT_SECONDS=1800
PIGEON_STORAGE_S3_READ_TIMEOUT_SECONDS=1800
PIGEON_STORAGE_S3_PRESIGN_EXPIRE_HOURS=72
```

## 6. Local 模式示例

```yaml
pigeon:
  storage:
    type: LOCAL
    temp-dir: /data/tmp/
  audio-file-path: /data/audio/
  video-file-path: /data/video/
  cover-file-path: /data/cover/
```

## 7. S3 通用示例

```yaml
pigeon:
  storage:
    type: S3
    temp-dir: /data/tmp/
    s3:
      endpoint: https://your-s3-endpoint.example.com
      region: us-east-1
      bucket: your-bucket
      access-key: your-access-key
      secret-key: your-secret-key
      path-style-access: true
      connect-timeout-seconds: 30
      socket-timeout-seconds: 1800
      read-timeout-seconds: 1800
      presign-expire-hours: 72
```

## 8. MinIO 示例

```yaml
pigeon:
  storage:
    type: S3
    s3:
      endpoint: https://minio.example.com
      region: us-east-1
      bucket: pigeon-pod
      access-key: minio-access-key
      secret-key: minio-secret-key
      path-style-access: true
```

## 9. Cloudflare R2 示例

```yaml
pigeon:
  storage:
    type: S3
    s3:
      endpoint: https://<ACCOUNT_ID>.r2.cloudflarestorage.com
      region: auto
      bucket: your-r2-bucket
      access-key: your-r2-access-key
      secret-key: your-r2-secret-key
      path-style-access: true
```

R2 备注：

- 建议使用 `region=auto`。
- 建议桶默认私有，使用预签名 URL 访问（PigeonPod 默认方案）。
- R2 控制台页面上传大文件可能出现约 300MB 限制提示；实际可通过 S3 API 上传更大文件。
