# Media Formats and Quality

This page explains how to choose audio and video settings in PigeonPod without overthinking them.

## What This Page Is For

Use this page when you want to understand:

- what the audio quality setting actually changes
- when to choose audio vs video
- how video quality and encoding affect compatibility, speed, and file size
- when subtitles are worth enabling

This page is practical guidance, not a media engineering reference.

## Prerequisites

Before using this page, you should already know how to edit a feed.

If not, see:

- [Feed Settings Explained](Feed-Settings-Explained)

## 1. Choose Audio or Video First

The most important media choice is not the fine-grained quality value. It is whether the feed should download:

- audio
- or video

### Choose audio when:

- you mainly listen in podcast apps
- you want smaller files
- you want faster downloads and simpler storage

### Choose video when:

- you actually want the full video files
- you care about on-screen content
- you accept larger files and heavier processing

For most users, audio is the default and simplest choice.

## 2. Audio Quality

### What the setting means

PigeonPod downloads audio and converts it to MP3 for stored episodes.

The `Audio quality` value is a quality level, not a fixed bitrate.

Important behavior:

- lower numbers mean better quality and usually larger files
- higher numbers mean stronger compression and usually smaller files
- `0` is best quality
- `10` is lowest quality
- leaving it empty keeps the default behavior

### Why file size may not change much

It is normal for some values to produce similar file sizes.

Why:

- the source audio coming from YouTube already has a practical quality ceiling
- asking for “better” output cannot add detail that does not exist
- moderate changes may not produce visible size differences

You usually see stronger size reduction only at more aggressive compression levels.

### Practical suggestions

| Goal | Suggested setting | What to expect |
| --- | --- | --- |
| Keep best quality | empty or `0` | largest files, least compression |
| Reduce size a bit | `6` or `7` | moderate compression |
| Minimize storage | `9` or `10` | strong compression, better for speech than music |

### Simple rule

If you are unsure:

- leave it empty first
- check real file sizes
- only tune it if storage actually becomes a problem

## 3. Video Quality

### What the setting means

`Video quality` controls the preferred output quality when a feed downloads video.

Important behavior:

- leaving it blank asks for the best available quality
- higher quality usually means larger files and slower processing

### Practical guidance

Use higher quality when:

- the visual detail matters
- you are comfortable with larger files

Use moderate quality when:

- you want a balance between clarity and storage use
- the videos are long

If you do not have a strong reason, avoid chasing the maximum quality on every feed.

## 4. Video Encoding

This is the most misunderstood video setting.

### Default

Default means PigeonPod keeps the source encoding behavior as much as possible.

Practical effect:

- fastest path
- least extra processing
- best when the source codec already works on your devices

Tradeoff:

- compatibility can be worse on older devices

### H.264

Choose H.264 when:

- you want the best playback compatibility
- you care more about “it plays everywhere” than about processing speed

Tradeoff:

- PigeonPod may need to re-encode the video
- re-encoding costs time and CPU

### H.265 / HEVC

Choose H.265 when:

- you want better compression efficiency
- your playback devices are modern enough

Tradeoff:

- compatibility is weaker than H.264
- re-encoding is often required
- processing can take significantly longer

## 5. Re-encoding Cost

If you choose H.264 or H.265 and the source video uses another codec, PigeonPod may need to re-encode it.

What that means in practice:

- higher CPU usage
- slower completion
- more noticeable cost on long or high-resolution videos

This is expected behavior, not necessarily a bug.

## 6. Best Practical Default for Video Users

If you want a safe balance for video feeds:

- start with `1080p`
- use `H.264`

Why this is a strong default:

- it usually gives good compatibility
- it avoids extreme storage growth
- it is less likely to surprise you later during playback

If default encoding already works well on all your target devices, you may not need to force H.264.

## 7. File Size vs Compatibility

Use this mental model:

- best compatibility usually costs more processing
- smallest files usually cost more compression
- fastest downloads usually come from leaving more things at default

You rarely get all three at once:

- smallest files
- fastest processing
- maximum compatibility

Pick the tradeoff that matches how you actually use the feed.

## 8. Subtitles

Subtitles are optional and should be enabled only when they add value for your workflow.

Use them when:

- you want transcripts or on-screen subtitle files
- you rely on subtitle-aware players or workflows
- accessibility matters for the feed

Important behavior:

- requested languages may fall back to auto-generated subtitles
- subtitle availability depends on the source video
- feed-level subtitle settings can override global defaults

### Subtitle format

- `VTT`: strong default for web use
- `SRT`: broader compatibility with many external tools and players

If you do not have a specific reason, keep subtitle usage simple.

## 9. Recommended Starting Profiles

### Spoken-word podcast style

- download type: audio
- audio quality: empty, `8`, `9`, or `10` depending on storage needs
- subtitles: optional

### Music or quality-sensitive listening

- download type: audio
- audio quality: empty to `4`

### General-purpose video archive

- download type: video
- video quality: moderate to high
- video encoding: default or `H.264`

### Maximum playback compatibility

- download type: video
- video quality: `1080p`
- video encoding: `H.264`

## Common Mistakes

### “I changed audio quality but file sizes barely moved”

That can be normal. Source audio quality may already be the real limiting factor.

### “I forced H.264 and downloads became slow”

That usually means re-encoding is happening.

### “I chose H.265 but some devices cannot play the file”

That is a compatibility tradeoff, not necessarily a failed download.

### “I enabled too many media options at once and now I cannot tell what helped”

Change one variable at a time:

- audio quality
- video quality
- encoding
- subtitles

This makes troubleshooting much easier.

## Related Pages

- [Feed Settings Explained](Feed-Settings-Explained)
- [Daily Usage](Daily-Usage)
- [Troubleshooting](Troubleshooting)
