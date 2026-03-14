# Feed Settings Explained

This page explains what the main per-feed settings actually do in PigeonPod.

## What This Page Is For

Use this page when you want to understand:

- which videos become visible in a feed
- which episodes auto-download
- why old downloaded files may disappear
- how audio, video, subtitles, and filters interact

This page focuses on behavior, not implementation details.

## Prerequisites

You should already know how to:

- create a feed
- open the feed edit dialog
- manually verify episode status on the feed detail page

If not, start with [Quick Start](Quick-Start).

## Mental Model

A feed in PigeonPod controls two separate things:

1. Which source items are considered visible and useful for this feed
2. Which of those visible items should be downloaded automatically

This distinction matters because many episodes can exist in the feed database even if they were never downloaded.

## Auto Download

### `Enable auto download`

When enabled, PigeonPod can automatically queue newly matched episodes for download.

When disabled:

- new episode metadata can still be synced into the feed
- automatic downloads stop
- you can still trigger manual downloads

Use this when you want to browse a feed without letting it download by itself.

### `Auto download limit`

This setting controls how many newly visible episodes are selected for automatic download.

Important behavior:

- it does **not** limit how many episode metadata records are saved
- it only limits how many of the newly matched episodes are auto-queued
- the rest stay in the feed and can be downloaded manually later

Practical examples:

- `3`: auto-download the first 3 newly visible episodes
- `10`: auto-download more aggressively during initial import and future syncs
- empty or non-positive value: PigeonPod falls back to the current default feed setting

If your feed syncs correctly but many items remain in `READY`, this setting is one of the first things to check.

### `Auto download delay (minutes)`

This delays auto-download after the video's publish time.

Behavior:

- `0`: no delay
- positive value: PigeonPod waits until the configured delay window has passed, then promotes the episode to download

This is useful when:

- very fresh uploads often fail or change shortly after publishing
- you want to avoid downloading immediately after a video goes live

If new videos show up but are not downloading yet, this setting may be working exactly as configured.

## Retention

### `Maximum episodes`

This setting controls how many **completed downloaded episodes** PigeonPod keeps for the feed.

Important behavior:

- it only counts episodes in `COMPLETED`
- when the count exceeds the limit, the oldest completed files are cleaned up automatically
- the episode metadata remains in the database
- cleaned items usually return to `READY`, so they remain visible but are no longer stored locally

Practical examples:

- empty: no automatic retention cleanup
- `20`: keep only the newest 20 completed downloads for this feed

Use this carefully. It is a storage retention setting, not a UI pagination setting.

## Filters

Filters decide which items are considered visible for the feed and therefore eligible for auto-download.

### Title include keywords

The title must match at least one configured rule.

Matching syntax:

- `,` means OR
- `+` means AND inside one rule

Example:

```text
raw+full highlights,smackdown+full highlights
```

This means:

- match titles containing both `raw` and `full highlights`
- OR match titles containing both `smackdown` and `full highlights`

### Title exclude keywords

If the title contains any exclude keyword rule, the item is filtered out.

Use this to remove:

- teasers
- shorts
- clips
- duplicate upload patterns

### Description include keywords

This works like title include keywords, but on the description field.

This is usually more useful in expert mode when the source title alone is not enough.

### Description exclude keywords

This works like title exclude keywords, but on the description field.

### Minimum and maximum duration

These let you exclude videos outside your preferred duration range.

Use them to avoid:

- very short clips
- very long livestream archives

If you combine keyword filters and duration filters, a video must still pass the whole filter set to become visible for the feed.

## Download Type

### Audio

Use audio mode when you mainly want podcast-style listening.

This is the default choice for most users.

### Video

Use video mode when you want full video files instead of audio-only output.

This usually consumes more storage and bandwidth.

## Audio Settings

### `Audio quality`

This controls the output quality target for audio downloads.

Important behavior:

- leaving it blank keeps the original quality behavior
- higher numbers mean lower quality

In practice:

- use blank when you want the simplest default
- use a lower number when you care more about quality
- use a higher number when you want smaller files

If you are unsure, start with the default behavior and only tune this after checking actual file sizes.

## Video Settings

### `Video quality`

This controls the preferred video quality.

Important behavior:

- leaving it blank asks for the best available quality
- higher quality usually means larger files and sometimes slower processing

### `Video encoding`

This affects codec preference and compatibility.

Practical guidance:

- leave it blank unless you have a clear compatibility or storage reason
- prefer more compatible settings when files must play across many devices
- prefer more efficient settings only if your playback environment supports them

Video settings are best treated as an advanced tuning area, not something every feed needs to customize.

## Subtitle Settings

### `Subtitle languages`

This chooses which subtitle languages PigeonPod should try to download for the feed.

Important behavior:

- if the feed field is empty, the feed can inherit the global default setting
- if subtitles are explicitly disabled, the feed will not download subtitles even if a global default exists
- if a requested subtitle is unavailable, auto-generated subtitles may be used as fallback

Use explicit feed-level values only when this feed needs behavior different from your global defaults.

### `Subtitle format`

PigeonPod supports common subtitle formats such as:

- `VTT`: good default for web workflows
- `SRT`: broader compatibility with many external tools and players

If you do not have a specific reason, keep your subtitle format strategy simple and consistent.

## Simple Mode vs Expert Mode

Simple mode is better when you only need:

- auto download
- basic duration limits
- basic media settings

Expert mode is useful when you need:

- description filters
- delay tuning
- retention tuning
- subtitle customization
- more aggressive feed-specific control

Do not enable complex filters unless you actually need them. Over-filtered feeds are a common cause of confusion.

## Recommended Defaults for Most Users

If you want a stable baseline:

- enable auto download
- keep auto-download delay at `0` unless fresh uploads are causing problems
- leave `maximumEpisodes` empty unless you need hard retention
- use audio mode first
- keep audio/video quality mostly at default values
- only add keyword filters after you have observed the real feed behavior

## Common Misunderstandings

### “Why do I see episodes that were not downloaded?”

Because feed sync and download are separate. Metadata can be saved even when the episode was not auto-downloaded.

### “Why did an old episode disappear from storage but still show in the UI?”

Because `maximumEpisodes` cleaned up the file but kept the metadata record.

### “Why didn’t a new video auto-download immediately?”

Common reasons:

- auto download is disabled
- the auto-download limit is too low
- the delay window has not passed yet
- the item was excluded by keywords or duration rules

### “Why are subtitles missing for one feed only?”

Because the feed may be overriding the global subtitle defaults, or subtitles were explicitly disabled for that feed.

## Related Pages

- [Quick Start](Quick-Start)
- [Configuration Overview](Configuration-Overview)
- [Media Formats and Quality](Media-Formats-and-Quality)
- [Troubleshooting](Troubleshooting)
