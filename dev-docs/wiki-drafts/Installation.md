# Installation

This page covers the supported installation path for PigeonPod: Docker-based deployment.

## What This Page Is For

Use this page when you want to:

- install PigeonPod on a server or home lab machine
- understand the required persistent storage
- choose a safe starting deployment pattern
- avoid common setup mistakes before first launch

This wiki does not cover direct JAR deployment.

## Prerequisites

Before you start, make sure you have:

- Docker
- Docker Compose
- a host with persistent disk space
- network access for pulling container images

You should also decide whether you want:

- a local-only instance on your LAN
- or a domain / reverse proxy setup for external access

## Recommended Deployment Pattern

For most users, start with:

- one PigeonPod container
- one persistent Docker volume mounted to `/data`
- built-in auth enabled
- local storage mode

This gives you the lowest-maintenance setup.

## Minimal Compose File

Create a `docker-compose.yml` like this:

```yml
version: '3.9'
services:
  pigeon-pod:
    image: ghcr.io/aizhimou/pigeon-pod:latest
    container_name: pigeon-pod
    restart: unless-stopped
    ports:
      - "8834:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db
      # Optional: disable built-in auth only behind another trusted auth layer
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

Start it with:

```bash
docker compose up -d
```

Then open:

```text
http://localhost:8834
```

Default credentials:

- username: `root`
- password: `Root@123`

## What Gets Stored Under `/data`

Your persistent `/data` volume is important because it holds runtime state such as:

- the SQLite database
- downloaded media
- cover files
- managed yt-dlp runtime files

If `/data` is not persistent, you should expect data loss after container recreation.

## First-Run Recommendations

After the container starts:

1. log in
2. change your credentials if needed
3. set your YouTube API key if you use YouTube feeds
4. review storage settings
5. add one test feed
6. verify one episode can download successfully

Related page:

- [Quick Start](Quick-Start)

## Authentication Guidance

Keep built-in auth enabled unless another trusted access layer already protects the instance.

Safe examples:

- an internal-only deployment on a trusted LAN
- VPN-only access
- an auth proxy in front of PigeonPod

Unsafe example:

- exposing an auth-disabled instance directly to the public Internet

## Local Storage vs S3

For a first installation, prefer `LOCAL` storage unless you already know you need S3-compatible object storage.

Choose `LOCAL` when:

- you want the simplest setup
- you are running one instance on one host
- local disk is acceptable

Choose `S3` when:

- you already use object storage
- you want storage outside the app host
- you understand the extra setup and migration implications

Important:

- switching storage modes later does not migrate old media automatically

Related page:

- [Configuration Overview](Configuration-Overview)

## Reverse Proxy and Domain Notes

If you plan to use RSS from other devices or outside your LAN:

- set a correct Base URL
- make sure your reverse proxy forwards requests correctly
- do not break media and RSS routes during proxying

If you do not need external access yet, start without a reverse proxy and add it later.

## Verify

After installation, verify:

1. the container stays up
2. the web UI opens
3. login succeeds
4. settings pages load
5. one feed can be created
6. one episode can reach `COMPLETED`

## Common Failures

### “The container starts and exits immediately”

Check:

- the image was pulled correctly
- the volume mount is valid
- the database path is writable
- the port is not already in use

### “The UI opens, but nothing persists after restart”

Check:

- whether `/data` is backed by a persistent volume or bind mount

### “I disabled auth and now the instance is exposed”

Fix:

- re-enable built-in auth
- or put a trusted access layer in front of the app before exposing it

## Related Pages

- [Quick Start](Quick-Start)
- [Configuration Overview](Configuration-Overview)
- [Troubleshooting](Troubleshooting)
- [Upgrade](Upgrade)
