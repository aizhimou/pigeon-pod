# Failed Download Notifications

## What this page is for

This page explains how PigeonPod sends failed-download digests after automatic retries are exhausted, and how to configure email or webhook delivery.

## Prerequisites

- Access to `Settings` -> `Notifications`
- At least one enabled channel: Email or Webhook
- A valid base URL if you want direct feed links in the digest

## Steps

### 1. Open notification settings

Sign in to PigeonPod, open `Settings`, then open `Notifications`.

### 2. Configure Email or Webhook

For Email, fill in:

- `SMTP host`
- `SMTP port`
- `From address`
- `Recipient address`

Optional Email fields:

- `Username`
- `Password`
- `Enable STARTTLS`
- `Enable SSL`

For Webhook, fill in:

- `Webhook URL`

Optional Webhook fields:

- `Custom HTTP headers`
- `Custom JSON body`

Webhook URLs must use `http://` or `https://`.

Custom headers must use one header per line in `Key: Value` format:

```text
Authorization: Bearer your-token
X-Api-Key: your-api-key
```

### 3. Use placeholders if you need a custom webhook template

These placeholders are supported in header values and JSON string values:

- `{title}`
- `{content}`
- `{generatedAt}`
- `{baseUrl}`
- `{total}`

Example custom JSON body:

```json
{
  "title": "{title}",
  "message": "{content}",
  "generatedAt": "{generatedAt}",
  "total": "{total}"
}
```

Only string values are rendered. If you need a number or boolean in JSON, write it directly instead of using a placeholder.

### 4. Save and test the channel

Use `Send test email` or `Send test webhook` before saving the final configuration.

### 5. Wait for the digest cycle

PigeonPod sends a failed-download digest only when an episode:

- is still in `FAILED`
- has already exceeded the automatic retry limit
- has no further automatic retry scheduled
- has not already been marked as notified

The notification job starts about 5 minutes after startup and then runs every 8 hours.

### 6. Understand delivery rules

If both channels are enabled, PigeonPod tries both.

- If at least one channel succeeds, the digest is treated as delivered.
- If all enabled channels fail, PigeonPod will try again in the next digest cycle.
- Manual retry clears the notification state, so the episode can appear again later if it fails all retries again.

## Verify

Check for these signals:

- Test email arrives as an HTML email.
- Test webhook reaches your receiver and includes the expected headers and JSON shape.
- A real digest includes the episode title, feed name, feed URL, retry count, published time, and the latest error summary.

If you leave `Custom JSON body` empty, the real webhook digest uses the built-in default payload, which is a JSON array of failed items:

```json
[
  {
    "title": "Episode title",
    "feedName": "Feed name",
    "feedURL": "https://your-base-url/channel/UCexample",
    "retryNumber": 6,
    "publishedAt": "2026-03-10T08:00",
    "error": "ERROR: unable to download video data: HTTP Error 403: Forbidden"
  }
]
```

## Common failures

### Email test fails

Check:

- SMTP host and port
- authentication username and password
- whether your provider requires an app password
- whether you enabled either STARTTLS or SSL, not both

### Webhook test fails

Check:

- the webhook URL is reachable
- headers use `Key: Value` format
- the custom body is valid JSON
- your receiver accepts `application/json`

### I enabled both channels but only one arrived

That is still considered a successful delivery. PigeonPod suppresses duplicates after one channel succeeds.

### Why do I not get repeated reminders for the same failed episode?

PigeonPod marks the exhausted failure as notified after successful delivery. You will see it again only if a later manual retry fails through the full retry cycle again.

## Related pages

- [Failed Download Auto Retry](Failed-Download-Auto-Retry)
- [Troubleshooting](Troubleshooting)
- [Configuration Overview](Configuration-Overview)
