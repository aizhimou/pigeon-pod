# Storage and Backup

This page explains where PigeonPod stores data, what should be backed up, and what changes are risky.

## What This Page Is For

Use this page when you want to:

- understand what lives under `/data`
- choose between `LOCAL` and `S3`
- back up your instance safely
- avoid losing media or database state during changes

## Prerequisites

Before using this page, you should already have:

- a Docker-based PigeonPod deployment
- a persistent `/data` volume or bind mount

If not, start with [Installation](Installation).

## 1. What PigeonPod Stores

The most important persistent location is:

```text
/data
```

This is where PigeonPod keeps runtime state such as:

- the SQLite database
- downloaded media files
- cover files
- managed yt-dlp runtime files

If `/data` is not persistent, container recreation can wipe important state.

## 2. LOCAL vs S3

PigeonPod supports:

- `LOCAL`
- `S3`

### LOCAL

Use `LOCAL` when:

- you want the simplest setup
- one host is enough
- local disk is acceptable for media storage

Typical local paths include:

- audio path
- video path
- cover path

### S3

Use `S3` when:

- you want object storage instead of local media storage
- your deployment model already uses S3-compatible storage
- you are comfortable managing endpoint, bucket, key, and timeout settings

Typical S3 fields include:

- endpoint
- region
- bucket
- access key
- secret key
- path style access
- timeout settings

## 3. Storage Switching Warning

Changing storage mode is operationally meaningful.

Important rule:

- switching storage mode does **not** migrate old media automatically

That means:

- new tasks may use the new storage mode
- historical files may still remain in the old location
- old episodes can become inaccessible if you switch without a migration plan

If you are changing storage mode:

1. back up first
2. test the target storage
3. plan historical media migration separately
4. only then switch

## 4. What You Should Back Up

At minimum, back up:

- the SQLite database
- downloaded media you do not want to lose
- custom covers if you rely on them
- your deployment file such as `docker-compose.yml`

If you use advanced configuration, also record:

- Base URL
- proxy settings
- storage settings
- any custom yt-dlp arguments

## 5. When to Back Up

Strongly recommended times to back up:

- before upgrading
- before changing storage mode
- before changing host machine or Docker volume layout
- before any manual cleanup that touches `/data`

## 6. Restoring Expectations

If you restore only the database but not the media files, the UI may still know about episodes while the underlying files are missing.

If you restore only media but not the database, feed state and metadata may no longer line up correctly.

The safest restore plan is to treat database and media as one logical system.

## 7. Built-in Tests and Validation

If you use advanced storage settings:

- use the built-in storage test in settings
- verify one real download after saving the configuration

Do not trust configuration syntax alone. Always validate with a real task.

## Common Failures

### “My instance came back, but old media is missing”

Check:

- whether `/data` was actually persistent
- whether storage mode changed
- whether files were cleaned up by retention

### “S3 configuration saved, but downloads still fail”

Check:

- endpoint
- bucket
- credentials
- path-style setting
- timeout values

Then verify with one real download, not only the connection test.

### “I switched storage and old episodes stopped working”

That usually means migration of historical media was skipped.

## Related Pages

- [Configuration Overview](Configuration-Overview)
- [Installation](Installation)
- [Upgrade](Upgrade)
- [Advanced Customization](Advanced-Customization)
