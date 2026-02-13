# PigeonPod Storage Guide (Local / S3)

This guide explains how to configure and use PigeonPod media storage.

## 1. Storage Modes

PigeonPod supports two exclusive storage modes:

- `LOCAL`: media files are saved on local disk paths.
- `S3`: media files are uploaded to an S3-compatible object storage service.

Supported S3-compatible providers include:

- AWS S3
- MinIO
- Cloudflare R2
- Other S3 API compatible services

Important:

- Only one mode can be enabled at a time (`LOCAL` or `S3`).
- Switching modes does not migrate existing media automatically.

## 2. Pros and Cons

| Mode | Pros | Cons | Recommended for |
| --- | --- | --- | --- |
| `LOCAL` | Simple setup, no external dependency, no S3 API cost | Requires local disk capacity, harder to scale, app server handles storage growth | Single-node home/self-hosting |
| `S3` | Scalable storage, easy capacity expansion, better for multi-node/cloud deployment | Requires S3 service and credentials, extra API/network cost, object storage troubleshooting | Production/cloud deployment |

## 3. Migration Warning (Must Read)

When you switch storage mode:

- Existing downloaded media stays in the original storage backend.
- PigeonPod will not move historical files for you.
- You must migrate files manually if you need old media in the new backend.

## 4. Core Configuration

You can configure storage in:

- `application.yml`, or
- environment variables (recommended for Docker)

Common keys:

- `pigeon.storage.type` (`LOCAL` / `S3`)
- `pigeon.storage.temp-dir`
- `pigeon.audio-file-path`
- `pigeon.video-file-path`
- `pigeon.cover-file-path`
- `pigeon.storage.s3.*` (endpoint, bucket, credentials, timeouts, etc.)

## 5. Docker Environment Variables

```bash
PIGEON_STORAGE_TYPE=LOCAL
PIGEON_STORAGE_TEMP_DIR=/data/tmp/
PIGEON_AUDIO_FILE_PATH=/data/audio/
PIGEON_VIDEO_FILE_PATH=/data/video/
PIGEON_COVER_FILE_PATH=/data/cover/

# S3 mode options (used when PIGEON_STORAGE_TYPE=S3)
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

## 6. Local Mode Example

```yaml
pigeon:
  storage:
    type: LOCAL
    temp-dir: /data/tmp/
  audio-file-path: /data/audio/
  video-file-path: /data/video/
  cover-file-path: /data/cover/
```

## 7. S3 Mode Example (Generic)

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

## 8. MinIO Example

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

## 9. Cloudflare R2 Example

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

Notes for R2:

- Use `region=auto`.
- Keep bucket private and use presigned URLs (default behavior in PigeonPod).
- The R2 dashboard upload UI may reject large files around 300 MB; use S3 API for larger uploads.
