# Quick Start

This page gets you from zero to a working PigeonPod instance with the smallest reasonable setup.

## What This Page Is For

Use this guide if you want to:

- deploy PigeonPod quickly
- log in for the first time
- configure the minimum required settings
- add your first subscription
- confirm that RSS and downloads work

## Prerequisites

Before you start, make sure you have:

- Docker and Docker Compose
- a machine with persistent storage
- a YouTube Data API v3 key if you plan to use YouTube subscriptions
- `cookies.txt` only if you need account-protected YouTube content or hit YouTube bot checks

If you only want Bilibili feeds, you can skip the YouTube API key.

## Step 1: Run PigeonPod

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

Start the service:

```bash
docker compose up -d
```

Then open:

```text
http://localhost:8834
```

Default credentials:

- Username: `root`
- Password: `Root@123`

## Step 2: Confirm the App Loaded

After login, confirm that:

- the home page opens normally
- the dashboard loads without a server error
- you can open the user settings page

If the page does not load, go to [Troubleshooting](Troubleshooting).

## Step 3: Configure the Minimum Required Settings

For YouTube feeds, open **User Settings** and set your YouTube API key.

- Guide: [YouTube API Key Setup](YouTube-API-Key-Setup)

You only need YouTube cookies if:

- downloads fail with `Sign in to confirm you're not a bot`
- you need private, age-restricted, or members-only content

- Guide: [YouTube Cookies Setup](YouTube-Cookies-Setup)

## Step 4: Add Your First Feed

From the home page:

1. Paste a YouTube channel URL, YouTube playlist URL, or Bilibili source URL
2. Let PigeonPod detect the source type and load a preview
3. Review the preview items
4. Create the subscription

For YouTube channels, if you only have a channel page and need the raw ID, see [Find a YouTube Channel ID](Find-a-YouTube-Channel-ID).

## Step 5: Check Feed Settings

Before relying on auto-downloads, review the most important feed settings:

- auto download
- delay minutes
- maximum episodes
- keyword filters
- audio or video mode

See [Feed Settings Explained](Feed-Settings-Explained).

## Step 6: Verify RSS and Downloads

Pick one subscription and confirm the basics:

1. Open the feed detail page
2. Trigger a manual download for one item if needed
3. Confirm the episode reaches `COMPLETED`
4. Open the RSS link in your podcast app or browser
5. Confirm playback works from the generated feed

## Common Failures

### The web UI does not open

Check:

- the container is running
- the port mapping is correct
- no other service is already using port `8834`

### Login works but YouTube subscriptions fail

Check:

- your YouTube API key is set correctly
- your API quota is not exhausted
- the source URL is valid

### Downloads fail with bot-check errors

Use cookies only when needed:

- [YouTube Cookies Setup](YouTube-Cookies-Setup)

### RSS is empty

PigeonPod only exposes downloaded items in RSS. If nothing has finished downloading yet, the feed can look empty.

## Related Pages

- [Configuration Overview](Configuration-Overview)
- [Feed Settings Explained](Feed-Settings-Explained)
- [Troubleshooting](Troubleshooting)
- [Advanced Customization](Advanced-Customization)
