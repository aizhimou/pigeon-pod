# Configuration Overview

This page gives you the configuration map for PigeonPod.

## What This Page Is For

Use this page when you want to understand:

- which settings matter most for a normal self-hosted setup
- which settings are required only for specific use cases
- which settings affect sync, downloads, RSS, storage, and networking

This page is intentionally high-level. It tells you what each group is for and when you should care about it.

## Prerequisites

Before using this page, you should already have:

- a running PigeonPod instance
- access to the web UI
- admin access to **User Settings**

If you have not deployed the app yet, start with [Quick Start](Quick-Start).

## Configuration Areas

## 1. Access and Authentication

These settings control how users reach and sign in to your instance.

### Built-in auth

By default, PigeonPod enables built-in authentication.

For most users:

- keep it enabled
- use the default login once
- change credentials after your first login

Only disable built-in auth if the instance is already protected by another trusted layer such as:

- an auth proxy
- VPN access
- reverse proxy access control
- a private network

If you disable built-in auth and expose the instance directly to the public Internet, that is a deployment mistake.

### Base URL

Base URL is used when PigeonPod generates or copies RSS links and other externally shared URLs.

Use it when:

- you access PigeonPod through a domain name
- you want RSS links to work outside your local browser session
- you want public links to point to the correct hostname

Example:

```text
https://podcasts.example.com
```

If this is empty, the app can still run, but copied RSS links may not be usable.

### Login captcha

This is an extra login protection option.

Most self-hosting users can leave it off unless:

- the instance is more exposed
- you want an additional login friction layer

## 2. YouTube Integration

These settings matter if you use YouTube channels or playlists.

### YouTube Data API Key

This is the main requirement for YouTube feed detection and sync.

Without it:

- YouTube channel and playlist workflows will not function correctly

You should configure this early in setup.

Related page:

- [YouTube API Key Setup](YouTube-API-Key-Setup)

### YouTube daily quota limit

PigeonPod can track and enforce a daily quota limit for YouTube API usage.

When the limit is reached:

- auto sync stops for the rest of the day
- sync resumes later when the quota window resets

Use this when:

- you want predictable API usage
- you are close to YouTube quota limits
- you manage many feeds

### Cookies

Cookies are not required for normal public content.

Use YouTube cookies only when needed, for example:

- `Sign in to confirm you're not a bot`
- age-restricted content
- members-only or account-protected content

Do not treat cookies as a default setup step.

Related page:

- [YouTube Cookies Setup](YouTube-Cookies-Setup)

## 3. Bilibili Integration

If you use Bilibili feeds, the main optional advanced setting is cookies.

Use Bilibili cookies when:

- yt-dlp hits browser verification or `412`
- public access is unreliable from your environment

If Bilibili works normally, you can leave cookies unset.

## 4. Feed Defaults

Feed defaults define the starting values used when you create new subscriptions.

These are especially useful if you create many feeds and want consistent behavior.

Important defaults include:

- auto download limit
- auto download delay
- minimum duration
- maximum episodes
- download type
- subtitle languages
- subtitle format

Use feed defaults to reduce repetitive setup, but still adjust individual feeds when one source needs special handling.

Related page:

- [Feed Settings Explained](Feed-Settings-Explained)

## 5. Network Proxy

PigeonPod supports an outbound proxy for:

- YouTube Data API requests
- yt-dlp requests

This is useful when:

- your network blocks or rate-limits YouTube access
- you need region-specific access
- your host environment needs a proxy to reach upstream services reliably

Supported proxy types include:

- HTTP/HTTPS
- SOCKS5

Practical notes:

- if PigeonPod runs in Docker and the proxy runs on the host machine, do not use `127.0.0.1`
- use `host.docker.internal` or the host LAN IP instead
- save the proxy first, then run the built-in proxy tests

If you do not need a proxy, leave it disabled.

## 6. Storage Strategy

PigeonPod supports two storage modes:

- `LOCAL`
- `S3`

### LOCAL

Use local storage when:

- you want the simplest setup
- you store media on the same machine as the app
- you do not need object storage

Main settings:

- local audio path
- local video path
- local cover path

### S3

Use S3-compatible storage when:

- you want external object storage
- you need more flexible cloud deployment
- local disk is not your preferred long-term media store

Main settings:

- endpoint
- region
- bucket
- access key
- secret key
- path style access
- timeout options
- presign expiry

Important limitation:

- switching storage mode does **not** migrate existing media
- if you move from `LOCAL` to `S3` or back, you must migrate old media manually

Use the built-in storage test before saving or switching.

## 7. yt-dlp

yt-dlp is the runtime PigeonPod uses for downloads.

The main advanced areas are:

- custom yt-dlp arguments
- managed yt-dlp runtime version

### Custom yt-dlp arguments

Use this only when you have a specific need and understand the risk.

These arguments can:

- improve compatibility in edge cases
- also break downloads if misused

If downloads start failing after adding custom arguments, remove them first and test again.

### Managed yt-dlp runtime

PigeonPod can manage the yt-dlp runtime under `/data`.

This helps when:

- upstream extraction behavior changes
- a newer yt-dlp version fixes a site-specific problem
- you want in-app control over updates

Do not update yt-dlp blindly on a stable instance unless you actually need the change.

## 8. RSS and External Access

PigeonPod can generate RSS feeds for podcast apps.

For RSS to work well outside your browser:

- set the correct Base URL
- make sure the instance is reachable from your client
- make sure at least one episode is actually downloaded

If RSS looks empty, the problem is often not the RSS feature itself. The real cause is usually that no episode has reached `COMPLETED`.

## 9. What Most Users Actually Need

For a normal setup, the minimum important configuration usually is:

1. YouTube API key, if using YouTube
2. Base URL, if you want usable copied RSS links
3. Storage mode and paths
4. Feed defaults

Everything else is conditional:

- cookies only when needed
- proxy only when needed
- yt-dlp args only when needed
- S3 only when you actually want object storage

## Verify

After configuring your instance, verify these checkpoints:

1. You can log in and open **User Settings**
2. A YouTube or Bilibili feed can be created successfully
3. One episode can be downloaded successfully
4. The feed can produce a usable RSS link
5. If you use proxy or S3, the built-in tests pass

## Common Failures

### “The app runs, but YouTube feeds do not work”

Usually:

- missing or invalid YouTube API key
- daily quota exhausted

### “Downloads fail after I changed advanced settings”

Usually:

- cookies are stale
- proxy is wrong
- custom yt-dlp arguments are unsafe

### “Old media became inaccessible after storage changes”

Usually:

- storage mode changed without migrating old files

## Related Pages

- [Quick Start](Quick-Start)
- [Feed Settings Explained](Feed-Settings-Explained)
- [Troubleshooting](Troubleshooting)
- [Advanced Customization](Advanced-Customization)
