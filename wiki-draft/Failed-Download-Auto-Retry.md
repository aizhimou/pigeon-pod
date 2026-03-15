# Failed Download Auto Retry

## What this page is for

This page explains how PigeonPod retries failed downloads, how long each retry waits, and when you should switch to manual intervention.

## Prerequisites

- A feed with download enabled, or a failed episode you started manually
- Access to the feed page or the failed-episodes dashboard

## Steps

### 1. Let the first failure settle

When a download fails, PigeonPod keeps the episode in `FAILED` and schedules the next retry with exponential backoff. It does not retry immediately.

### 2. Wait for the automatic retry window

By default, PigeonPod allows up to 5 automatic retries after the initial failed attempt.

| Attempt | Wait after failure |
| --- | --- |
| Automatic retry 1 | 30 minutes |
| Automatic retry 2 | 1 hour |
| Automatic retry 3 | 2 hours |
| Automatic retry 4 | 4 hours |
| Automatic retry 5 | 8 hours |

That means one episode can be tried up to 6 times in total: 1 initial attempt plus 5 automatic retries.

### 3. Let the scheduler pick it up

The download scheduler checks for due retries every 30 seconds. A retry starts only when:

- the scheduled retry time has arrived
- a download worker slot is available

Because of that, the real retry time can be slightly later than the exact scheduled time.

### 4. Retry manually only when needed

Use manual retry when:

- you already fixed the likely cause, such as cookies, network access, or format settings
- the episode has exhausted the automatic retry policy

Manual retry resets the retry state, clears the previous failure-notification flag, and immediately submits a new download attempt.

## Verify

Check for these signals:

- A newly failed episode stays in `FAILED` until its next retry window is due.
- A successful retry leaves the failed state and returns to the normal completed flow.
- An exhausted episode stays in `FAILED` with no further automatic retry scheduled.

## Common failures

### The episode is still failed and nothing happened

The next retry time may not be due yet, or all download workers may still be busy.

### The same episode keeps failing with the same error

That usually means the problem is not temporary. Check:

- platform cookies
- network or proxy access
- yt-dlp availability
- feed download format settings

### Why does PigeonPod stop retrying?

The built-in policy stops after 5 automatic retries to avoid endless retry loops and background noise.

## Related pages

- [Troubleshooting](Troubleshooting)
- [Failed Download Notifications](Failed-Download-Notifications)
- [Daily Usage](Daily-Usage)
