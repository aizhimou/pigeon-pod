# Failed Download Auto Retry Guide

PigeonPod includes a built-in automatic retry mechanism for failed downloads. This guide explains how it works, what to expect, and how to verify that it is behaving correctly.

## What This Feature Does

When an episode download fails, PigeonPod does not immediately retry it in a tight loop. Instead, it places the failed task into a scheduled retry flow with exponential backoff.

This improves the success rate for temporary problems such as:

- Remote site rate limits
- Short network interruptions
- Temporary YouTube or BiliBili delivery errors
- yt-dlp format resolution failures that may succeed later

## Current Retry Schedule

PigeonPod currently allows up to **5 automatic retries after the initial failed download**.

That means a single episode can be attempted up to **6 times in total**:

1. Initial download attempt
2. Automatic retry 1 after 30 minutes
3. Automatic retry 2 after 1 hour
4. Automatic retry 3 after 2 hours
5. Automatic retry 4 after 4 hours
6. Automatic retry 5 after 8 hours

If the episode still fails after the fifth automatic retry, PigeonPod stops retrying it automatically and leaves it in the `FAILED` state for manual attention.

## Important Behavior

### Retries are not immediate

PigeonPod does not retry again right after a failure. Each failure schedules the next retry time using exponential backoff.

### The scheduler checks regularly

The download scheduler runs every **30 seconds**. It looks for failed episodes whose `next retry time` has already arrived and submits them again when a download worker is available.

This means:

- An episode is retried only after its backoff delay has elapsed
- The actual retry may happen slightly after the exact scheduled time, depending on worker availability

### Successful downloads stop the retry flow

As soon as a retry succeeds, the episode returns to the normal completed state and no more retry scheduling is applied.

### Manual retry resets the retry state

If you manually retry a failed episode from the UI, PigeonPod resets its retry scheduling state and starts a fresh download attempt.

## What Users Need to Configure

Nothing. This feature is built in and does not require a separate switch.

If an episode is downloaded and fails, the retry policy is applied automatically.

## How to See It in the UI

You can usually observe the flow like this:

1. An episode download fails and appears in the `Failed` list.
2. PigeonPod records the failure and schedules the next retry time.
3. When the scheduled time arrives, the download is automatically retried.
4. If the retry succeeds, the episode leaves the failed state.
5. If all automatic retries are exhausted, the episode remains failed until you intervene manually.

## Best Practices

- Give temporary failures some time before manually retrying. PigeonPod may recover them automatically.
- If the same episode fails repeatedly with the same permanent-looking error, review the feed settings, cookies, or download format settings.
- If many episodes start failing at once, check network access, platform cookie status, and whether the remote site is rate limiting your server.

## Troubleshooting

### Why is a failed episode still sitting there?

The next retry may not be due yet. PigeonPod intentionally waits before trying again.

### Why does it not retry forever?

Because endless retries create noisy background traffic and usually do not help after repeated failures. After the configured retry limit is reached, PigeonPod stops and waits for manual handling.

### Why does the actual retry happen a little later than the scheduled time?

Because the scheduler checks periodically and also depends on available download worker slots. A small delay is normal.

### What happens after the retry limit is exhausted?

The episode stays in `FAILED`, no further automatic retries are scheduled, and the failed-download notification system can include it in a later digest if notifications are configured.

## Summary

PigeonPod's failed-download retry behavior is designed to be conservative and practical:

- No immediate retry loops
- Exponential backoff
- 5 automatic retries
- Automatic recovery for temporary failures
- Clean handoff to manual handling when the failure is persistent
