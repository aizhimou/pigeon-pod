# Advanced Customization

This page covers the advanced knobs that are useful for technical self-hosting users, without turning the wiki into an internal engineering manual.

## What This Page Is For

Use this page when you want to:

- run PigeonPod behind a reverse proxy
- disable built-in auth safely
- use an outbound proxy for YouTube API and yt-dlp
- switch storage strategies intentionally
- tune yt-dlp behavior
- build your own container image from source

This page is for customization and deployment control, not for deep architecture design.

## Prerequisites

Before using this page, you should already have:

- a working Docker-based PigeonPod deployment
- admin access to **User Settings**
- enough comfort with Docker, networking, and logs to validate your changes

If not, start with [Installation](Installation) and [Quick Start](Quick-Start).

## 1. Reverse Proxy and Domain Setup

Running PigeonPod behind a reverse proxy is useful when:

- you want HTTPS
- you want a stable public or private domain
- you want to place access control in front of the app

Important rules:

- set the correct Base URL in PigeonPod
- make sure `/api`, `/media`, and RSS routes remain reachable
- verify media playback still works after proxying

Good use cases:

- private domain on a home server
- internal access behind a VPN
- HTTPS termination at the proxy layer

Common mistake:

- the proxy works for the web UI, but media or RSS links break because the Base URL or forwarded routing is wrong

If you change proxy behavior, always re-test:

1. login
2. one feed page
3. one RSS link
4. one media playback link

## 2. Disabling Built-in Auth

PigeonPod enables built-in authentication by default. This is the correct choice for most users.

Only disable built-in auth if another trusted layer already protects the instance, such as:

- an auth proxy
- a reverse proxy with strong access control
- VPN-only access
- a private network that is already controlled

Example environment variable:

```text
PIGEON_AUTH_ENABLED=false
```

Do this only when you are certain the instance is not directly exposed.

Unsafe pattern:

- auth disabled
- public Internet exposure
- no other access control in front

That is not an advanced setup. It is a security error.

## 3. Outbound Proxy for YouTube API and yt-dlp

PigeonPod supports an outbound proxy for:

- YouTube Data API requests
- yt-dlp download requests

Use this when:

- your network cannot reach upstream services reliably
- you need a different egress region
- your host environment requires a proxy

Supported proxy types:

- HTTP/HTTPS
- SOCKS5

Important Docker note:

- if PigeonPod runs in Docker and your proxy runs on the host, do not use `127.0.0.1`
- use `host.docker.internal` or your host LAN IP

Practical workflow:

1. enable the proxy in settings
2. save the configuration
3. run the built-in proxy tests
4. verify both YouTube API and yt-dlp succeed

If only one test passes, treat that as a partial failure and keep debugging.

## 4. Storage Strategy Changes

PigeonPod supports `LOCAL` and `S3` storage modes.

Switching storage can be a valid advanced move when:

- local disk is no longer the right fit
- you want object storage for operational reasons

Important limitation:

- switching storage mode does **not** migrate old media automatically

That means:

- new tasks may use the new storage mode
- old files may stay where they were unless you migrate them yourself

Safe workflow:

1. back up your data
2. confirm the target storage works with the built-in test
3. plan media migration separately
4. only then switch modes

If you switch casually, old episodes may become inaccessible.

## 5. Custom yt-dlp Arguments

PigeonPod allows custom yt-dlp arguments for advanced cases.

Use this only when:

- you have a specific upstream compatibility problem
- you know what the argument does
- you are prepared to remove it if downloads start failing

Do not use custom yt-dlp arguments as a first-line tuning tool.

Good mindset:

- default first
- small, deliberate override second
- remove the override first when debugging

If downloads become unstable after adding custom arguments, assume the custom arguments are guilty until proven otherwise.

## 6. Managed yt-dlp Runtime

PigeonPod can manage the yt-dlp runtime under `/data`.

This is useful when:

- upstream sites change behavior
- a newer yt-dlp version fixes extraction issues
- you want controlled updates from the UI

Important behavior:

- the managed yt-dlp runtime survives container recreation because it is stored under `/data`
- updating the app image and updating yt-dlp are related but different actions

Practical advice:

- do not update yt-dlp blindly on a stable setup
- update it when you have a real reason, then verify one known-good download

## 7. Building Your Own Container Image

If you want to patch PigeonPod or test your own changes, build your own image from the repository root.

The project already includes a multi-stage [Dockerfile](https://github.com/aizhimou/pigeon-pod/blob/main/Dockerfile) that:

- builds the frontend
- packages the backend
- creates the runtime image

Basic local build:

```bash
docker build -t pigeon-pod:local .
```

Then update your compose file to use:

```text
pigeon-pod:local
```

Use this when:

- you changed source code
- you want to validate a patch before pushing it
- you need a custom image for your own deployment

## 8. Local Development and Deeper Changes

If you need to go beyond deployment customization and actually change behavior, the wiki should stop being your primary source.

For deeper work, go to the repository docs:

- [Architecture Design (English)](https://github.com/aizhimou/pigeon-pod/blob/main/dev-docs/architecture/architecture-design-en.md)
- [Wiki Documentation Plan (Chinese)](https://github.com/aizhimou/pigeon-pod/blob/main/dev-docs/design/github-wiki-documentation-design-zh.md)

Those documents are better suited for:

- architecture understanding
- implementation planning
- extension work
- code-level reasoning

## Common Mistakes

### “I disabled auth because I thought my server was private enough”

If you are not sure, it is not private enough. Keep built-in auth enabled.

### “I changed proxy settings and now only some requests work”

That usually means:

- the proxy config is only partially correct
- YouTube API and yt-dlp are behaving differently through that proxy

Use the built-in tests and debug them separately.

### “I switched storage and old episodes disappeared”

That usually means media migration was skipped. The storage switch itself does not move historical files for you.

### “I added custom yt-dlp arguments and now downloads are worse”

Remove the custom arguments first. Advanced overrides increase the debugging surface.

## Related Pages

- [Configuration Overview](Configuration-Overview)
- [Installation](Installation)
- [Upgrade](Upgrade)
- [Troubleshooting](Troubleshooting)
