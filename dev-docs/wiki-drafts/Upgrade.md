# Upgrade

This page explains how to upgrade a Docker-based PigeonPod deployment safely.

## What This Page Is For

Use this page when you want to:

- move your instance to a newer container image
- reduce upgrade risk
- verify that data, feeds, and downloads still work after an update

This wiki does not cover direct JAR upgrades.

## Prerequisites

Before upgrading, make sure you have:

- a Docker-based PigeonPod deployment
- persistent `/data`
- access to your compose file or deployment manifest
- enough time to verify the instance after recreation

## Upgrade Strategy

For most users, a safe upgrade is:

1. back up data
2. pull the new image
3. recreate the container
4. verify login, feeds, RSS, and one download

If you follow `latest`, your workflow is usually just pull-and-recreate.

If you pin image tags, update the tag in your deployment first, then recreate.

## Before You Upgrade

At minimum, back up:

- the SQLite database under `/data`
- any downloaded media you do not want to risk losing
- your deployment file such as `docker-compose.yml`

Also record:

- your current PigeonPod version
- your storage mode
- any custom proxy or yt-dlp settings

## Basic Compose Upgrade Flow

If you use Docker Compose:

```bash
docker compose pull
docker compose up -d
```

This pulls the current image defined in your compose file and recreates the container if needed.

## If You Pin Image Tags

If your compose file uses a fixed tag instead of `latest`:

1. change the image tag to the target release
2. run:

```bash
docker compose pull
docker compose up -d
```

## What Should Persist Across Upgrade

If `/data` is persistent, you should keep:

- database state
- feeds and episodes
- downloaded media
- cover files
- managed yt-dlp runtime files

Container recreation should not remove these by itself.

## What to Check After Upgrade

After the upgrade, verify these items in order:

1. the container is healthy and stays running
2. you can log in
3. the home page and settings page load normally
4. one existing feed opens correctly
5. RSS links still work
6. one manual download succeeds
7. scheduled sync behavior looks normal

If your instance uses S3 or a proxy, also re-run the built-in tests in settings.

## Database Migrations

PigeonPod uses database migrations during startup.

In normal upgrades, this should happen automatically when the container starts with the existing database.

If startup fails right after an upgrade:

- check container logs first
- do not keep restarting blindly without reading the error

## yt-dlp After Upgrade

Application upgrades and yt-dlp runtime updates are related but not identical.

Important points:

- upgrading the app container does not automatically mean your managed yt-dlp runtime changed in the way you expect
- the managed yt-dlp runtime is stored under `/data` and survives container recreation
- if a site-specific download issue appears after upgrade, check both the app version and the yt-dlp runtime status

## Common Failures

### “The upgrade finished, but the app no longer starts”

Check:

- container logs
- database migration errors
- invalid storage or proxy configuration
- filesystem permission problems on `/data`

### “The app starts, but old media is missing”

Check:

- whether `/data` was really mounted persistently
- whether storage mode changed recently
- whether media files were cleaned up earlier by retention rules

### “Feeds are present, but RSS looks empty after upgrade”

Check:

- whether completed media still exists
- whether old items were already cleaned by `maximumEpisodes`
- whether the Base URL is still correct

### “Downloads started failing after upgrade”

Check:

- proxy configuration
- cookies freshness
- custom yt-dlp arguments
- managed yt-dlp runtime version

Do not assume every post-upgrade download failure is caused by the app version alone.

## Rollback Mindset

If a release causes a real regression in your environment:

- stop making unrelated config changes
- identify whether the problem is app version, yt-dlp runtime, proxy, cookies, or storage
- restore from backup or roll back the container image only if you have enough evidence that the release is the cause

Keep rollback decisions disciplined. Randomly changing multiple variables makes recovery slower.

## Related Pages

- [Installation](Installation)
- [Quick Start](Quick-Start)
- [Configuration Overview](Configuration-Overview)
- [Troubleshooting](Troubleshooting)
