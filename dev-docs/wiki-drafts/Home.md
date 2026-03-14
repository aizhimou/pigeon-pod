# PigeonPod Wiki

PigeonPod turns YouTube channels, YouTube playlists, and Bilibili subscriptions into podcast-friendly feeds that you can self-host and control.

This wiki is written for technical self-hosting users who want to:

- install PigeonPod quickly
- configure the app correctly
- understand the main feed and download settings
- troubleshoot common failures
- make light customizations when needed

## Start Here

- [Quick Start](Quick-Start): Fastest path from zero to a working instance
- [Troubleshooting](Troubleshooting): Common symptoms and how to fix them
- [Advanced Customization](Advanced-Customization): Reverse proxy, auth, and custom builds

## What PigeonPod Can Do

- Subscribe to YouTube channels and playlists, plus Bilibili sources
- Generate protected RSS feeds for podcast apps
- Download audio or video with configurable quality
- Auto-sync new items and backfill older history
- Support cookies, proxies, subtitles, and chapters
- Let you manage downloads, retries, cleanup, and retention from the web UI

## Who This Wiki Is For

This wiki assumes you are comfortable with at least some of the following:

- Docker and basic container operations
- basic file paths and storage management
- reading logs when something fails
- configuring reverse proxies or private access controls if needed

If you want the shortest possible setup path, start with [Quick Start](Quick-Start).

## Recommended Reading Order

1. [Quick Start](Quick-Start)
2. [Configuration Overview](Configuration-Overview)
3. [Feed Settings Explained](Feed-Settings-Explained)
4. [Troubleshooting](Troubleshooting)

## Important Links

- [Repository](https://github.com/aizhimou/pigeon-pod)
- [Releases](https://github.com/aizhimou/pigeon-pod/releases)
- [Issues](https://github.com/aizhimou/pigeon-pod/issues)
- [Discussions](https://github.com/aizhimou/pigeon-pod/discussions)

## Security Note

PigeonPod enables built-in authentication by default. If you disable it, only do so behind another trusted access layer such as an auth proxy, VPN, or private network. Do not expose an auth-disabled instance directly to the public Internet.
