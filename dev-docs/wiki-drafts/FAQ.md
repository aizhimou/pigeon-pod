# FAQ

This page collects short answers to common PigeonPod questions from technical self-hosting users.

## General

### Does PigeonPod support both YouTube and Bilibili?

Yes. PigeonPod can subscribe to YouTube channels, YouTube playlists, and Bilibili sources.

### Is PigeonPod meant for self-hosting?

Yes. The normal usage model is self-hosting on your own machine, server, or home lab environment.

### Does PigeonPod work like a normal podcast feed generator?

Yes, but with an important rule: RSS only includes episodes that are actually downloaded and available.

## Installation and Access

### What is the recommended way to run PigeonPod?

Docker-based deployment is the recommended path.

### Should I disable built-in authentication?

Usually no.

Only disable it if another trusted access layer already protects the instance.

### Why does my RSS link not work correctly outside the browser?

Usually because the Base URL is missing or wrong, or the instance is not reachable from the client using the feed.

## YouTube

### Do I need a YouTube API key?

Yes, if you use YouTube feeds.

Without it, YouTube channel and playlist workflows will not function correctly.

### Do I always need YouTube cookies?

No.

You usually need them only for:

- bot-check failures
- age-restricted content
- members-only or account-protected content

### Why is using a raw channel ID recommended?

Because it is usually:

- more accurate
- less ambiguous
- lighter on YouTube API quota

## Feeds and Downloads

### Why do I see episodes that were never downloaded?

Because PigeonPod stores metadata and downloads separately. A feed can show an episode even if that episode was never auto-downloaded.

### Why is my feed visible but RSS empty?

Because no episode has reached `COMPLETED` yet, or previously completed media was cleaned up.

### What does `auto download limit` really do?

It limits how many newly visible episodes are auto-queued for download. It does not limit how many episode metadata records are saved.

### What does `maximumEpisodes` really do?

It is a retention limit for completed downloaded files. When the completed count exceeds the configured limit, older downloaded media can be cleaned up automatically while metadata remains visible.

### Why didn’t a new video auto-download immediately?

Common reasons:

- auto download is disabled
- the auto-download limit is too low
- the delay window has not passed yet
- the item was filtered out by keywords or duration rules

### Can I still download an episode manually if auto download is off?

Yes.

Disabling auto download stops automatic queueing, but manual download is still available.

## Media and Formats

### Is audio or video the better default?

For most users, audio is the simpler and more storage-friendly default.

### Why didn’t changing audio quality reduce file size much?

Because the source audio quality may already be the real limiting factor. Moderate quality changes do not always produce a noticeable file size difference.

### Why did forcing H.264 make downloads slower?

Because PigeonPod may need to re-encode the video for compatibility.

### Should I use H.265?

Only if you understand the compatibility tradeoff and your playback devices support it well.

## Storage and Operations

### Can I switch from LOCAL storage to S3 later?

Yes, but switching storage does not migrate historical media automatically.

Plan migration separately.

### What must be persistent in Docker?

Your `/data` volume.

That is where database state, media files, cover files, and managed yt-dlp runtime data live.

### Does the managed yt-dlp runtime survive container recreation?

Yes, as long as `/data` is persistent.

## Troubleshooting

### Downloads fail with `Sign in to confirm you're not a bot`. What should I do?

Upload YouTube cookies only if normal public access is failing, then retry one affected download.

### The web UI works, but YouTube sync fails. What should I check first?

Check:

- YouTube API key
- quota state
- source accuracy

### After an upgrade, downloads started failing. Did the release break my setup?

Maybe, but do not assume that first.

Also check:

- cookies freshness
- proxy configuration
- custom yt-dlp arguments
- managed yt-dlp runtime state

### When should I open a GitHub issue?

Open one when:

- the problem is reproducible
- logs show a real app error
- behavior changed after an upgrade
- the documented workflow no longer matches reality

## Related Pages

- [Quick Start](Quick-Start)
- [Troubleshooting](Troubleshooting)
- [Feed Settings Explained](Feed-Settings-Explained)
- [Advanced Customization](Advanced-Customization)
