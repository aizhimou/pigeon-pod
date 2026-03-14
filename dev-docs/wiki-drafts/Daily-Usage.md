# Daily Usage

This page explains the normal day-to-day workflow for using PigeonPod after installation.

## What This Page Is For

Use this page when you want to:

- add and manage subscriptions
- refresh feeds and backfill older items
- download, retry, cancel, or delete episodes
- use RSS with a podcast client
- understand where to look when something feels off

## Prerequisites

Before using this page, make sure you already have:

- a running PigeonPod instance
- at least one configured source type you care about
- a YouTube API key if you use YouTube feeds

If not, start with [Quick Start](Quick-Start).

## 1. Add a Feed

From the home page:

1. paste a YouTube channel URL, YouTube playlist URL, Bilibili source URL, or a raw channel ID
2. let PigeonPod detect the source and load a preview
3. review the preview items
4. adjust feed settings if needed
5. create the feed

Practical advice:

- if a YouTube channel URL search is inaccurate, use the raw channel ID
- if you are unsure about settings, start with simple defaults and refine later

Related pages:

- [Find a YouTube Channel ID](Find-a-YouTube-Channel-ID)
- [Feed Settings Explained](Feed-Settings-Explained)

## 2. Review the Feed Page

After creating a feed, open its detail page.

This is where you normally:

- browse episodes
- search by title
- sort newest or oldest
- inspect download status
- trigger manual actions

Common statuses include:

- `READY`
- `PENDING`
- `DOWNLOADING`
- `COMPLETED`
- `FAILED`

Practical rule:

- `READY` usually means metadata exists but the episode has not been queued yet
- `COMPLETED` means the media file is available

## 3. Refresh a Feed

Use refresh when:

- you expect new videos and want to sync now
- you do not want to wait for scheduled sync
- you changed a source and want to confirm the latest items appear

Refresh updates the feed metadata and can trigger new auto-downloads for newly matched items.

If refresh finds new items but they do not download, check:

- auto download settings
- delay settings
- filters
- quota or cookie problems

## 4. Backfill Older History

New subscriptions usually start with recent items first. If you need older content:

- use the history / backfill action for that feed

Use backfill when:

- you want older archive content
- you only received a recent slice during initial setup

Do not assume missing old items mean the feed is broken. Often they simply have not been backfilled yet.

## 5. Download Episodes

There are two normal ways episodes get downloaded:

- automatically, based on the feed settings
- manually, from the feed page or dashboard

Manual download is useful when:

- auto download is disabled
- an episode was outside the auto-download limit
- you only want selected items

Practical rule:

- not every visible episode must be downloaded
- PigeonPod can keep metadata for many items while only storing some media files locally

## 6. Retry, Cancel, and Delete

On the feed page or dashboard, you can manage problem episodes directly.

### Retry

Use retry when:

- the episode is in `FAILED`
- the cause was temporary, such as cookies, proxy, or upstream site conditions

### Cancel

Use cancel when:

- an episode is still pending and you no longer want it queued

### Delete

Use delete when:

- you want to remove stored media and related generated files
- you want to clean up failed or completed items manually

Be careful:

- deleting stored media is different from simply keeping the metadata visible in the feed

## 7. Use the Dashboard

The dashboard is the best place to monitor download activity across all feeds.

Use it when you want to:

- inspect all `PENDING`, `DOWNLOADING`, `COMPLETED`, or `FAILED` episodes
- batch retry failed items
- batch cancel pending items
- batch delete selected groups

This is the most efficient place to manage many feeds at once.

## 8. Use RSS in a Podcast App

PigeonPod is designed to expose podcast-friendly RSS feeds.

Important behavior:

- RSS only includes episodes that are actually downloaded and available
- if nothing is `COMPLETED`, the RSS feed can look empty

Before sharing or using an RSS link in a podcast app:

1. make sure Base URL is set correctly
2. make sure at least one episode is downloaded
3. make sure the client can reach your instance

If RSS looks broken, first check download completion before blaming the RSS feature itself.

## 9. Share a Single Episode

If your version includes episode sharing, you can share a public page for one completed episode.

Use this when:

- you want to send one playable item to another person
- you do not want them to subscribe to the whole feed

Important behavior:

- sharing depends on the episode still being available
- if the media is later cleaned up or no longer downloadable, the share page may stop working

## 10. Adjust Feed Settings Over Time

Most feeds need a little tuning after real-world use.

Common reasons to revisit feed settings:

- too many downloads
- too many missed items
- short clips polluting the feed
- storage growing too fast
- subtitles or format choices not matching your workflow

Do not try to perfect every setting before real usage. It is usually faster to observe one feed for a while and then tune it.

## Verify

Your day-to-day workflow is healthy when:

1. new feeds can be added successfully
2. refresh works
3. backfill works when needed
4. manual downloads succeed
5. the dashboard reflects real queue state
6. RSS exposes completed items correctly

## Common Failures

### “The feed exists, but nothing downloads”

Check:

- auto download enabled
- auto-download limit
- delay settings
- keyword and duration filters
- cookies or quota issues

### “I only see recent items”

Check:

- whether you already ran the history / backfill flow

### “The dashboard shows many failures”

Check:

- cookies
- proxy
- upstream site status
- yt-dlp runtime and arguments

## Related Pages

- [Quick Start](Quick-Start)
- [Feed Settings Explained](Feed-Settings-Explained)
- [Configuration Overview](Configuration-Overview)
- [Troubleshooting](Troubleshooting)
